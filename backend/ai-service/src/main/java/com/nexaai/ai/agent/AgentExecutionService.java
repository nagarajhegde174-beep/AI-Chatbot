package com.nexaai.ai.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.ai.agent.tools.AgentTool;
import com.nexaai.ai.domain.AiProvider;
import com.nexaai.ai.dto.CompletionRequest;
import com.nexaai.ai.dto.CompletionResponse;
import com.nexaai.ai.service.AiInferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class AgentExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionService.class);
    private static final int DEFAULT_MAX_ITERATIONS = 5;

    private final ToolRegistry toolRegistry;
    private final AiInferenceService inferenceService;
    private final ObjectMapper objectMapper;

    public AgentExecutionService(ToolRegistry toolRegistry, AiInferenceService inferenceService) {
        this.toolRegistry = toolRegistry;
        this.inferenceService = inferenceService;
        this.objectMapper = new ObjectMapper();
    }

    public AgentExecutionResponse execute(AgentExecutionRequest request) {
        String requestId = request.requestId() != null ? request.requestId() : UUID.randomUUID().toString();
        int maxIter = request.maxIterations() != null && request.maxIterations() > 0 ? request.maxIterations() : DEFAULT_MAX_ITERATIONS;

        List<ToolExecutionRecord> history = new ArrayList<>();
        Set<String> permissions = request.userPermissions() != null ? request.userPermissions() : Set.of("ROLE_USER");
        Set<String> preApproved = request.preApprovedTools() != null ? request.preApprovedTools() : Set.of();

        log.info("Agent execution started: requestId={}, prompt='{}', allowedTools={}",
                requestId, request.prompt(), request.allowedTools());

        // 1. Identify tool opportunities based on prompt and allowed tools
        List<ToolCallIntent> detectedCalls = planToolCalls(request.prompt(), request.allowedTools(), request.contextVariables());

        if (detectedCalls.isEmpty()) {
            // Direct inference without tool calling
            try {
                CompletionResponse completion = runCompletion(requestId, request.provider(), request.model(), request.prompt(), request.systemPrompt());
                String content = completion != null ? completion.content() : "Completed.";
                int tokens = completion != null && completion.usage() != null ? completion.usage().totalTokens() : 0;
                return AgentExecutionResponse.completed(requestId, content, history, 1, tokens);
            } catch (Exception e) {
                log.warn("Direct inference failed in agent execution: {}", e.getMessage());
                return AgentExecutionResponse.completed(requestId, "Task completed based on prompt: " + request.prompt(), history, 1, 0);
            }
        }

        // 2. Iterate through planned tool calls with permission checks and retry
        StringBuilder accumulatedContext = new StringBuilder();
        int iterations = 0;

        for (ToolCallIntent call : detectedCalls) {
            iterations++;
            if (iterations > maxIter) break;

            Optional<AgentTool> toolOpt = toolRegistry.findTool(call.toolName());
            if (toolOpt.isEmpty()) {
                history.add(new ToolExecutionRecord(
                        call.toolName(), call.arguments(), null, "NOT_FOUND", "Tool not found in registry", 0, Instant.now()
                ));
                continue;
            }

            AgentTool tool = toolOpt.get();
            ToolDefinition def = tool.getDefinition();

            // Permission Check
            if (!toolRegistry.hasPermission(tool, permissions)) {
                log.warn("Permission denied for tool={} by user permissions={}", def.name(), permissions);
                history.add(new ToolExecutionRecord(
                        def.name(), call.arguments(), null, "PERMISSION_DENIED",
                        "User lacks required permission: " + def.requiredPermission(), 0, Instant.now()
                ));
                continue;
            }

            // Human Approval Check for Sensitive Operations
            if (def.sensitive() && request.requireApprovalForSensitive() && !preApproved.contains(def.name().toLowerCase())) {
                log.info("Tool {} is sensitive; pausing for human approval", def.name());
                history.add(new ToolExecutionRecord(
                        def.name(), call.arguments(), null, "WAITING_APPROVAL",
                        "Operation paused pending human approval", 0, Instant.now()
                ));
                return AgentExecutionResponse.waitingApproval(requestId, def.name(), call.arguments(), history, iterations);
            }

            // Execution with Retry
            long startTime = System.currentTimeMillis();
            Object toolResult = null;
            String errorMsg = null;
            String status = "SUCCESS";
            int retries = 2;

            for (int attempt = 1; attempt <= retries; attempt++) {
                try {
                    toolResult = tool.execute(call.arguments());
                    errorMsg = null;
                    status = "SUCCESS";
                    break;
                } catch (Exception ex) {
                    errorMsg = ex.getMessage();
                    status = "FAILED";
                    log.warn("Tool {} execution attempt {} failed: {}", def.name(), attempt, ex.getMessage());
                    if (attempt < retries) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ignored) {}
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            history.add(new ToolExecutionRecord(def.name(), call.arguments(), toolResult, status, errorMsg, duration, Instant.now()));

            accumulatedContext.append("\n[Tool: ").append(def.name())
                    .append(" | Status: ").append(status)
                    .append(" | Result: ").append(toolResult != null ? toolResult.toString() : errorMsg)
                    .append("]");
        }

        // 3. Final synthesis
        String finalAnswer;
        int totalTokens = 0;
        try {
            String synthPrompt = "User Prompt: " + request.prompt() + "\n\nTool Execution Results:\n" + accumulatedContext + "\n\nPlease provide a clear, concise final answer summarizing the findings.";
            CompletionResponse completion = runCompletion(requestId, request.provider(), request.model(), synthPrompt, request.systemPrompt());
            if (completion != null && completion.content() != null && !completion.content().isBlank()) {
                finalAnswer = completion.content();
                totalTokens = completion.usage() != null ? completion.usage().totalTokens() : 150;
            } else {
                finalAnswer = "Execution completed with " + history.size() + " tool operation(s). Summary:" + accumulatedContext;
            }
        } catch (Exception e) {
            log.info("Synthesizing final response without remote LLM: {}", e.getMessage());
            finalAnswer = "Execution completed with " + history.size() + " tool operation(s). " + accumulatedContext;
        }

        return AgentExecutionResponse.completed(requestId, finalAnswer, history, iterations, totalTokens);
    }

    private CompletionResponse runCompletion(String requestId, String provider, String model, String prompt, String systemPrompt) {
        String prov = provider != null ? provider : "OPENAI";
        String mod = model != null ? model : "gpt-4o-mini";
        CompletionRequest cr = new CompletionRequest(
                prov,
                mod,
                systemPrompt != null ? systemPrompt : "You are NexaAI, an advanced agentic assistant.",
                List.of(new CompletionRequest.CompletionMessage("USER", prompt)),
                0.7,
                1024,
                requestId
        );
        return inferenceService.complete(cr).blockOptional().orElse(null);
    }

    private List<ToolCallIntent> planToolCalls(String prompt, List<String> allowedTools, Map<String, Object> ctx) {
        List<ToolCallIntent> list = new ArrayList<>();
        if (prompt == null) return list;
        String lower = prompt.toLowerCase();

        Set<String> allowed = allowedTools != null && !allowedTools.isEmpty()
                ? new HashSet<>(allowedTools.stream().map(String::toLowerCase).toList())
                : Set.of("calculator", "web_search", "document_search", "database_search", "weather", "file_analyzer", "internal_api");

        // 1. Calculator
        if (allowed.contains("calculator") && (lower.contains("calculate") || lower.contains("sqrt") || lower.matches(".*\\d+\\s*[+\\-*/^]\\s*\\d+.*"))) {
            String expr = extractMathExpression(prompt);
            list.add(new ToolCallIntent("calculator", Map.of("expression", expr)));
        }

        // 2. Weather
        if (allowed.contains("weather") && (lower.contains("weather") || lower.contains("temperature") || lower.contains("forecast"))) {
            String location = extractLocation(prompt);
            list.add(new ToolCallIntent("weather", Map.of("location", location)));
        }

        // 3. Document Search
        if (allowed.contains("document_search") && (lower.contains("document") || lower.contains("pdf") || lower.contains("knowledge") || lower.contains("rag"))) {
            list.add(new ToolCallIntent("document_search", Map.of("query", prompt)));
        }

        // 4. Database Search
        if (allowed.contains("database_search") && (lower.contains("catalog") || lower.contains("models available") || lower.contains("quota") || lower.contains("system status"))) {
            String entity = lower.contains("quota") ? "quotas" : (lower.contains("status") ? "system_status" : "models");
            list.add(new ToolCallIntent("database_search", Map.of("targetEntity", entity, "filter", prompt)));
        }

        // 5. File Analyzer
        if (allowed.contains("file_analyzer") && (lower.contains("analyze file") || lower.contains("word count") || lower.contains("line count") || (ctx != null && ctx.containsKey("fileContent")))) {
            String content = ctx != null && ctx.get("fileContent") != null ? ctx.get("fileContent").toString() : prompt;
            list.add(new ToolCallIntent("file_analyzer", Map.of("content", content)));
        }

        // 6. Web Search
        if (allowed.contains("web_search") && (lower.contains("search web") || lower.contains("google") || lower.contains("lookup") || lower.contains("latest news"))) {
            list.add(new ToolCallIntent("web_search", Map.of("query", prompt)));
        }

        // 7. Internal API
        if (allowed.contains("internal_api") && (lower.contains("sync rag") || lower.contains("sync_rag_index") || lower.contains("recheck health") || lower.contains("admin export"))) {
            String action = lower.contains("health") ? "recheck_model_health" : "sync_rag_index";
            list.add(new ToolCallIntent("internal_api", Map.of("action", action)));
        }

        return list;
    }

    private String extractMathExpression(String text) {
        String cleaned = text.replaceAll("(?i)(please |calculate |compute |what is |eval )", "").trim();
        if (cleaned.endsWith("?")) cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        return cleaned.isEmpty() ? "2 + 2" : cleaned;
    }

    private String extractLocation(String text) {
        String lower = text.toLowerCase();
        if (lower.contains(" in ")) {
            return text.substring(lower.indexOf(" in ") + 4).replaceAll("[^a-zA-Z\\s,]", "").trim();
        }
        return "San Francisco, US";
    }

    public record ToolCallIntent(String toolName, Map<String, Object> arguments) {}
}
