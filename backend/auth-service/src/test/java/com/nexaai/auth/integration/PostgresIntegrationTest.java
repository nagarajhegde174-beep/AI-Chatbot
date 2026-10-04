package com.nexaai.auth.integration;

import com.nexaai.auth.support.TestKeys;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need a real PostgreSQL.
 *
 * <p><strong>Real PostgreSQL, never H2.</strong> A repository test against an in-memory
 * database proves nothing about the SQL this service actually runs: H2 accepts syntax
 * PostgreSQL rejects, and it has none of the constraints, indexes or collation behaviour this
 * schema depends on. The isolation this service relies on is enforced by PostgreSQL grants, so
 * the tests that matter must run on PostgreSQL
 * ({@code docs/TEST_PLAN.md} section 2).
 *
 * <p><strong>Two ways to get a database, chosen at runtime.</strong> CI uses Testcontainers,
 * which needs a Docker daemon. A machine without one supplies a PostgreSQL through
 * {@code NEXA_TEST_PG_URL}. The suite is never silently skipped: if neither is available the
 * tests fail with an explanation, because a green build that ran nothing is worse than a red
 * one ({@code docs/RULES.md} section 11).
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
                    .withDatabaseName("nexa_auth")
                    .withUsername("nexa_auth")
                    .withPassword("nexa_auth_test_pw");
            container.start();
        }
        return container;
    }

    private static synchronized String jdbcUrl() {
        if (EXTERNAL_URL != null) {
            return EXTERNAL_URL;
        }
        return container().getJdbcUrl();
    }

    private static synchronized String username() {
        return EXTERNAL_URL != null
                ? System.getenv().getOrDefault("NEXA_TEST_PG_USER", "nexa_auth")
                : container().getUsername();
    }

    private static synchronized String password() {
        return EXTERNAL_URL != null
                ? System.getenv().getOrDefault("NEXA_TEST_PG_PASSWORD", "")
                : container().getPassword();
    }

    /** A human-readable description of which database is in use, for test output. */
    public static synchronized String databaseDescription() {
        return EXTERNAL_URL != null ? "external PostgreSQL from NEXA_TEST_PG_URL" : "Testcontainers";
    }

    /**
     * Supplies the datasource and signing key to the Spring context.
     *
     * <p>The RS256 key is generated fresh for each run and never committed, for the same
     * reason {@link TestKeys} generates one: a checked-in test key outlives the test.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        TestKeys.TestKeyMaterial material = TestKeys.material();

        registry.add("spring.datasource.url", PostgresIntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", PostgresIntegrationTest::username);
        registry.add("spring.datasource.password", PostgresIntegrationTest::password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("nexa.auth.jwt.private-key", material::privateKeyPem);
        registry.add("nexa.auth.jwt.public-key", material::publicKeyPem);

        // Off in tests: the publisher would try to reach a broker that is not there.
        registry.add("nexa.auth.outbox.enabled", () -> "false");
        // Plain HTTP locally, or a Secure cookie is never returned and login appears to fail.
        registry.add("nexa.auth.cookie.secure", () -> "false");
        registry.add("nexa.auth.google.enabled", () -> "false");
        // Flyway owns the schema. validate proves the migration matches the entities.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }
}