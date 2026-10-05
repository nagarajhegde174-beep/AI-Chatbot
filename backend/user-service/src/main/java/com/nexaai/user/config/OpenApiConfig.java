package com.nexaai.user.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI documentation for User Service.
 *
 * <p>Declares the bearer scheme so the Swagger UI "Authorize" button works and the spec does
 * not describe secured routes as if they were anonymous.
 *
 * <p>This documents <em>this service's</em> routes only. The API Gateway aggregates these
 * documents; it does not redefine them.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI userServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NexaAI User Service API")
                        .version("v1")
                        .description("""
                                User profiles, preferences, account status and administrative user
                                management.

                                ## Authentication

                                Every route requires an RS256 access token issued by NexaAI Auth
                                Service. Send it as `Authorization: Bearer <token>`. This service
                                verifies the token with a public key and holds no signing key.

                                ## What this service does not have

                                No password hashes, no tokens and no secrets are stored or returned
                                by this service, because it does not have them. It also cannot read
                                Auth Service's database: the database role is granted on
                                `nexa_user` only.

                                ## Self-service vs administrative

                                `/api/v1/me/**` is always about the caller and takes no user
                                identifier. `/api/v1/admin/users/**` addresses users by their
                                auth-service id and requires the `ADMIN` role.
                                """)
                        .contact(new Contact().name("NexaAI"))
                        .license(new License().name("Proprietary")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("RS256 access token issued by NexaAI Auth Service.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}