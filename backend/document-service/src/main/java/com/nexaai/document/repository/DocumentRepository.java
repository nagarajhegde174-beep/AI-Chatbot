package com.nexaai.document.repository;

import com.nexaai.document.domain.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<Document> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    Page<Document> findByUserIdAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(UUID userId, String title, Pageable pageable);
}
