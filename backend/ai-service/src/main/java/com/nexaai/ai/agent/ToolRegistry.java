package com.nexaai.ai.agent;

import com.nexaai.ai.agent.tools.AgentTool;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<AgentTool> toolList) {
        if (toolList != null) {
            for (AgentTool tool : toolList) {
                tools.put(tool.getDefinition().name().toLowerCase(), tool);
            }
        }
    }

    public Optional<AgentTool> findTool(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(tools.get(name.trim().toLowerCase()));
    }

    public List<ToolDefinition> getAllDefinitions() {
        return tools.values().stream()
                .map(AgentTool::getDefinition)
                .toList();
    }

    public List<ToolDefinition> getFilteredDefinitions(List<String> allowedNames) {
        if (allowedNames == null || allowedNames.isEmpty()) {
            return getAllDefinitions();
        }
        Set<String> allowedSet = new HashSet<>();
        for (String s : allowedNames) {
            allowedSet.add(s.trim().toLowerCase());
        }
        return tools.values().stream()
                .filter(t -> allowedSet.contains(t.getDefinition().name().toLowerCase()))
                .map(AgentTool::getDefinition)
                .toList();
    }

    public boolean hasPermission(AgentTool tool, Set<String> userPermissions) {
        String required = tool.getDefinition().requiredPermission();
        if (required == null || required.isBlank()) return true;
        if (userPermissions == null) return false;
        return userPermissions.contains("ROLE_ADMIN")
                || userPermissions.contains("ADMIN")
                || userPermissions.contains(required);
    }
}
