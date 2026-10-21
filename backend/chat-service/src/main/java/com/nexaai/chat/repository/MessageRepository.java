package com.nexaai.chat.repository;

import com.nexaai.chat.domain.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdOrderBySequenceNumberAsc(UUID conversationId);

    Page<Message> findByConversationIdOrderBySequenceNumberAsc(UUID conversationId, Pageable pageable);

    Optional<Message> findTopByConversationIdOrderBySequenceNumberDesc(UUID conversationId);

    Optional<Message> findByIdAndConversationId(UUID id, UUID conversationId);

    long countByConversationId(UUID conversationId);

    /**
     * Fetches the N most-recent messages for a conversation (desc order).
     * Callers should reverse the result to get chronological order.
     */
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :convId ORDER BY m.sequenceNumber DESC")
    List<Message> findRecentByConversationId(@Param("convId") UUID conversationId, Pageable pageable);

    /**
     * Fetches all messages with sequenceNumber strictly greater than the
     * watermark, in ascending order. Used to identify turns not yet in the
     * context summary.
     */
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :convId AND m.sequenceNumber > :seq ORDER BY m.sequenceNumber ASC")
    List<Message> findByConversationIdAndSequenceNumberAfter(
            @Param("convId") UUID conversationId,
            @Param("seq") int sequenceNumber);

    /**
     * Deletes all messages belonging to a conversation. Used for clear-memory.
     */
    void deleteByConversationId(UUID conversationId);
}
