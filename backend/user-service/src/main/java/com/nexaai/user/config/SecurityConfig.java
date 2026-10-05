package com.nexaai.user.config;

import com.nexaai.user.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Security configuration.
 *
 * <p>Stateless, token-verified, no session. There is no form login, no HTTP Basic and no
 * remember-me: the access token is the only credential, and a session would reintroduce a
 * second way in.
 *
 * <p>Every path is stated explicitly. A path not listed here falls through to
 * {@code anyRequest().authenticated()}, which is closed to anonymous callers.
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
        // The CSRF token lives in a cookie the browser sends automatically and the SPA reads to
        // put in a header. Required for a cookie-based API, and statelessness does not remove
        // the need: a browser sends a cookie without the application's consent.
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");

        http
                // Spring Boot 4 / Spring Security 7 removed the no-arg enable/disable style
                // initialisers. Lambdas are the supported form, and disabling a configurer
                // means removing it from the chain rather than calling disable().
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        // Actuator exposes no state-changing endpoint. Everything else,
                        // including every write route below, stays CSRF-protected. There is no
                        // HttpMethod.ANY in Spring's HttpMethod, so the methods that could
                        // matter are listed.
                        .ignoringRequestMatchers(
                                PathPatternRequestMatcher.pathPattern(
                                        HttpMethod.POST, "/actuator/**"),
                                PathPatternRequestMatcher.pathPattern(
                                        HttpMethod.PUT, "/actuator/**"),
                                PathPatternRequestMatcher.pathPattern(
                                        HttpMethod.PATCH, "/actuator/**"),
                                PathPatternRequestMatcher.pathPattern(
                                        HttpMethod.DELETE, "/actuator/**")))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .anonymous(anonymous -> anonymous.disable())
                .authorizeHttpRequests(auth -> auth
                        // Operational surface. Health is needed by the container healthcheck
                        // and reveals nothing beyond liveness.
                        .requestMatchers(
                                PathPatternRequestMatcher.pathPattern("/actuator/health"),
                                PathPatternRequestMatcher.pathPattern("/actuator/health/**"),
                                PathPatternRequestMatcher.pathPattern("/actuator/info"))
                        .permitAll()
                        // API documentation.
                        .requestMatchers(
                                PathPatternRequestMatcher.pathPattern("/v3/api-docs"),
                                PathPatternRequestMatcher.pathPattern("/v3/api-docs/**"),
                                PathPatternRequestMatcher.pathPattern("/swagger-ui/**"),
                                PathPatternRequestMatcher.pathPattern("/swagger-ui.html"))
                        .permitAll()
                        // Everything else needs a verified token. A bad token leaves the
                        // request unauthenticated, so this denies it with 401.
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        // 401 rather than a redirect to a login page: this is an API, there is
                        // no login page to redirect to.
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}