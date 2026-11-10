package com.nexaai.chat.dto;

import java.util.List;
import java.util.Map;

public record ExecuteWorkflowRequest(
        String inputPayload,
        Map<String, Object> variables,
        List<String> preApprovedNodeIds
) {}
