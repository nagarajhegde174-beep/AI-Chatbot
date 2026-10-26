package com.nexaai.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

/**
 * Foundation for Redis-based atomic usage counters.
 * Aggregates inference token usage per user and model.
 * Losing this cache degrades usage caching speed, never correctness (events in Kafka are the system of record).
 */
@Service
public class UsageCounterService {

    private static final Logger log = LoggerFactory.getLogger(UsageCounterService.class);
    private static final Duration USAGE_TTL = Duration.ofDays(30);

    private final StringRedisTemplate stringRedisTemplate;

    public UsageCounterService(@Autowired(required = false) StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void recordUsage(String userId, String model, int promptTokens, int completionTokens) {
        if (stringRedisTemplate == null || userId == null || model == null) {
            return;
        }
        try {
            String date = LocalDate.now().toString();
            String promptKey = String.format("ai:usage:%s:%s:%s:prompt", userId, model, date);
            String completionKey = String.format("ai:usage:%s:%s:%s:completion", userId, model, date);

            Long pCount = stringRedisTemplate.opsForValue().increment(promptKey, promptTokens);
            if (pCount != null && pCount == promptTokens) {
                stringRedisTemplate.expire(promptKey, USAGE_TTL);
            }

            Long cCount = stringRedisTemplate.opsForValue().increment(completionKey, completionTokens);
            if (cCount != null && cCount == completionTokens) {
                stringRedisTemplate.expire(completionKey, USAGE_TTL);
            }
        } catch (Exception e) {
            log.warn("Redis usage counter failed: {}", e.getMessage());
        }
    }

    public long getDailyTokens(String userId, String model, LocalDate date) {
        if (stringRedisTemplate == null || userId == null || model == null) {
            return 0L;
        }
        try {
            String dateStr = date.toString();
            String pVal = stringRedisTemplate.opsForValue().get(String.format("ai:usage:%s:%s:%s:prompt", userId, model, dateStr));
            String cVal = stringRedisTemplate.opsForValue().get(String.format("ai:usage:%s:%s:%s:completion", userId, model, dateStr));

            long prompt = pVal != null ? Long.parseLong(pVal) : 0L;
            long comp = cVal != null ? Long.parseLong(cVal) : 0L;
            return prompt + comp;
        } catch (Exception e) {
            log.warn("Failed to get daily tokens from Redis: {}", e.getMessage());
            return 0L;
        }
    }
}
