package com.nexaai.chat.dto;

import com.nexaai.chat.domain.FeedbackType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MessageFeedbackRequest(
        @NotNull(message = "Feedback type is required (THUMBS_UP or THUMBS_DOWN)")
        FeedbackType feedback,

        @Size(max = 2000, message = "Comment must not exceed 2000 characters")
        String comment
) {
}
