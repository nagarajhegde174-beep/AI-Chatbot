package com.nexaai.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public record UpdatePreferencesRequest(
        @NotBlank(message = "Theme is required")
        @Pattern(regexp = "^(light|dark|system)$", message = "Theme must be 'light', 'dark', or 'system'")
        String theme,

        @Size(max = 100, message = "Model identifier must not exceed 100 characters")
        String defaultModel,

        Boolean ragEnabledByDefault,

        Boolean streamResponses
) {}
