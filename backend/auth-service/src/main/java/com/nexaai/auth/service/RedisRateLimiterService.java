package com.nexaai.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Foundation for Redis-based rate limiting on sensitive authentication operations.
 * Fails open (allows request) if Redis is unavailable, so cache outages do not cause service outages (RULES.md §7).
 */
@Service
public class RedisRateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiterService.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisRateLimiterService(@Autowired(required = false) StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Legacy login rate-limit check.
     */
    public boolean isAllowed(String key, int maxAllowed, Duration window) {
        return isAllowed("login", key, maxAllowed, window);
    }

    /**
     * Increment and check if the count for action:key exceeds maxAllowed within window.
     *
     * @param action      the operation namespace (e.g. "login", "password_reset", "sensitive")
     * @param key         the rate limit key (e.g. ip address or email)
     * @param maxAllowed  the maximum allowed requests
     * @param window      the time window
     * @return true if request is permitted, false if rate limited
     */
    public boolean isAllowed(String action, String key, int maxAllowed, Duration window) {
        if (stringRedisTemplate == null || key == null) {
            return true;
        }

        String redisKey = "auth:ratelimit:" + action + ":" + key;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(redisKey, window);
            }
            return count != null && count <= maxAllowed;
        } catch (Exception ex) {
            log.warn("Redis rate-limiter unavailable, failing open: {}", ex.getMessage());
            return true;
        }
    }

    public void reset(String key) {
        reset("login", key);
    }

    public void reset(String action, String key) {
        if (stringRedisTemplate == null || key == null) return;
        try {
            stringRedisTemplate.delete("auth:ratelimit:" + action + ":" + key);
        } catch (Exception ex) {
            log.warn("Failed to reset rate limit key: {}", ex.getMessage());
        }
    }
}
