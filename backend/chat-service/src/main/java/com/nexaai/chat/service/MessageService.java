package com.nexaai.chat.service;

import com.nexaai.chat.domain.*;
import com.nexaai.chat.dto.*;
import com.nexaai.chat.exception.ConversationAccessDeniedException;
import com.nexaai.chat.exception.ConversationNotFoundException;
import com.nexaai.chat.exception.MessageNotFoundException;
import com.nexaai.chat.repository.ConversationRepository;
import com.nexaai.chat.repository.MessageCitationRepository;
import com.nexaai.chat.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class MessageService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageCitationRepository citationRepository;
    private final AiClientBoundary aiClientBoundary;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ChatEventPublisher eventPublisher;

    public MessageService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            MessageCitationRepository citationRepository,
            AiClientBoundary aiClientBoundary
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.citationRepository = citationRepository;
        this.aiClientBoundary = aiClientBoundary;
    }

    /**
     * List all messages for conversation
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(UUID conversationId, UUID userId, boolean isAdmin) {
        verifyAccess(conversationId, userId, isAdmin);
        return messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId).stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());
    }

    /**
     * 8, 9, 10: Send message, store user message, invoke AI integration boundary, store AI response placeholder
     */
    public MessageResponse sendMessage(UUID conversationId, UUID userId, boolean isAdmin, SendMessageRequest request) {
        Conversation conversation = verifyAccess(conversationId, userId, isAdmin);

        int nextSeq = messageRepository.findTopByConversationIdOrderBySequenceNumberDesc(conversationId)
                .map(m -> m.getSequenceNumber() + 1)
                .orElse(1);

        String model = (request.model() != null && !request.model().isBlank())
                ? request.model().trim()
                : conversation.getModel();

        // 9. Store user message
        Message userMessage = new Message(
                UUID.randomUUID(),
                conversation,
                userId,
                MessageRole.USER,
                request.content().trim(),
                nextSeq,
                model
        );
        messageRepository.save(userMessage);

        if (eventPublisher != null) {
            eventPublisher.publishMessageSent(userMessage, null);
            eventPublisher.publishAiRequested(conversationId, userId, model, userMessage.getContent().length(), null);
        }

        // 10. Store AI response placeholder/integration boundary
        AiClientBoundary.AiResponseResult aiResult = aiClientBoundary.generateResponse(conversation, userMessage);

        Message assistantMessage = new Message(
                UUID.randomUUID(),
                conversation,
                userId,
                MessageRole.ASSISTANT,
                aiResult.content(),
                nextSeq + 1,
                aiResult.model()
        );
        assistantMessage.setPromptTokens(aiResult.promptTokens());
        assistantMessage.setCompletionTokens(aiResult.completionTokens());
        assistantMessage.setTotalTokens(aiResult.totalTokens());
        assistantMessage.setStatus(MessageStatus.COMPLETED);

        Message savedAssistant = messageRepository.save(assistantMessage);

        if (eventPublisher != null) {
            eventPublisher.publishMessageSent(savedAssistant, null);
        }

        // Update conversation activity
        conversation.setLastMessageAt(savedAssistant.getCreatedAt());
        conversation.setUpdatedAt(savedAssistant.getCreatedAt());
        conversationRepository.save(conversation);

        return toMessageResponse(savedAssistant);
    }

    /**
     * 11. Regenerate foundation: triggers regeneration of assistant response
     */
    public MessageResponse regenerateMessage(UUID conversationId, UUID messageId, UUID userId, boolean isAdmin) {
        Conversation conversation = verifyAccess(conversationId, userId, isAdmin);

        Message targetMessage = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new MessageNotFoundException(messageId));

        Message userTurn;
        if (targetMessage.getRole() == MessageRole.USER) {
            userTurn = targetMessage;
        } else {
            // Find preceding user message
            List<Message> allMessages = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId);
            int idx = allMessages.indexOf(targetMessage);
            userTurn = (idx > 0) ? allMessages.get(idx - 1) : targetMessage;
        }

        AiClientBoundary.AiResponseResult aiResult = aiClientBoundary.generateResponse(conversation, userTurn);

        int nextSeq = messageRepository.findTopByConversationIdOrderBySequenceNumberDesc(conversationId)
                .map(m -> m.getSequenceNumber() + 1)
                .orElse(1);

        Message newAssistantMessage = new Message(
                UUID.randomUUID(),
                conversation,
                userId,
                MessageRole.ASSISTANT,
                aiResult.content(),
                nextSeq,
                aiResult.model()
        );
        newAssistantMessage.setPromptTokens(aiResult.promptTokens());
        newAssistantMessage.setCompletionTokens(aiResult.completionTokens());
        newAssistantMessage.setTotalTokens(aiResult.totalTokens());
        newAssistantMessage.setStatus(MessageStatus.COMPLETED);
        newAssistantMessage.setRegenerated(true);
        newAssistantMessage.setOriginalMessageId(targetMessage.getId());

        Message saved = messageRepository.save(newAssistantMessage);

        conversation.setLastMessageAt(saved.getCreatedAt());
        conversation.setUpdatedAt(saved.getCreatedAt());
        conversationRepository.save(conversation);

        return toMessageResponse(saved);
    }

    /**
     * 12. Edit/resend foundation: edits user message content and regenerates assistant response
     */
    public MessageResponse editAndResend(UUID conversationId, UUID messageId, UUID userId, boolean isAdmin, EditMessageRequest request) {
        Conversation conversation = verifyAccess(conversationId, userId, isAdmin);

        Message userMessage = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new MessageNotFoundException(messageId));

        if (userMessage.getRole() != MessageRole.USER) {
            throw new IllegalArgumentException("Only user messages can be edited and resent.");
        }

        userMessage.setContent(request.content().trim());
        userMessage.setUpdatedAt(Instant.now());
        messageRepository.save(userMessage);

        // Generate updated response
        AiClientBoundary.AiResponseResult aiResult = aiClientBoundary.generateResponse(conversation, userMessage);

        int nextSeq = messageRepository.findTopByConversationIdOrderBySequenceNumberDesc(conversationId)
                .map(m -> m.getSequenceNumber() + 1)
                .orElse(1);

        Message newAssistantMessage = new Message(
                UUID.randomUUID(),
                conversation,
                userId,
                MessageRole.ASSISTANT,
                aiResult.content(),
                nextSeq,
                aiResult.model()
        );
        newAssistantMessage.setPromptTokens(aiResult.promptTokens());
        newAssistantMessage.setCompletionTokens(aiResult.completionTokens());
        newAssistantMessage.setTotalTokens(aiResult.totalTokens());
        newAssistantMessage.setStatus(MessageStatus.COMPLETED);

        Message saved = messageRepository.save(newAssistantMessage);

        conversation.setLastMessageAt(saved.getCreatedAt());
        conversation.setUpdatedAt(saved.getCreatedAt());
        conversationRepository.save(conversation);

        return toMessageResponse(saved);
    }

    /**
     * 13. Feedback on message
     */
    public MessageResponse submitFeedback(UUID conversationId, UUID messageId, UUID userId, boolean isAdmin, MessageFeedbackRequest request) {
        verifyAccess(conversationId, userId, isAdmin);

        Message message = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new MessageNotFoundException(messageId));

        message.setFeedback(request.feedback());
        message.setFeedbackComment(request.comment());
        message.setUpdatedAt(Instant.now());

        Message updated = messageRepository.save(message);
        return toMessageResponse(updated);
    }

    /**
     * Delete message
     */
    public void deleteMessage(UUID conversationId, UUID messageId, UUID userId, boolean isAdmin) {
        verifyAccess(conversationId, userId, isAdmin);

        Message message = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new MessageNotFoundException(messageId));

        messageRepository.delete(message);
    }

    private Conversation verifyAccess(UUID conversationId, UUID userId, boolean isAdmin) {
        Conversation conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        if (!isAdmin && !conversation.getUserId().equals(userId)) {
            throw new ConversationAccessDeniedException(conversationId);
        }
        return conversation;
    }

    public MessageResponse toMessageResponse(Message m) {
        List<MessageCitationResponse> citations = citationRepository.findByMessageId(m.getId()).stream()
                .map(c -> new MessageCitationResponse(c.getId(), c.getDocumentId(), c.getTitle(), c.getChunkIndex(), c.getExcerpt()))
                .collect(Collectors.toList());

        return new MessageResponse(
                m.getId(),
                m.getConversation().getId(),
                m.getUserId(),
                m.getRole(),
                m.getContent(),
                m.getModel(),
                m.getStatus(),
                m.getPromptTokens(),
                m.getCompletionTokens(),
                m.getTotalTokens(),
                m.getSequenceNumber(),
                m.getFeedback(),
                m.getFeedbackComment(),
                m.isRegenerated(),
                m.getOriginalMessageId(),
                m.getCreatedAt(),
                m.getUpdatedAt(),
                citations
        );
    }
}
