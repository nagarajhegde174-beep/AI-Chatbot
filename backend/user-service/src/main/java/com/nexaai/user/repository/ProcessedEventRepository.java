package com.nexaai.user.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@code processed_event}, the idempotency key for consumed Kafka events.
 *
 * <p>Kafka delivers at least once, so {@code auth.user.registered.v1} will arrive twice at some
 * point. Deduplicating here is what stops a replay from creating a second profile.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    boolean existsById(UUID eventId);
}