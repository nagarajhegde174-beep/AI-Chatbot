package com.nexaai.ai.agent.tools;

import com.nexaai.ai.agent.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DatabaseSearchTool implements AgentTool {

    private static final ToolDefinition DEFINITION = ToolDefinition.of(
            "database_search",
            "Search system metadata, model catalog, and user metrics through controlled, read-only APIs. Raw SQL is strictly forbidden.",
            "TOOL_DATABASE_SEARCH",
            false,
            Map.of("targetEntity", "Entity to search: 'models', 'system_status', 'quotas'", "filter", "Keyword filter")
    );

    @Override
    public ToolDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public Object execute(Map<String, Object> arguments) throws Exception {
        Object entityObj = arguments.get("targetEntity");
        if (entityObj == null) {
            entityObj = arguments.get("entity");
        }
        if (entityObj == null) {
            throw new IllegalArgumentException("Missing required parameter 'targetEntity'");
        }
        String entity = entityObj.toString().trim().toLowerCase();
        String filter = arguments.get("filter") != null ? arguments.get("filter").toString().toLowerCase() : "";

        // Strictly enforce controlled API responses (NO raw SQL access)
        if (entity.contains("select") || entity.contains("from") || entity.contains("where") || entity.contains(";")) {
            throw new SecurityException("Raw SQL queries are forbidden. Use controlled entity lookups only.");
        }

        return switch (entity) {
            case "models" -> Map.of(
                    "entity", "models",
                    "count", 3,
                    "records", List.of(
                            Map.of("id", "gpt-4o-mini", "provider", "OPENAI", "contextWindow", 128000, "status", "ONLINE"),
                            Map.of("id", "gemini-2.0-flash", "provider", "GEMINI", "contextWindow", 1048576, "status", "ONLINE"),
                            Map.of("id", "llama-3.3-70b-versatile", "provider", "GROQ", "contextWindow", 128000, "status", "ONLINE")
                    )
            );
            case "system_status" -> Map.of(
                    "entity", "system_status",
                    "status", "HEALTHY",
                    "activeServices", List.of("ai-service", "chat-service", "rag-service", "document-service"),
                    "circuitBreaker", "CLOSED"
            );
            case "quotas" -> Map.of(
                    "entity", "quotas",
                    "plan", "PRO",
                    "monthlyLimitTokens", 10000000,
                    "remainingTokens", 8450000,
                    "rateLimitPerMinute", 300
            );
            default -> Map.of(
                    "entity", entity,
                    "filter", filter,
                    "message", "Controlled query returned zero records for authorized entity: " + entity
            );
        };
    }
}
