package com.nexaai.rag.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EmbeddingService}. The Spring context is started without an actual EmbeddingModel bean,
 * forcing the service to use the deterministic local fallback. This validates that the fallback is
 * deterministic, returns vectors of the correct dimension and that equal inputs yield equal vectors.
 */
@SpringBootTest(classes = EmbeddingService.class, properties = {"nexa.rag.embedding-model=text-embedding-3-small", "spring.ai.openai.enabled=false"})
class EmbeddingServiceTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    void fallbackGeneratesDeterministicVector() {
        String text = "The quick brown fox jumps over the lazy dog";
        float[] vec1 = embeddingService.embed(text);
        float[] vec2 = embeddingService.embed(text);
        assertNotNull(vec1, "Vector must not be null");
        assertEquals(EmbeddingService.EMBEDDING_DIMENSION, vec1.length, "Vector must have configured dimension");
        assertArrayEquals(vec1, vec2, "Same input must yield identical vectors");
    }

    @Test
    void fallbackHandlesBlankInput() {
        float[] vec = embeddingService.embed("   ");
        assertNotNull(vec);
        assertEquals(EmbeddingService.EMBEDDING_DIMENSION, vec.length);
        // All zeros for blank input
        for (float v : vec) {
            assertEquals(0f, v);
        }
    }
}
