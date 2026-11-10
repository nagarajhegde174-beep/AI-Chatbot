package com.nexaai.ai.agent;

import java.time.Instant;
import java.util.Map;

public record ToolExecutionRecord(
        String toolName,
        Map<String, Object> inputArgs,
        Object outputResult,
        String status, // SUCCESS, FAILED, WAITING_APPROVAL, PERMISSION_DENIED
        String errorMessage,
        long durationMs,
        Instant timestamp
) {}
