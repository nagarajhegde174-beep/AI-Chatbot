package com.nexaai.chat.repository;

import com.nexaai.chat.domain.ConversationMemory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Conversation memory.
 *
 * <p><strong>Every finder takes the owner.</strong> There is deliberately no
 * {@code findByConversationId} — the single-argument form would be the convenient one to reach for
 * and the one that leaks. Isolation is a property of the repository's API rather than of a
 * predicate someone has to remember to write.
 *
 * <p>The unique constraint on {@code conversation_id} means at most one row exists per
 * conversation, so the "upsert" path is load-or-create followed by a save inside a transaction.
 */
public interface ConversationMemoryRepository extends JpaRepository<ConversationMemory, UUID> {

    Optional<ConversationMemory> findByConversationIdAndOwnerAuthUserId(UUID conversationId,
                                                                        UUID ownerAuthUserId);

    long countByConversationIdAndOwnerAuthUserId(UUID conversationId, UUID ownerAuthUserId);
}