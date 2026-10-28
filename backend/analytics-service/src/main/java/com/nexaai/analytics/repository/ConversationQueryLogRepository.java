package com.nexaai.analytics.repository;

import com.nexaai.analytics.domain.ConversationQueryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationQueryLogRepository extends JpaRepository<ConversationQueryLog, UUID> {

    List<ConversationQueryLog> findByUserId(UUID userId);

    @Query("SELECT COUNT(c) FROM ConversationQueryLog c WHERE c.createdAt >= :start AND c.createdAt < :end")
    long countBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COUNT(c) FROM ConversationQueryLog c WHERE c.createdAt < :cutoff")
    long countBefore(@Param("cutoff") Instant cutoff);

    List<ConversationQueryLog> findByCreatedAtAfter(Instant after);
}
