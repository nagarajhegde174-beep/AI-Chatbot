package com.nexaai.ai.agent;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record AgentExecutionRequest(
        String requestId,
        String provider,
        String model,
        @NotBlank String prompt,
        String systemPrompt,
        List<String> allowedTools,
        Set<String> userPermissions,
        boolean requireApprovalForSensitive,
        Set<String> preApprovedTools,
        Integer maxIterations,
        Map<String, Object> contextVariables
) {}
