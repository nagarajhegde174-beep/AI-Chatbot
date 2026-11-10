package com.nexaai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateWorkflowRequest(
        @NotBlank String name,
        String description,
        String triggerType,
        List<WorkflowNodeDto> nodes,
        List<WorkflowEdgeDto> edges
) {}
