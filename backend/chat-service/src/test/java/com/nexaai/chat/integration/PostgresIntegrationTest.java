package com.nexaai.chat.integration;

import com.nexaai.chat.support.TestTokens;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need a real PostgreSQL.
 *
 * <p><strong>Real PostgreSQL, never H2.</strong> The isolation this service depends on is enforced
 * by PostgreSQL grants, and this schema uses features H2 does not share: partial indexes,
 * {@code CHECK} constraints, {@code timestamptz}, and a JSONB-free but constraint-heavy design.
 * A repository test against an in-memory database proves nothing about the SQL this service runs.
 *
 * <p><strong>Two routes to a database.</strong> CI uses Testcontainers; a machine with no Docker
 * supplies one through {@code NEXA_TEST_PG_URL}. If neither is available the tests fail with an
 * explanation rather than skipping — a green build that ran nothing is worse than a red one
 * ({@code docs/RULES.md} §11).
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
                    .withDatabaseName("nexa_chat")
                    .withUsername("nexa_chat")
                    .withPassword("nexa_chat_test_pw");
            container.start();
        }
        return container;
    }

    private static synchronized String jdbcUrl() {
        return EXTERNAL_URL != null ? EXTERNAL_URL : container().getJdbcUrl();
    }

    private static synchronized String username() {
        return EXTERNAL_URL != null
                ? System.getenv().getOrDefault("NEXA_TEST_PG_USER", "nexa_chat")
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
     * Supplies the datasource and the verification key.
     *
     * <p>The RS256 key pair is generated in-process and never committed: a checked-in test key
     * outlives the test that generated it.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresIntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", PostgresIntegrationTest::username);
        registry.add("spring.datasource.password", PostgresIntegrationTest::password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Verification only. The test asserts no signing key is even configurable.
        registry.add("nexa.chat.jwt.public-key", TestTokens::publicKeyPem);

        // No AI Service in this phase, so the placeholder path is what runs. Asserted directly.
        registry.add("nexa.chat.generation.enabled", () -> "false");
        // No broker in tests: the listener would try to reach one that is not there.
        registry.add("nexa.chat.event.enabled", () -> "false");
        registry.add("nexa.chat.user-service.enabled", () -> "false");

        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}