package com.nexaai.chat.web;

import com.nexaai.chat.client.AiServiceStreamClient;
import com.nexaai.chat.config.ChatProperties;
import com.nexaai.chat.domain.FeedbackRating;
import com.nexaai.chat.security.CurrentCaller;
import com.nexaai.chat.service.FeedbackService;
import com.nexaai.chat.service.ConversationService;
import com.nexaai.chat.service.MessageService;
import com.nexaai.chat.web.dto.EditMessageRequest;
import com.nexaai.chat.web.dto.FeedbackRequest;
import com.nexaai.chat.web.dto.MessageResponse;
import com.nexaai.chat.web.dto.SendMessageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Messages: sending, reading, regenerating, editing, and rating.
 *
 * <p>Every route is scoped to the caller. Messages are addressed by their own id and ownership is
 * checked against both the message and its conversation, so a mismatched pair cannot be acted on.
 *
 * <p><strong>The streamed send is a separate route, not a flag on the existing one.</strong> A
 * single route that sometimes streams and sometimes does not has no content type a client can
 * commit to in advance, and a caller has to discover the mode from the response — which it can
 * only do after it has already chosen how to read the body.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Messages", description = "Sending, reading and curating messages in your own "
        + "conversations.")
public class MessageController {

    private final MessageService messages;
    private final FeedbackService feedback;
    private final AiServiceStreamClient streamClient;
    private final ChatProperties properties;
    private final ConversationService conversations;

    public MessageController(MessageService messages, FeedbackService feedback,
                             AiServiceStreamClient streamClient, ChatProperties properties,
                             ConversationService conversations) {
        this.messages = messages;
        this.feedback = feedback;
        this.streamClient = streamClient;
        this.properties = properties;
        this.conversations = conversations;
    }

    // ------------------------------------------------------------------
    // Streamed send
    // ------------------------------------------------------------------

    /**
     * Streams a reply as Server-Sent Events.
     *
     * <p><strong>Relays AI Service's events, and does not interpret them.</strong> The event
     * contract — {@code meta}, then {@code token}s, then {@code done} or {@code error} — is
     * AI Service's, and this method deliberately has no second implementation of it to disagree
     * with.
     *
     * <p><strong>The emitter timeout is not the generation timeout.</strong> It bounds how long
     * this thread will hold the connection open with nothing to say. A provider may legitimately
     * take minutes, and cutting the connection off mid-answer would leave the user with a partial
     * message and no indication that anything failed.
     *
     * @param conversationId ownership is checked before anything is streamed
     */
    @PostMapping(value = "/conversations/{conversationId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream a reply",
            description = "Emits meta, then one token event per chunk, then done — or a single "
                    + "error. Progressive rendering depends on chunks arriving as they are "
                    + "produced.")
    public SseEmitter streamReply(@PathVariable UUID conversationId,
                                  @Valid @RequestBody SendMessageRequest request) {
        UUID owner = CurrentCaller.authUserId();

        // Ownership is proven BEFORE a stream opens. A stream that opens and then 404s leaves the
        // client holding a connection to nothing, and makes "you may not do this" look like a
        // network failure. Another user's conversation raises the same 404 as a nonexistent one,
        // so this cannot be used to discover real ids.
        conversations.getOwned(owner, conversationId);

        SseEmitter emitter = new SseEmitter(properties.getGeneration().getStreamTimeout().toMillis());

        // Runs off the request thread: SseEmitter.send blocks until the browser consumes each
        // frame, so doing this inline would pin a container thread for the whole generation.
        Thread.ofVirtual().name("nexa-chat-stream-" + conversationId).start(() ->
                streamClient.stream(conversationId, request.content(),
                        request.model() == null || request.model().isBlank()
                                ? properties.getGeneration().getDefaultModel()
                                : request.model(),
                        emitter));

        return emitter;
    }

    // ------------------------------------------------------------------
    // History
    // ------------------------------------------------------------------

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "A conversation's history",
            description = "Oldest first, current messages only. Superseded messages from an edit "
                    + "or a regenerate are excluded.")
    public List<MessageResponse> history(@PathVariable UUID conversationId) {
        return messages.history(CurrentCaller.authUserId(), conversationId);
    }

    // ------------------------------------------------------------------
    // 8, 9, 10. send, store the user message, store the placeholder
    // ------------------------------------------------------------------

    @PostMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Send a message",
            description = "Stores the user message and creates the assistant placeholder. When "
                    + "generation is unavailable the placeholder is returned PENDING, which means "
                    + "a reply is expected and has not arrived. That is not an error: nothing "
                    + "failed, nothing was attempted.")
    public MessageResponse.SendResponse send(@PathVariable UUID conversationId,
                                             @Valid @RequestBody SendMessageRequest request) {
        MessageService.SendResult result = messages.send(CurrentCaller.authUserId(),
                conversationId, request.content(), request.model());
        return new MessageResponse.SendResponse(result.userMessage(), result.assistantMessage());
    }

    // ------------------------------------------------------------------
    // 11. regenerate
    // ------------------------------------------------------------------

    @PostMapping("/messages/{messageId}/regenerate")
    @Operation(summary = "Regenerate an assistant message",
            description = "Supersedes the current answer and creates a fresh attempt. The previous "
                    + "attempt is kept, not deleted.")
    public MessageResponse regenerate(@PathVariable UUID messageId) {
        return messages.regenerate(CurrentCaller.authUserId(), messageId);
    }

    // ------------------------------------------------------------------
    // 12. edit and resend
    // ------------------------------------------------------------------

    @PutMapping("/messages/{messageId}")
    @Operation(summary = "Edit a user message and resend",
            description = "The original is superseded and kept. A new user message and a new "
                    + "assistant placeholder follow it.")
    public MessageResponse.EditResponse edit(@PathVariable UUID messageId,
                                             @Valid @RequestBody EditMessageRequest request) {
        MessageService.EditResult result = messages.editAndResend(CurrentCaller.authUserId(),
                messageId, request.content());
        return new MessageResponse.EditResponse(result.userMessage(), result.assistantMessage());
    }

    @DeleteMapping("/messages/{messageId}")
    @Operation(summary = "Delete one of your messages")
    public ResponseEntity<Void> delete(@PathVariable UUID messageId) {
        messages.deleteMessage(CurrentCaller.authUserId(), messageId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // 13. feedback
    // ------------------------------------------------------------------

    @PutMapping("/messages/{messageId}/feedback")
    @Operation(summary = "Rate a message",
            description = "One rating per message: re-rating updates it. Only accepted on a "
                    + "current, non-system message.")
    public MessageResponse.FeedbackResponse rate(@PathVariable UUID messageId,
                                                 @Valid @RequestBody FeedbackRequest request) {
        return feedback.rate(CurrentCaller.authUserId(), messageId, parseRating(request.rating()),
                request.comment());
    }

    @DeleteMapping("/messages/{messageId}/feedback")
    @Operation(summary = "Remove your rating from a message")
    public ResponseEntity<Void> clearRating(@PathVariable UUID messageId) {
        feedback.clear(CurrentCaller.authUserId(), messageId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Parses the rating, rejecting an unknown value rather than defaulting.
     *
     * <p>Silently treating an unrecognised rating as UP would corrupt the sentiment data with no
     * signal that anything went wrong.
     */
    private static FeedbackRating parseRating(String value) {
        try {
            return FeedbackRating.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown rating '" + value + "'. Expected UP or DOWN.");
        }
    }
}