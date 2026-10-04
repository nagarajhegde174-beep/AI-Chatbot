package com.nexaai.auth.repository;

import com.nexaai.auth.domain.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the outbox.
 *
 * <p>Only unpublished rows are read, and always through a
 * {@link Pageable} limit so a backlog cannot be loaded whole.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

    long countByPublishedAtIsNull();
}