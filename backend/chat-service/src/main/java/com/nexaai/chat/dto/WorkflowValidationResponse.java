package com.nexaai.chat.dto;

import java.util.List;

public record WorkflowValidationResponse(
        boolean valid,
        List<String> errors,
        List<String> warnings
) {
    public static WorkflowValidationResponse ok() {
        return new WorkflowValidationResponse(true, List.of(), List.of());
    }

    public static WorkflowValidationResponse failure(List<String> errors) {
        return new WorkflowValidationResponse(false, errors != null ? errors : List.of(), List.of());
    }
}
