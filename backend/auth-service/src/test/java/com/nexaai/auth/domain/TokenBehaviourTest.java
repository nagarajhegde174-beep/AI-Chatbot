package com.nexaai.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Token behaviour: expiry, single use, and rotation-family grouping. */
class OneTimeTokenTest {

    private static final String HASH = "a".repeat(64);

    @Test
    @DisplayName("a new token is redeemable and unconsumed")
    void newTokenIsRedeemable() {
        OneTimeToken token = new OneTimeToken(UUID.randomUUID(), HASH,
                OneTimeTokenPurpose.PASSWORD_RESET, Duration.ofHours(1));

        assertThat(token.isRedeemable()).isTrue();
        assertThat(token.isConsumed()).isFalse();
        assertThat(token.isExpired()).isFalse();
        assertThat(token.getAttemptCount()).isZero();
    }

    @Test
    @DisplayName("consuming is single use")
    void consumeIsSingleUse() {
        // This is the property that limits the damage if a link is intercepted.
        OneTimeToken token = new OneTimeToken(UUID.randomUUID(), HASH,
                OneTimeTokenPurpose.EMAIL_VERIFICATION, Duration.ofHours(24));

        token.consume();

        assertThat(token.isConsumed()).isTrue();
        assertThat(token.isRedeemable()).isFalse();
    }

    @Test
    @DisplayName("an expired token is not redeemable")
    void expiryBlocksRedeem() {
        OneTimeToken token = new OneTimeToken(UUID.randomUUID(), HASH,
                OneTimeTokenPurpose.PASSWORD_RESET, Duration.ofSeconds(-1));

        assertThat(token.isExpired()).isTrue();
        assertThat(token.isRedeemable()).isFalse();
    }

    @Test
    @DisplayName("failed attempts are counted")
    void attemptsAreCounted() {
        OneTimeToken token = new OneTimeToken(UUID.randomUUID(), HASH,
                OneTimeTokenPurpose.PASSWORD_RESET, Duration.ofHours(1));

        assertThat(token.recordFailedAttempt()).isEqualTo(1);
        assertThat(token.recordFailedAttempt()).isEqualTo(2);
        assertThat(token.getAttemptCount()).isEqualTo(2);
        // Counting an attempt must not consume the token.
        assertThat(token.isRedeemable()).isTrue();
    }

    @Test
    @DisplayName("toString omits the token hash")
    void toStringOmitsHash() {
        OneTimeToken token = new OneTimeToken(UUID.randomUUID(), HASH,
                OneTimeTokenPurpose.PASSWORD_RESET, Duration.ofHours(1));

        assertThat(token.toString()).doesNotContain(HASH);
    }

    @Test
    @DisplayName("a refresh token is usable only while live and unexpired")
    void refreshTokenUsability() {
        UUID userId = UUID.randomUUID();
        RefreshToken live = new RefreshToken(UUID.randomUUID(), HASH, userId,
                Instant.now().plus(Duration.ofDays(30)), TokenSource.PASSWORD);
        assertThat(live.isUsable()).isTrue();

        live.revoke();
        assertThat(live.isUsable()).isFalse();
        assertThat(live.isAlreadyUsed()).isTrue();

        RefreshToken expired = new RefreshToken(UUID.randomUUID(), "b".repeat(64), userId,
                Instant.now().minus(Duration.ofSeconds(1)), TokenSource.PASSWORD);
        assertThat(expired.isUsable()).isFalse();
    }

    @Test
    @DisplayName("revoking twice keeps the first timestamp")
    void revokeIsIdempotent() {
        RefreshToken token = new RefreshToken(UUID.randomUUID(), HASH, UUID.randomUUID(),
                Instant.now().plus(Duration.ofDays(1)), TokenSource.GOOGLE);

        token.revoke();
        Instant first = token.getRevokedAt();
        token.revoke();

        // Overwriting the revocation time would hide when the session actually ended.
        assertThat(token.getRevokedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("a refresh token toString omits the hash")
    void refreshToStringOmitsHash() {
        RefreshToken token = new RefreshToken(UUID.randomUUID(), HASH, UUID.randomUUID(),
                Instant.now().plus(Duration.ofDays(1)), TokenSource.PASSWORD);

        assertThat(token.toString()).doesNotContain(HASH);
    }

    @Test
    @DisplayName("purgeable only once the token would have expired naturally")
    void purgeableOnlyAfterExpiry() {
        UUID userId = UUID.randomUUID();
        RevokedToken live = new RevokedToken("jti-live", userId,
                Instant.now().plus(Duration.ofMinutes(5)), "LOGOUT");
        assertThat(live.isPurgeable()).isFalse();

        RevokedToken dead = new RevokedToken("jti-dead", userId,
                Instant.now().minus(Duration.ofMinutes(5)), "LOGOUT");
        assertThat(dead.isPurgeable()).isTrue();
    }

    @Test
    @DisplayName("the only two roles exist")
    void exactlyTwoRoles() {
        // Adding a third role must be a compile error, not a string that slips through.
        assertThat(Role.values()).containsExactly(Role.USER, Role.ADMIN);
    }

    @Test
    @DisplayName("token purposes are separate")
    void purposesAreDistinct() {
        assertThat(OneTimeTokenPurpose.values())
                .containsExactly(OneTimeTokenPurpose.EMAIL_VERIFICATION,
                        OneTimeTokenPurpose.PASSWORD_RESET);
    }

    @Test
    @DisplayName("an outbox event records failure without losing the error")
    void outboxRecordsFailure() {
        var outbox = new OutboxEvent("t.v1", "t.v1", UUID.randomUUID(), "{}", "cid");
        assertThat(outbox.isExhausted(3)).isFalse();

        outbox.recordFailure("broker unavailable");
        assertThat(outbox.getAttempts()).isEqualTo(1);
        assertThat(outbox.getLastError()).isEqualTo("broker unavailable");

        outbox.markPublished();
        assertThat(outbox.getPublishedAt()).isNotNull();
        assertThat(outbox.getLastError()).isNull();
    }
}