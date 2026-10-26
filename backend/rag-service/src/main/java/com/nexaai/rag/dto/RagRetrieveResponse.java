package com.nexaai.rag.dto;

import java.util.List;

public record RagRetrieveResponse(
        List<RetrievedPassage> passages,
        boolean used,
        String embeddingModel
) {
}
