package com.nexaai.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.chat.domain.Workflow;
import com.nexaai.chat.dto.*;
import com.nexaai.chat.exception.WorkflowAccessDeniedException;
import com.nexaai.chat.repository.WorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final WorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;

    private static final Set<String> SUPPORTED_NODE_TYPES = Set.of(
            "TRIGGER",
            "DOCUMENT_INPUT",
            "TEXT_EXTRACTION",
            "AI_PROMPT",
            "SUMMARIZATION",
            "EXTRACTION",
            "DOCUMENT_SEARCH",
            "CONDITION",
            "OUTPUT",
            "NOTIFICATION"
    );

    public WorkflowService(WorkflowRepository workflowRepository, ObjectMapper objectMapper) {
        this.workflowRepository = workflowRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponse> listWorkflows(UUID userId) {
        return workflowRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowResponse getWorkflow(UUID id, UUID userId) {
        Workflow wf = getWorkflowOrThrow(id, userId);
        return toResponse(wf);
    }

    @Transactional
    public WorkflowResponse createWorkflow(UUID userId, CreateWorkflowRequest req) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        String nodesJson = serialize(req.nodes() != null ? req.nodes() : List.of());
        String edgesJson = serialize(req.edges() != null ? req.edges() : List.of());

        Workflow wf = new Workflow(
                id,
                userId,
                req.name(),
                req.description(),
                req.triggerType() != null ? req.triggerType() : "MANUAL",
                nodesJson,
                edgesJson,
                true,
                now,
                now
        );

        Workflow saved = workflowRepository.save(wf);
        log.info("Created workflow id={} for user={}", saved.getId(), userId);
        return toResponse(saved);
    }

    @Transactional
    public WorkflowResponse updateWorkflow(UUID id, UUID userId, UpdateWorkflowRequest req) {
        Workflow wf = getWorkflowOrThrow(id, userId);

        wf.setName(req.name());
        wf.setDescription(req.description());
        if (req.triggerType() != null) wf.setTriggerType(req.triggerType());
        if (req.nodes() != null) wf.setNodesJson(serialize(req.nodes()));
        if (req.edges() != null) wf.setEdgesJson(serialize(req.edges()));
        if (req.isEnabled() != null) wf.setEnabled(req.isEnabled());
        wf.setUpdatedAt(Instant.now());

        Workflow saved = workflowRepository.save(wf);
        log.info("Updated workflow id={} for user={}", saved.getId(), userId);
        return toResponse(saved);
    }

    @Transactional
    public void deleteWorkflow(UUID id, UUID userId) {
        Workflow wf = getWorkflowOrThrow(id, userId);
        workflowRepository.delete(wf);
        log.info("Deleted workflow id={} for user={}", id, userId);
    }

    public WorkflowValidationResponse validateWorkflow(List<WorkflowNodeDto> nodes, List<WorkflowEdgeDto> edges) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (nodes == null || nodes.isEmpty()) {
            errors.add("Workflow must contain at least one node.");
            return new WorkflowValidationResponse(false, errors, warnings);
        }

        // Check for at least one Trigger node
        boolean hasTrigger = nodes.stream().anyMatch(n -> n.type() != null && n.type().equalsIgnoreCase("TRIGGER"));
        if (!hasTrigger) {
            errors.add("Workflow must begin with a TRIGGER node.");
        }

        // Validate node types and labels
        Set<String> nodeIds = new HashSet<>();
        for (WorkflowNodeDto node : nodes) {
            if (node.id() == null || node.id().isBlank()) {
                errors.add("Every node must have a valid identifier.");
            } else if (!nodeIds.add(node.id())) {
                errors.add("Duplicate node id detected: " + node.id());
            }

            if (node.type() == null || !SUPPORTED_NODE_TYPES.contains(node.type().toUpperCase())) {
                errors.add("Unsupported node type '" + node.type() + "' in node " + node.id() + ". Supported types: " + SUPPORTED_NODE_TYPES);
            }
        }

        // Check for at least one Output or Notification node
        boolean hasOutput = nodes.stream().anyMatch(n -> n.type() != null &&
                (n.type().equalsIgnoreCase("OUTPUT") || n.type().equalsIgnoreCase("NOTIFICATION")));
        if (!hasOutput) {
            warnings.add("Workflow has no explicit OUTPUT or NOTIFICATION terminal node.");
        }

        // Validate edge endpoints if edges present
        if (edges != null) {
            for (WorkflowEdgeDto edge : edges) {
                if (!nodeIds.contains(edge.source())) {
                    errors.add("Edge source '" + edge.source() + "' does not match any node id.");
                }
                if (!nodeIds.contains(edge.target())) {
                    errors.add("Edge target '" + edge.target() + "' does not match any node id.");
                }
            }
        }

        return new WorkflowValidationResponse(errors.isEmpty(), errors, warnings);
    }

    public Workflow getWorkflowOrThrow(UUID id, UUID userId) {
        return workflowRepository.findById(id)
                .map(wf -> {
                    if (!wf.getUserId().equals(userId)) {
                        log.warn("Access denied: user {} attempted to access workflow {} owned by {}",
                                userId, id, wf.getUserId());
                        throw new WorkflowAccessDeniedException(id);
                    }
                    return wf;
                })
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found with id: " + id));
    }

    public WorkflowResponse toResponse(Workflow wf) {
        List<WorkflowNodeDto> nodes = deserialize(wf.getNodesJson(), new TypeReference<>() {});
        List<WorkflowEdgeDto> edges = deserialize(wf.getEdgesJson(), new TypeReference<>() {});

        return new WorkflowResponse(
                wf.getId(),
                wf.getUserId(),
                wf.getName(),
                wf.getDescription(),
                wf.getTriggerType(),
                nodes != null ? nodes : List.of(),
                edges != null ? edges : List.of(),
                wf.isEnabled(),
                wf.getCreatedAt(),
                wf.getUpdatedAt()
        );
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize workflow json", e);
            return "[]";
        }
    }

    private <T> T deserialize(String json, TypeReference<T> ref) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ref);
        } catch (Exception e) {
            log.error("Failed to deserialize workflow json: {}", json, e);
            return null;
        }
    }
}
