package com.nexaai.ai.agent;

import java.util.List;
import java.util.Map;

public record ToolDefinition(
        String name,
        String description,
        String requiredPermission,
        boolean sensitive,
        Map<String, Object> parameterSchema
) {
    public static ToolDefinition of(String name, String description, String requiredPermission, boolean sensitive, Map<String, Object> schema) {
        return new ToolDefinition(name, description, requiredPermission, sensitive, schema != null ? schema : Map.of());
    }
}
