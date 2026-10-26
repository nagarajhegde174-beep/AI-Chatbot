package com.nexaai.document.dto.event;

import java.util.List;
import java.util.UUID;

public record DocumentChunkedPayload(
        UUID documentId,
        UUID userId,
        String title,
        int chunkCount,
        List<ChunkItem> chunks
) {
    public record ChunkItem(
            int chunkIndex,
            String text
    ) {}
}
