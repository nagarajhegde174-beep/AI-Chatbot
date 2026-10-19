package com.nexaai.chat.controller;

import com.nexaai.chat.dto.ConversationResponse;
import com.nexaai.chat.dto.ConversationSummaryResponse;
import com.nexaai.chat.service.ConversationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/chat/conversations")
@PreAuthorize("hasRole('ADMIN')")
public class AdminChatController {

    private final ConversationService conversationService;

    public AdminChatController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public ResponseEntity<List<ConversationSummaryResponse>> listAll() {
        return ResponseEntity.ok(conversationService.listAllForAdmin());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getConversation(@PathVariable UUID id) {
        return ResponseEntity.ok(conversationService.getConversation(id, null, true));
    }
}
