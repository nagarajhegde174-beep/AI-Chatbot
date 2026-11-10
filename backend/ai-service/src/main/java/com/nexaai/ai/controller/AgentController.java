package com.nexaai.ai.controller;

import com.nexaai.ai.agent.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/ai/agent")
public class AgentController {

    private final AgentExecutionService agentService;
    private final ToolRegistry toolRegistry;

    public AgentController(AgentExecutionService agentService, ToolRegistry toolRegistry) {
        this.agentService = agentService;
        this.toolRegistry = toolRegistry;
    }

    @PostMapping("/execute")
    public Mono<AgentExecutionResponse> execute(@Valid @RequestBody AgentExecutionRequest request) {
        return Mono.fromCallable(() -> agentService.execute(request));
    }

    @GetMapping("/tools")
    public Mono<List<ToolDefinition>> listTools() {
        return Mono.just(toolRegistry.getAllDefinitions());
    }
}
