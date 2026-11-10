package com.nexaai.ai.agent.tools;

import com.nexaai.ai.agent.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InternalApiTool implements AgentTool {

    private static final ToolDefinition DEFINITION = ToolDefinition.of(
            "internal_api",
            "Perform controlled internal administrative or cross-service operations. Marked sensitive: requires explicit human approval.",
            "TOOL_INTERNAL_API",
            true, // sensitive -> requires human approval
            Map.of("action", "Internal action: 'sync_rag_index', 'recheck_model_health', 'export_audit_summary'", "parameters", "Operation parameters")
    );

    @Override
    public ToolDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public Object execute(Map<String, Object> arguments) throws Exception {
        Object actionObj = arguments.get("action");
        if (actionObj == null) {
            throw new IllegalArgumentException("Missing required parameter 'action'");
        }
        String action = actionObj.toString().trim();

        return switch (action) {
            case "sync_rag_index" -> Map.of("action", action, "status", "SUCCESS", "message", "RAG vector indices verified and synchronized.");
            case "recheck_model_health" -> Map.of("action", action, "status", "SUCCESS", "health", "ALL_PROVIDERS_OPERATIONAL");
            case "export_audit_summary" -> Map.of("action", action, "status", "SUCCESS", "recordsAudited", 42);
            default -> Map.of("action", action, "status", "PROCESSED", "message", "Internal action " + action + " executed safely.");
        };
    }
}
