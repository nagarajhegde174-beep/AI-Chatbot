package com.nexaai.auth.integration;

import com.nexaai.auth.service.EmailNotificationService;
import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.stereotype.Component;

/**
 * Captures the links the service would email, so tests can follow them.
 *
 * <p>Phase 1 has no mail transport, so the verification and reset flows publish a domain event
 * carrying the link rather than sending mail. This listener collects those events, which lets a
 * test follow a <em>real</em> issued token through the real redemption path instead of writing
 * a token into the database by hand.
 *
 * <p>A queue rather than a single slot: several tests share one application context, and a test
 * that grabbed another test's link would fail in a confusing way.
 */
@Component
public class TestEmailLinkCapture {

    private static final Deque<String> VERIFICATION_LINKS = new ArrayDeque<>();
    private static final Deque<String> RESET_LINKS = new ArrayDeque<>();

    @org.springframework.context.event.EventListener
    public void onVerification(EmailNotificationService.VerificationEmailReady event) {
        synchronized (VERIFICATION_LINKS) {
            VERIFICATION_LINKS.addLast(event.link());
        }
    }

    @org.springframework.context.event.EventListener
    public void onPasswordReset(EmailNotificationService.PasswordResetEmailReady event) {
        synchronized (RESET_LINKS) {
            RESET_LINKS.addLast(event.link());
        }
    }

    /** The most recent verification link, or null. */
    public static String lastVerificationLink() {
        synchronized (VERIFICATION_LINKS) {
            return VERIFICATION_LINKS.peekLast();
        }
    }

    /** The most recent reset link, or null. */
    public static String lastResetLink() {
        synchronized (RESET_LINKS) {
            return RESET_LINKS.peekLast();
        }
    }

    /** Clears both queues, so one test cannot consume another's link. */
    public static void clear() {
        synchronized (VERIFICATION_LINKS) {
            VERIFICATION_LINKS.clear();
        }
        synchronized (RESET_LINKS) {
            RESET_LINKS.clear();
        }
    }
}