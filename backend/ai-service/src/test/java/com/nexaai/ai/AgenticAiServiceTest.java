package com.nexaai.ai;

import com.nexaai.ai.agent.*;
import com.nexaai.ai.agent.tools.*;
import com.nexaai.ai.dto.CompletionResponse;
import com.nexaai.ai.service.AiInferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AgenticAiServiceTest {

    private ToolRegistry toolRegistry;
    private AiInferenceService inferenceService;
    private AgentExecutionService agentExecutionService;

    @BeforeEach
    void setUp() {
        CalculatorTool calculator = new CalculatorTool();
        WebSearchTool webSearch = new WebSearchTool();
        DocumentSearchTool docSearch = new DocumentSearchTool();
        DatabaseSearchTool dbSearch = new DatabaseSearchTool();
        WeatherTool weather = new WeatherTool();
        FileAnalyzerTool fileAnalyzer = new FileAnalyzerTool();
        InternalApiTool internalApi = new InternalApiTool();

        toolRegistry = new ToolRegistry(List.of(
                calculator, webSearch, docSearch, dbSearch, weather, fileAnalyzer, internalApi
        ));

        inferenceService = Mockito.mock(AiInferenceService.class);
        CompletionResponse mockResp = new CompletionResponse(
                "req-1", "OPENAI", "gpt-4o-mini", "Calculated successfully.",
                com.nexaai.ai.domain.FinishReason.STOP,
                new CompletionResponse.TokenUsage(10, 20, 30),
                50L
        );
        when(inferenceService.complete(any())).thenReturn(Mono.just(mockResp));

        agentExecutionService = new AgentExecutionService(toolRegistry, inferenceService);
    }

    @Test
    @DisplayName("ToolRegistry lists all 7 available tools")
    void toolRegistry_listsAllTools() {
        List<ToolDefinition> defs = toolRegistry.getAllDefinitions();
        assertEquals(7, defs.size());
        assertTrue(defs.stream().anyMatch(d -> d.name().equals("calculator")));
        assertTrue(defs.stream().anyMatch(d -> d.name().equals("weather")));
        assertTrue(defs.stream().anyMatch(d -> d.name().equals("database_search")));
    }

    @Test
    @DisplayName("CalculatorTool evaluates arithmetic expressions correctly")
    void calculatorTool_computesValues() throws Exception {
        CalculatorTool tool = new CalculatorTool();
        Map<?, ?> res1 = (Map<?, ?>) tool.execute(Map.of("expression", "25 * 4"));
        assertEquals(100.0, ((Number) res1.get("result")).doubleValue());

        Map<?, ?> res2 = (Map<?, ?>) tool.execute(Map.of("expression", "sqrt(144)"));
        assertEquals(12.0, ((Number) res2.get("result")).doubleValue());
    }

    @Test
    @DisplayName("DatabaseSearchTool blocks raw SQL injection and allows controlled entity queries")
    void databaseSearchTool_securityCheck() throws Exception {
        DatabaseSearchTool tool = new DatabaseSearchTool();

        // 1. Raw SQL must be rejected with SecurityException
        assertThrows(SecurityException.class, () ->
                tool.execute(Map.of("targetEntity", "SELECT * FROM users;")));

        // 2. Controlled entity query succeeds
        Map<?, ?> res = (Map<?, ?>) tool.execute(Map.of("targetEntity", "models"));
        assertEquals("models", res.get("entity"));
        assertNotNull(res.get("records"));
    }

    @Test
    @DisplayName("Agent execution enforces permission checks server-side")
    void agentExecution_enforcesPermissions() {
        // User only has TOOL_CALCULATOR permission, but prompt asks for weather
        AgentExecutionRequest req = new AgentExecutionRequest(
                "req-p1", "OPENAI", "gpt-4o-mini", "what is the weather in Paris?",
                null, List.of("weather"),
                Set.of("TOOL_CALCULATOR"), // lacks TOOL_WEATHER
                false, Set.of(), 5, Map.of()
        );

        AgentExecutionResponse resp = agentExecutionService.execute(req);
        assertNotNull(resp);
        assertEquals(1, resp.toolExecutions().size());
        ToolExecutionRecord rec = resp.toolExecutions().get(0);
        assertEquals("PERMISSION_DENIED", rec.status());
        assertTrue(rec.errorMessage().contains("TOOL_WEATHER"));
    }

    @Test
    @DisplayName("Agent execution pauses with WAITING_APPROVAL when sensitive tool is invoked without pre-approval")
    void agentExecution_pausesOnSensitiveTool() {
        AgentExecutionRequest req = new AgentExecutionRequest(
                "req-s1", "OPENAI", "gpt-4o-mini", "sync rag index now",
                null, List.of("internal_api"),
                Set.of("ROLE_ADMIN", "TOOL_INTERNAL_API"),
                true, // requireApprovalForSensitive = true
                Set.of(), // no pre-approved tools
                5, Map.of()
        );

        AgentExecutionResponse resp = agentExecutionService.execute(req);
        assertEquals(AgentExecutionStatus.WAITING_APPROVAL, resp.status());
        assertEquals("internal_api", resp.pendingApprovalTool());
        assertNotNull(resp.pendingApprovalArgs());
    }

    @Test
    @DisplayName("Agent execution completes sensitive tool if pre-approved")
    void agentExecution_completesWhenPreApproved() {
        AgentExecutionRequest req = new AgentExecutionRequest(
                "req-s2", "OPENAI", "gpt-4o-mini", "sync rag index now",
                null, List.of("internal_api"),
                Set.of("ROLE_ADMIN", "TOOL_INTERNAL_API"),
                true,
                Set.of("internal_api"), // pre-approved!
                5, Map.of()
        );

        AgentExecutionResponse resp = agentExecutionService.execute(req);
        assertEquals(AgentExecutionStatus.COMPLETED, resp.status());
        assertEquals(1, resp.toolExecutions().size());
        assertEquals("SUCCESS", resp.toolExecutions().get(0).status());
    }
}
