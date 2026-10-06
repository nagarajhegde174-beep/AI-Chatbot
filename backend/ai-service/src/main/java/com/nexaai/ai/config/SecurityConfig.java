package com.nexaai.ai.config;

import com.nexaai.ai.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Security: stateless, token-verified, no session, and no CSRF token.
 *
 * <p>No form login, no HTTP Basic, no remember-me. The access token is the only credential and a
 * session would reintroduce a second way in.
 *
 * <p><strong>CSRF is deliberately disabled, and that is not a shortcut.</strong> CSRF protects a
 * credential the browser attaches <em>by itself</em> — a cookie. This service has no cookie
 * credential and no browser-facing route at all: every route under {@code /internal/**} is called
 * by Chat Service with an {@code Authorization: Bearer} header that JavaScript on another origin
 * cannot cause a browser to send and cannot read. A cross-origin page has nothing to forge a
 * request with here.
 *
 * <p>The other services keep CSRF enabled, and they must: they authenticate the browser with an
 * HTTP-only cookie, so a third-party page really can make their browser act on the user's behalf.
 * Copying that configuration here would be cargo-culting protection for an attack that does not
 * apply, while adding a token round-trip to every internal call for no benefit.
 *
 * <p>If a route is ever added here that a browser reaches with a cookie, CSRF protection has to
 * come back with it. That is the condition this decision is conditional on, and it is why the
 * exception is a documented choice rather than a missing line.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .anonymous(anonymous -> anonymous.disable())
                .authorizeHttpRequests(auth -> auth
                        // Liveness only, and deliberately without details: an unauthenticated
                        // probe should learn that the process is up, not how it is configured.
                        .requestMatchers(
                                PathPatternRequestMatcher.pathPattern("/actuator/health"),
                                PathPatternRequestMatcher.pathPattern("/actuator/health/**"),
                                PathPatternRequestMatcher.pathPattern("/actuator/info"))
                        .permitAll()
                        .requestMatchers(
                                PathPatternRequestMatcher.pathPattern("/v3/api-docs"),
                                PathPatternRequestMatcher.pathPattern("/v3/api-docs/**"),
                                PathPatternRequestMatcher.pathPattern("/swagger-ui/**"),
                                PathPatternRequestMatcher.pathPattern("/swagger-ui.html"))
                        .permitAll()
                        // Everything else, including all of /internal/v1/ai/**, needs a
                        // verified token. An invalid token leaves the request unauthenticated
                        // rather than authenticated-with-fewer-claims, so this denies with 401.
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}