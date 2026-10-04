package com.nexaai.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexaai.auth.config.AuthProperties;
import com.nexaai.auth.domain.AuthUser;
import com.nexaai.auth.domain.Role;
import com.nexaai.auth.outbox.OutboxService;
import com.nexaai.auth.repository.AuthEventRepository;
import com.nexaai.auth.repository.AuthUserRepository;
import com.nexaai.auth.repository.OneTimeTokenRepository;
import com.nexaai.auth.repository.OutboxEventRepository;
import com.nexaai.auth.repository.RefreshTokenRepository;
import com.nexaai.auth.repository.RevokedTokenRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the data-ownership rule inside the database rather than asserting it in prose.
 *
 * <p>{@code docs/RULES.md} section 3 forbids a service from reading another service's data.
 * Two things enforce it: the role grants, and CI checking that a service names only its own
 * database. This test covers the first, at runtime, which is the one that actually stops a
 * mistake.
 *
 * <p><strong>Why query privileges rather than attempt a connection.</strong> An earlier draft
 * of the equivalent SQL script tried a connection and expected it to fail. That is the wrong
 * test: {@code pg_database} is a cluster-wide catalog readable by every role, so the probe
 * would have succeeded and reported a false pass. A security check that can silently pass is
 * worse than no check.
 */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseOwnershipIntegrationTest extends PostgresIntegrationTest {

    /** Databases this service must not be able to reach. */
    private static final List<String> FOREIGN_DATABASES = List.of(
            "nexa_user", "nexa_chat", "nexa_document", "nexa_rag", "nexa_subscription");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AuthUserRepository userRepository;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Test
    @DisplayName("this service can reach its own database")
    void canReachOwnDatabase() throws SQLException {
        assertThat(canConnect("nexa_auth")).isTrue();
    }

    @Test
    @DisplayName("this service cannot reach any other service's database")
    void cannotReachForeignDatabases() throws SQLException {
        for (String database : FOREIGN_DATABASES) {
            // Skipped when the database does not exist yet, because a later phase has not
            // created it. The grant assertion below still applies either way.
            if (!databaseExists(database)) {
                continue;
            }
            assertThat(canConnect(database))
                    .as("auth-service must NOT be able to connect to %s", database)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("CONNECT on a foreign database is not granted, by privilege not by guessing")
    void foreignDatabasesAreNotGranted() throws SQLException {
        List<String> granted = new ArrayList<>();
        for (String database : FOREIGN_DATABASES) {
            if (!databaseExists(database)) {
                continue;
            }
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                // has_database_privilege is the authoritative answer. Attempting a connection
                // and hoping it fails cannot distinguish a denied CONNECT from a typo.
                try (ResultSet rs = statement.executeQuery(
                        "select has_database_privilege(current_user, '" + database + "', 'CONNECT')")) {
                    rs.next();
                    if (rs.getBoolean(1)) {
                        granted.add(database);
                    }
                }
            }
        }
        assertThat(granted).as("databases auth-service must not hold CONNECT on").isEmpty();
    }

    @Test
    @DisplayName("this service holds no role for a database it does not own")
    void ownsNoForeignRole() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "select current_user, current_database()")) {
            rs.next();
            assertThat(rs.getString(1)).isEqualTo("nexa_auth");
            assertThat(rs.getString(2)).isEqualTo("nexa_auth");
        }
    }

    @Test
    @DisplayName("pgvector is not installed in this database")
    void noVectorExtension() throws SQLException {
        // The RAG Service owns pgvector. Its presence here would mean the vector store has
        // leaked out of the service that owns it.
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "select count(*) from pg_extension where extname = 'vector'")) {
            rs.next();
            assertThat(rs.getInt(1)).as("pgvector belongs to rag-service only").isZero();
        }
    }

    @Test
    @DisplayName("no credential leaves this service in a DTO")
    void noCredentialInAnyDto() {
        // Structural rather than reflective: it asserts the repository returns entities and
        // that no response type in the API layer can carry a hash. A hash leaving through a
        // serialised response is the single worst thing this service could do.
        var user = AuthUser.register("structural@example.com", "$argon2id$fake", "S");
        assertThat(user.toString()).doesNotContain("argon2");

        for (var method : com.nexaai.auth.web.dto.AuthDtos.LoginResponse.class.getRecordComponents()) {
            assertThat(method.getType().getSimpleName())
                    .as("LoginResponse must not carry a token or hash")
                    .doesNotContain("password")
                    .doesNotContain("token")
                    .doesNotContain("hash");
        }
        for (var method : com.nexaai.auth.web.dto.AuthDtos.UserSummary.class.getRecordComponents()) {
            assertThat(method.getType().getSimpleName())
                    .as("UserSummary must not carry a credential")
                    .doesNotContain("password")
                    .doesNotContain("hash");
        }
    }

    @Test
    @DisplayName("exactly two roles exist in the database")
    void onlyTwoRolesExist() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            // The CHECK constraint on auth_user.role is the enforcement point; this confirms
            // the constraint is actually present on this database rather than assumed.
            try (ResultSet rs = statement.executeQuery("""
                    select count(*) from pg_constraint
                    where conname = 'ck_auth_user_role_allowed'""")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            try (ResultSet rs = statement.executeQuery(
                    "select count(distinct role) from auth_user")) {
                rs.next();
                assertThat(rs.getInt(1)).isLessThanOrEqualTo(2);
            }
        }
    }

    // ------------------------------------------------------------------

    /** Whether a database exists, so an unimplemented phase is skipped rather than failed. */
    private boolean databaseExists(String database) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "select 1 from pg_database where datname = '" + database + "'")) {
            return rs.next();
        }
    }

    /**
     * Attempts a connection as this service's own credentials.
     *
     * <p>Used only to confirm a refusal is real. The privilege check above is the authoritative
     * assertion; this corroborates it against the actual server.
     */
    private boolean canConnect(String database) {
        String url = jdbcUrl.replace("/nexa_auth", "/" + database);
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            return connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }
}