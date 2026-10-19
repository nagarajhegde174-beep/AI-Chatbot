package com.nexaai.chat.repository;

import com.nexaai.chat.domain.MessageCitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageCitationRepository extends JpaRepository<MessageCitation, UUID> {
    List<MessageCitation> findByMessageId(UUID messageId);
}
