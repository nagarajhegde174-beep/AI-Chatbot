package com.nexaai.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Service for temporary OTP and verification token state in Redis.
 * Redis holds this state with an explicit TTL; PostgreSQL holds persistent user verification status.
 */
@Service
public class RedisOtpStateService {

    private static final Logger log = LoggerFactory.getLogger(RedisOtpStateService.class);
    private static final Duration DEFAULT_OTP_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisOtpStateService(@Autowired(required = false) StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void storeOtp(String email, String otp) {
        storeOtp(email, otp, DEFAULT_OTP_TTL);
    }

    public void storeOtp(String email, String otp, Duration ttl) {
        if (stringRedisTemplate == null) {
            log.warn("StringRedisTemplate not available; skipping Redis OTP store for {}", email);
            return;
        }
        try {
            String key = "auth:otp:" + email.toLowerCase().trim();
            stringRedisTemplate.opsForValue().set(key, otp, ttl);
            log.debug("Stored temporary OTP for {}", email);
        } catch (Exception ex) {
            log.warn("Failed to store temporary OTP in Redis: {}", ex.getMessage());
        }
    }

    public Optional<String> getOtp(String email) {
        if (stringRedisTemplate == null) {
            return Optional.empty();
        }
        try {
            String key = "auth:otp:" + email.toLowerCase().trim();
            String otp = stringRedisTemplate.opsForValue().get(key);
            return Optional.ofNullable(otp);
        } catch (Exception ex) {
            log.warn("Failed to read temporary OTP from Redis: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public boolean verifyAndClearOtp(String email, String inputOtp) {
        if (stringRedisTemplate == null || inputOtp == null) {
            return false;
        }
        try {
            String key = "auth:otp:" + email.toLowerCase().trim();
            String stored = stringRedisTemplate.opsForValue().get(key);
            if (stored != null && stored.equals(inputOtp.trim())) {
                stringRedisTemplate.delete(key);
                return true;
            }
            return false;
        } catch (Exception ex) {
            log.warn("Failed to verify temporary OTP in Redis: {}", ex.getMessage());
            return false;
        }
    }
}
