package com.nexaai.analytics.controller;

import com.nexaai.analytics.dto.*;
import com.nexaai.analytics.service.AdminAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminAnalyticsService adminService;

    public AdminController(AdminAnalyticsService adminService) {
        this.adminService = adminService;
    }

    // 1. Admin overview (users, active users, AI requests, tokens, errors, subscription statistics)
    @GetMapping("/overview")
    public ResponseEntity<AdminOverviewDto> getOverview() {
        return ResponseEntity.ok(adminService.getOverview());
    }

    // 2. User management (search, filter, profile, activation, deactivation, suspension, subscription, usage, login info)
    @GetMapping("/users")
    public ResponseEntity<List<AdminUserManagementDtos.AdminUserSummaryDto>> listUsers(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "status", required = false) String status
    ) {
        return ResponseEntity.ok(adminService.listUsers(query, status));
    }

    @PostMapping("/users/{userId}/status")
    public ResponseEntity<AdminUserManagementDtos.AdminUserSummaryDto> updateUserStatus(
            @PathVariable("userId") UUID userId,
            @RequestBody AdminUserManagementDtos.UpdateUserStatusRequest request
    ) {
        return ResponseEntity.ok(adminService.updateUserStatus(userId, request.status()));
    }

    // 3. Conversation monitoring (queries: today, yesterday, older, user-wise query monitoring; respecting privacy)
    @GetMapping("/conversations/monitoring")
    public ResponseEntity<ConversationMonitoringDtos.ConversationMonitoringDto> getConversationMonitoring() {
        return ResponseEntity.ok(adminService.getConversationMonitoring());
    }

    // 4. Model management (enabled/disabled, default model, provider status, usage, errors)
    @GetMapping("/models")
    public ResponseEntity<List<ModelManagementDtos.ModelItemDto>> listModels() {
        return ResponseEntity.ok(adminService.listModels());
    }

    @PostMapping("/models/{modelName}/toggle")
    public ResponseEntity<ModelManagementDtos.ModelItemDto> toggleModel(
            @PathVariable("modelName") String modelName,
            @RequestBody ModelManagementDtos.ToggleModelRequest request
    ) {
        return ResponseEntity.ok(adminService.toggleModel(modelName, request.enabled()));
    }

    @PostMapping("/models/{modelName}/set-default")
    public ResponseEntity<Map<String, Object>> setDefaultModel(@PathVariable("modelName") String modelName) {
        adminService.setDefaultModel(modelName);
        return ResponseEntity.ok(Map.of("model", modelName, "isDefault", true));
    }

    // 5. RAG monitoring (documents, owner, processing status, chunks, embeddings, failures, reprocess, delete)
    @GetMapping("/rag/monitoring")
    public ResponseEntity<RagMonitoringDtos.RagMonitoringDto> getRagMonitoring() {
        return ResponseEntity.ok(adminService.getRagMonitoring());
    }

    @PostMapping("/rag/documents/{documentId}/reprocess")
    public ResponseEntity<Map<String, Object>> reprocessDocument(@PathVariable("documentId") UUID documentId) {
        adminService.markReprocessing(documentId);
        return ResponseEntity.ok(Map.of("documentId", documentId, "status", "REPROCESSING"));
    }

    @DeleteMapping("/rag/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable("documentId") UUID documentId) {
        adminService.deleteDocumentReadModel(documentId);
        return ResponseEntity.noContent().build();
    }

    // 6. Analytics Funnel & Cost Tracking (request -> provider -> model -> tokens -> latency -> estimated AI cost)
    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsFunnelDtos.AnalyticsFunnelDto> getAnalytics() {
        return ResponseEntity.ok(adminService.getAnalyticsFunnel());
    }
}
