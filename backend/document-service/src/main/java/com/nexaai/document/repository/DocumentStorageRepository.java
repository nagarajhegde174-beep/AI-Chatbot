package com.nexaai.document.repository;

import com.nexaai.document.domain.DocumentStorage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentStorageRepository extends JpaRepository<DocumentStorage, UUID> {

    Optional<DocumentStorage> findByDocumentId(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}
