package com.nexaai.chat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ChatBoundaryInfoController {

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, Object>> healthz() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "chat-service");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/internal/v1/chat/info")
    public ResponseEntity<Map<String, Object>> chatInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", "chat-service");
        info.put("port", 8083);
        info.put("database", "nexa_chat");
        info.put("phase", 3);
        info.put("features", new String[]{
                "create conversation",
                "list conversations",
                "get conversation",
                "rename",
                "delete",
                "archive",
                "search",
                "send message",
                "store user message",
                "store AI response placeholder",
                "regenerate foundation",
                "edit/resend foundation",
                "feedback",
                "export foundation"
        });
        return ResponseEntity.ok(info);
    }
}
