package com.nexaai.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Foundation for Redis-based rate limiting on sensitive authentication operations.
 * Fails open (allows request) if Redis is unavailable, so cache outages do not cause service outages.
 */
@Service
public class RedisRateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiterService.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisRateLimiterService(@Autowired(required = false) StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Increment and check if the count for key exceeds maxAllowed within window.
     *
     * @param key         the rate limit key (e.g. ip address or email)
     * @param maxAllowed  the maximum allowed requests
     * @param window      the time window
     * @return true if request is permitted, false if rate limited
     */
    public boolean isAllowed(String key, int maxAllowed, Duration window) {
        if (stringRedisTemplate == null) {
            return true;
        }

        String redisKey = "auth:ratelimit:login:" + key;
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
        if (stringRedisTemplate == null) return;
        try {
            stringRedisTemplate.delete("auth:ratelimit:login:" + key);
        } catch (Exception ex) {
            log.warn("Failed to reset rate limit key: {}", ex.getMessage());
        }
    }
}
