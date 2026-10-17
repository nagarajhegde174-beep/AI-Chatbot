package com.nexaai.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    public void sendVerificationEmail(String email, String token) {
        log.info("Sending email verification to: {} (Token: {})", email, token);
    }

    public void sendPasswordResetEmail(String email, String token, String otp) {
        log.info("Sending password reset email to: {} (Token: {}, OTP: {})", email, token, otp);
    }
}
