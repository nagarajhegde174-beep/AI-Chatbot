package com.nexaai.chat.service;

import com.nexaai.chat.dto.ConversationSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ephemeral Redis caching for conversation summaries in chat-service.
 * Redis is never the system of record: failures degrade performance, never correctness.
 */
@Service
public class ChatCacheService {

    private static final Logger log = LoggerFactory.getLogger(ChatCacheService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "chat:conversations:";

    private final RedisTemplate<String, Object> redisTemplate;

    public ChatCacheService(@Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @SuppressWarnings("unchecked")
    public Optional<List<ConversationSummaryResponse>> getConversations(UUID userId) {
        if (redisTemplate == null || userId == null) {
            return Optional.empty();
        }
        try {
            Object cached = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
            if (cached instanceof List<?> list) {
                log.debug("Cache hit for user conversations {}", userId);
                return Optional.of((List<ConversationSummaryResponse>) list);
            }
        } catch (Exception e) {
            log.warn("Redis read failed for chat:conversations:{}, falling back to DB: {}", userId, e.getMessage());
        }
        return Optional.empty();
    }

    public void putConversations(UUID userId, List<ConversationSummaryResponse> conversations) {
        if (redisTemplate == null || userId == null || conversations == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + userId, conversations, CACHE_TTL);
            log.debug("Cached conversations for user {}", userId);
        } catch (Exception e) {
            log.warn("Redis write failed for chat:conversations:{}: {}", userId, e.getMessage());
        }
    }

    public void evictConversations(UUID userId) {
        if (redisTemplate == null || userId == null) {
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + userId);
            log.debug("Evicted conversation cache for user {}", userId);
        } catch (Exception e) {
            log.warn("Redis eviction failed for chat:conversations:{}: {}", userId, e.getMessage());
        }
    }
}
