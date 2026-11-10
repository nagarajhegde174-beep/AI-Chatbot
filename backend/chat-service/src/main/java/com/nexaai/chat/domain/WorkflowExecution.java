package com.nexaai.chat.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_execution")
public class WorkflowExecution {

    @Id
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String status; // PENDING, RUNNING, WAITING_APPROVAL, COMPLETED, FAILED

    @Column(name = "current_step_index", nullable = false)
    private int currentStepIndex = 0;

    @Column(name = "input_payload", columnDefinition = "TEXT")
    private String inputPayload;

    @Column(name = "output_payload", columnDefinition = "TEXT")
    private String outputPayload;

    @Column(name = "step_logs_json", columnDefinition = "TEXT")
    private String stepLogsJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = false;

    @Column(name = "pending_approval_node_id")
    private String pendingApprovalNodeId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public WorkflowExecution() {}

    public WorkflowExecution(UUID id, UUID workflowId, UUID userId, String status, int currentStepIndex,
                             String inputPayload, String outputPayload, String stepLogsJson, String errorMessage,
                             boolean requiresApproval, String pendingApprovalNodeId, Instant startedAt, Instant completedAt) {
        this.id = id;
        this.workflowId = workflowId;
        this.userId = userId;
        this.status = status != null ? status : "PENDING";
        this.currentStepIndex = currentStepIndex;
        this.inputPayload = inputPayload;
        this.outputPayload = outputPayload;
        this.stepLogsJson = stepLogsJson != null ? stepLogsJson : "[]";
        this.errorMessage = errorMessage;
        this.requiresApproval = requiresApproval;
        this.pendingApprovalNodeId = pendingApprovalNodeId;
        this.startedAt = startedAt != null ? startedAt : Instant.now();
        this.completedAt = completedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getWorkflowId() { return workflowId; }
    public void setWorkflowId(UUID workflowId) { this.workflowId = workflowId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getCurrentStepIndex() { return currentStepIndex; }
    public void setCurrentStepIndex(int currentStepIndex) { this.currentStepIndex = currentStepIndex; }

    public String getInputPayload() { return inputPayload; }
    public void setInputPayload(String inputPayload) { this.inputPayload = inputPayload; }

    public String getOutputPayload() { return outputPayload; }
    public void setOutputPayload(String outputPayload) { this.outputPayload = outputPayload; }

    public String getStepLogsJson() { return stepLogsJson; }
    public void setStepLogsJson(String stepLogsJson) { this.stepLogsJson = stepLogsJson; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }

    public String getPendingApprovalNodeId() { return pendingApprovalNodeId; }
    public void setPendingApprovalNodeId(String pendingApprovalNodeId) { this.pendingApprovalNodeId = pendingApprovalNodeId; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
