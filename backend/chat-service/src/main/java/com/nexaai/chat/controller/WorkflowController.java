package com.nexaai.chat.controller;

import com.nexaai.chat.dto.*;
import com.nexaai.chat.security.UserPrincipal;
import com.nexaai.chat.service.WorkflowExecutionService;
import com.nexaai.chat.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowExecutionService executionService;

    public WorkflowController(WorkflowService workflowService, WorkflowExecutionService executionService) {
        this.workflowService = workflowService;
        this.executionService = executionService;
    }

    @GetMapping
    public ResponseEntity<List<WorkflowResponse>> listWorkflows(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(workflowService.listWorkflows(principal.getUserId()));
    }

    @PostMapping
    public ResponseEntity<WorkflowResponse> createWorkflow(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateWorkflowRequest request
    ) {
        WorkflowResponse resp = workflowService.createWorkflow(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowResponse> getWorkflow(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(workflowService.getWorkflow(id, principal.getUserId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkflowResponse> updateWorkflow(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkflowRequest request
    ) {
        return ResponseEntity.ok(workflowService.updateWorkflow(id, principal.getUserId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkflow(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        workflowService.deleteWorkflow(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/validate")
    public ResponseEntity<WorkflowValidationResponse> validateWorkflow(
            @RequestBody CreateWorkflowRequest request
    ) {
        return ResponseEntity.ok(workflowService.validateWorkflow(request.nodes(), request.edges()));
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<WorkflowExecutionResponse> executeWorkflow(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) ExecuteWorkflowRequest request
    ) {
        WorkflowExecutionResponse resp = executionService.startExecution(id, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
    }

    @GetMapping("/{id}/executions")
    public ResponseEntity<List<WorkflowExecutionResponse>> listExecutions(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(executionService.listExecutions(id, principal.getUserId()));
    }

    @GetMapping("/executions/{executionId}")
    public ResponseEntity<WorkflowExecutionResponse> getExecution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID executionId
    ) {
        return ResponseEntity.ok(executionService.getExecution(executionId, principal.getUserId()));
    }

    @PostMapping("/executions/{executionId}/approve")
    public ResponseEntity<WorkflowExecutionResponse> approveExecution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID executionId
    ) {
        return ResponseEntity.ok(executionService.approveExecution(executionId, principal.getUserId()));
    }
}
