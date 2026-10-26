package com.nexaai.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.chat.domain.Conversation;
import com.nexaai.chat.domain.ConversationModelHistory;
import com.nexaai.chat.domain.Message;
import com.nexaai.chat.dto.*;
import com.nexaai.chat.exception.ConversationAccessDeniedException;
import com.nexaai.chat.exception.ConversationNotFoundException;
import com.nexaai.chat.repository.ConversationModelHistoryRepository;
import com.nexaai.chat.repository.ConversationRepository;
import com.nexaai.chat.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConversationModelHistoryRepository modelHistoryRepository;
    private final ObjectMapper objectMapper;
    private final ContextWindowService contextWindowService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ChatEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ChatCacheService cacheService;

    public ConversationService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ConversationModelHistoryRepository modelHistoryRepository,
            ObjectMapper objectMapper,
            ContextWindowService contextWindowService
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.modelHistoryRepository = modelHistoryRepository;
        this.objectMapper = objectMapper;
        this.contextWindowService = contextWindowService;
    }

    /**
     * 1. Create conversation
     */
    public ConversationResponse createConversation(UUID userId, CreateConversationRequest request) {
        String title = (request != null && request.title() != null && !request.title().isBlank())
                ? request.title().trim()
                : "New Conversation";
        String model = (request != null && request.model() != null && !request.model().isBlank())
                ? request.model().trim()
                : "gpt-4o-mini";

        Conversation conversation = new Conversation(UUID.randomUUID(), userId, title, model);
        if (request != null) {
            if (request.systemPrompt() != null) {
                conversation.setSystemPrompt(request.systemPrompt().trim());
            }
            if (request.ragEnabled() != null) {
                conversation.setRagEnabled(request.ragEnabled());
            }
        }

        Conversation saved = conversationRepository.save(conversation);
        if (eventPublisher != null) {
            eventPublisher.publishConversationCreated(saved, null);
        }
        if (cacheService != null) {
            cacheService.evictConversations(userId);
        }
        return toConversationResponse(saved);
    }

    /**
     * 2. List conversations for user (optionally filtering by archive status)
     */
    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> listConversations(UUID userId, Boolean archived) {
        if (archived == null && cacheService != null) {
            var cached = cacheService.getConversations(userId);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        List<Conversation> conversations;
        if (archived != null) {
            conversations = conversationRepository.findByUserIdAndIsArchivedAndDeletedAtIsNullOrderByUpdatedAtDesc(userId, archived);
        } else {
            conversations = conversationRepository.findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId);
        }
        List<ConversationSummaryResponse> results = conversations.stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());

        if (archived == null && cacheService != null) {
            cacheService.putConversations(userId, results);
        }
        return results;
    }

    /**
     * 3. Get conversation by ID (with ownership check or admin access)
     */
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID conversationId, UUID userId, boolean isAdmin) {
        Conversation conversation = findAndVerifyAccess(conversationId, userId, isAdmin);
        return toConversationResponse(conversation);
    }

    /**
     * 4. Rename / Update conversation
     */
    public ConversationResponse updateConversation(UUID conversationId, UUID userId, boolean isAdmin, UpdateConversationRequest request) {
        Conversation conversation = findAndVerifyAccess(conversationId, userId, isAdmin);

        if (request.title() != null && !request.title().isBlank()) {
            conversation.setTitle(request.title().trim());
        }

        if (request.model() != null && !request.model().isBlank() && !request.model().equals(conversation.getModel())) {
            String oldModel = conversation.getModel();
            conversation.setModel(request.model().trim());
            modelHistoryRepository.save(new ConversationModelHistory(
                    UUID.randomUUID(),
                    conversation,
                    oldModel,
                    conversation.getModel(),
                    userId
            ));
        }

        if (request.systemPrompt() != null) {
            conversation.setSystemPrompt(request.systemPrompt().trim());
        }

        if (request.ragEnabled() != null) {
            conversation.setRagEnabled(request.ragEnabled());
        }

        if (request.isArchived() != null) {
            conversation.setArchived(request.isArchived());
        }

        conversation.setUpdatedAt(Instant.now());
        Conversation updated = conversationRepository.save(conversation);
        if (cacheService != null) {
            cacheService.evictConversations(userId);
        }
        return toConversationResponse(updated);
    }

    /**
     * 5. Delete conversation (soft delete)
     */
    public void deleteConversation(UUID conversationId, UUID userId, boolean isAdmin) {
        Conversation conversation = findAndVerifyAccess(conversationId, userId, isAdmin);
        conversation.setDeletedAt(Instant.now());
        conversationRepository.save(conversation);
        if (cacheService != null) {
            cacheService.evictConversations(userId);
        }
    }

    /**
     * 6. Archive / Unarchive conversation
     */
    public ConversationResponse setArchiveStatus(UUID conversationId, UUID userId, boolean isAdmin, boolean isArchived) {
        Conversation conversation = findAndVerifyAccess(conversationId, userId, isAdmin);
        conversation.setArchived(isArchived);
        conversation.setUpdatedAt(Instant.now());
        Conversation updated = conversationRepository.save(conversation);
        if (cacheService != null) {
            cacheService.evictConversations(userId);
        }
        return toConversationResponse(updated);
    }

    /**
     * Phase 5 — Clear conversation memory.
     * Deletes all messages and resets the rolling context summary while
     * preserving the conversation metadata (title, model, system prompt).
     */
    public void clearMemory(UUID conversationId, UUID userId, boolean isAdmin) {
        Conversation conversation = findAndVerifyAccess(conversationId, userId, isAdmin);
        contextWindowService.clearMemory(conversation);
        if (cacheService != null) {
            cacheService.evictConversations(userId);
        }
    }

    /**
     * 7. Search conversations and messages for user
     */
    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> searchConversations(UUID userId, String query) {
        if (query == null || query.isBlank()) {
            return listConversations(userId, null);
        }
        return conversationRepository.searchConversations(userId, query.trim()).stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());
    }

    /**
     * 14. Export foundation (JSON, Markdown, Plain Text)
     */
    @Transactional(readOnly = true)
    public ConversationExportResponse exportConversation(UUID conversationId, UUID userId, boolean isAdmin, String format) {
        Conversation conversation = findAndVerifyAccess(conversationId, userId, isAdmin);
        List<Message> messages = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId);

        String exportFormat = (format != null && !format.isBlank()) ? format.toLowerCase() : "json";
        String content;

        if ("markdown".equals(exportFormat) || "md".equals(exportFormat)) {
            content = formatAsMarkdown(conversation, messages);
            exportFormat = "markdown";
        } else if ("text".equals(exportFormat) || "txt".equals(exportFormat)) {
            content = formatAsText(conversation, messages);
            exportFormat = "text";
        } else {
            content = formatAsJson(conversation, messages);
            exportFormat = "json";
        }

        return new ConversationExportResponse(conversation.getId(), conversation.getTitle(), exportFormat, Instant.now(), content);
    }

    /**
     * Admin view of all active conversations
     */
    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> listAllForAdmin() {
        return conversationRepository.findByDeletedAtIsNull(org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent()
                .stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());
    }

    public Conversation findAndVerifyAccess(UUID conversationId, UUID userId, boolean isAdmin) {
        Conversation conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        if (!isAdmin && !conversation.getUserId().equals(userId)) {
            throw new ConversationAccessDeniedException(conversationId);
        }
        return conversation;
    }

    public ConversationResponse toConversationResponse(Conversation c) {
        long count = messageRepository.countByConversationId(c.getId());
        return new ConversationResponse(
                c.getId(),
                c.getUserId(),
                c.getTitle(),
                c.getModel(),
                c.getSystemPrompt(),
                c.isRagEnabled(),
                c.isArchived(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getLastMessageAt(),
                count
        );
    }

    public ConversationSummaryResponse toSummaryResponse(Conversation c) {
        long count = messageRepository.countByConversationId(c.getId());
        return new ConversationSummaryResponse(
                c.getId(),
                c.getUserId(),
                c.getTitle(),
                c.getModel(),
                c.isArchived(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getLastMessageAt(),
                count
        );
    }

    private String formatAsMarkdown(Conversation c, List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(c.getTitle()).append("\n\n");
        sb.append("*Created: ").append(c.getCreatedAt()).append(" | Model: ").append(c.getModel()).append("*\n\n---\n\n");
        for (Message m : messages) {
            sb.append("### ").append(m.getRole()).append(" (").append(m.getCreatedAt()).append(")\n\n");
            sb.append(m.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    private String formatAsText(Conversation c, List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("TITLE: ").append(c.getTitle()).append("\n");
        sb.append("MODEL: ").append(c.getModel()).append("\n");
        sb.append("CREATED: ").append(c.getCreatedAt()).append("\n");
        sb.append("--------------------------------------------------\n\n");
        for (Message m : messages) {
            sb.append("[").append(m.getRole()).append("]: ").append(m.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    private String formatAsJson(Conversation c, List<Message> messages) {
        try {
            var map = new java.util.LinkedHashMap<String, Object>();
            map.put("conversationId", c.getId());
            map.put("title", c.getTitle());
            map.put("model", c.getModel());
            map.put("createdAt", c.getCreatedAt().toString());
            map.put("messages", messages.stream().map(m -> {
                var mm = new java.util.LinkedHashMap<String, Object>();
                mm.put("id", m.getId());
                mm.put("role", m.getRole().name());
                mm.put("content", m.getContent());
                mm.put("createdAt", m.getCreatedAt().toString());
                return mm;
            }).collect(Collectors.toList()));
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
