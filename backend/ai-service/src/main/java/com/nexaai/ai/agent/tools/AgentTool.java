package com.nexaai.ai.agent.tools;

import com.nexaai.ai.agent.ToolDefinition;
import java.util.Map;

public interface AgentTool {
    ToolDefinition getDefinition();
    Object execute(Map<String, Object> arguments) throws Exception;
}
