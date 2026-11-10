package com.nexaai.chat.dto;

import com.nexaai.chat.domain.Workflow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowResponse(
        UUID id,
        UUID userId,
        String name,
        String description,
        String triggerType,
        List<WorkflowNodeDto> nodes,
        List<WorkflowEdgeDto> edges,
        boolean isEnabled,
        Instant createdAt,
        Instant updatedAt
) {}
