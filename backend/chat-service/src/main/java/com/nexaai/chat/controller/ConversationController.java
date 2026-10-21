package com.nexaai.chat.controller;

import com.nexaai.chat.dto.*;
import com.nexaai.chat.security.UserPrincipal;
import com.nexaai.chat.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * 1. Create conversation
     */
    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody(required = false) CreateConversationRequest request
    ) {
        ConversationResponse response = conversationService.createConversation(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 2. List conversations
     */
    @GetMapping
    public ResponseEntity<List<ConversationSummaryResponse>> listConversations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Boolean archived
    ) {
        List<ConversationSummaryResponse> list = conversationService.listConversations(principal.getUserId(), archived);
        return ResponseEntity.ok(list);
    }

    /**
     * 7. Search conversations
     */
    @GetMapping("/search")
    public ResponseEntity<List<ConversationSummaryResponse>> searchConversations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String q
    ) {
        List<ConversationSummaryResponse> list = conversationService.searchConversations(principal.getUserId(), q);
        return ResponseEntity.ok(list);
    }

    /**
     * 3. Get conversation
     */
    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        ConversationResponse response = conversationService.getConversation(id, principal.getUserId(), principal.isAdmin());
        return ResponseEntity.ok(response);
    }

    /**
     * 4. Rename / update conversation
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ConversationResponse> updateConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConversationRequest request
    ) {
        ConversationResponse response = conversationService.updateConversation(id, principal.getUserId(), principal.isAdmin(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * 5. Delete conversation
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        conversationService.deleteConversation(id, principal.getUserId(), principal.isAdmin());
        return ResponseEntity.noContent().build();
    }

    /**
     * 6. Archive conversation
     */
    @PostMapping("/{id}/archive")
    public ResponseEntity<ConversationResponse> archiveConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        ConversationResponse response = conversationService.setArchiveStatus(id, principal.getUserId(), principal.isAdmin(), true);
        return ResponseEntity.ok(response);
    }

    /**
     * Unarchive conversation
     */
    @PostMapping("/{id}/unarchive")
    public ResponseEntity<ConversationResponse> unarchiveConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        ConversationResponse response = conversationService.setArchiveStatus(id, principal.getUserId(), principal.isAdmin(), false);
        return ResponseEntity.ok(response);
    }

    /**
     * 14. Export foundation
     */
    @GetMapping("/{id}/export")
    public ResponseEntity<ConversationExportResponse> exportConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "json") String format
    ) {
        ConversationExportResponse response = conversationService.exportConversation(id, principal.getUserId(), principal.isAdmin(), format);
        return ResponseEntity.ok(response);
    }

    /**
     * Phase 5 — Clear conversation memory.
     * Deletes all messages and resets the rolling context summary.
     * The conversation itself (title, model, settings) is preserved.
     */
    @DeleteMapping("/{id}/memory")
    public ResponseEntity<Void> clearMemory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        conversationService.clearMemory(id, principal.getUserId(), principal.isAdmin());
        return ResponseEntity.noContent().build();
    }
}
