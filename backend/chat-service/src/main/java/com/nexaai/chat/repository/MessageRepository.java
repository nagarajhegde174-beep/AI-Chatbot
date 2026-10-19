package com.nexaai.chat.repository;

import com.nexaai.chat.domain.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
