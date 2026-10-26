package com.nexaai.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    public static final int EMBEDDING_DIMENSION = 1536;

    private final EmbeddingModel springAiEmbeddingModel;
    private final String configuredModelName;

    public EmbeddingService(
            @Autowired(required = false) EmbeddingModel springAiEmbeddingModel,
            @Value("${nexa.rag.embedding-model:text-embedding-3-small}") String configuredModelName
    ) {
        this.springAiEmbeddingModel = springAiEmbeddingModel;
        this.configuredModelName = configuredModelName;
        if (springAiEmbeddingModel != null) {
            log.info("EmbeddingService initialized with Spring AI EmbeddingModel: {}", configuredModelName);
        } else {
            log.info("EmbeddingService initialized in deterministic local fallback mode (dim={})", EMBEDDING_DIMENSION);
        }
    }

    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[EMBEDDING_DIMENSION];
        }

        if (springAiEmbeddingModel != null) {
            try {
                return springAiEmbeddingModel.embed(text);
            } catch (Exception e) {
                log.warn("Spring AI embedding failed, falling back to deterministic local embedding: {}", e.getMessage());
            }
        }

        return generateDeterministicVector(text, EMBEDDING_DIMENSION);
    }

    public List<float[]> embedAll(List<String> texts) {
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    public String getModelName() {
        return configuredModelName;
    }

    /**
     * Generates a deterministic, normalized vector of length dim using SHA-256 and token frequency.
     * This provides consistent semantic cosine similarities for tests and offline environments.
     */
    public static float[] generateDeterministicVector(String text, int dim) {
        float[] vector = new float[dim];
        if (text == null || text.isBlank()) {
            return vector;
        }

        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(text.getBytes(StandardCharsets.UTF_8));

            // Seed vector positions from hash
            for (int i = 0; i < hash.length && i < dim; i++) {
                vector[i] = (hash[i] & 0xFF) / 255.0f;
            }

            // Add word-token based features
            String[] tokens = text.toLowerCase().split("\\W+");
            for (String token : tokens) {
                if (token.isBlank()) continue;
                int tokenHash = Math.abs(token.hashCode()) % dim;
                vector[tokenHash] += 1.0f;
            }

            // Normalize vector to unit length (L2 norm)
            double sumSquares = 0.0;
            for (float v : vector) {
                sumSquares += v * v;
            }
            double norm = Math.sqrt(sumSquares);
            if (norm > 0) {
                for (int i = 0; i < dim; i++) {
                    vector[i] = (float) (vector[i] / norm);
                }
            }
        } catch (NoSuchAlgorithmException ignored) {
        }

        return vector;
    }

    /**
     * Compute cosine similarity between two vectors: (A . B) / (||A|| * ||B||)
     */
    public static double cosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length == 0 || v2.length == 0 || v1.length != v2.length) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator == 0.0) {
            return 0.0;
        }
        return dotProduct / denominator;
    }
}
