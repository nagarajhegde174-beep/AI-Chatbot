package com.nexaai.rag.dto;

import java.util.UUID;

public record RetrievedPassage(
        UUID documentId,
        String documentTitle,
        int chunkIndex,
        String text,
        double score,
        int citationIndex
) {
}
