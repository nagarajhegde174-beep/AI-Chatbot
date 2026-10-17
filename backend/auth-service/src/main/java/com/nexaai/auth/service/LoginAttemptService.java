package com.nexaai.auth.service;

import com.nexaai.auth.domain.entity.LoginAttempt;
import com.nexaai.auth.domain.repository.LoginAttemptRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class LoginAttemptService {

    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    private final int maxAttempts;
    private final int lockoutDurationMinutes;

    public LoginAttemptService(LoginAttemptRepository loginAttemptRepository,
                               PasswordEncoder passwordEncoder,
                               @Value("${auth.lockout.max-attempts:5}") int maxAttempts,
                               @Value("${auth.lockout.lockout-duration-minutes:15}") int lockoutDurationMinutes) {
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordEncoder = passwordEncoder;
        this.maxAttempts = maxAttempts;
        this.lockoutDurationMinutes = lockoutDurationMinutes;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-constant-time-salt-nexaai");
    }

    @Transactional
    public void recordSuccess(String email, String ipAddress) {
        loginAttemptRepository.save(new LoginAttempt(null, email, ipAddress, true));
    }

    @Transactional
    public void recordFailure(String email, String ipAddress) {
        loginAttemptRepository.save(new LoginAttempt(null, email, ipAddress, false));
    }

    @Transactional(readOnly = true)
    public boolean isLocked(String email) {
        Instant since = Instant.now().minus(lockoutDurationMinutes, ChronoUnit.MINUTES);
        long failedAttempts = loginAttemptRepository.countFailedAttemptsSince(email, since);
        return failedAttempts >= maxAttempts;
    }

    public void executeDummyPasswordCheck(String rawPassword) {
        passwordEncoder.matches(rawPassword, dummyPasswordHash);
    }
}
