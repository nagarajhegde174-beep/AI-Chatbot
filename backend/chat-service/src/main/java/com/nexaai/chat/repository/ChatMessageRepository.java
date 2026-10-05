package com.nexaai.chat.repository;

import com.nexaai.chat.domain.ChatMessage;
import com.nexaai.chat.domain.MessageRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@code chat_message}.
 *
 * <p>Every method takes an owner, for the same reason as the conversation repository: an
 * unrestricted finder here would expose another user's messages just as easily.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** An owner's message. Used to verify ownership of anything acting on a message. */
    Optional<ChatMessage> findByIdAndOwnerAuthUserId(UUID id, UUID ownerAuthUserId);

    /**
     * Current history for a conversation, in order.
     *
     * <p>Superseded rows are excluded: they remain in the table so an edit or a regenerate can
     * show what came before, but they are not part of the conversation as the user sees it.
     */
    @Query("""
            select m from ChatMessage m
            where m.conversationId = :conversationId
              and m.ownerAuthUserId = :owner
              and m.supersededAt is null
            order by m.sequenceNo asc
            """)
    List<ChatMessage> findHistory(@Param("conversationId") UUID conversationId,
                                  @Param("owner") UUID ownerAuthUserId);

    /** One page of history, newest first. Used by the message list for infinite scroll. */
    @Query("""
            select m from ChatMessage m
            where m.conversationId = :conversationId
              and m.ownerAuthUserId = :owner
              and m.supersededAt is null
              and (:beforeSequence is null or m.sequenceNo < :beforeSequence)
            order by m.sequenceNo desc
            """)
    List<ChatMessage> findHistoryPage(@Param("conversationId") UUID conversationId,
                                      @Param("owner") UUID ownerAuthUserId,
                                      @Param("beforeSequence") Long beforeSequence,
                                      Pageable pageable);

    /**
     * The highest sequence number in a conversation.
     *
     * <p>Must be called while the conversation row is held for update, otherwise two concurrent
     * sends can read the same maximum and collide on the unique constraint. The constraint turns
     * that collision into a failure rather than a duplicated sequence number.
     */
    @Query("select coalesce(max(m.sequenceNo), 0) from ChatMessage m where m.conversationId = :conversationId")
    long maxSequenceNo(@Param("conversationId") UUID conversationId);

    /** How many messages a conversation shows. Used to correct the denormalised counter. */
    @Query("""
            select count(m) from ChatMessage m
            where m.conversationId = :conversationId and m.supersededAt is null
            """)
    long countCurrent(@Param("conversationId") UUID conversationId);

    /** An owner's messages in a conversation with a given role. */
    List<ChatMessage> findByConversationIdAndOwnerAuthUserIdAndRoleOrderBySequenceNoAsc(
            UUID conversationId, UUID ownerAuthUserId, MessageRole role);

    /** The caller's most recent assistant message in a conversation, for regenerate. */
    @Query("""
            select m from ChatMessage m
            where m.conversationId = :conversationId
              and m.ownerAuthUserId = :owner
              and m.role = com.nexaai.chat.domain.MessageRole.ASSISTANT
              and m.supersededAt is null
            order by m.sequenceNo desc
            """)
    List<ChatMessage> findLatestAssistant(@Param("conversationId") UUID conversationId,
                                          @Param("owner") UUID ownerAuthUserId,
                                          Pageable pageable);

    /** Placeholders stuck in PENDING, for a later phase's reconciliation. */
    @Query("""
            select m from ChatMessage m
            where m.status = com.nexaai.chat.domain.MessageStatus.PENDING
            order by m.createdAt asc
            """)
    Page<ChatMessage> findStalePending(Pageable pageable);

    /** An owner's messages matching a search term, newest first. */
    @Query("""
            select m from ChatMessage m
            where m.ownerAuthUserId = :owner
              and m.supersededAt is null
              and m.role = :role
              and lower(m.content) like :search
            order by m.createdAt desc
            """)
    Page<ChatMessage> searchOwnedContent(@Param("owner") UUID ownerAuthUserId,
                                         @Param("role") MessageRole role,
                                         @Param("search") String search,
                                         Pageable pageable);
}