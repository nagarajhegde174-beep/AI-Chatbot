package com.nexaai.user.integration;

import com.nexaai.user.support.TestTokens;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need a real PostgreSQL.
 *
 * <p><strong>Real PostgreSQL, never H2.</strong> A repository test against an in-memory
 * database proves nothing about the SQL this service runs, and it cannot test the property
 * this service most depends on: that {@code nexa_user} cannot see another service's tables.
 * That isolation is enforced by PostgreSQL grants, so the test has to be PostgreSQL
 * ({@code docs/TEST_PLAN.md} §2).
 *
 * <p><strong>Two routes to a database, chosen at runtime.</strong> CI uses Testcontainers; a
 * machine with no Docker daemon supplies one through {@code NEXA_TEST_PG_URL}. If neither is
 * available the tests fail with an explanation rather than skipping — a green build that ran
 * nothing is worse than a red one ({@code docs/RULES.md} §11).
 */
public abstract class PostgresIntegrationTest {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:17-alpine");

    /** A PostgreSQL supplied from outside, for machines with no Docker daemon. */
    private static final String EXTERNAL_URL = trimToNull(System.getenv("NEXA_TEST_PG_URL"));

    private static Boolean dockerAvailable;

    private static PostgreSQLContainer<?> container;

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Whether a usable database is available, by either route. */
    public static synchronized boolean databaseAvailable() {
        return EXTERNAL_URL != null || isDockerAvailable();
    }

    private static synchronized boolean isDockerAvailable() {
        if (dockerAvailable == null) {
            try {
                dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
            } catch (RuntimeException e) {
                dockerAvailable = false;
            }
        }
        return dockerAvailable;
    }

    private static synchronized PostgreSQLContainer<?> container() {
        if (container == null) {
            container = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                    .withDatabaseName("nexa_user")
                    .withUsername("nexa_user")
                    .withPassword("nexa_user_test_pw");
            container.start();
        }
        return container;
    }

    private static synchronized String jdbcUrl() {
        return EXTERNAL_URL != null ? EXTERNAL_URL : container().getJdbcUrl();
    }

    private static synchronized String username() {
        return EXTERNAL_URL != null
                ? System.getenv().getOrDefault("NEXA_TEST_PG_USER", "nexa_user")
                : container().getUsername();
    }

    private static synchronized String password() {
        return EXTERNAL_URL != null
                ? System.getenv().getOrDefault("NEXA_TEST_PG_PASSWORD", "")
                : container().getPassword();
    }

    /** Which database is in use, for test output. */
    public static synchronized String databaseDescription() {
        return EXTERNAL_URL != null ? "external PostgreSQL from NEXA_TEST_PG_URL" : "Testcontainers";
    }

    /**
     * Supplies the datasource and the verification key to the Spring context.
     *
     * <p>The RS256 key pair is generated in-process and never committed. The public half is what
     * this service verifies with; a checked-in test key outlives the test that generated it.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresIntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", PostgresIntegrationTest::username);
        registry.add("spring.datasource.password", PostgresIntegrationTest::password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Verification only. This service is never issued a signing key, and the test asserts
        // that no such property exists.
        registry.add("nexa.user.jwt.public-key", TestTokens::publicKeyPem);

        // No broker in tests. The listener bean is absent, so no connection is attempted.
        registry.add("nexa.user.event.enabled", () -> "false");
        // Subscription Service does not exist until Phase 9; the admin view reports usage as
        // unavailable rather than making a call that cannot succeed.
        registry.add("nexa.user.client.enabled", () -> "false");

        // Flyway owns the schema; validate proves the migration matches the entities.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}