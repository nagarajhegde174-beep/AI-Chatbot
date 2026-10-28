package com.nexaai.analytics.repository;

import com.nexaai.analytics.domain.AiRequestMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AiRequestMetricRepository extends JpaRepository<AiRequestMetric, UUID> {

    List<AiRequestMetric> findByUserId(UUID userId);

    List<AiRequestMetric> findByUserIdAndCreatedAtAfter(UUID userId, Instant after);

    long countByUserId(UUID userId);

    long countByStatus(String status);

    @Query("SELECT COUNT(m) FROM AiRequestMetric m WHERE m.status <> 'SUCCESS'")
    long countErrors();

    @Query("SELECT SUM(m.totalTokens) FROM AiRequestMetric m")
    Long sumTotalTokens();

    @Query("SELECT SUM(m.totalTokens) FROM AiRequestMetric m WHERE m.userId = :userId")
    Long sumTotalTokensByUserId(@Param("userId") UUID userId);

    @Query("SELECT SUM(m.promptTokens) FROM AiRequestMetric m WHERE m.userId = :userId")
    Long sumPromptTokensByUserId(@Param("userId") UUID userId);

    @Query("SELECT SUM(m.completionTokens) FROM AiRequestMetric m WHERE m.userId = :userId")
    Long sumCompletionTokensByUserId(@Param("userId") UUID userId);

    @Query("SELECT AVG(m.latencyMs) FROM AiRequestMetric m WHERE m.status = 'SUCCESS'")
    Double averageLatencyMs();

    List<AiRequestMetric> findByCreatedAtAfter(Instant after);
}
