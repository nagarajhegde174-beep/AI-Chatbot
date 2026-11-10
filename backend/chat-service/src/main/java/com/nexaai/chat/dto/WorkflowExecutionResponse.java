package com.nexaai.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WorkflowExecutionResponse(
        UUID id,
        UUID workflowId,
        UUID userId,
        String status,
        int currentStepIndex,
        String inputPayload,
        String outputPayload,
        List<Map<String, Object>> stepLogs,
        String errorMessage,
        boolean requiresApproval,
        String pendingApprovalNodeId,
        Instant startedAt,
        Instant completedAt
) {}
