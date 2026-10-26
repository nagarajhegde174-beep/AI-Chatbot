package com.nexaai.document.dto;

import com.nexaai.document.domain.DocumentChunk;

import java.util.UUID;

public record DocumentChunkResponse(
        UUID id,
        int chunkIndex,
        String text,
        int characterCount
) {
    public static DocumentChunkResponse from(DocumentChunk chunk) {
        return new DocumentChunkResponse(
                chunk.getId(),
                chunk.getChunkIndex(),
                chunk.getText(),
                chunk.getCharacterCount()
        );
    }
}
