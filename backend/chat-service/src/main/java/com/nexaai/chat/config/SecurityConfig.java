package com.nexaai.chat.config;

import com.nexaai.chat.security.JwtAuthenticationFilter;
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
 * Security configuration: stateless, token-verified, no session.
 *
 * <p>No form login, no HTTP Basic, no remember-me. The access token is the only credential, and a
 * session would reintroduce a second way in.
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
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");

        http
                // Spring Boot 4 / Spring Security 7 removed the no-arg enable/disable
                // initialisers. Lambdas are the supported form.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        // Actuator exposes no state-changing endpoint, so it is exempt. Every
                        // write route below stays protected: the API authenticates with a
                        // cookie, and a browser sends a cookie without the application's
                        // consent, which is exactly what CSRF protection is for.
                        .ignoringRequestMatchers(
                                PathPatternRequestMatcher.pathPattern(HttpMethod.POST,
                                        "/actuator/**"),
                                PathPatternRequestMatcher.pathPattern(HttpMethod.PUT,
                                        "/actuator/**"),
                                PathPatternRequestMatcher.pathPattern(HttpMethod.PATCH,
                                        "/actuator/**"),
                                PathPatternRequestMatcher.pathPattern(HttpMethod.DELETE,
                                        "/actuator/**")))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .anonymous(anonymous -> anonymous.disable())
                .authorizeHttpRequests(auth -> auth
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
                        // Everything else needs a verified token. A bad token leaves the request
                        // unauthenticated, so this denies it with 401.
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}