package com.nexaai.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexaai.gateway.support.GatewayTestTokens;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * What the gateway forwards, and what it refuses to forward.
 *
 * <p>Every assertion is made against {@link com.nexaai.gateway.support.UpstreamStub}, which
 * records the bytes that crossed the wire. That is the only way to test a proxy: an assertion
 * about the gateway's own state would be satisfied by a gateway that believed it had stripped
 * a header while forwarding it intact.
 */
class GatewayRoutingIntegrationTest extends GatewayIntegrationTest {

    @BeforeEach
    void reset() {
        resetUpstream();
    }

    // ==================================================================
    // Routing
    // ==================================================================

    @Nested
    @DisplayName("routing")
    class Routing {

        @Test
        @DisplayName("/api/auth/login reaches Auth Service as /api/v1/auth/login")
        void routesAuthOntoTheServicePath() {
            get("/api/auth/login", GatewayTestTokens.userToken());

            assertThat(upstream().lastRequest().path()).isEqualTo("/api/v1/auth/login");
        }

        @Test
        @DisplayName("the rest of an auth path survives the rewrite")
        void routesNestedAuthPath() {
            get("/api/auth/password/forgot", GatewayTestTokens.userToken());

            // A single-segment path cannot distinguish a working rewrite from one that ate too
            // much, so this uses a nested one.
            assertThat(upstream().lastRequest().path())
                    .isEqualTo("/api/v1/auth/password/forgot");
        }

        @Test
        @DisplayName("/api/chat/conversations reaches Chat Service as /api/v1/conversations")
        void routesChatOntoTheServicePath() {
            get("/api/chat/conversations", GatewayTestTokens.userToken());

            assertThat(upstream().lastRequest().path()).isEqualTo("/api/v1/conversations");
        }

        @Test
        @DisplayName("a nested chat path survives the rewrite")
        void routesNestedChatPath() {
            // A single-segment path cannot distinguish a working rewrite from one that ate too
            // much, so this uses a nested one.
            get("/api/chat/conversations/abc/messages", GatewayTestTokens.userToken());

            assertThat(upstream().lastRequest().path())
                    .isEqualTo("/api/v1/conversations/abc/messages");
        }

        @Test
        @DisplayName("Auth Service's internal surface is NOT reachable through the gateway")
        void internalAuthSurfaceIsNotRouted() {
            // /internal/v1/auth is the service-to-service API. Exposing it at the public edge
            // would publish an endpoint nobody reviewed as a public one.
            ResponseEntity<String> response =
                    get("/api/internal/auth/session", GatewayTestTokens.userToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(upstream().requests()).isEmpty();
        }

        @Test
        @DisplayName("/api/users/me reaches User Service as /api/v1/me")
        void routesUsersWithRewrite() {
            get("/api/users/me", GatewayTestTokens.userToken());

            assertThat(upstream().lastRequest().path()).isEqualTo("/api/v1/me");
        }

        @Test
        @DisplayName("/api/users/admin/users reaches User Service as /api/v1/admin/users")
        void routesNestedUserPath() {
            get("/api/users/admin/users", GatewayTestTokens.adminToken());

            assertThat(upstream().lastRequest().path()).isEqualTo("/api/v1/admin/users");
        }

        @Test
        @DisplayName("an unrouted path is 404 and reaches no upstream")
        void unroutedPathIs404() {
            ResponseEntity<String> response = get("/api/nope/anything",
                    GatewayTestTokens.userToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(upstream().requests()).isEmpty();
        }

        @Test
        @DisplayName("the 404 body is JSON, never the framework's HTML error page")
        void unroutedPathReturnsJson() {
            ResponseEntity<String> response = get("/api/nope", GatewayTestTokens.userToken());

            assertThat(response.getHeaders().getContentType()).isNotNull();
            assertThat(response.getHeaders().getContentType().getType()).isEqualTo("application");
            assertThat(response.getBody()).contains("\"code\"");
        }
    }

    // ==================================================================
    // The header-trust boundary: the gateway's central security property
    // ==================================================================

    @Nested
    @DisplayName("identity headers")
    class IdentityHeaders {

        @Test
        @DisplayName("a verified token becomes an X-User-Id header")
        void verifiedTokenBecomesIdentityHeader() {
            UUID userId = UUID.randomUUID();

            get("/api/users/me", GatewayTestTokens.userToken(userId));

            assertThat(upstream().lastRequest().header(HEADER_USER_ID))
                    .isEqualTo(userId.toString());
        }

        @Test
        @DisplayName("the roles come from the verified token")
        void rolesComeFromTheToken() {
            get("/api/users/admin/users", GatewayTestTokens.adminToken());

            assertThat(upstream().lastRequest().header(HEADER_USER_ROLES)).contains("ADMIN");
        }

        @ParameterizedTest(name = "strips a client-supplied {0}")
        @MethodSource("com.nexaai.gateway.GatewayRoutingIntegrationTest#spoofableHeaderNames")
        @DisplayName("strips every client-supplied identity header, even alongside a valid token")
        void stripsSpoofableHeaders(String header) {
            http().get().uri("/api/users/me")
                    .headers(h -> {
                        h.set("Authorization", bearer(GatewayTestTokens.userToken()));
                        // The attacker sends a real token AND claims to be someone else. If the
                        // gateway set its own header without removing theirs first, the
                        // upstream would see the attacker's value too.
                        h.set(header, "attacker-controlled-value");
                    })
                    .exchangeToMono(r -> r.toEntity(String.class))
                    .block();

            String forwarded = upstream().lastRequest().header(header);

            // Header names are case-insensitive on the wire, so the filter must remove them
            // case-insensitively. A filter matching only the exact case it writes is defeated
            // by a differently-cased request header.
            assertThat(forwarded == null || !forwarded.contains("attacker-controlled-value"))
                    .as("gateway forwarded the client's %s header: %s", header, forwarded)
                    .isTrue();
        }

        @Test
        @DisplayName("strips a spoofed X-User-Id when NO valid token is present")
        void stripsSpoofedIdentityWithoutAToken() {
            ResponseEntity<String> response = http().get().uri("/api/users/me")
                    .headers(h -> h.set(HEADER_USER_ID,
                            "11111111-1111-1111-1111-111111111111"))
                    .exchangeToMono(r -> r.toEntity(String.class))
                    .block();

            // Refused at the edge, so no upstream ever sees the spoofed header at all.
            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(upstream().requests()).isEmpty();
        }

        @Test
        @DisplayName("does NOT forward the bearer token to the upstream")
        void stripsTheAuthorizationHeader() {
            get("/api/users/me", GatewayTestTokens.userToken());

            assertThat(upstream().lastRequest().hasHeader("Authorization"))
                    .as("forwarding the token hands every upstream a replayable credential")
                    .isFalse();
        }

        @Test
        @DisplayName("forwards the caller's email only from the verified token")
        void emailComesFromTheToken() {
            http().get().uri("/api/users/me")
                    .headers(h -> {
                        h.set("Authorization", bearer(GatewayTestTokens.userToken()));
                        h.set(HEADER_USER_EMAIL, "victim@example.test");
                    })
                    .exchangeToMono(r -> r.toEntity(String.class))
                    .block();

            assertThat(upstream().lastRequest().header(HEADER_USER_EMAIL))
                    .isEqualTo("user@example.com")
                    .doesNotContain("victim@example.test");
        }
    }

    static Stream<String> spoofableHeaderNames() {
        return SPOOFABLE_HEADERS.stream();
    }

    // ==================================================================
    // Public paths
    // ==================================================================

    @Nested
    @DisplayName("public paths")
    class PublicPaths {

        @Test
        @DisplayName("login is reachable without a token")
        void loginNeedsNoToken() {
            postJson("/api/auth/login", "{\"email\":\"a@example.com\",\"password\":\"x\"}");

            assertThat(upstream().requests()).hasSize(1);
        }

        @Test
        @DisplayName("register is reachable without a token")
        void registerNeedsNoToken() {
            postJson("/api/auth/register", "{\"email\":\"a@example.com\",\"password\":\"x\"}");

            assertThat(upstream().requests()).hasSize(1);
        }

        @Test
        @DisplayName("an authenticated endpoint still needs a token")
        void protectedPathStillNeedsAToken() {
            ResponseEntity<String> response = get("/api/users/me");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(upstream().requests()).isEmpty();
        }

        @Test
        @DisplayName("a public path does not leak to its siblings")
        void publicPathDoesNotLeakToSiblings() {
            // /api/auth/login is public; /api/auth/me is not. A prefix-based rule would expose
            // every path under /api/auth, which is most of the interesting surface.
            ResponseEntity<String> response = get("/api/auth/me");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(upstream().requests()).isEmpty();
        }

        @Test
        @DisplayName("actuator/health is public, because the healthcheck has no token")
        void healthIsPublic() {
            assertThat(get("/actuator/health").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("other actuator endpoints are not exposed")
        void otherActuatorsAreClosed() {
            assertThat(get("/actuator/env").getStatusCode()).isNotEqualTo(HttpStatus.OK);
        }
    }

    // ==================================================================
    // Token rejection
    // ==================================================================

    @Nested
    @DisplayName("bad tokens")
    class BadTokens {

        @ParameterizedTest(name = "refuses {0}")
        @MethodSource("badTokens")
        @DisplayName("refuses every unusable token with 401 and forwards nothing")
        void refusesBadTokens(String description, java.util.function.Supplier<String> token) {
            resetUpstream();

            ResponseEntity<String> response = get("/api/users/me", token.get());

            assertThat(response.getStatusCode())
                    .as("%s should be refused", description)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(upstream().requests())
                    .as("%s must not reach an upstream", description)
                    .isEmpty();
        }

        @SuppressWarnings("unchecked")
        static Stream<org.junit.jupiter.params.provider.Arguments> badTokens() {
            return Stream.of(
                    org.junit.jupiter.params.provider.Arguments.of("an expired token",
                            (java.util.function.Supplier<String>)
                                    GatewayTestTokens::expiredToken),
                    org.junit.jupiter.params.provider.Arguments.of("a wrong-issuer token",
                            (java.util.function.Supplier<String>)
                                    GatewayTestTokens::wrongIssuerToken),
                    org.junit.jupiter.params.provider.Arguments.of("a wrong-audience token",
                            (java.util.function.Supplier<String>)
                                    GatewayTestTokens::wrongAudienceToken),
                    org.junit.jupiter.params.provider.Arguments.of("an unsigned token",
                            (java.util.function.Supplier<String>)
                                    GatewayTestTokens::unsignedToken),
                    org.junit.jupiter.params.provider.Arguments.of(
                            "an HS256 algorithm-confusion token",
                            (java.util.function.Supplier<String>)
                                    GatewayTestTokens::algorithmConfusionToken),
                    org.junit.jupiter.params.provider.Arguments.of("a foreign-key forgery",
                            (java.util.function.Supplier<String>)
                                    GatewayTestTokens::foreignKeyToken),
                    org.junit.jupiter.params.provider.Arguments.of("a non-JWT string",
                            (java.util.function.Supplier<String>)
                                    GatewayTestTokens::garbageToken));
        }

        @Test
        @DisplayName("an algorithm-confusion ADMIN token grants nothing")
        void algorithmConfusionGrantsNothing() {
            ResponseEntity<String> response =
                    get("/api/users/admin/users", GatewayTestTokens.algorithmConfusionToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(upstream().requests()).isEmpty();
        }

        @Test
        @DisplayName("the 401 body does not echo the token back")
        void rejectionDoesNotEchoTheToken() {
            String bad = GatewayTestTokens.foreignKeyToken();

            ResponseEntity<String> response = get("/api/users/me", bad);

            assertThat(response.getBody()).isNotNull().doesNotContain(bad);
        }

        @Test
        @DisplayName("a non-Bearer scheme is refused")
        void refusesNonBearerScheme() {
            http().get().uri("/api/users/me")
                    .headers(h -> h.set("Authorization", "Basic " + GatewayTestTokens.userToken()))
                    .exchangeToMono(r -> r.toEntity(String.class))
                    .block();

            assertThat(upstream().requests()).isEmpty();
        }
    }

    // ==================================================================
    // Correlation id
    // ==================================================================

    @Nested
    @DisplayName("correlation id")
    class Correlation {

        @Test
        @DisplayName("generates one when the caller supplies none")
        void generatesWhenAbsent() {
            ResponseEntity<String> response =
                    get("/api/users/me", GatewayTestTokens.userToken());

            String id = response.getHeaders().getFirst(HEADER_CORRELATION_ID);
            assertThat(id).isNotNull();
            assertThat(UUID.fromString(id)).isNotNull();
        }

        @Test
        @DisplayName("honours an inbound UUID so one trace spans every service")
        void honoursInboundUuid() {
            UUID supplied = UUID.randomUUID();

            http().get().uri("/api/users/me")
                    .headers(h -> {
                        h.set("Authorization", bearer(GatewayTestTokens.userToken()));
                        h.set(HEADER_CORRELATION_ID, supplied.toString());
                    })
                    .exchangeToMono(r -> r.toEntity(String.class))
                    .block();

            assertThat(upstream().lastRequest().header(HEADER_CORRELATION_ID))
                    .isEqualTo(supplied.toString());
        }

        @Test
        @DisplayName("replaces a non-UUID inbound value rather than trusting it")
        void rejectsNonUuidInboundValue() {
            // An arbitrary string would be written into logs and error bodies. That is log
            // injection: newlines let a caller forge log entries.
            String hostile = "forged-log-line";

            http().get().uri("/api/users/me")
                    .headers(h -> {
                        h.set("Authorization", bearer(GatewayTestTokens.userToken()));
                        h.set(HEADER_CORRELATION_ID, hostile);
                    })
                    .exchangeToMono(r -> r.toEntity(String.class))
                    .block();

            String forwarded = upstream().lastRequest().header(HEADER_CORRELATION_ID);
            assertThat(forwarded).isNotNull().doesNotContain("forged-log-line");
            assertThat(UUID.fromString(forwarded)).isNotNull();
        }
    }

    // ==================================================================
    // CORS
    // ==================================================================

    @Nested
    @DisplayName("CORS")
    class Cors {

        @Test
        @DisplayName("allows a configured origin")
        void allowsConfiguredOrigin() {
            ResponseEntity<Void> response = http().get().uri("/api/users/me")
                    .headers(h -> {
                        h.set("Authorization", bearer(GatewayTestTokens.userToken()));
                        h.set("Origin", "https://app.nexaai.test");
                    })
                    .exchangeToMono(r -> r.toEntity(Void.class))
                    .block();

            assertThat(response).isNotNull();
            assertThat(response.getHeaders().getAccessControlAllowOrigin())
                    .isEqualTo("https://app.nexaai.test");
        }

        @Test
        @DisplayName("does NOT allow an unconfigured origin")
        void refusesUnknownOrigin() {
            ResponseEntity<Void> response = http().get().uri("/api/users/me")
                    .headers(h -> {
                        h.set("Authorization", bearer(GatewayTestTokens.userToken()));
                        h.set("Origin", "https://evil.test");
                    })
                    .exchangeToMono(r -> r.toEntity(Void.class))
                    .block();

            assertThat(response).isNotNull();
            // No Access-Control-Allow-Origin means the browser refuses to hand the response to
            // the calling script. Note the request still ran server-side: CORS is a browser
            // control, not an authorisation mechanism.
            assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
        }

        @Test
        @DisplayName("answers a preflight for a configured origin")
        void answersPreflight() {
            ResponseEntity<String> response = options("/api/users/me",
                    "https://app.nexaai.test", "POST", "Authorization");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNotNull();
            assertThat(response.getHeaders().getAccessControlAllowCredentials()).isTrue();
        }
    }

    // ==================================================================
    // Upstream behaviour
    // ==================================================================

    @Nested
    @DisplayName("upstream behaviour")
    class Upstream {

        @Test
        @DisplayName("passes the upstream's status and body through unchanged")
        void passesUpstreamResponseThrough() {
            upstream().respondWith(404, "{\"code\":\"USER_NOT_FOUND\"}");

            ResponseEntity<String> response =
                    get("/api/users/me", GatewayTestTokens.userToken());

            // The gateway does not reinterpret a service's answer. Wrapping a 404 into a
            // gateway-level error would hide which layer failed, from the caller and from
            // whoever reads the logs.
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).contains("USER_NOT_FOUND");
        }

        @Test
        @DisplayName("forwards the request body and method")
        void forwardsRequestBody() {
            postJson("/api/users/me/preferences", GatewayTestTokens.userToken(),
                    "{\"theme\":\"dark\"}");

            assertThat(upstream().lastRequest().body()).isEqualTo("{\"theme\":\"dark\"}");
            assertThat(upstream().lastRequest().method()).isEqualTo("POST");
        }

        @Test
        @DisplayName("preserves the query string")
        void preservesQueryString() {
            get("/api/users/admin/users?status=SUSPENDED&size=5",
                    GatewayTestTokens.adminToken());

            assertThat(upstream().lastRequest().query()).isEqualTo("status=SUSPENDED&size=5");
        }
    }
}