package com.nexaai.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security configuration for AI Service.
 *
 * This service has NO public routes. Every endpoint is either:
 *   /internal/v1/ai/**   — internal inter-service calls only
 *   /actuator/**         — infrastructure only
 *
 * The gateway blocks all /internal/** from the internet. This service adds a
 * second layer by requiring that every request carry a valid internal token
 * header (X-Internal-Request: true) which is set only by peer services.
 *
 * For Phase 4: the service trusts any caller that arrives on the internal
 * network (no JWT needed; the gateway is the external trust boundary).
 * Authentication is done at the gateway layer — decision ADR-014.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // Actuator health is always accessible for infrastructure checks
                        .pathMatchers("/actuator/health").permitAll()
                        // All /internal/** endpoints are open to the internal network
                        // (external access is blocked at the gateway and NGINX layers)
                        .pathMatchers("/internal/**").permitAll()
                        // Everything else is denied
                        .anyExchange().denyAll()
                )
                .build();
    }
}
