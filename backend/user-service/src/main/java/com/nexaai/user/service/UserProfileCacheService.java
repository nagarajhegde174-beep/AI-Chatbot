package com.nexaai.user.service;

import com.nexaai.user.dto.response.UserProfileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Ephemeral Redis cache for frequently accessed user profiles.
 * Adheres to ADR-005 and RULES §7: losing Redis keys degrades performance, never correctness.
 */
@Service
public class UserProfileCacheService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileCacheService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "user:profile:";

    private final RedisTemplate<String, Object> redisTemplate;

    public UserProfileCacheService(@Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<UserProfileResponse> get(UUID userId) {
        if (redisTemplate == null || userId == null) {
            return Optional.empty();
        }
        try {
            Object cached = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
            if (cached instanceof UserProfileResponse response) {
                log.debug("Cache hit for user profile {}", userId);
                return Optional.of(response);
            }
        } catch (Exception e) {
            log.warn("Redis read failed for user:profile:{}, falling back to database: {}", userId, e.getMessage());
        }
        return Optional.empty();
    }

    public void put(UUID userId, UserProfileResponse profile) {
        if (redisTemplate == null || userId == null || profile == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + userId, profile, CACHE_TTL);
            log.debug("Cached user profile {}", userId);
        } catch (Exception e) {
            log.warn("Redis write failed for user:profile:{}: {}", userId, e.getMessage());
        }
    }

    public void evict(UUID userId) {
        if (redisTemplate == null || userId == null) {
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + userId);
            log.debug("Evicted user profile cache for {}", userId);
        } catch (Exception e) {
            log.warn("Redis eviction failed for user:profile:{}: {}", userId, e.getMessage());
        }
    }
}
