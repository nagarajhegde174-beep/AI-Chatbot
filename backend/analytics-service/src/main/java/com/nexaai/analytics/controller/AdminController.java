package com.nexaai.analytics.controller;

import com.nexaai.analytics.dto.*;
import com.nexaai.analytics.service.AdminAnalyticsService;
import com.nexaai.analytics.service.AuditLogService;
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
    private final AuditLogService auditLogService;

    public AdminController(AdminAnalyticsService adminService, AuditLogService auditLogService) {
        this.adminService = adminService;
        this.auditLogService = auditLogService;
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
        AdminUserManagementDtos.AdminUserSummaryDto res = adminService.updateUserStatus(userId, request.status());
        auditLogService.logEvent("admin", "USER_STATUS_CHANGE", userId.toString(), "SUCCESS", null, "Status updated to " + request.status());
        return ResponseEntity.ok(res);
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
        ModelManagementDtos.ModelItemDto res = adminService.toggleModel(modelName, request.enabled());
        auditLogService.logEvent("admin", "MODEL_TOGGLE", modelName, "SUCCESS", null, "Enabled=" + request.enabled());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/models/{modelName}/set-default")
    public ResponseEntity<Map<String, Object>> setDefaultModel(@PathVariable("modelName") String modelName) {
        adminService.setDefaultModel(modelName);
        auditLogService.logEvent("admin", "MODEL_SET_DEFAULT", modelName, "SUCCESS", null, "Default model set");
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
        auditLogService.logEvent("admin", "RAG_REPROCESS", documentId.toString(), "SUCCESS", null, "Reprocess triggered");
        return ResponseEntity.ok(Map.of("documentId", documentId, "status", "REPROCESSING"));
    }

    @DeleteMapping("/rag/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable("documentId") UUID documentId) {
        adminService.deleteDocumentReadModel(documentId);
        auditLogService.logEvent("admin", "DOCUMENT_DELETE", documentId.toString(), "SUCCESS", null, "Document deleted");
        return ResponseEntity.noContent().build();
    }

    // 6. Analytics Funnel & Cost Tracking (request -> provider -> model -> tokens -> latency -> estimated AI cost)
    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsFunnelDtos.AnalyticsFunnelDto> getAnalytics() {
        return ResponseEntity.ok(adminService.getAnalyticsFunnel());
    }

    // 7. Security & Administrative Audit Logs (immutable event stream)
    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLogDto>> getAuditLogs(@RequestParam(value = "filter", required = false) String filter) {
        return ResponseEntity.ok(auditLogService.getAuditLogs(filter));
    }
}
