package com.nexaai.chat.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class WorkflowAccessDeniedException extends ChatException {

    public WorkflowAccessDeniedException(UUID workflowId) {
        super("WORKFLOW_ACCESS_DENIED", "Access denied to workflow: " + workflowId, HttpStatus.FORBIDDEN);
    }

    public WorkflowAccessDeniedException(String message) {
        super("WORKFLOW_ACCESS_DENIED", message, HttpStatus.FORBIDDEN);
    }
}
