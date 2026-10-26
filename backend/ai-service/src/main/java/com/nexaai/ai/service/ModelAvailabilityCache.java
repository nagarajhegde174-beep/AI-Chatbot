package com.nexaai.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Ephemeral Redis cache for AI model availability.
 * Caches model health status with a short TTL (30s) to avoid slamming provider APIs.
 */
@Service
public class ModelAvailabilityCache {

    private static final Logger log = LoggerFactory.getLogger(ModelAvailabilityCache.class);
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final String KEY_PREFIX = "ai:models:availability:";

    private final StringRedisTemplate stringRedisTemplate;

    public ModelAvailabilityCache(@Autowired(required = false) StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Optional<Boolean> isAvailable(String modelId) {
        if (stringRedisTemplate == null || modelId == null) {
            return Optional.empty();
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(KEY_PREFIX + modelId);
            if (value != null) {
                return Optional.of(Boolean.parseBoolean(value));
            }
        } catch (Exception e) {
            log.warn("Redis read failed for model availability: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public void setAvailability(String modelId, boolean available) {
        if (stringRedisTemplate == null || modelId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + modelId, String.valueOf(available), TTL);
        } catch (Exception e) {
            log.warn("Redis write failed for model availability: {}", e.getMessage());
        }
    }
}
