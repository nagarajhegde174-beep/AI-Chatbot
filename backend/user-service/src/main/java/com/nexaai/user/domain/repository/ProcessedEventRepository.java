package com.nexaai.user.domain.repository;

import com.nexaai.user.domain.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    boolean existsByEventId(String eventId);

    @Modifying
    @Query("DELETE FROM ProcessedEvent e WHERE e.processedAt < :before")
    int deleteByProcessedAtBefore(@Param("before") Instant before);
}
