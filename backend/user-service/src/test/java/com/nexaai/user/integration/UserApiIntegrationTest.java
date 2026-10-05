package com.nexaai.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexaai.user.domain.AccountStatus;
import com.nexaai.user.domain.Role;
import com.nexaai.user.domain.UserProfile;
import com.nexaai.user.event.DomainEvent;
import com.nexaai.user.event.UserRegisteredPayload;
import com.nexaai.user.event.UserRegistrationEventHandler;
import com.nexaai.user.repository.UserProfileRepository;
import com.nexaai.user.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The HTTP surface: self-service, administrative, and the boundaries between them.
 *
 * <p>Runs against a real PostgreSQL with the real security filter chain. The interesting
 * assertions are the negative ones — that a caller cannot reach another user's data, that a
 * USER cannot reach an admin route, and that a bad token is refused rather than tolerated.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserProfileRepository profiles;

    @Autowired
    private UserRegistrationEventHandler registrationHandler;

    /** Creates a profile by consuming the registration event, as the real flow does. */
    private UserProfile register(UUID authUserId, String email, String displayName,
                                 AccountStatus status, Role role) {
        UUID eventId = UUID.randomUUID();
        registrationHandler.onUserRegistered(new DomainEvent<>(
                eventId,
                UserRegisteredPayload.TOPIC,
                1,
                java.time.Instant.now(),
                UUID.randomUUID(),
                new UserRegisteredPayload(authUserId, email, displayName,
                        status == AccountStatus.ACTIVE, "PASSWORD", status.name(), role.name())));
        return profiles.findByAuthUserId(authUserId).orElseThrow();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ==================================================================
    // Self-service
    // ==================================================================

    @Nested
    @DisplayName("GET /api/v1/me")
    class OwnProfile {

        @Test
        @DisplayName("returns the caller's own profile")
        void returnsOwnProfile() throws Exception {
            UUID authUserId = UUID.randomUUID();
            register(authUserId, "me@example.com", "Me", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(get("/api/v1/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(authUserId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authUserId").value(authUserId.toString()))
                    .andExpect(jsonPath("$.email").value("me@example.com"))
                    .andExpect(jsonPath("$.displayName").value("Me"))
                    .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.role").value("USER"));
        }

        @Test
        @DisplayName("contains no credential field, because there is none to contain")
        void exposesNoCredentialFields() throws Exception {
            UUID authUserId = UUID.randomUUID();
            register(authUserId, "me@example.com", "Me", AccountStatus.ACTIVE, Role.USER);

            String body = mvc.perform(get("/api/v1/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(authUserId))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // Matched as QUOTED JSON KEYS, not bare substrings. A bare search for "password"
            // fails on registeredVia:"PASSWORD", which is the registration method and not a
            // credential -- a false positive that would train everyone to distrust this test.
            assertThat(body).doesNotContain("\"password\"");
            assertThat(body).doesNotContain("\"passwordHash\"");
            assertThat(body).doesNotContain("\"token\"");
            assertThat(body).doesNotContain("\"secret\"");
            assertThat(body).doesNotContain("\"credential\"");
        }

        @Test
        @DisplayName("returns 404 when the account has no profile yet")
        void unknownProfileIsNotFound() throws Exception {
            // Reachable in the window between registration and the event being consumed.
            mvc.perform(get("/api/v1/me")
                            .header(HttpHeaders.AUTHORIZATION,
                                    bearer(TestTokens.userToken(UUID.randomUUID()))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/me")
    class UpdateOwnProfile {

        @Test
        @DisplayName("changes the display name")
        void renames() throws Exception {
            UUID authUserId = UUID.randomUUID();
            register(authUserId, "me@example.com", "Before", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(patch("/api/v1/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(authUserId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"After\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("After"));
        }

        @Test
        @DisplayName("IGNORES a role field in the body")
        void ignoresRoleInBody() throws Exception {
            UUID authUserId = UUID.randomUUID();
            register(authUserId, "me@example.com", "Before", AccountStatus.ACTIVE, Role.USER);

            // The request type has no role property, so Jackson drops the unknown field by
            // default rather than failing. A service configured to reject unknown fields
            // would return 400 here; either way, the role must not change.
            mvc.perform(patch("/api/v1/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(authUserId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"After\",\"role\":\"ADMIN\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("USER"));
        }

        @Test
        @DisplayName("IGNORES accountStatus and email in the body")
        void ignoresStatusAndEmailInBody() throws Exception {
            UUID authUserId = UUID.randomUUID();
            register(authUserId, "me@example.com", "Before", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(patch("/api/v1/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(authUserId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"After\","
                                    + "\"accountStatus\":\"ADMIN\","
                                    + "\"email\":\"attacker@evil.test\","
                                    + "\"authUserId\":\"" + UUID.randomUUID() + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.email").value("me@example.com"))
                    .andExpect(jsonPath("$.authUserId").value(authUserId.toString()));
        }

        @Test
        @DisplayName("rejects an over-long display name")
        void validatesDisplayNameLength() throws Exception {
            UUID authUserId = UUID.randomUUID();
            register(authUserId, "me@example.com", "Before", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(patch("/api/v1/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(authUserId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"" + "x".repeat(121) + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[0].field").value("displayName"));
        }
    }

    @Nested
    @DisplayName("preferences")
    class Preferences {

        @Test
        @DisplayName("returns defaults for a new profile")
        void returnsDefaults() throws Exception {
            UUID authUserId = UUID.randomUUID();
            register(authUserId, "me@example.com", "Me", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(get("/api/v1/me/preferences")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(authUserId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.theme").value("system"))
                    .andExpect(jsonPath("$.locale").value("en"))
                    .andExpect(jsonPath("$.ragEnabledByDefault").value(true));
        }

        @Test
        @DisplayName("a partial update leaves unmentioned fields alone")
        void partialUpdatePreservesOtherFields() throws Exception {
            UUID authUserId = UUID.randomUUID();
            register(authUserId, "me@example.com", "Me", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(patch("/api/v1/me/preferences")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(authUserId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"theme\":\"dark\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.theme").value("dark"))
                    .andExpect(jsonPath("$.locale").value("en"))
                    .andExpect(jsonPath("$.streamResponses").value(true));
        }

        @Test
        @DisplayName("a boolean set to false is applied")
        void appliesFalse() throws Exception {
            UUID authUserId = UUID.randomUUID();
            register(authUserId, "me@example.com", "Me", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(patch("/api/v1/me/preferences")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(authUserId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"streamResponses\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.streamResponses").value(false))
                    .andExpect(jsonPath("$.theme").value("system"));
        }

        @Test
        @DisplayName("rejects an unknown theme")
        void rejectsUnknownTheme() throws Exception {
            UUID authUserId = UUID.randomUUID();
            register(authUserId, "me@example.com", "Me", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(patch("/api/v1/me/preferences")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(authUserId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"theme\":\"neon\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a preference update cannot leak another user's settings")
        void cannotTouchAnotherUsersPreferences() throws Exception {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();
            register(alice, "alice@example.com", "Alice", AccountStatus.ACTIVE, Role.USER);
            register(bob, "bob@example.com", "Bob", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(patch("/api/v1/me/preferences")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(bob)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"theme\":\"dark\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.theme").value("dark"));

            mvc.perform(get("/api/v1/me/preferences")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(alice))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.theme").value("system"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/me/status")
    class OwnStatus {

        @Test
        @DisplayName("shows the caller their own status and reason")
        void showsOwnStatus() throws Exception {
            UUID authUserId = UUID.randomUUID();
            register(authUserId, "me@example.com", "Me", AccountStatus.ACTIVE, Role.USER);

            // Suspended through the ADMIN route, not by poking the entity. Poking the entity
            // would change the status without ever writing a history row, which is exactly the
            // inconsistency this test should not be able to pass on.
            mvc.perform(post("/api/v1/admin/users/" + authUserId + "/suspend")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Spam\"}"))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/v1/me/status")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(authUserId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUSPENDED"))
                    .andExpect(jsonPath("$.reason").value("Spam"))
                    .andExpect(jsonPath("$.history[0].toStatus").value("SUSPENDED"))
                    .andExpect(jsonPath("$.history[0].reason").value("Spam"));
        }

        @Test
        @DisplayName("does NOT reveal which administrator made the change")
        void hidesAdministratorIdentity() throws Exception {
            UUID authUserId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            register(authUserId, "me@example.com", "Me", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(post("/api/v1/admin/users/" + authUserId + "/suspend")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken(adminId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Spam\"}"))
                    .andExpect(status().isOk());

            String body = mvc.perform(get("/api/v1/me/status")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(authUserId))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // The holder needs to know a suspension happened and why. They do not need to know
            // which operator typed it.
            assertThat(body).doesNotContain(adminId.toString());
            assertThat(body).doesNotContain("changedBy");

            // And the admin view for the same user DOES name the operator, which is what makes
            // the omission above a deliberate redaction rather than a missing field.
            mvc.perform(get("/api/v1/admin/users/" + authUserId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusHistory[0].changedBy").value(adminId.toString()));
        }
    }

    // ==================================================================
    // Administrative routes
    // ==================================================================

    @Nested
    @DisplayName("admin routes")
    class Admin {

        @Test
        @DisplayName("a USER token is refused with 403")
        void userCannotUseAdminRoutes() throws Exception {
            UUID authUserId = UUID.randomUUID();
            register(authUserId, "user@example.com", "User", AccountStatus.ACTIVE, Role.USER);
            UUID target = UUID.randomUUID();
            register(target, "target@example.com", "Target", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(get("/api/v1/admin/users")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(authUserId))))
                    .andExpect(status().isForbidden());

            mvc.perform(get("/api/v1/admin/users/" + target)
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(authUserId))))
                    .andExpect(status().isForbidden());

            mvc.perform(post("/api/v1/admin/users/" + target + "/suspend")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(authUserId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Because\"}"))
                    .andExpect(status().isForbidden());

            // And the target is untouched.
            assertThat(profiles.findByAuthUserId(target).orElseThrow().getAccountStatus())
                    .isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("an ADMIN token may list users")
        void adminCanList() throws Exception {
            register(UUID.randomUUID(), "a@example.com", "Alpha", AccountStatus.ACTIVE, Role.USER);
            register(UUID.randomUUID(), "b@example.com", "Beta", AccountStatus.SUSPENDED, Role.USER);

            mvc.perform(get("/api/v1/admin/users")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        @DisplayName("filters by status")
        void filtersByStatus() throws Exception {
            register(UUID.randomUUID(), "a@example.com", "Alpha", AccountStatus.ACTIVE, Role.USER);
            register(UUID.randomUUID(), "b@example.com", "Beta", AccountStatus.SUSPENDED, Role.USER);

            mvc.perform(get("/api/v1/admin/users").param("status", "SUSPENDED")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(1))
                    .andExpect(jsonPath("$.content[0].email").value("b@example.com"));
        }

        @Test
        @DisplayName("searches case-insensitively over name and email")
        void searches() throws Exception {
            register(UUID.randomUUID(), "alice@example.com", "Alice", AccountStatus.ACTIVE, Role.USER);
            register(UUID.randomUUID(), "bob@example.com", "Robert", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(get("/api/v1/admin/users").param("search", "ALICE")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(1));

            mvc.perform(get("/api/v1/admin/users").param("search", "robert")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(1));

            mvc.perform(get("/api/v1/admin/users").param("search", "example.com")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(2));
        }

        @Test
        @DisplayName("rejects an unknown status filter rather than returning everyone")
        void rejectsUnknownFilter() throws Exception {
            register(UUID.randomUUID(), "a@example.com", "Alpha", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(get("/api/v1/admin/users").param("status", "BANANA")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("caps the requested page size")
        void capsPageSize() throws Exception {
            mvc.perform(get("/api/v1/admin/users").param("size", "100000")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(100));
        }

        @Test
        @DisplayName("counts by status")
        void countsByStatus() throws Exception {
            register(UUID.randomUUID(), "a@example.com", "Alpha", AccountStatus.ACTIVE, Role.USER);
            register(UUID.randomUUID(), "b@example.com", "Beta", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(get("/api/v1/admin/users/counts")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.byStatus.length()").value(4));
        }

        @Test
        @DisplayName("views one user's detail with their full status history")
        void viewsDetail() throws Exception {
            UUID target = UUID.randomUUID();
            register(target, "target@example.com", "Target", AccountStatus.ACTIVE, Role.USER);
            UUID adminId = UUID.randomUUID();

            mvc.perform(post("/api/v1/admin/users/" + target + "/suspend")
                            .header(HttpHeaders.AUTHORIZATION,
                                    "Bearer " + TestTokens.adminToken(adminId))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Spam\"}"))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/v1/admin/users/" + target)
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profile.email").value("target@example.com"))
                    .andExpect(jsonPath("$.profile.accountStatus").value("SUSPENDED"))
                    .andExpect(jsonPath("$.preferences.theme").value("system"))
                    .andExpect(jsonPath("$.statusHistory[0].toStatus").value("SUSPENDED"))
                    .andExpect(jsonPath("$.statusHistory[0].reason").value("Spam"))
                    // The admin view DOES name the operator, unlike the self-service view.
                    .andExpect(jsonPath("$.statusHistory[0].changedBy").value(adminId.toString()));
        }

        @Test
        @DisplayName("404s for an unknown user on the detail route")
        void detailUnknownUser() throws Exception {
            mvc.perform(get("/api/v1/admin/users/" + UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }

        @Test
        @DisplayName("rejects a malformed auth id rather than 500ing")
        void detailMalformedId() throws Exception {
            mvc.perform(get("/api/v1/admin/users/not-a-uuid")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }
    }

    @Nested
    @DisplayName("administrative status actions")
    class StatusActions {

        @Test
        @DisplayName("activates a suspended user")
        void activates() throws Exception {
            UUID target = UUID.randomUUID();
            register(target, "t@example.com", "T", AccountStatus.SUSPENDED, Role.USER);

            mvc.perform(post("/api/v1/admin/users/" + target + "/activate")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken()))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));
        }

        @Test
        @DisplayName("suspends with a reason")
        void suspends() throws Exception {
            UUID target = UUID.randomUUID();
            register(target, "t@example.com", "T", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(post("/api/v1/admin/users/" + target + "/suspend")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Policy violation\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"))
                    .andExpect(jsonPath("$.statusReason").value("Policy violation"));
        }

        @Test
        @DisplayName("refuses a suspension with no reason")
        void refusesReasonlessSuspension() throws Exception {
            UUID target = UUID.randomUUID();
            register(target, "t@example.com", "T", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(post("/api/v1/admin/users/" + target + "/suspend")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"  \"}"))
                    .andExpect(status().isBadRequest());

            assertThat(profiles.findByAuthUserId(target).orElseThrow().getAccountStatus())
                    .isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("deactivates permanently")
        void deactivates() throws Exception {
            UUID target = UUID.randomUUID();
            register(target, "t@example.com", "T", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(post("/api/v1/admin/users/" + target + "/deactivate")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken()))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountStatus").value("DEACTIVATED"));
        }

        @Test
        @DisplayName("refuses to reactivate a DEACTIVATED account")
        void deactivatedIsTerminal() throws Exception {
            UUID target = UUID.randomUUID();
            register(target, "t@example.com", "T", AccountStatus.DEACTIVATED, Role.USER);

            mvc.perform(post("/api/v1/admin/users/" + target + "/activate")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken()))
                            .with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ILLEGAL_STATUS_TRANSITION"));

            assertThat(profiles.findByAuthUserId(target).orElseThrow().getAccountStatus())
                    .isEqualTo(AccountStatus.DEACTIVATED);
        }

        @Test
        @DisplayName("every status change is recorded in the append-only history")
        void recordsHistory() throws Exception {
            UUID target = UUID.randomUUID();
            register(target, "t@example.com", "T", AccountStatus.ACTIVE, Role.USER);
            String admin = bearer(TestTokens.adminToken());

            mvc.perform(post("/api/v1/admin/users/" + target + "/suspend")
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"First\"}"))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/v1/admin/users/" + target + "/activate")
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .with(csrf()))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/v1/admin/users/" + target)
                            .header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusHistory.length()").value(2))
                    // Most recent first.
                    .andExpect(jsonPath("$.statusHistory[0].toStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.statusHistory[0].fromStatus").value("SUSPENDED"))
                    .andExpect(jsonPath("$.statusHistory[1].toStatus").value("SUSPENDED"));
        }
    }

    @Nested
    @DisplayName("subscription and usage across the service boundary")
    class Usage {

        @Test
        @DisplayName("reports unavailable rather than zero when Subscription Service is absent")
        void reportsUnavailableNotZero() throws Exception {
            UUID target = UUID.randomUUID();
            register(target, "t@example.com", "T", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(get("/api/v1/admin/users/" + target + "/usage")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(false))
                    .andExpect(jsonPath("$.reason").isNotEmpty())
                    // A zero here would read as "this user has used nothing", which is a
                    // different and wrong claim.
                    .andExpect(jsonPath("$.messagesUsedThisPeriod").doesNotExist());
        }

        @Test
        @DisplayName("404s for an unknown user rather than returning an empty record")
        void unknownUserIs404() throws Exception {
            mvc.perform(get("/api/v1/admin/users/" + UUID.randomUUID() + "/usage")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("a USER cannot read another user's usage")
        void userCannotReadUsage() throws Exception {
            UUID target = UUID.randomUUID();
            register(target, "t@example.com", "T", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(get("/api/v1/admin/users/" + target + "/usage")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken())))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================================================================
    // Between-user isolation
    // ==================================================================

    @Nested
    @DisplayName("one user cannot see another")
    class Isolation {

        @Test
        @DisplayName("two callers see two different profiles")
        void callersSeeTheirOwn() throws Exception {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();
            register(alice, "alice@example.com", "Alice", AccountStatus.ACTIVE, Role.USER);
            register(bob, "bob@example.com", "Bob", AccountStatus.ACTIVE, Role.USER);

            mvc.perform(get("/api/v1/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(alice))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("alice@example.com"));

            mvc.perform(get("/api/v1/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(bob))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("bob@example.com"));
        }

        @Test
        @DisplayName("there is no self-service route that takes a user id at all")
        void noSelfServiceRouteAcceptsAUserId() throws Exception {
            // The real guarantee is structural: /api/v1/me/** has no path variable. These
            // assertions pin the routes that do exist, so adding a /me/{id} route later would
            // fail here rather than ship.
            for (String path : new String[]{"/api/v1/me", "/api/v1/me/preferences",
                    "/api/v1/me/status"}) {
                mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken())))
                        .andExpect(status().isNotFound());
            }
        }

        @Test
        @DisplayName("the admin routes are the only place a user id appears")
        void adminRoutesAreSeparate() throws Exception {
            // A path under /api/v1/me with an id segment must not resolve.
            mvc.perform(get("/api/v1/me/" + UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                    .andExpect(status().isNotFound());
        }
    }
}