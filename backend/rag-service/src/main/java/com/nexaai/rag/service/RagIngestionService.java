package com.nexaai.rag.service;

import com.nexaai.rag.domain.DocumentEmbedding;
import com.nexaai.rag.repository.DocumentEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RagIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RagIngestionService.class);

    private final DocumentEmbeddingRepository embeddingRepository;
    private final EmbeddingService embeddingService;
    private final RagChunkingService chunkingService;
    private final RagEventPublisher eventPublisher;

    public RagIngestionService(
            DocumentEmbeddingRepository embeddingRepository,
            EmbeddingService embeddingService,
            RagChunkingService chunkingService,
            RagEventPublisher eventPublisher
    ) {
        this.embeddingRepository = embeddingRepository;
        this.embeddingService = embeddingService;
        this.chunkingService = chunkingService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Ingest chunks, generate embeddings, and persist vectors with tenant scoping.
     */
    public long ingestChunks(UUID userId, UUID documentId, String documentTitle, List<String> chunks, String correlationId) {
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }

        // Delete existing vectors for this document and user (ensures idempotency on replay/reprocess)
        embeddingRepository.deleteByUserIdAndDocumentId(userId, documentId);

        String model = embeddingService.getModelName();
        List<DocumentEmbedding> entities = new ArrayList<>(chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            float[] vector = embeddingService.embed(chunkText);

            DocumentEmbedding entity = new DocumentEmbedding(
                    UUID.randomUUID(),
                    userId,
                    documentId,
                    documentTitle,
                    i,
                    chunkText,
                    vector,
                    model
            );
            entities.add(entity);
        }

        embeddingRepository.saveAll(entities);
        log.info("Persisted {} vector embeddings for document {} (user {})", entities.size(), documentId, userId);

        eventPublisher.publishDocumentIndexed(documentId, userId, entities.size(), model, correlationId);
        return entities.size();
    }

    /**
     * Ingest raw text: chunk first, then embed.
     */
    public long ingestRawText(UUID userId, UUID documentId, String documentTitle, String rawText, String correlationId) {
        List<String> chunks = chunkingService.chunkText(rawText);
        return ingestChunks(userId, documentId, documentTitle, chunks, correlationId);
    }

    /**
     * Delete all embeddings for a document.
     */
    public void deleteDocumentVectors(UUID userId, UUID documentId) {
        embeddingRepository.deleteByUserIdAndDocumentId(userId, documentId);
        log.info("Deleted all embeddings for document {} and user {}", documentId, userId);
    }
}
