package com.nexaai.chat.client;

import com.nexaai.chat.config.ChatProperties;
import com.nexaai.chat.security.CallTokenHolder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Relays AI Service's Server-Sent Events to the browser.
 *
 * <p><strong>Why Chat Service is in this path at all.</strong> The browser cannot call AI Service:
 * it is an internal surface, and the only provider credentials in the platform live behind it. The
 * request therefore goes browser → Chat Service → AI Service, and Chat Service sits in the middle
 * for two reasons — it owns the message row, and it is the only component that knows who the
 * user is.
 *
 * <p><strong>Why the JDK HTTP client rather than RestClient.</strong>
 * {@code BodyHandlers.ofLines()} returns a lazy {@link Stream} of lines that blocks per line —
 * exactly the shape Server-Sent Events need. RestClient's streaming story in Spring 7 is still
 * awkward, and forcing a buffering read here would mean implementing a line reader by hand.
 * Nothing in this class depends on that choice beyond doing the obvious thing.
 *
 * <p><strong>No provider name, no model catalogue, no retry.</strong> Those belong to AI Service
 * ({@code docs/RULES.md} §7). A model name passes through untouched because Chat Service stores the
 * name in a conversation and never needs to know which provider answers it.
 */
@Component
public class AiServiceStreamClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceStreamClient.class);

    private final HttpClient httpClient;
    private final ChatProperties properties;
    private final CallTokenHolder callToken;

    public AiServiceStreamClient(ChatProperties properties, CallTokenHolder callToken) {
        this.properties = properties;
        this.callToken = callToken;
        this.httpClient = HttpClient.newBuilder()
                // How long to wait for a connection. Deliberately short: if AI Service cannot be
                // reached at all, the browser should learn that now rather than hold an open
                // connection for the whole emitter timeout.
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Streams one generation to the browser.
     *
     * @param conversationId for correlation and logging. Never forwarded to the provider
     * @param content        the user's new message
     * @param model          the model name, passed through untouched
     */
    public void stream(UUID conversationId, String content, String model, SseEmitter emitter) {
        String token = callToken.get();
        if (token == null) {
            fail(emitter, "UNAUTHENTICATED",
                    "This request could not be authenticated for generation.");
            return;
        }

        ChatProperties.Generation generation = properties.getGeneration();
        URI uri = URI.create(joinUrl(generation.getAiServiceUrl(), generation.getStreamPath()));

        String body = "{\"model\":" + jsonString(model)
                + ",\"message\":" + jsonString(content)
                + ",\"conversationId\":" + jsonString(conversationId.toString()) + "}";

        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                // The caller's own token, forwarded unchanged. AI Service verifies it exactly as
                // it verifies anything else, so the request that reaches a model is attributable
                // to the person who made it rather than to Chat Service asking anonymously.
                .header("Authorization", "Bearer " + token)
                .timeout(generation.getStreamTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            // ofLines() blocks per line, so this returns as soon as the response HEADERS arrive
            // and the body is consumed lazily below. A body handler that buffered the whole
            // response would defeat streaming at the first hop.
            HttpResponse<Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() >= 400) {
                logUpstreamError(conversationId, response.statusCode());
                fail(emitter, response.statusCode() == 503 ? "AI_SERVICE_UNAVAILABLE"
                                : "GENERATION_FAILED",
                        response.statusCode() == 503
                                ? "The AI service is temporarily unavailable. Please try again."
                                : "The message could not be generated.");
                return;
            }

            try (Stream<String> lines = response.body()) {
                relay(lines, emitter);
            }
            emitter.complete();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(emitter, "GENERATION_INTERRUPTED", "Generation was interrupted.");
        } catch (IOException e) {
            // Unreachable, reset, or timed out. Named by TYPE only: the exception may carry the
            // user's message, and this is a log line rather than an archive.
            log.warn("AI Service stream failed for conversation {}: {}",
                    conversationId, e.getClass().getSimpleName());
            fail(emitter, "AI_SERVICE_UNREACHABLE", "The AI service could not be reached.");
        } catch (Exception e) {
            log.error("AI Service stream client failed for conversation {}: {}",
                    conversationId, e.getClass().getSimpleName(), e);
            fail(emitter, "GENERATION_FAILED", "The message could not be generated.");
        }
    }

    /**
     * Re-assembles upstream SSE frames and re-emits them one at a time.
     *
     * <p>An SSE frame is an {@code event:} line and a {@code data:} line belonging to the SAME
     * event. Forwarding them as two separate sends would produce two malformed events and a
     * browser that receives neither, so parsing is unavoidable. What is avoided is
     * <em>interpreting</em>: no value is inspected, renamed, defaulted or reordered, so there
     * remains exactly one implementation of the event contract and it lives in AI Service.
     *
     * <p>Each frame is flushed as soon as it is complete. Buffering until the upstream stream
     * ended would produce correct final text and no progressive rendering at all — a feature that
     * passes a test which only reads the whole body and does nothing for a real user.
     */
    private void relay(Stream<String> lines, SseEmitter emitter) throws IOException {
        String eventName = null;
        StringBuilder data = new StringBuilder();
        boolean sawField = false;

        for (String line : (Iterable<String>) lines::iterator) {

            if (line.isEmpty()) {
                // Blank line: end of frame.
                if (sawField) {
                    emitter.send(frame(eventName, data.toString()));
                }
                eventName = null;
                data.setLength(0);
                sawField = false;
                continue;
            }

            if (line.startsWith(":")) {
                // A comment, used as a keep-alive. Dropped: forwarding it would surface as an
                // empty event in the browser.
                continue;
            }

            sawField = true;
            if (line.startsWith("event:")) {
                eventName = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).stripLeading());
            }
            // id: and retry: are dropped. Nothing in the contract uses them, and forwarding an
            // id the browser cannot act on would suggest a resume capability that does not exist.
        }

        // A stream that ends without a trailing blank line still has a final frame to deliver.
        if (sawField) {
            emitter.send(frame(eventName, data.toString()));
        }
    }

    /**
     * One SSE frame.
     *
     * <p>{@code text/plain}, not {@code application/json}: a String written as JSON is quoted and
     * escaped a second time, turning the payload into a JSON string literal and breaking the
     * browser's parse. The bytes must reach the browser exactly as AI Service wrote them.
     */
    private SseEmitter.SseEventBuilder frame(String eventName, String payload) {
        SseEmitter.SseEventBuilder builder = SseEmitter.event();
        if (eventName != null && !eventName.isEmpty()) {
            builder.name(eventName);
        }
        return builder.data(payload, MediaType.TEXT_PLAIN);
    }

    /** Logs the upstream status. The body is never forwarded: it may name a provider. */
    private void logUpstreamError(UUID conversationId, int status) {
        log.warn("AI Service returned {} for streamed conversation {}", status, conversationId);
    }

    /**
     * Emits one error event and closes.
     *
     * <p>Always closes. A stream that stops without a terminal event leaves the client unable to
     * tell a finished generation from a dropped connection, so it waits forever.
     */
    private void fail(SseEmitter emitter, String code, String message) {
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data("{\"code\":\"" + code + "\",\"message\":" + jsonString(message)
                            + ",\"retryable\":true}", MediaType.TEXT_PLAIN));
        } catch (IOException | IllegalStateException e) {
            // The browser is already gone. Nothing to do and nothing to report.
            log.debug("Could not deliver a stream error event; the client had already gone");
        } finally {
            emitter.complete();
        }
    }

    /** Joins a base URL and a path without doubling or dropping the slash. */
    private static String joinUrl(String base, String path) {
        String trimmedBase = base.endsWith("/")
                ? base.substring(0, base.length() - 1) : base;
        String trimmedPath = path.startsWith("/") ? path : "/" + path;
        return trimmedBase + trimmedPath;
    }

    /**
     * Escapes a string for a JSON string literal.
     *
     * <p>Hand-rolled because this request body is built here rather than serialised by a
     * converter that is not otherwise on this classpath. Control characters are escaped, not
     * dropped: dropping them would silently alter the user's message before it reaches a model.
     */
    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}