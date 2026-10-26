package com.nexaai.rag.service;

import com.nexaai.rag.domain.DocumentEmbedding;
import com.nexaai.rag.dto.RagRetrieveRequest;
import com.nexaai.rag.dto.RagRetrieveResponse;
import com.nexaai.rag.dto.RetrievedPassage;
import com.nexaai.rag.repository.DocumentEmbeddingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RagRetrievalService {

    private final DocumentEmbeddingRepository repository;
    private final EmbeddingService embeddingService;
    private final int defaultTopK;
    private final double defaultMinScore;

    public RagRetrievalService(
            DocumentEmbeddingRepository repository,
            EmbeddingService embeddingService,
            @Value("${nexa.rag.default-top-k:5}") int defaultTopK,
            @Value("${nexa.rag.default-min-score:0.1}") double defaultMinScore
    ) {
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.defaultTopK = defaultTopK;
        this.defaultMinScore = defaultMinScore;
    }

    /**
     * Retrieve relevant passages. Tenant isolation is strictly enforced at database query level.
     */
    public RagRetrieveResponse retrieve(RagRetrieveRequest request) {
        UUID userId = request.userId();
        if (userId == null) {
            throw new IllegalArgumentException("userId is required for tenant isolation");
        }

        String query = request.query();
        if (query == null || query.isBlank()) {
            return new RagRetrieveResponse(List.of(), false, embeddingService.getModelName());
        }

        int topK = (request.topK() != null && request.topK() > 0) ? request.topK() : defaultTopK;
        double minScore = (request.minScore() != null) ? request.minScore() : defaultMinScore;

        // 1. Database query with strict tenant isolation: user_id is in the DB query!
        List<DocumentEmbedding> candidates;
        if (request.documentIds() != null && !request.documentIds().isEmpty()) {
            candidates = repository.findByUserIdAndDocumentIdIn(userId, request.documentIds());
        } else {
            candidates = repository.findByUserId(userId);
        }

        if (candidates.isEmpty()) {
            return new RagRetrieveResponse(List.of(), false, embeddingService.getModelName());
        }

        // 2. Embed the user query
        float[] queryVector = embeddingService.embed(query);

        // 3. Compute vector similarity scores
        record ScoredCandidate(DocumentEmbedding entity, double score) {}

        List<ScoredCandidate> scoredList = new ArrayList<>();
        for (DocumentEmbedding candidate : candidates) {
            float[] candVector = candidate.getEmbeddingVector();
            double score = EmbeddingService.cosineSimilarity(queryVector, candVector);
            if (score >= minScore) {
                scoredList.add(new ScoredCandidate(candidate, score));
            }
        }

        // 4. Order descending by similarity score and limit to topK
        scoredList.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed());

        List<RetrievedPassage> passages = new ArrayList<>();
        int citationIdx = 1;
        for (int i = 0; i < Math.min(topK, scoredList.size()); i++) {
            ScoredCandidate sc = scoredList.get(i);
            DocumentEmbedding de = sc.entity();
            passages.add(new RetrievedPassage(
                    de.getDocumentId(),
                    de.getDocumentTitle(),
                    de.getChunkIndex(),
                    de.getText(),
                    Math.round(sc.score() * 10000.0) / 10000.0,
                    citationIdx++
            ));
        }

        return new RagRetrieveResponse(
                passages,
                !passages.isEmpty(),
                embeddingService.getModelName()
        );
    }
}
