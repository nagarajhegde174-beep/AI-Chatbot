package com.nexaai.analytics.repository;

import com.nexaai.analytics.domain.DocumentReadModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentReadModelRepository extends JpaRepository<DocumentReadModel, UUID> {

    List<DocumentReadModel> findByUserId(UUID userId);

    List<DocumentReadModel> findByUserIdAndStatusNot(UUID userId, String status);

    long countByUserIdAndStatusNot(UUID userId, String status);

    @Query("SELECT SUM(d.fileSizeBytes) FROM DocumentReadModel d WHERE d.userId = :userId AND d.status <> 'DELETED'")
    Long sumStorageBytesByUserId(@Param("userId") UUID userId);

    long countByStatus(String status);

    @Query("SELECT SUM(d.chunkCount) FROM DocumentReadModel d WHERE d.status <> 'DELETED'")
    Long sumTotalChunks();

    @Query("SELECT SUM(d.embeddingCount) FROM DocumentReadModel d WHERE d.status <> 'DELETED'")
    Long sumTotalEmbeddings();
}
