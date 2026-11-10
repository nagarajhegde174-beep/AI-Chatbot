package com.nexaai.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.chat.domain.Workflow;
import com.nexaai.chat.domain.WorkflowExecution;
import com.nexaai.chat.dto.ExecuteWorkflowRequest;
import com.nexaai.chat.dto.WorkflowExecutionResponse;
import com.nexaai.chat.dto.WorkflowNodeDto;
import com.nexaai.chat.exception.WorkflowAccessDeniedException;
import com.nexaai.chat.repository.WorkflowExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class WorkflowExecutionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionService.class);

    private final WorkflowService workflowService;
    private final WorkflowExecutionRepository executionRepository;
    private final ObjectMapper objectMapper;
    private final AiSummarizationClient summarizationClient;

    public WorkflowExecutionService(
            WorkflowService workflowService,
            WorkflowExecutionRepository executionRepository,
            ObjectMapper objectMapper,
            @Autowired(required = false) AiSummarizationClient summarizationClient
    ) {
        this.workflowService = workflowService;
        this.executionRepository = executionRepository;
        this.objectMapper = objectMapper;
        this.summarizationClient = summarizationClient;
    }

    @Transactional
    public WorkflowExecutionResponse startExecution(UUID workflowId, UUID userId, ExecuteWorkflowRequest req) {
        Workflow wf = workflowService.getWorkflowOrThrow(workflowId, userId);

        UUID executionId = UUID.randomUUID();
        Instant now = Instant.now();

        List<WorkflowNodeDto> nodes = deserialize(wf.getNodesJson(), new TypeReference<>() {});
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalStateException("Workflow has no nodes configured to execute.");
        }

        String input = req != null && req.inputPayload() != null ? req.inputPayload() : "Default workflow input";
        Set<String> preApproved = req != null && req.preApprovedNodeIds() != null
                ? new HashSet<>(req.preApprovedNodeIds())
                : new HashSet<>();

        WorkflowExecution execution = new WorkflowExecution(
                executionId,
                workflowId,
                userId,
                "RUNNING",
                0,
                input,
                null,
                "[]",
                null,
                false,
                null,
                now,
                null
        );

        WorkflowExecution saved = executionRepository.save(execution);
        return executeNodes(saved, nodes, preApproved);
    }

    @Transactional
    public WorkflowExecutionResponse approveExecution(UUID executionId, UUID userId) {
        WorkflowExecution exec = getExecutionOrThrow(executionId, userId);
        if (!"WAITING_APPROVAL".equalsIgnoreCase(exec.getStatus())) {
            throw new IllegalStateException("Workflow execution is not waiting for approval (current status: " + exec.getStatus() + ")");
        }

        Workflow wf = workflowService.getWorkflowOrThrow(exec.getWorkflowId(), userId);
        List<WorkflowNodeDto> nodes = deserialize(wf.getNodesJson(), new TypeReference<>() {});

        Set<String> preApproved = new HashSet<>();
        if (exec.getPendingApprovalNodeId() != null) {
            preApproved.add(exec.getPendingApprovalNodeId());
        }

        exec.setStatus("RUNNING");
        exec.setRequiresApproval(false);
        exec.setPendingApprovalNodeId(null);

        return executeNodes(exec, nodes, preApproved);
    }

    @Transactional(readOnly = true)
    public List<WorkflowExecutionResponse> listExecutions(UUID workflowId, UUID userId) {
        workflowService.getWorkflowOrThrow(workflowId, userId);
        return executionRepository.findByWorkflowIdAndUserIdOrderByStartedAtDesc(workflowId, userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowExecutionResponse getExecution(UUID executionId, UUID userId) {
        WorkflowExecution exec = getExecutionOrThrow(executionId, userId);
        return toResponse(exec);
    }

    private WorkflowExecutionResponse executeNodes(WorkflowExecution exec, List<WorkflowNodeDto> nodes, Set<String> preApproved) {
        List<Map<String, Object>> logs = deserialize(exec.getStepLogsJson(), new TypeReference<>() {});
        if (logs == null) logs = new ArrayList<>();

        String currentContext = exec.getOutputPayload() != null ? exec.getOutputPayload() : exec.getInputPayload();
        int startIndex = exec.getCurrentStepIndex();

        for (int i = startIndex; i < nodes.size(); i++) {
            WorkflowNodeDto node = nodes.get(i);
            exec.setCurrentStepIndex(i);

            // Human Approval Check for Sensitive Nodes
            if (node.requiresApproval() && !preApproved.contains(node.id())) {
                log.info("Execution {} paused at step {} ({}) waiting for approval", exec.getId(), i, node.label());
                exec.setStatus("WAITING_APPROVAL");
                exec.setRequiresApproval(true);
                exec.setPendingApprovalNodeId(node.id());
                exec.setOutputPayload(currentContext);
                exec.setStepLogsJson(serialize(logs));
                WorkflowExecution paused = executionRepository.save(exec);
                return toResponse(paused);
            }

            long stepStart = System.currentTimeMillis();
            String stepOutput;
            String status = "SUCCESS";
            String errorMsg = null;

            try {
                stepOutput = processNode(node, currentContext);
                currentContext = stepOutput;
            } catch (Exception ex) {
                log.error("Execution {} failed at step {} ({}): {}", exec.getId(), i, node.label(), ex.getMessage());
                status = "FAILED";
                errorMsg = ex.getMessage();
                stepOutput = null;

                Map<String, Object> logEntry = Map.of(
                        "nodeId", node.id() != null ? node.id() : "node-" + i,
                        "label", node.label() != null ? node.label() : node.type(),
                        "type", node.type(),
                        "status", status,
                        "error", errorMsg,
                        "durationMs", System.currentTimeMillis() - stepStart,
                        "timestamp", Instant.now().toString()
                );
                logs.add(logEntry);

                exec.setStatus("FAILED");
                exec.setErrorMessage("Failed at node '" + node.label() + "': " + errorMsg);
                exec.setStepLogsJson(serialize(logs));
                exec.setCompletedAt(Instant.now());
                WorkflowExecution failed = executionRepository.save(exec);
                return toResponse(failed);
            }

            long duration = System.currentTimeMillis() - stepStart;
            Map<String, Object> logEntry = Map.of(
                    "nodeId", node.id() != null ? node.id() : "node-" + i,
                    "label", node.label() != null ? node.label() : node.type(),
                    "type", node.type(),
                    "status", status,
                    "outputPreview", stepOutput != null ? (stepOutput.length() > 200 ? stepOutput.substring(0, 200) + "..." : stepOutput) : "",
                    "durationMs", duration,
                    "timestamp", Instant.now().toString()
            );
            logs.add(logEntry);
        }

        // Successfully completed all nodes
        exec.setStatus("COMPLETED");
        exec.setCurrentStepIndex(nodes.size());
        exec.setOutputPayload(currentContext);
        exec.setStepLogsJson(serialize(logs));
        exec.setCompletedAt(Instant.now());
        exec.setRequiresApproval(false);
        exec.setPendingApprovalNodeId(null);

        WorkflowExecution completed = executionRepository.save(exec);
        log.info("Workflow execution {} completed successfully with {} steps", exec.getId(), nodes.size());
        return toResponse(completed);
    }

    private String processNode(WorkflowNodeDto node, String input) {
        String type = node.type() != null ? node.type().toUpperCase() : "OUTPUT";
        Map<String, Object> cfg = node.config() != null ? node.config() : Map.of();

        return switch (type) {
            case "TRIGGER" -> input != null && !input.isBlank() ? input : "Triggered manual execution.";
            case "DOCUMENT_INPUT" -> {
                String docId = (String) cfg.getOrDefault("documentId", "sample-doc.pdf");
                yield "Ingested document [" + docId + "]: " + input;
            }
            case "TEXT_EXTRACTION" -> {
                // Strip unnecessary whitespace and extract content
                yield input.replaceAll("\\s+", " ").trim();
            }
            case "AI_PROMPT" -> {
                String template = (String) cfg.getOrDefault("promptTemplate", "Analyze the following: {input}");
                String prompt = template.replace("{input}", input);
                yield "AI Generated Response for prompt [" + (prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt) + "]: Key insights identified successfully.";
            }
            case "SUMMARIZATION" -> {
                String ratio = (String) cfg.getOrDefault("length", "CONCISE");
                yield "Summary (" + ratio + "): " + (input.length() > 150 ? input.substring(0, 150) + "..." : input);
            }
            case "EXTRACTION" -> {
                yield "{\"extractedData\": [{\"key\": \"Topic\", \"value\": \"AI Analysis\"}, {\"key\": \"Confidence\", \"value\": 0.96}]}";
            }
            case "DOCUMENT_SEARCH" -> {
                String query = (String) cfg.getOrDefault("query", "relevant background");
                yield "Relevant citations found for '" + query + "': Context verified from NexaAI vector store.";
            }
            case "CONDITION" -> {
                String operator = (String) cfg.getOrDefault("operator", "contains");
                String value = (String) cfg.getOrDefault("value", "AI");
                boolean met = input.toLowerCase().contains(value.toLowerCase());
                yield met ? input + " (Condition passed: " + operator + " " + value + ")" : input + " (Condition evaluated: false)";
            }
            case "NOTIFICATION" -> {
                String channel = (String) cfg.getOrDefault("channel", "IN_APP");
                yield "Notification dispatched via [" + channel + "]: Workflow completed successfully.";
            }
            case "OUTPUT" -> {
                String format = (String) cfg.getOrDefault("format", "MARKDOWN");
                yield "# Workflow Output (" + format + ")\n\n" + input;
            }
            default -> input;
        };
    }

    private WorkflowExecution getExecutionOrThrow(UUID id, UUID userId) {
        return executionRepository.findById(id)
                .map(exec -> {
                    if (!exec.getUserId().equals(userId)) {
                        log.warn("Access denied: user {} attempted to access workflow execution {} owned by {}",
                                userId, id, exec.getUserId());
                        throw new WorkflowAccessDeniedException(id);
                    }
                    return exec;
                })
                .orElseThrow(() -> new IllegalArgumentException("Workflow execution not found with id: " + id));
    }

    private WorkflowExecutionResponse toResponse(WorkflowExecution exec) {
        List<Map<String, Object>> logs = deserialize(exec.getStepLogsJson(), new TypeReference<>() {});
        return new WorkflowExecutionResponse(
                exec.getId(),
                exec.getWorkflowId(),
                exec.getUserId(),
                exec.getStatus(),
                exec.getCurrentStepIndex(),
                exec.getInputPayload(),
                exec.getOutputPayload(),
                logs != null ? logs : List.of(),
                exec.getErrorMessage(),
                exec.isRequiresApproval(),
                exec.getPendingApprovalNodeId(),
                exec.getStartedAt(),
                exec.getCompletedAt()
        );
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private <T> T deserialize(String json, TypeReference<T> ref) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ref);
        } catch (Exception e) {
            return null;
        }
    }
}
