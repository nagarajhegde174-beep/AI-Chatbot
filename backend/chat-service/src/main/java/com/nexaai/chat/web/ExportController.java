package com.nexaai.chat.web;

import com.nexaai.chat.domain.ChatMessage;
import com.nexaai.chat.security.CurrentCaller;
import com.nexaai.chat.service.ConversationService;
import com.nexaai.chat.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conversation export.
 *
 * <p><strong>A foundation, deliberately.</strong> This renders synchronously and returns the
 * conversation as Markdown or JSON. It does not queue a job, email a file, or write to object
 * storage, because none of that exists yet and a synchronous render of a few hundred messages is
 * genuinely sufficient at this scale. The response shape is what a later asynchronous exporter
 * will have to produce, so a client built against this does not break when it moves to a job.
 *
 * <p>Export is owner-only, and there is no administrative export route. See
 * {@code AdminChatService} for why.
 */
@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/export")
@Tag(name = "Export", description = "Export one of your own conversations.")
public class ExportController {

    private final ConversationService conversations;
    private final MessageService messages;

    public ExportController(ConversationService conversations, MessageService messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Export a conversation as Markdown",
            description = "The full current history, oldest first, with the title as a heading.")
    public ResponseEntity<String> exportMarkdown(@PathVariable UUID conversationId) {
        conversations.getOwned(CurrentCaller.authUserId(), conversationId);
        List<ChatMessage> history =
                messages.currentHistory(CurrentCaller.authUserId(), conversationId);

        StringBuilder out = new StringBuilder();
        String title = conversations.getOwned(CurrentCaller.authUserId(), conversationId)
                .getTitle();
        out.append("# ").append(title).append("\n\n");

        for (ChatMessage message : history) {
            out.append("**").append(labelFor(message)).append("**\n\n");
            out.append(message.getContent().isBlank()
                    ? "_" + message.getStatus().name().toLowerCase(java.util.Locale.ROOT) + "_"
                    : message.getContent())
                    .append("\n\n");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition(conversationId, "md"))
                .contentType(MediaType.TEXT_PLAIN)
                .body(out.toString());
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Export a conversation as JSON",
            description = "The machine-readable form. This is the shape a future asynchronous "
                    + "exporter will also produce, so a client does not break when it becomes a "
                    + "job.")
    public ResponseEntity<ExportDocument> exportJson(@PathVariable UUID conversationId,
                                                     @RequestParam(defaultValue = "false")
                                                     boolean includeFeedback) {

        var conversation = conversations.getOwned(CurrentCaller.authUserId(), conversationId);
        List<ChatMessage> history =
                messages.currentHistory(CurrentCaller.authUserId(), conversationId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition(conversationId, "json"))
                .body(new ExportDocument(
                        "1.0",
                        conversationId,
                        conversation.getTitle(),
                        conversation.getModel(),
                        conversation.getStatus().name(),
                        conversation.getCreatedAt(),
                        Instant.now(),
                        history.stream().map(ExportMessage::from).toList()));
    }

    private static String labelFor(ChatMessage message) {
        return switch (message.getRole()) {
            case USER -> "You";
            case ASSISTANT -> message.getModel() == null
                    ? "Assistant" : "Assistant (" + message.getModel() + ")";
            case SYSTEM -> "System";
        };
    }

    /**
     * A filename for {@code Content-Disposition}.
     *
     * <p>The title is deliberately excluded: it is user-supplied text, and putting it in a
     * header is how a newline in a title becomes header injection. The conversation id is a UUID,
     * so it is always a safe filename.
     */
    private static String contentDisposition(UUID conversationId, String extension) {
        return "attachment; filename=\"conversation-" + conversationId + "." + extension + "\"";
    }

    /** The JSON export document. */
    public record ExportDocument(
            String formatVersion,
            UUID conversationId,
            String title,
            String model,
            String status,
            Instant createdAt,
            Instant exportedAt,
            List<ExportMessage> messages) {
    }

    /** One exported message. */
    public record ExportMessage(
            UUID id,
            long sequenceNo,
            String role,
            String status,
            String content,
            String model,
            boolean edited,
            boolean regenerated,
            Instant createdAt) {

        public static ExportMessage from(ChatMessage m) {
            return new ExportMessage(
                    m.getId(),
                    m.getSequenceNo(),
                    m.getRole().name(),
                    m.getStatus().name(),
                    m.getContent(),
                    m.getModel(),
                    m.getEditedFromId() != null,
                    m.getRegeneratedFromId() != null,
                    m.getCreatedAt());
        }
    }
}