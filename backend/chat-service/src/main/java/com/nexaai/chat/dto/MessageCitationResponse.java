package com.nexaai.chat.dto;

import java.util.UUID;

public record MessageCitationResponse(
        UUID id,
        String documentId,
        String title,
        Integer chunkIndex,
        String excerpt
) {
}
