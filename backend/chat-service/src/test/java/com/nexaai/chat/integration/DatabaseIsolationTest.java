package com.nexaai.chat.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves this service cannot read another service's database.
 *
 * <p><strong>The central claim of this service.</strong> "Chat Service must NOT access User
 * Service's database" is a property of PostgreSQL grants, not of Java code, and no amount of
 * reading the source can establish it. It has to be executed.
 *
 * <p>The test connects as {@code nexa_chat} — the role the application actually uses — and
 * attempts to reach every other service's database. Each must fail with a permission error.
 *
 * <p><strong>Why this provisions its own cluster.</strong> It needs several databases and a
 * <em>separate</em> superuser, because a superuser bypasses grants and would make every assertion
 * vacuous. That trap was hit in Phase 1, where a cluster built with
 * {@code initdb -U nexa_auth} made the service role a superuser and the test passed while
 * proving nothing. So {@code nexa_user} not being a superuser is asserted <em>before</em> any
 * denial is relied on.
 *
 * <p><strong>This test does not skip.</strong> An earlier version used an assumption when the
 * shared cluster was absent, which meant CI — where only {@code NEXA_TEST_PG_URL} is unset —
 * skipped every one of these. The pipeline rule forbidding {@code @Disabled} does not catch an
 * assumption, so the most important test in the service reported green having run nothing.
 */
class DatabaseIsolationTest {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:17-alpine");

    private static final String CHAT_ROLE = "nexa_chat";
    private static final String CHAT_PASSWORD = "nexa_chat_local_pw";

    /** Every service database except this one's own. */
    private static final List<String> FOREIGN_DATABASES = List.of(
            "nexa_auth",
            "nexa_user",
            "nexa_document",
            "nexa_rag",
            "nexa_subscription");

    private static final List<String> ALL_ROLES = List.of(
            "nexa_auth", "nexa_chat", "nexa_user", "nexa_document", "nexa_rag",
            "nexa_subscription");

    private static final String EXTERNAL_URL = trimToNull(System.getenv("NEXA_TEST_PG_URL"));

    private static PostgreSQLContainer<?> container;

    private static String host;
    private static String port;
    private static String adminRole;
    private static String adminPassword;
    private static boolean clusterReady;

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Which cluster is in use, for test output. */
    static String clusterIsUsable() {
        return EXTERNAL_URL != null ? "external (NEXA_TEST_PG_URL)" : "Testcontainers";
    }

    @BeforeAll
    static void provisionCluster() {
        if (EXTERNAL_URL != null) {
            host = System.getenv().getOrDefault("NEXA_TEST_PG_HOST", "127.0.0.1");
            port = System.getenv().getOrDefault("NEXA_TEST_PG_PORT", "5434");
            adminRole = System.getenv().getOrDefault("NEXA_TEST_PG_ADMIN_USER", "nexa_admin");
            adminPassword = System.getenv().getOrDefault("NEXA_TEST_PG_ADMIN_PASSWORD", "");
        } else if (dockerAvailable()) {
            if (container == null) {
                // nexa_admin as the superuser, NOT nexa_chat. Creating the container with
                // nexa_chat would make it a superuser and void every assertion below.
                container = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                        .withDatabaseName("postgres")
                        .withUsername("nexa_admin")
                        .withPassword("nexa_admin_test_pw");
                container.start();
            }
            host = container.getHost();
            port = String.valueOf(container.getFirstMappedPort());
            adminRole = container.getUsername();
            adminPassword = container.getPassword();
        } else {
            throw new IllegalStateException("""
                    No usable PostgreSQL for the database-isolation test.

                    Start Docker so Testcontainers can provide one, or set NEXA_TEST_PG_URL to a
                    cluster with the NexaAI grant shape (ordinary, non-superuser service roles
                    plus one database each).

                    This test is NOT skipped when it cannot run. A green build in which the
                    central isolation claim was never executed is worse than a red one
                    (docs/RULES.md section 11).""");
        }

        createRolesAndDatabases();
        clusterReady = true;
    }

    /** Creates the roles, the databases, and the revocations that make the test meaningful. */
    private static void createRolesAndDatabases() {
        try (Connection admin = connectAsAdmin("postgres");
             Statement statement = admin.createStatement()) {

            for (String role : ALL_ROLES) {
                // Idempotent, so an operator-supplied cluster that already has the right shape
                // is not disturbed.
                statement.execute("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE "
                        + "rolname = '" + role + "') THEN CREATE ROLE " + role
                        + " LOGIN PASSWORD '" + role + "_local_pw'; END IF; END $$;");
            }

            for (String role : ALL_ROLES) {
                if (!databaseExists(statement, role)) {
                    statement.execute("CREATE DATABASE " + role + " OWNER " + role);
                }
            }

            // The line that makes the denials below mean anything. Without it, PUBLIC can
            // CONNECT to every database and a role's own credentials would reach all of them.
            for (String role : ALL_ROLES) {
                statement.execute("REVOKE ALL ON DATABASE " + role + " FROM PUBLIC");
                statement.execute("GRANT CONNECT ON DATABASE " + role + " TO " + role);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Could not provision the isolation-test cluster. Set NEXA_TEST_PG_URL to a "
                            + "cluster whose admin role may create databases.", e);
        }
    }

    private static boolean databaseExists(Statement statement, String name) throws SQLException {
        try (var rs = statement.executeQuery(
                "select 1 from pg_database where datname = '" + name + "'")) {
            return rs.next();
        }
    }

    // ==================================================================
    // The grant structure
    // ==================================================================

    @Test
    @DisplayName("the application role is NOT a superuser")
    void applicationRoleIsNotASuperuser() throws SQLException {
        assertThat(clusterReady)
                .as("the cluster must be provisioned before any assertion is meaningful")
                .isTrue();

        try (Connection connection = connectAsAdmin("postgres");
             Statement statement = connection.createStatement();
             var rs = statement.executeQuery(
                     "select rolsuper from pg_roles where rolname = '" + CHAT_ROLE + "'")) {

            assertThat(rs.next()).as("role %s must exist", CHAT_ROLE).isTrue();
            assertThat(rs.getBoolean("rolsuper"))
                    .as("%s must not be a superuser, or every isolation test here is "
                            + "meaningless", CHAT_ROLE)
                    .isFalse();
        }
    }

    // ==================================================================
    // The isolation itself
    // ==================================================================

    @ParameterizedTest(name = "cannot reach {0}")
    @MethodSource("foreignDatabases")
    @DisplayName("cannot connect to another service's database")
    void cannotConnectToForeignDatabase(String database) {
        assertThat(clusterReady).isTrue();

        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;

        assertThatThrownBy(() -> {
            try (Connection connection =
                         DriverManager.getConnection(url, CHAT_ROLE, CHAT_PASSWORD);
                 Statement statement = connection.createStatement()) {
                // CONNECT and USAGE are separate privileges, so run a query too: a successful
                // connection alone would not prove the tables are out of reach.
                statement.execute("select 1");
            }
        })
                .as("nexa_chat must not be able to reach %s", database)
                .isInstanceOf(SQLException.class)
                .satisfies(thrown -> {
                    SQLException sql = (SQLException) thrown;
                    assertThat(sql.getSQLState())
                            .as("expected a permission error from %s, got: %s",
                                    database, sql.getMessage())
                            .isIn("42501", "28000", "3D000");
                });
    }

    @Test
    @DisplayName("CAN reach its own database")
    void canReachItsOwnDatabase() throws SQLException {
        // The mirror image. Without it the tests above would pass merely because the credentials
        // were wrong, which proves nothing about isolation.
        try (Connection connection = connectAsChat(CHAT_ROLE);
             Statement statement = connection.createStatement();
             var rs = statement.executeQuery("select count(*) from information_schema.tables "
                     + "where table_schema = 'nexa_chat'")) {

            assertThat(rs.next()).isTrue();
        }
    }

    // ==================================================================
    // What the schema itself must not contain
    // ==================================================================

    @Test
    @DisplayName("has no column that could hold a credential")
    void storesNoCredentials() throws SQLException {
        // Message content is private data but not a credential, so this is about passwords and
        // credentials specifically: this service has no hash to leak because it has no column.
        //
        // input_tokens and output_tokens are excluded by name, and deliberately so. They match
        // '%token%' but are usage metering, not secrets -- a count of tokens consumed, with no
        // relation to any token that authenticates anybody. Renaming them to dodge a pattern
        // match would make the schema worse to read in order to satisfy a test.
        try (Connection connection = connectAsChat(CHAT_ROLE);
             Statement statement = connection.createStatement();
             var rs = statement.executeQuery("""
                     select table_name, column_name from information_schema.columns
                     where table_schema = 'nexa_chat'
                       and column_name not in ('input_tokens', 'output_tokens')
                       and (column_name like '%password%'
                         or column_name like '%hash%'
                         or column_name like '%secret%'
                         or column_name like '%token%'
                         or column_name like '%credential%')
                     """)) {

            boolean found = rs.next();
            // Read into locals first: putting rs.getString() in the AssertJ description would
            // evaluate it before next(), and reading an unpositioned ResultSet throws.
            String table = found ? rs.getString("table_name") : null;
            String column = found ? rs.getString("column_name") : null;

            assertThat(found)
                    .as("nexa_chat must not contain a credential column, but found %s.%s",
                            table, column)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("the token columns are metering counts, and hold no token")
    void tokenColumnsAreCounts() throws SQLException {
        // Asserts the exclusion above is justified rather than a loophole: both columns are
        // integers, which a stored token could never be.
        try (Connection connection = connectAsChat(CHAT_ROLE);
             Statement statement = connection.createStatement();
             var rs = statement.executeQuery("""
                     select column_name, data_type from information_schema.columns
                     where table_schema = 'nexa_chat'
                       and column_name in ('input_tokens', 'output_tokens')
                     """)) {

            int found = 0;
            while (rs.next()) {
                found++;
                assertThat(rs.getString("data_type"))
                        .as("%s must be a numeric count, not a stored token",
                                rs.getString("column_name"))
                        .isEqualTo("integer");
            }
            assertThat(found).as("both metering columns should exist").isEqualTo(2);
        }
    }

    @Test
    @DisplayName("does not carry a copy of another service's tables")
    void doesNotOwnForeignTables() throws SQLException {
        try (Connection connection = connectAsChat(CHAT_ROLE);
             Statement statement = connection.createStatement();
             var rs = statement.executeQuery("""
                     select table_name from information_schema.tables
                     where table_schema = 'nexa_chat'
                       and table_name in (
                         'auth_user', 'user_profile', 'refresh_token',
                         'message_feedback_global', 'document', 'chat_embedding')
                     """)) {

            assertThat(rs.next())
                    .as("nexa_chat must not contain another service's tables")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("its tables live in the nexa_chat schema, not in public")
    void schemaIsNamespaced() throws SQLException {
        // A table in public is reachable by any role that can connect, which would quietly undo
        // the isolation if another service were granted CONNECT to nexa_chat.
        try (Connection connection = connectAsChat(CHAT_ROLE);
             Statement statement = connection.createStatement();
             var rs = statement.executeQuery("""
                     select count(*) from information_schema.tables
                     where table_schema = 'public'
                       and table_name in ('chat_conversation', 'chat_message',
                                          'message_feedback', 'processed_event')
                     """)) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong(1))
                    .as("the chat tables must not also exist in public")
                    .isZero();
        }
    }

    private static Stream<String> foreignDatabases() {
        return FOREIGN_DATABASES.stream();
    }

    private static Connection connectAsAdmin(String database) throws SQLException {
        String base = EXTERNAL_URL != null
                ? "jdbc:postgresql://" + host + ":" + port + "/"
                : container.getJdbcUrl().replaceAll("/[^/]*$", "/");
        return DriverManager.getConnection(base + database, adminRole, adminPassword);
    }

    private static Connection connectAsChat(String database) throws SQLException {
        return DriverManager.getConnection(
                "jdbc:postgresql://" + host + ":" + port + "/" + database,
                CHAT_ROLE, CHAT_PASSWORD);
    }
}