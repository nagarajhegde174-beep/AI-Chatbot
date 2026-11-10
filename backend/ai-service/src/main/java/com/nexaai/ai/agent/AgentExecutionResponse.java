package com.nexaai.ai.agent;

import java.util.List;
import java.util.Map;

public record AgentExecutionResponse(
        String requestId,
        AgentExecutionStatus status,
        String responseText,
        List<ToolExecutionRecord> toolExecutions,
        int iterations,
        int totalTokens,
        String pendingApprovalTool,
        Map<String, Object> pendingApprovalArgs,
        String errorMessage
) {
    public static AgentExecutionResponse completed(String requestId, String responseText, List<ToolExecutionRecord> toolExecutions, int iterations, int totalTokens) {
        return new AgentExecutionResponse(requestId, AgentExecutionStatus.COMPLETED, responseText, toolExecutions, iterations, totalTokens, null, null, null);
    }

    public static AgentExecutionResponse waitingApproval(String requestId, String toolName, Map<String, Object> args, List<ToolExecutionRecord> toolExecutions, int iterations) {
        return new AgentExecutionResponse(requestId, AgentExecutionStatus.WAITING_APPROVAL, "Paused: Operation '" + toolName + "' requires human approval.", toolExecutions, iterations, 0, toolName, args, null);
    }

    public static AgentExecutionResponse failed(String requestId, String error, List<ToolExecutionRecord> toolExecutions, int iterations) {
        return new AgentExecutionResponse(requestId, AgentExecutionStatus.FAILED, null, toolExecutions, iterations, 0, null, null, error);
    }
}
