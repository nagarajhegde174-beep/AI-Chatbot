package com.nexaai.ai.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexaai.ai.support.ProviderStubs;
import com.nexaai.ai.support.TestTokens;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Streaming, over real HTTP semantics.
 *
 * <p><strong>The assertions are about the wire format, not the return value.</strong> A test that
 * called the controller method and collected the returned {@code Flux} would pass while the
 * browser got nothing, because the whole risk in a streamed response is in the framing: what
 * arrives, in what order, and whether the stream ends with something the client can rely on.
 *
 * <p>The event contract asserted here is the one recorded in
 * {@code docs/SERVICE_CONTRACTS.md} §8: {@code meta}, then one {@code token} per chunk, then
 * {@code done} — or a single {@code error}.
 */
class AiStreamingTest extends AiIntegrationTestBase {

    private static final Pattern EVENT = Pattern.compile("event:([a-z]+)\\ndata:(.*?)\\n\\n",
            Pattern.DOTALL);

    /** One SSE frame, parsed the way a browser's EventSource would see it. */
    private record Frame(String event, String data) {
    }

    private List<Frame> stream(String body) {
        List<Frame> frames = new ArrayList<>();
        Matcher matcher = EVENT.matcher(body);
        while (matcher.find()) {
            frames.add(new Frame(matcher.group(1), matcher.group(2)));
        }
        return frames;
    }

    private List<Frame> stream(String requestBody, String token) throws Exception {
        MvcResult started = mockMvc.perform(post("/internal/v1/ai/generate/stream")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult result = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType())
                .as("a client selects its reader by content type")
                .startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);

        return stream(result.getResponse().getContentAsString());
    }

    private List<Frame> streamOk(String requestBody) throws Exception {
        return stream(requestBody, TestTokens.userToken());
    }

    // ==================================================================
    // The contract
    // ==================================================================

    @Nested
    @DisplayName("the stream contract")
    class Contract {

        @Test
        @DisplayName("meta, then tokens, then done — in that order")
        void emitsMetaTokensThenDone() throws Exception {
            ProviderStubs.streaming().emitting(List.of("Hello", " world"));

            List<Frame> frames = streamOk("{\"message\":\"hi\"}");

            // The order IS the contract. A client renders on the first token and finalises on
            // `done`, so anything out of sequence breaks progressive rendering or finalisation.
            assertThat(frames).extracting(Frame::event)
                    .containsExactly("meta", "token", "token", "done");
        }

        @Test
        @DisplayName("every chunk arrives as its own token event")
        void chunksArriveSeparately() throws Exception {
            ProviderStubs.streaming().emitting(List.of("a", "b", "c", "d"));

            List<Frame> tokens = streamOk("{\"message\":\"hi\"}").stream()
                    .filter(frame -> frame.event().equals("token"))
                    .toList();

            // If these arrived joined, a browser would show nothing until the very end — the
            // exact failure streaming exists to prevent, and one a naive concatenation-based
            // test would not catch.
            assertThat(tokens).hasSize(4);
            assertThat(tokens).extracting(Frame::data)
                    .containsExactly("{\"text\":\"a\"}", "{\"text\":\"b\"}",
                            "{\"text\":\"c\"}", "{\"text\":\"d\"}");
        }

        @Test
        @DisplayName("meta is valid JSON naming the model and provider")
        void metaIsWellFormed() throws Exception {
            List<Frame> frames = streamOk("{\"model\":\"nexa-default\",\"message\":\"hi\"}");

            String meta = frames.get(0).data();
            assertThat(meta).doesNotEndWith(",}");
            assertThat(meta).contains("\"model\":\"nexa-default\"")
                    .contains("\"provider\":\"OPENAI\"")
                    .contains("\"requestId\"");
        }

        @Test
        @DisplayName("done carries the same request id as meta")
        void doneCorrelatesWithMeta() throws Exception {
            // Without this a client cannot tell which of two in-flight streams finished.
            List<Frame> frames = streamOk("{\"message\":\"hi\"}");

            String metaId = between(frames.get(0).data(), "\"requestId\":\"", "\"");
            String doneId = between(frames.get(frames.size() - 1).data(),
                    "\"requestId\":\"", "\"");

            assertThat(doneId).isEqualTo(metaId);
        }

        @Test
        @DisplayName("a token containing a quote does not break the framing")
        void escapesQuotesInTokens() throws Exception {
            // A model emitting a quotation mark must not be able to terminate the JSON string
            // and corrupt every event after it. Control characters are escaped, not dropped --
            // dropping them would corrupt the answer the user is reading.
            ProviderStubs.streaming().emitting(List.of("he said \"hi\"", "\nnewline\ttab"));

            List<Frame> frames = streamOk("{\"message\":\"hi\"}");

            assertThat(frames).extracting(Frame::event)
                    .containsExactly("meta", "token", "token", "done");
            assertThat(frames.get(1).data()).isEqualTo("{\"text\":\"he said \\\"hi\\\"\"}");
            assertThat(frames.get(2).data()).isEqualTo("{\"text\":\"\\nnewline\\ttab\"}");
        }

        private String between(String text, String prefix, String suffix) {
            int start = text.indexOf(prefix) + prefix.length();
            return text.substring(start, text.indexOf(suffix, start));
        }
    }

    // ==================================================================
    // Failure handling
    // ==================================================================

    @Nested
    @DisplayName("failures")
    class Failures {

        @Test
        @DisplayName("an unavailable provider yields one error event, not a truncated stream")
        void unavailableProviderErrorsTheStream() throws Exception {
            // A Gemini request when Gemini has no credential. A client must be able to tell this
            // apart from a stream that simply stopped.
            List<Frame> frames = streamOk("{\"model\":\"nexa-gemini-flash\",\"message\":\"hi\"}");

            assertThat(frames).hasSize(1);
            assertThat(frames.get(0).event()).isEqualTo("error");
            assertThat(frames.get(0).data()).contains("PROVIDER_UNAVAILABLE");
        }

        @Test
        @DisplayName("a provider fault mid-stream becomes an error event")
        void midStreamFaultBecomesAnErrorEvent() throws Exception {
            // Already-emitted tokens stay emitted; the client keeps what it has and learns the
            // generation failed. Silently closing the connection would look like success.
            ProviderStubs.streaming()
                    .failingWith(new IllegalStateException("connection reset"));

            List<Frame> frames = streamOk("{\"message\":\"hi\"}");

            assertThat(frames).extracting(Frame::event).contains("error");
            assertThat(frames).extracting(Frame::event).doesNotContain("done");
        }

        @Test
        @DisplayName("an error event says whether a retry could help")
        void errorEventCarriesRetryability() throws Exception {
            // The frontend offers a retry button. It must know whether retrying is worth it, and
            // that is only knowable if the server says so.
            List<Frame> frames = streamOk("{\"model\":\"nexa-gemini-flash\",\"message\":\"hi\"}");

            assertThat(frames.get(0).data()).contains("\"retryable\":false");
        }

        @Test
        @DisplayName("an unknown model is refused before any stream is opened")
        void unknownModelIsRejected() throws Exception {
            // No asyncStarted() here, and that is the point. Model selection and validation run
            // while the handler is invoked, so a bad request is refused with a normal 400 and
            // never produces a stream. Opening a stream that can only fail immediately would
            // leave the client holding a connection to nothing.
            mockMvc.perform(post("/internal/v1/ai/generate/stream")
                            .header("Authorization", bearer(TestTokens.userToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"model\":\"no-such-model\",\"message\":\"hi\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.code").value("UNKNOWN_MODEL"));
        }

        @Test
        @DisplayName("a blank message is refused with the offending field named")
        void blankMessageIsRejected() throws Exception {
            mockMvc.perform(post("/internal/v1/ai/generate/stream")
                            .header("Authorization", bearer(TestTokens.userToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"   \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("an unauthenticated stream request is refused")
        void unauthenticatedIsRefused() throws Exception {
            // A streamed response is committed before it can fail, so an auth failure here
            // cannot be reported in the body. It has to be refused before the stream opens.
            mockMvc.perform(post("/internal/v1/ai/generate/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"hi\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================================================================
    // Usage
    // ==================================================================

    @Nested
    @DisplayName("accounting")
    class Accounting {

        @Test
        @DisplayName("a completed stream is recorded as streamed usage")
        void completedStreamIsRecorded() throws Exception {
            streamOk("{\"message\":\"hi\"}");

            // Streamed and blocking generations are metered separately, because they cost the
            // platform differently and conflating them hides a regression in one behind the
            // other.
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/internal/v1/ai/usage")
                            .header("Authorization", bearer(TestTokens.userToken())))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.recent[?(@.streamed == true)]").exists());
        }
    }
}