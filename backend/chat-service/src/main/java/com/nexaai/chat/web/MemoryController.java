package com.nexaai.chat.web;

import com.nexaai.chat.domain.MessageRole;
import com.nexaai.chat.security.CurrentCaller;
import com.nexaai.chat.service.ConversationContext;
import com.nexaai.chat.service.ConversationMemoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conversation memory: what a conversation remembers, and forgetting it.
 *
 * <p><strong>Every route is scoped to the caller</strong>, and a conversation that is not the
 * caller's raises the same 404 as one that does not exist. That is what stops these routes being
 * used to discover whether a given conversation id is real.
 *
 * <p><strong>No route returns memory content.</strong> A summary is a paraphrase of the user's own
 * words. These routes report that one exists, how many turns it covers and how big it is — enough
 * for the UI to explain itself — and never the text. Every additional copy of a user's words is
 * another place they could leak from.
 */
@RestController
@RequestMapping("/api/v1/conversations/{conversationId}")
@Tag(name = "Memory", description = "What a conversation remembers, and forgetting it.")
public class MemoryController {

    private final ConversationMemoryService memory;
    private final com.nexaai.chat.service.ConversationService conversations;

    public MemoryController(ConversationMemoryService memory,
                            com.nexaai.chat.service.ConversationService conversations) {
        this.memory = memory;
        this.conversations = conversations;
    }

    @GetMapping("/memory")
    @Operation(summary = "What this conversation remembers",
            description = "Counts and sizes only. The summary text is never returned.")
    public ConversationMemoryService.MemoryStatus status(@PathVariable UUID conversationId) {
        UUID owner = CurrentCaller.authUserId();
        conversations.getOwned(owner, conversationId);
        return memory.status(owner, conversationId);
    }

    @PostMapping("/memory/refresh")
    @Operation(summary = "Compress older turns now",
            description = "Forces a summarisation pass rather than waiting for the budget to be "
                    + "exceeded. Returns what was compressed, or zero when there was nothing "
                    + "worth compressing.")
    public RefreshResponse refresh(@PathVariable UUID conversationId) {
        UUID owner = CurrentCaller.authUserId();
        conversations.getOwned(owner, conversationId);
        int compressed = memory.refresh(owner, conversationId);
        return new RefreshResponse(conversationId, compressed, memory.status(owner, conversationId));
    }

    @DeleteMapping("/memory")
    @Operation(summary = "Forget this conversation's memory",
            description = "Removes the compressed summary only. The conversation's messages are "
                    + "not touched: forgetting what was discussed and deleting the discussion "
                    + "are different requests. Memory is rebuilt from the remaining messages on "
                    + "the next send.")
    public ResponseEntity<ClearResponse> clear(@PathVariable UUID conversationId) {
        UUID owner = CurrentCaller.authUserId();
        conversations.getOwned(owner, conversationId);
        boolean hadMemory = memory.clear(owner, conversationId);
        return ResponseEntity.ok(new ClearResponse(conversationId, hadMemory,
                memory.status(owner, conversationId)));
    }

    /** What a refresh did. */
    public record RefreshResponse(
            UUID conversationId,
            int compressedTurns,
            ConversationMemoryService.MemoryStatus memory) {
    }

    /**
     * The outcome of clearing memory.
     *
     * @param hadMemory false when there was nothing remembered. A clear that reports success on a
     *                  conversation that never had memory is technically true and useless
     */
    public record ClearResponse(
            UUID conversationId,
            boolean hadMemory,
            ConversationMemoryService.MemoryStatus memory) {
    }
}