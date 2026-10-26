package com.nexaai.rag.repository;

import com.nexaai.rag.domain.DocumentEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentEmbeddingRepository extends JpaRepository<DocumentEmbedding, UUID> {

    List<DocumentEmbedding> findByUserId(UUID userId);

    List<DocumentEmbedding> findByUserIdAndDocumentIdIn(UUID userId, List<UUID> documentIds);

    List<DocumentEmbedding> findByUserIdAndDocumentIdOrderByChunkIndexAsc(UUID userId, UUID documentId);

    @Modifying
    @Query("DELETE FROM DocumentEmbedding de WHERE de.userId = :userId AND de.documentId = :documentId")
    void deleteByUserIdAndDocumentId(@Param("userId") UUID userId, @Param("documentId") UUID documentId);

    long countByUserIdAndDocumentId(UUID userId, UUID documentId);

    long countByDocumentId(UUID documentId);
}
