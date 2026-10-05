package com.nexaai.chat.repository;

import com.nexaai.chat.domain.ChatConversation;
import com.nexaai.chat.domain.ConversationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

/**
 * Repository for {@code chat_conversation}.
 *
 * <p><strong>Every method here takes an owner.</strong> There is deliberately no
 * {@code findById(UUID)} exposed to the service layer for a plain conversation lookup: an
 * unrestricted finder is one refactor away from being called from a controller with a path
 * variable, and that is how one service ends up serving another user's history.
 *
 * <p>The administrative listing is the single exception and is named {@code searchAll} to make it
 * conspicuous at every call site.
 */
public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

    /** An owner's conversation. The isolation predicate is part of the query, not the caller. */
    Optional<ChatConversation> findByIdAndOwnerAuthUserId(UUID id, UUID ownerAuthUserId);

    /**
     * Loads an owner's conversation for update.
     *
     * <p>The row lock serialises concurrent sends, which is what makes the
     * {@code max(sequence_no) + 1} computation safe without a retry loop.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChatConversation c where c.id = :id and c.ownerAuthUserId = :owner")
    Optional<ChatConversation> findOwnedForUpdate(@Param("id") UUID id,
                                                 @Param("owner") UUID ownerAuthUserId);

    /**
     * An owner's conversations, filtered, searched and paged.
     *
     * <p>A single query rather than filter-then-paginate in memory. Soft-deleted rows are
     * excluded here and in every other read, so a deleted conversation cannot reappear.
     */
    @Query("""
            select c from ChatConversation c
            where c.ownerAuthUserId = :owner
              and c.deletedAt is null
              and (:status is null or c.status = :status)
              and (:search is null or lower(c.title) like :search)
            """)
    Page<ChatConversation> searchOwned(@Param("owner") UUID ownerAuthUserId,
                                       @Param("status") ConversationStatus status,
                                       @Param("search") String search,
                                       Pageable pageable);

    /**
     * Searches across an owner's conversations by title OR by message content.
     *
     * <p><strong>LEFT JOIN, not inner.</strong> An inner join silently excludes every
     * conversation that has no messages yet, which is the state a conversation is in the moment
     * it is created and before the first message is sent. A user who creates a conversation,
     * renames it and searches for it must find it, so the message side of the join has to be
     * optional.
     *
     * <p>Separate from {@code searchOwned} because searching message content needs the join at
     * all, and a title-only search must not pay for it.
     */
    @Query(value = """
            select distinct c from ChatConversation c
            left join ChatMessage m on m.conversationId = c.id
            where c.ownerAuthUserId = :owner
              and c.deletedAt is null
              and (lower(c.title) like :search or lower(m.content) like :search)
            """,
            countQuery = """
            select count(distinct c) from ChatConversation c
            left join ChatMessage m on m.conversationId = c.id
            where c.ownerAuthUserId = :owner
              and c.deletedAt is null
              and (lower(c.title) like :search or lower(m.content) like :search)
            """)
    Page<ChatConversation> searchOwnedByContent(@Param("owner") UUID ownerAuthUserId,
                                                @Param("search") String search,
                                                Pageable pageable);

    /** An owner's conversation count, for the sidebar footer. */
    @Query("""
            select count(c) from ChatConversation c
            where c.ownerAuthUserId = :owner and c.deletedAt is null
            """)
    long countOwned(@Param("owner") UUID ownerAuthUserId);

    /**
     * ADMIN listing across all owners. Metadata only.
     *
     * <p>Named to be conspicuous. Cross-user access is metadata-only by design; see
     * {@code docs/SERVICE_CONTRACTS.md} §7 for why message content is not included here.
     */
    @Query("""
            select c from ChatConversation c
            where c.deletedAt is null
              and (:owner is null or c.ownerAuthUserId = :owner)
              and (:status is null or c.status = :status)
              and (:search is null or lower(c.title) like :search)
            """)
    Page<ChatConversation> searchAll(@Param("owner") UUID ownerAuthUserId,
                                     @Param("status") ConversationStatus status,
                                     @Param("search") String search,
                                     Pageable pageable);

    /** Cross-user counts for the administrative view. */
    @Query("select count(c) from ChatConversation c where c.deletedAt is null")
    long countNotDeleted();

    @Query("""
            select count(c) from ChatConversation c
            where c.deletedAt is null and c.status = :status
            """)
    long countByStatus(@Param("status") ConversationStatus status);
}