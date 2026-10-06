package com.nexaai.chat.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI documentation for Chat Service. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI chatServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NexaAI Chat Service API")
                        .version("v1")
                        .description("""
                                Conversations, messages, history and feedback.

                                ## Authentication

                                Every route requires an RS256 access token issued by NexaAI Auth
                                Service, sent as `Authorization: Bearer <token>`. This service
                                verifies the token with a public key and holds no signing key.

                                ## Isolation

                                No route accepts a user identifier. Conversations and messages are
                                addressed by their own id and scoped to the caller, so there is no
                                parameter through which one user could ask for another's
                                conversations. A conversation belonging to someone else returns
                                the same 404 as one that does not exist, because a different answer
                                would be a reliable oracle for discovering which ids are real.

                                ## AI responses

                                Sending a message stores the user message and creates an
                                assistant placeholder with status `PENDING`. Generation is not
                                wired in this phase, so the placeholder stays `PENDING`: a reply is
                                expected and has not arrived. That is not an error.

                                ## Administrative access

                                `/api/v1/admin/conversations/**` requires the `ADMIN` role and
                                returns conversation metadata only. No route in this service
                                exposes another user's message content.
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