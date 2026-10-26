package com.nexaai.document.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkingServiceTest {

    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        // Target chunk size 200 chars, overlap 50 chars
        chunkingService = new ChunkingService(200, 50);
    }

    @Test
    void testEmptyTextReturnsEmptyList() {
        assertTrue(chunkingService.chunkText(null).isEmpty());
        assertTrue(chunkingService.chunkText("").isEmpty());
        assertTrue(chunkingService.chunkText("   ").isEmpty());
    }

    @Test
    void testShortTextProducesSingleChunk() {
        String text = "This is a short paragraph well under the two hundred character limit.";
        List<String> chunks = chunkingService.chunkText(text);

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0));
    }

    @Test
    void testParagraphBoundaryChunking() {
        String p1 = "First paragraph containing important details about section one with lots of descriptive sentences to ensure the character threshold is reached.";
        String p2 = "Second paragraph explaining the concepts of section two in comprehensive detail so that chunk boundaries are triggered accurately.";
        String p3 = "Third paragraph wrapping up the conclusion with final thoughts and explanations covering additional considerations.";

        String fullText = p1 + "\n\n" + p2 + "\n\n" + p3;
        List<String> chunks = chunkingService.chunkText(fullText);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() >= 2);
    }

    @Test
    void testLongParagraphSplitsSentencesWithOverlap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            sb.append("Sentence number ").append(i).append(" provides critical context for testing. ");
        }

        List<String> chunks = chunkingService.chunkText(sb.toString().trim());
        assertTrue(chunks.size() > 1);

        // Verify content integrity across chunks
        assertTrue(chunks.get(0).contains("Sentence number 1"));
        assertTrue(chunks.get(chunks.size() - 1).contains("Sentence number 10"));
    }
}
