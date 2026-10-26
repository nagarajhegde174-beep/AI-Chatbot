package com.nexaai.document.dto.event;

import java.util.UUID;

public record DocumentDeletedPayload(
        UUID documentId,
        UUID userId
) {
}
