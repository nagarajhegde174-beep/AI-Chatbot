package com.nexaai.chat.dto;

import java.util.Map;

public record WorkflowNodeDto(
        String id,
        String type, // TRIGGER, DOCUMENT_INPUT, TEXT_EXTRACTION, AI_PROMPT, SUMMARIZATION, EXTRACTION, DOCUMENT_SEARCH, CONDITION, OUTPUT, NOTIFICATION
        String label,
        Map<String, Object> config,
        boolean requiresApproval
) {}
