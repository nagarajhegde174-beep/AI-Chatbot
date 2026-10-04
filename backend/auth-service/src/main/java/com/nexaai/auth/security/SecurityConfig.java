package com.nexaai.auth.security;

import com.nexaai.auth.config.AuthProperties;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * The HTTP security configuration.
 *
 * <p>Key decisions, each with the reason it is not the obvious alternative:
 *
 * <ul>
 *   <li><strong>Stateless.</strong> No {@code HttpSession}. A session would mean
 *       session-store state in a service meant to hold none
 *       ({@code docs/ARCHITECTURE.md} decision 007).</li>
 *   <li><strong>CSRF protection is ON.</strong> This is the direct consequence of issuing
 *       cookies: a browser sends them automatically, including cross-site. CSRF protection
 *       is not optional here the way it would be for a bearer-header API.</li>
 *   <li><strong>Permit-all is an explicit allowlist.</strong> Everything else needs
 *       authentication, so a new endpoint is closed by default rather than open
 *       ({@code docs/SECURITY.md} section 4.2).</li>
 *   <li><strong>{@code /internal/**} requires an authority, not merely a valid token.</strong>
 *       A user token must not reach an internal endpoint even through a misconfigured route
 *       ({@code docs/ARCHITECTURE.md} decision 014).</li>
 *   <li><strong>CORS is not configured here.</strong> The gateway owns cross-origin policy.
 *       Two places each allowing origins is one too many.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    /** Endpoints reachable without authentication. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset",
            "/api/v1/auth/email/verify",
            "/api/v1/auth/email/verify-link",
            "/error",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    };

    /**
     * The authority an internal caller must present.
     *
     * <p>Set by a service-to-service credential, never by a user token. The distinction
     * matters: user-service calling the introspection endpoint is legitimate; a user
     * reaching it is not.
     */
    public static final String INTERNAL_AUTHORITY = "SCOPE_internal";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtAuthenticationFilter,
                                            PasswordEncoder passwordEncoder,
                                            AuthProperties properties,
                                            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
                                            ObjectProvider<OAuth2AuthenticationSuccessHandler> oauth2SuccessHandler)
            throws Exception {

        http
                .csrf(csrf -> configureCsrf(csrf, properties))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // Internal endpoints require a service-to-service authority, not just a
                        // valid token. A user token must not reach them even via a misconfigured
                        // route.
                        .requestMatchers("/internal/**").hasAuthority(INTERNAL_AUTHORITY)
                        // Everything else under actuator is denied; health is already permitted above.
                        .requestMatchers("/actuator/**").denyAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("""
                                    {"error":{"code":"AUTH_UNAUTHENTICATED",\
                                    "message":"Authentication is required."}}""");
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write("""
                                    {"error":{"code":"AUTH_ACCESS_DENIED",\
                                    "message":"You do not have permission to perform this action."}}""");
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // formLogin, httpBasic and logout are NOT disabled, because Spring Security 7 removed
        // HttpSecurity.disable() and AbstractHttpConfigurer::disable entirely.
        //
        // They do not need disabling. Declaring an explicit SecurityFilterChain bean makes
        // Spring Boot's default chain back off, so the HttpSecurity instance handed to this
        // method has no form login, no basic auth and no logout filter unless they are invoked.
        // Calling nothing is what removes them.

        // OAuth2 client wiring only when a provider is actually registered. Doing this
        // unconditionally would make a deployment without Google credentials fail at startup,
        // which would make the Google requirement load-bearing for everyone.
        ClientRegistrationRepository registry = clientRegistrations.getIfAvailable();
        OAuth2AuthenticationSuccessHandler successHandler = oauth2SuccessHandler.getIfAvailable();
        if (registry != null && successHandler != null) {
            http.oauth2Login(oauth -> oauth
                    .authorizationEndpoint(authorization -> authorization
                            .authorizationRequestResolver(
                                    new org.springframework.security.oauth2.client.web
                                            .DefaultOAuth2AuthorizationRequestResolver(
                                            registry, "/oauth2/authorization")))
                    .successHandler(successHandler)
                    // A failed Google sign-in redirects back rather than showing a stack trace
                    // to a user who only clicked a button.
                    .failureHandler((request, response, exception) -> {
                        response.sendRedirect(request.getContextPath() + "/login?error=oauth2_failed");
                    }));
        }

        return http.build();
    }

    /**
     * Configures CSRF protection.
     *
     * <p>The token lives in a cookie readable by JavaScript on purpose: the frontend has to
     * read it to echo it in a header. That is the standard double-submit arrangement and it
     * is safe because the cookie is {@code SameSite=Strict}, so a script on another origin
     * cannot read the value to replay.
     */
    private void configureCsrf(CsrfConfigurer<?> csrf, AuthProperties properties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath(properties.getCookie().getPath());
        repository.setCookieCustomizer(cookie -> cookie
                .secure(properties.getCookie().isSecure())
                .sameSite(properties.getCookie().getSameSite()));

        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        // Opt out of the BREACH-protection deferred token, which would otherwise make the
        // header differ from the cookie on every request and break the double submit.
        handler.setCsrfRequestAttributeName(null);
        csrf.csrfTokenRepository(repository).csrfTokenRequestHandler(handler);

        // The pre-authentication endpoints are exempt. There is no cookie-backed session to
        // protect at that point, the caller is unauthenticated by definition, and the cookie
        // is SameSite=Strict so it is not attached cross-site anyway.
        //
        // AntPathRequestMatcher was REMOVED in Spring Security 7; PathPatternRequestMatcher
        // is its replacement. Note the argument order: (HttpMethod, pattern), the reverse
        // of the old (pattern, method) form.
        csrf.ignoringRequestMatchers(
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/login"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/register"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/refresh"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/password/forgot"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/password/reset"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/email/verify"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/email/verify-link"));
    }

    /**
     * Signing key for the CSRF cookie.
     *
     * <p>Generated per process rather than configured, because a CSRF token is not a
     * credential: it protects a connection that is already authenticated, and restarting the
     * service invalidating outstanding CSRF tokens is harmless. Configured "just in case"
     * would be a secret with no owner and no rotation story.
     */
    @Bean
    public SecretKey csrfSigningKey() {
        byte[] material = new byte[32];
        new java.security.SecureRandom().nextBytes(material);
        return new SecretKeySpec(material, "HmacSHA256");
    }
}
