package com.nexaai.chat.repository;

import com.nexaai.chat.domain.ConversationModelHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationModelHistoryRepository extends JpaRepository<ConversationModelHistory, UUID> {
    List<ConversationModelHistory> findByConversationIdOrderByChangedAtDesc(UUID conversationId);
}
