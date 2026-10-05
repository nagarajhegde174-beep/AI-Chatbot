package com.nexaai.chat.web;

import com.nexaai.chat.security.CurrentCaller;
import com.nexaai.chat.service.AdminChatService;
import com.nexaai.chat.service.ConversationService;
import com.nexaai.chat.web.dto.AdminConversationSummary;
import com.nexaai.chat.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative access to conversations.
 *
 * <p>Requires the ADMIN role, enforced by method security so it holds regardless of how the route
 * was reached.
 *
 * <p><strong>There is deliberately no administrative route to message content.</strong> An
 * administrator gets conversation metadata: who owns it, when, how many messages, whether
 * archived. Not the messages themselves, and not an export.
 *
 * <p>That is a decision, not an oversight, and the reasoning is worth stating because it looks
 * like a missing feature. Message content is the user's private data. Moderation needs to know a
 * conversation exists, who owns it and when it happened — it does not need to read it. A role
 * that can read every user's conversations without limit is a surveillance capability, and no
 * dashboard justifies one.
 *
 * <p>Where an operator genuinely must read content, for an abuse investigation or a support
 * ticket, that needs a separate feature with a recorded reason, an expiry and an audit record.
 * It is not this route and it is not built in this phase.
 */
@RestController
@RequestMapping("/api/v1/admin/conversations")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Conversations",
        description = "Administrative conversation metadata. No message content is exposed here.")
public class AdminConversationController {

    private final AdminChatService adminChat;
    private final com.nexaai.chat.config.ChatProperties properties;

    public AdminConversationController(AdminChatService adminChat,
                                      com.nexaai.chat.config.ChatProperties properties) {
        this.adminChat = adminChat;
        this.properties = properties;
    }

    @GetMapping
    @Operation(summary = "List conversations across all users",
            description = "Metadata only: owner, title, timestamps, message count, status. The "
                    + "title is the user's own text for their conversation and is included because "
                    + "it is what makes a moderation search useful.")
    public PageResponse<AdminConversationSummary> list(
            @RequestParam(required = false) String ownerAuthUserId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID owner = parseUuid(ownerAuthUserId);
        Page<AdminConversationSummary> result = adminChat.listAll(owner,
                ConversationService.parseStatus(status), search, page, size,
                properties.getLimits().getMaxPageSize());
        return PageResponse.from(result, r -> r);
    }

    @GetMapping("/counts")
    @Operation(summary = "Aggregate conversation counts")
    public AdminChatService.AdminCounts counts() {
        return adminChat.counts();
    }

    @GetMapping("/feedback-totals")
    @Operation(summary = "Feedback rating totals",
            description = "Counts only. No feedback comment text is exposed here.")
    public Map<String, Long> feedbackTotals() {
        return adminChat.feedbackTotals();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One conversation's metadata",
            description = "Not ownership-filtered: an administrator may know a conversation "
                    + "exists. Its messages are not reachable from this service by any route.")
    public AdminConversationSummary metadata(@PathVariable UUID id) {
        return adminChat.metadata(id);
    }

    /**
     * Parses an optional filter value.
     *
     * <p>Malformed input is rejected rather than ignored: a misspelled owner filter that silently
     * returned every conversation is the kind of thing that gets cached and screenshotted.
     */
    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "ownerAuthUserId must be a UUID, was '" + value + "'.");
        }
    }
}