package com.nexaai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.chat.domain.Workflow;
import com.nexaai.chat.domain.WorkflowExecution;
import com.nexaai.chat.dto.*;
import com.nexaai.chat.exception.WorkflowAccessDeniedException;
import com.nexaai.chat.repository.WorkflowExecutionRepository;
import com.nexaai.chat.repository.WorkflowRepository;
import com.nexaai.chat.service.WorkflowExecutionService;
import com.nexaai.chat.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class WorkflowServiceSecurityTest {

    private WorkflowRepository workflowRepo;
    private WorkflowExecutionRepository executionRepo;
    private ObjectMapper objectMapper;
    private WorkflowService workflowService;
    private WorkflowExecutionService executionService;

    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workflowRepo = Mockito.mock(WorkflowRepository.class);
        executionRepo = Mockito.mock(WorkflowExecutionRepository.class);
        objectMapper = new ObjectMapper();

        workflowService = new WorkflowService(workflowRepo, objectMapper);
        executionService = new WorkflowExecutionService(workflowService, executionRepo, objectMapper, null);
    }

    @Test
    @DisplayName("Workflow validation validates trigger requirement and detects invalid node types")
    void validateWorkflow_rules() {
        // 1. Missing trigger fails validation
        WorkflowNodeDto node1 = new WorkflowNodeDto("n1", "SUMMARIZATION", "Summarize", Map.of(), false);
        WorkflowValidationResponse res1 = workflowService.validateWorkflow(List.of(node1), List.of());
        assertFalse(res1.valid());
        assertTrue(res1.errors().stream().anyMatch(e -> e.contains("TRIGGER")));

        // 2. Valid pipeline passes
        WorkflowNodeDto trigger = new WorkflowNodeDto("n0", "TRIGGER", "Manual Start", Map.of(), false);
        WorkflowNodeDto extract = new WorkflowNodeDto("n2", "TEXT_EXTRACTION", "Extract Text", Map.of(), false);
        WorkflowNodeDto output = new WorkflowNodeDto("n3", "OUTPUT", "Final Output", Map.of(), false);
        WorkflowEdgeDto edge1 = new WorkflowEdgeDto("e1", "n0", "n2", null);
        WorkflowEdgeDto edge2 = new WorkflowEdgeDto("e2", "n2", "n3", null);

        WorkflowValidationResponse res2 = workflowService.validateWorkflow(List.of(trigger, extract, output), List.of(edge1, edge2));
        assertTrue(res2.valid());
        assertTrue(res2.errors().isEmpty());
    }

    @Test
    @DisplayName("Cross-user isolation: User B cannot access User A's workflow")
    void crossUserIsolation_blocksUnauthorizedAccess() {
        UUID wfId = UUID.randomUUID();
        Workflow wfA = new Workflow(
                wfId, userA, "User A Secret Workflow", "Description", "MANUAL",
                "[]", "[]", true, Instant.now(), Instant.now()
        );
        when(workflowRepo.findById(wfId)).thenReturn(Optional.of(wfA));

        // User A succeeds
        assertNotNull(workflowService.getWorkflow(wfId, userA));

        // User B is strictly denied with WorkflowAccessDeniedException
        assertThrows(WorkflowAccessDeniedException.class, () ->
                workflowService.getWorkflow(wfId, userB));

        // User B cannot update
        UpdateWorkflowRequest updateReq = new UpdateWorkflowRequest("Hacked", null, null, null, null, null);
        assertThrows(WorkflowAccessDeniedException.class, () ->
                workflowService.updateWorkflow(wfId, userB, updateReq));

        // User B cannot delete
        assertThrows(WorkflowAccessDeniedException.class, () ->
                workflowService.deleteWorkflow(wfId, userB));
    }

    @Test
    @DisplayName("Workflow execution executes pipeline steps and records execution log")
    void executeWorkflow_pipeline() {
        UUID wfId = UUID.randomUUID();
        List<WorkflowNodeDto> nodes = List.of(
                new WorkflowNodeDto("t1", "TRIGGER", "Start", Map.of(), false),
                new WorkflowNodeDto("d1", "DOCUMENT_INPUT", "Read Document", Map.of("documentId", "report.pdf"), false),
                new WorkflowNodeDto("s1", "SUMMARIZATION", "Summarize Text", Map.of("length", "CONCISE"), false),
                new WorkflowNodeDto("o1", "OUTPUT", "Result Output", Map.of("format", "MARKDOWN"), false)
        );

        try {
            String nodesJson = objectMapper.writeValueAsString(nodes);
            Workflow wf = new Workflow(wfId, userA, "Doc Summary Flow", "Desc", "MANUAL", nodesJson, "[]", true, Instant.now(), Instant.now());
            when(workflowRepo.findById(wfId)).thenReturn(Optional.of(wf));
            when(executionRepo.save(any(WorkflowExecution.class))).thenAnswer(inv -> inv.getArgument(0));

            ExecuteWorkflowRequest req = new ExecuteWorkflowRequest("Sample quarterly financial report content.", Map.of(), List.of());
            WorkflowExecutionResponse res = executionService.startExecution(wfId, userA, req);

            assertNotNull(res);
            assertEquals("COMPLETED", res.status());
            assertEquals(4, res.currentStepIndex());
            assertNotNull(res.outputPayload());
            assertTrue(res.outputPayload().contains("Workflow Output"));
            assertEquals(4, res.stepLogs().size());
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Workflow execution pauses on sensitive node waiting for human approval")
    void executeWorkflow_pausesForApproval() {
        UUID wfId = UUID.randomUUID();
        List<WorkflowNodeDto> nodes = List.of(
                new WorkflowNodeDto("t1", "TRIGGER", "Start", Map.of(), false),
                new WorkflowNodeDto("sensitive1", "AI_PROMPT", "Sensitive Action", Map.of(), true), // requiresApproval = true!
                new WorkflowNodeDto("o1", "OUTPUT", "Finish", Map.of(), false)
        );

        try {
            String nodesJson = objectMapper.writeValueAsString(nodes);
            Workflow wf = new Workflow(wfId, userA, "Approval Flow", "Desc", "MANUAL", nodesJson, "[]", true, Instant.now(), Instant.now());
            when(workflowRepo.findById(wfId)).thenReturn(Optional.of(wf));
            when(executionRepo.save(any(WorkflowExecution.class))).thenAnswer(inv -> inv.getArgument(0));

            // Start without pre-approval -> must pause at step 1
            ExecuteWorkflowRequest req = new ExecuteWorkflowRequest("Input data", Map.of(), List.of());
            WorkflowExecutionResponse res = executionService.startExecution(wfId, userA, req);

            assertEquals("WAITING_APPROVAL", res.status());
            assertTrue(res.requiresApproval());
            assertEquals("sensitive1", res.pendingApprovalNodeId());

            // Approve execution -> resumes and completes
            when(executionRepo.findById(res.id())).thenReturn(Optional.of(new WorkflowExecution(
                    res.id(), wfId, userA, "WAITING_APPROVAL", 1, "Input data", "Input data", "[]", null, true, "sensitive1", Instant.now(), null
            )));

            WorkflowExecutionResponse approvedRes = executionService.approveExecution(res.id(), userA);
            assertEquals("COMPLETED", approvedRes.status());
            assertFalse(approvedRes.requiresApproval());
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }
}
