package com.nexaai.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves this service cannot read Auth Service's database.
 *
 * <p><strong>This is the central claim of Phase 2.</strong> "User Service MUST NOT access Auth
 * Service's database" is a property of PostgreSQL grants, not of Java code, and no amount of
 * reading the source can establish it. It has to be executed.
 *
 * <p>The test connects as {@code nexa_user} — the role the application actually uses — and
 * attempts to reach Auth Service's database. Every attempt must fail with a permission error.
 * If one succeeds, the isolation this architecture depends on does not exist, and everything
 * else in this service is beside the point.
 *
 * <p><strong>Why this file does not use the shared {@link PostgresIntegrationTest} cluster.</strong>
 * It needs a cluster with several databases and a <em>separate</em> superuser, because a
 * superuser bypasses grants and would make every assertion below vacuous. In Phase 1 a cluster
 * created with {@code initdb -U nexa_auth} made that role a superuser; the resulting test passed
 * while proving nothing.
 *
 * <p>So this test provisions its own shape, and asserts {@code nexa_user} is not a superuser
 * <em>before</em> relying on any denial:
 *
 * <ul>
 *   <li>CI: a Testcontainers cluster built here with {@code nexa_admin} as the superuser.</li>
 *   <li>A machine with no Docker: {@code NEXA_TEST_PG_URL}, which the operator must point at an
 *       equivalently-shaped cluster. {@link #clusterIsUsable()} says which was used.</li>
 * </ul>
 *
 * <p><strong>This test deliberately does not skip.</strong> An earlier version used
 * {@code Assumptions.assumeTrue} when the shared cluster was absent, which meant CI — where only
 * {@code NEXA_TEST_PG_URL} is unset — skipped all six of these tests. The pipeline rule that
 * forbids {@code @Disabled} does not catch an assumption, so the most important test in the
 * service reported green having run nothing. Running is always better than skipping, so the
 * class fails loudly instead if it truly cannot obtain a usable cluster.
 */
class DatabaseIsolationTest {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:17-alpine");

    private static final String USER_ROLE = "nexa_user";
    private static final String USER_PASSWORD = "nexa_user_local_pw";

    /** Databases this service must not be able to reach. */
    private static final List<String> FOREIGN_DATABASES = List.of(
            "nexa_auth",
            "nexa_chat",
            "nexa_document",
            "nexa_rag",
            "nexa_subscription");

    private static PostgreSQLContainer<?> container;

    /** A cluster supplied from outside, for machines with no Docker daemon. */
    private static final String EXTERNAL_URL = trimToNull(System.getenv("NEXA_TEST_PG_URL"));

    private static boolean clusterReady;

    private static String host;
    private static String port;
    private static String adminRole;
    private static String adminPassword;

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

    /**
     * Brings up a correctly-shaped cluster: one superuser, several ordinary service roles, one
     * database each, and no {@code PUBLIC} CONNECT on any of them.
     */
    @BeforeAll
    static void provisionCluster() {
        if (EXTERNAL_URL != null) {
            host = System.getenv().getOrDefault("NEXA_TEST_PG_HOST", "127.0.0.1");
            port = System.getenv().getOrDefault("NEXA_TEST_PG_PORT", "5434");
            adminRole = System.getenv().getOrDefault("NEXA_TEST_PG_ADMIN_USER", "nexa_admin");
            adminPassword = System.getenv().getOrDefault("NEXA_TEST_PG_ADMIN_PASSWORD", "");
        } else if (dockerAvailable()) {
            if (container == null) {
                // nexa_admin as the superuser, NOT nexa_user. Creating the container with
                // nexa_user would make it a superuser and void every assertion below.
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
                    cluster with the NexaAI grant shape (an ordinary, non-superuser nexa_user
                    role plus the other service databases).

                    This test is NOT skipped when it cannot run. A green build in which the
                    central isolation claim was never executed is worse than a red one
                    (docs/RULES.md section 11).""");
        }

        createRolesAndDatabases();
        clusterReady = true;
    }

    /** Creates the roles, the databases, and the revocations that make the test meaningful. */
    private static void createRolesAndDatabases() {
        List<String> roles = List.of("nexa_auth", "nexa_user", "nexa_chat", "nexa_document",
                "nexa_rag", "nexa_subscription");

        try (Connection admin = connectAsAdmin("postgres");
             Statement statement = admin.createStatement()) {

            for (String role : roles) {
                // IF NOT EXISTS keeps this idempotent against an operator-supplied cluster that
                // already has the right shape.
                statement.execute("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE "
                        + "rolname = '" + role + "') THEN CREATE ROLE " + role
                        + " LOGIN PASSWORD '" + role + "_local_pw'; END IF; END $$;");
            }

            for (String role : roles) {
                boolean exists = databaseExists(statement, role);
                if (!exists) {
                    statement.execute("CREATE DATABASE " + role + " OWNER " + role);
                }
            }

            // The important line. A role must not be able to CONNECT to a database it does not
            // own; without this, PUBLIC can and every denial below would be vacuous.
            for (String role : roles) {
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
    // The grant structure this service relies on
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
                     "select rolsuper from pg_roles where rolname = '" + USER_ROLE + "'")) {

            boolean exists = rs.next();
            assertThat(exists).as("role %s must exist in the cluster", USER_ROLE).isTrue();

            assertThat(rs.getBoolean("rolsuper"))
                    .as("%s must not be a superuser, or every isolation test here is "
                            + "meaningless", USER_ROLE)
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
                         DriverManager.getConnection(url, USER_ROLE, USER_PASSWORD);
                 Statement statement = connection.createStatement()) {
                // A successful CONNECT is not enough on its own: CONNECT and USAGE are separate
                // privileges, so run a query too.
                statement.execute("select 1");
            }
        })
                .as("nexa_user must not be able to reach %s", database)
                .isInstanceOf(SQLException.class)
                .satisfies(thrown -> {
                    SQLException sql = (SQLException) thrown;
                    // 42501 insufficient_privilege, 28000 invalid_authorization_specification,
                    // 3D000 invalid_catalog_name.
                    assertThat(sql.getSQLState())
                            .as("expected a permission error from %s, got: %s",
                                    database, sql.getMessage())
                            .isIn("42501", "28000", "3D000");
                });
    }

    @Test
    @DisplayName("CAN reach its own database")
    void canReachItsOwnDatabase() throws SQLException {
        // The mirror image. Without this, the tests above would pass merely because the role's
        // credentials were wrong, which proves nothing about isolation.
        try (Connection connection = connect(USER_ROLE);
             Statement statement = connection.createStatement();
             var rs = statement.executeQuery("select count(*) from information_schema.tables "
                     + "where table_schema = 'nexa_user'")) {

            assertThat(rs.next()).isTrue();
        }
    }

    // ==================================================================
    // What the schema itself must not contain
    // ==================================================================

    @Test
    @DisplayName("does not carry a copy of Auth Service's tables")
    void doesNotOwnAuthTables() throws SQLException {
        try (Connection connection = connect(USER_ROLE);
             Statement statement = connection.createStatement();
             var rs = statement.executeQuery("""
                     select table_name from information_schema.tables
                     where table_schema = 'nexa_user'
                       and table_name in (
                         'auth_user', 'refresh_token', 'password_reset_token',
                         'email_verification_token', 'login_attempt', 'auth_user_role')
                     """)) {

            assertThat(rs.next())
                    .as("nexa_user must not contain Auth Service's tables")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("has no column that could hold a credential")
    void storesNoCredentials() throws SQLException {
        // The structural counterpart to the absence of cross-database access: this service has
        // no hash to leak because it has no column to hold one.
        try (Connection connection = connect(USER_ROLE);
             Statement statement = connection.createStatement();
             var rs = statement.executeQuery("""
                     select table_name, column_name from information_schema.columns
                     where table_schema = 'nexa_user'
                       and (column_name like '%password%'
                         or column_name like '%hash%'
                         or column_name like '%secret%'
                         or column_name like '%token%'
                         or column_name like '%credential%')
                     """)) {

            boolean found = rs.next();
            // Read into locals first. Putting rs.getString() inside the AssertJ description
            // would evaluate it before next() was called, and reading an unpositioned
            // ResultSet throws, replacing a clear failure with a confusing one.
            String table = found ? rs.getString("table_name") : null;
            String column = found ? rs.getString("column_name") : null;

            assertThat(found)
                    .as("nexa_user must not contain a credential column, but found %s.%s",
                            table, column)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("its own tables live in the nexa_user schema, not in public")
    void schemaIsNamespaced() throws SQLException {
        // A table in public is reachable by any role that can connect, which would quietly undo
        // the isolation if another service were granted CONNECT to nexa_user.
        try (Connection connection = connect(USER_ROLE);
             Statement statement = connection.createStatement();
             var rs = statement.executeQuery("""
                     select count(*) from information_schema.tables
                     where table_schema = 'public'
                       and table_name in ('user_profile', 'user_preference',
                                          'user_status_history', 'processed_event')
                     """)) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong(1))
                    .as("the user tables must not also exist in public")
                    .isZero();
        }
    }

    private static java.util.stream.Stream<String> foreignDatabases() {
        return FOREIGN_DATABASES.stream();
    }

    private static Connection connectAsAdmin(String database) throws SQLException {
        String base = EXTERNAL_URL != null
                ? "jdbc:postgresql://" + host + ":" + port + "/"
                : container.getJdbcUrl().replaceAll("/[^/]*$", "/");
        return DriverManager.getConnection(base + database, adminRole, adminPassword);
    }

    private static Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(
                "jdbc:postgresql://" + host + ":" + port + "/" + database,
                USER_ROLE, USER_PASSWORD);
    }
}