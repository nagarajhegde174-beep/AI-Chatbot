package com.nexaai.chat.dto;

public record WorkflowEdgeDto(
        String id,
        String source,
        String target,
        String conditionBranch // optional: "true", "false", "default"
) {}
