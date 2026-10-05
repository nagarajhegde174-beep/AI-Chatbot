package com.nexaai.gateway;

import com.nexaai.gateway.support.GatewayTestTokens;
import com.nexaai.gateway.support.UpstreamStub;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Base class for tests that exercise the gateway over real HTTP.
 *
 * <p>Runs the gateway on a real port and points both upstream routes at an {@link UpstreamStub}.
 * Nothing is mocked: real sockets, real routing, real header rewriting. A gateway test that
 * mocked the routing layer would prove only that the gateway's own data structures agree with
 * each other.
 *
 * <p>The stub is created in a static initialiser rather than {@code @BeforeEach}, because
 * {@link #upstreamProperties} resolves its URI while the Spring context starts, which happens
 * before any {@code @BeforeEach}. Creating it later would leave the property pointing nowhere,
 * and the failure would surface as a confusing connection error rather than as the ordering bug
 * it is.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class GatewayIntegrationTest {

    private static final UpstreamStub UPSTREAM = startStub();

    @LocalServerPort
    private int port;

    private WebClient http;

    private static UpstreamStub startStub() {
        try {
            return new UpstreamStub();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the upstream stub.", e);
        }
    }

    @AfterAll
    static void stopStub() {
        UPSTREAM.close();
    }

    /**
 * Routes, verification key and CORS are all supplied here.
 *
 * <p>The routes are defined as indexed properties rather than in YAML on purpose. Declaring
 * them in {@code application-test.yml} with a {@code ${PLACEHOLDER}} for the upstream URI
 * depends on that placeholder resolving from a dynamic property source; when it does not
 * resolve, the routes silently fail to bind and every request 404s at the gateway. The tests
 * then assert a 404 and pass while proving nothing. Indexed properties bind explicitly or fail
 * loudly.
 */
    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("nexa.gateway.jwt.public-key", GatewayTestTokens::publicKeyPem);

        // /api/auth/** with the /api prefix stripped, matching the production route shape.
        // The prefix is spring.cloud.gateway.server.webflux, NOT spring.cloud.gateway.
        //
        // Spring Cloud Gateway 5 renamed it, and the old prefix is silently ignored rather
        // than rejected. That produces a gateway which starts cleanly, reports healthy, and
        // has zero routes -- so every request 404s at the gateway and the routing tests pass
        // while asserting nothing. This exact failure cost an hour to find.
        // /api/auth/login -> /api/v1/auth/login, which is the path Auth Service actually serves.
        // NOT StripPrefix=1: that yields /auth/login, which Auth Service 404s. Stripping a
        // segment only works when what remains happens to match.
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "auth-service");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].uri", UPSTREAM::baseUri);
        registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0]",
                () -> "Path=/api/auth/**");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[0]",
                () -> "RewritePath=/api/auth/(?<remaining>.*), /api/v1/auth/$\\{remaining}");

        // /api/users/** rewritten to the /api/v1/** paths User Service serves.
        registry.add("spring.cloud.gateway.server.webflux.routes[1].id", () -> "user-service");
        registry.add("spring.cloud.gateway.server.webflux.routes[1].uri", UPSTREAM::baseUri);
        registry.add("spring.cloud.gateway.server.webflux.routes[1].predicates[0]",
                () -> "Path=/api/users/**");
        registry.add("spring.cloud.gateway.server.webflux.routes[1].filters[0]",
                () -> "RewritePath=/api/users/(?<remaining>.*), /api/v1/$\\{remaining}");
    }

    /**
     * The test's own HTTP client.
     *
     * <p>Built directly rather than injected: this is a reactive application, so there is no
     * {@code WebClient.Builder} bean to inject, and pulling one in just to talk to the gateway
     * under test would test the wrong thing.
     */
    protected WebClient http() {
        if (http == null) {
            http = WebClient.builder().baseUrl("http://127.0.0.1:" + port).build();
        }
        return http;
    }

    protected UpstreamStub upstream() {
        return UPSTREAM;
    }

    protected void resetUpstream() {
        UPSTREAM.clear();
        UPSTREAM.respondWith(200, "{\"stub\":true}");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Every header a client might try to use to assert its own identity. */
    protected static final List<String> SPOOFABLE_HEADERS = List.of(
            "X-User-Id", "X-User-Email", "X-User-Roles", "X-User-Name",
            "X-Authenticated", "X-Forwarded-For", "X-Real-IP");

    protected static final String HEADER_USER_ID = "X-User-Id";

    protected static final String HEADER_USER_ROLES = "X-User-Roles";

    protected static final String HEADER_USER_EMAIL = "X-User-Email";

    protected static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    protected static HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // ==================================================================
    // Request helpers
    //
    // These exist so the tests do not repeat `.onStatus(...)` on every call. `retrieve()`
    // throws on a 4xx or 5xx, which would mean every negative test needs an exception
    // handler and a stack trace to read. exchangeToMono returns the response as-is, which
    // is what a negative test is actually asserting about.
    // ==================================================================

    /** GET with an optional bearer token. The response is returned whatever its status. */
    protected ResponseEntity<String> get(String uri, String token) {
        WebClient.RequestHeadersSpec<?> request = http().get().uri(uri);
        if (token != null) {
            request = request.headers(h -> h.set(HttpHeaders.AUTHORIZATION, bearer(token)));
        }
        return send(request);
    }

    protected ResponseEntity<String> get(String uri) {
        return get(uri, null);
    }

    /** POST a JSON body with an optional bearer token. */
    protected ResponseEntity<String> postJson(String uri, String token, String body) {
        WebClient.RequestBodySpec request =
                http().post().uri(uri).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request = request.headers(h -> h.set(HttpHeaders.AUTHORIZATION, bearer(token)));
        }
        return send(request.bodyValue(body));
    }

    protected ResponseEntity<String> postJson(String uri, String body) {
        return postJson(uri, null, body);
    }

    protected ResponseEntity<String> options(String uri, String origin, String method,
                                            String requestHeaders) {
        return send(http().options().uri(uri).headers(h -> {
            h.set(HttpHeaders.ORIGIN, origin);
            h.set(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method);
            if (requestHeaders != null) {
                h.set(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, requestHeaders);
            }
        }));
    }

    private ResponseEntity<String> send(WebClient.RequestHeadersSpec<?> request) {
        ResponseEntity<String> response = request
                .exchangeToMono(r -> r.toEntity(String.class))
                .onErrorResume(throwable -> Mono.just(
                        ResponseEntity.status(HttpStatus.BAD_GATEWAY).build()))
                .block();

        if (response == null) {
            throw new IllegalStateException("The gateway returned no response.");
        }
        return response;
    }
}