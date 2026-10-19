package com.nexaai.chat.controller;

import com.nexaai.chat.dto.EditMessageRequest;
import com.nexaai.chat.dto.MessageFeedbackRequest;
import com.nexaai.chat.dto.MessageResponse;
import com.nexaai.chat.dto.SendMessageRequest;
import com.nexaai.chat.security.UserPrincipal;
import com.nexaai.chat.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat/conversations/{conversationId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Get chat history for conversation
     */
    @GetMapping
    public ResponseEntity<List<MessageResponse>> getMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId
    ) {
        List<MessageResponse> list = messageService.getMessages(conversationId, principal.getUserId(), principal.isAdmin());
        return ResponseEntity.ok(list);
    }

    /**
     * 8, 9, 10. Send message, store user message, store AI response placeholder
     */
    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        MessageResponse response = messageService.sendMessage(conversationId, principal.getUserId(), principal.isAdmin(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 11. Regenerate foundation
     */
    @PostMapping("/{messageId}/regenerate")
    public ResponseEntity<MessageResponse> regenerateMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId
    ) {
        MessageResponse response = messageService.regenerateMessage(conversationId, messageId, principal.getUserId(), principal.isAdmin());
        return ResponseEntity.ok(response);
    }

    /**
     * 12. Edit / resend foundation
     */
    @PostMapping("/{messageId}/edit")
    public ResponseEntity<MessageResponse> editAndResend(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            @Valid @RequestBody EditMessageRequest request
    ) {
        MessageResponse response = messageService.editAndResend(conversationId, messageId, principal.getUserId(), principal.isAdmin(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * 13. Feedback on message
     */
    @PostMapping("/{messageId}/feedback")
    public ResponseEntity<MessageResponse> submitFeedback(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            @Valid @RequestBody MessageFeedbackRequest request
    ) {
        MessageResponse response = messageService.submitFeedback(conversationId, messageId, principal.getUserId(), principal.isAdmin(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete message
     */
    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId
    ) {
        messageService.deleteMessage(conversationId, messageId, principal.getUserId(), principal.isAdmin());
        return ResponseEntity.noContent().build();
    }
}
