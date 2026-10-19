package com.nexaai.chat.repository;

import com.nexaai.chat.domain.Conversation;
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
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID userId);

    List<Conversation> findByUserIdAndIsArchivedAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID userId, boolean isArchived);

    Page<Conversation> findByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

    Optional<Conversation> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Conversation> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    @Query("SELECT DISTINCT c FROM Conversation c LEFT JOIN c.messages m " +
           "WHERE c.userId = :userId AND c.deletedAt IS NULL AND " +
           "(LOWER(c.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY c.updatedAt DESC")
    List<Conversation> searchConversations(@Param("userId") UUID userId, @Param("query") String query);

    Page<Conversation> findByDeletedAtIsNull(Pageable pageable);
}
