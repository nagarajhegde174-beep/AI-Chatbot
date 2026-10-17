package com.nexaai.auth.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexaai.auth.config.KafkaConfig;
import com.nexaai.auth.dto.event.EventEnvelope;
import com.nexaai.auth.dto.event.UserEmailVerifiedPayload;
import com.nexaai.auth.dto.event.UserRegisteredPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventContractTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void testUserRegisteredEventContract() throws Exception {
        UUID userId = UUID.randomUUID();
        UserRegisteredPayload payload = new UserRegisteredPayload(
                userId,
                "user@example.com",
                "Ada",
                List.of("USER"),
                "PENDING_VERIFICATION",
                Instant.now()
        );

        EventEnvelope<UserRegisteredPayload> event = EventEnvelope.create(
                KafkaConfig.TOPIC_USER_REGISTERED,
                1,
                "correlation-123",
                userId.toString(),
                userId.toString(),
                payload
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode root = objectMapper.readTree(json);

        // Assert envelope fields
        assertNotNull(root.get("eventId"));
        assertEquals("auth.user.registered.v1", root.get("eventType").asText());
        assertEquals(1, root.get("eventVersion").asInt());
        assertNotNull(root.get("occurredAt"));
        assertEquals("correlation-123", root.get("correlationId").asText());
        assertEquals(userId.toString(), root.get("actorId").asText());
        assertEquals(userId.toString(), root.get("subjectId").asText());

        // Assert payload fields
        JsonNode payloadNode = root.get("payload");
        assertEquals(userId.toString(), payloadNode.get("userId").asText());
        assertEquals("user@example.com", payloadNode.get("email").asText());
        assertEquals("Ada", payloadNode.get("displayName").asText());
        assertEquals("PENDING_VERIFICATION", payloadNode.get("status").asText());
        assertTrue(payloadNode.get("roles").isArray());
        assertEquals("USER", payloadNode.get("roles").get(0).asText());
    }

    @Test
    void testUserEmailVerifiedEventContract() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEmailVerifiedPayload payload = new UserEmailVerifiedPayload(
                userId,
                "user@example.com",
                "ACTIVE",
                Instant.now()
        );

        EventEnvelope<UserEmailVerifiedPayload> event = EventEnvelope.create(
                KafkaConfig.TOPIC_USER_EMAIL_VERIFIED,
                1,
                "correlation-456",
                userId.toString(),
                userId.toString(),
                payload
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("auth.user.email_verified.v1", root.get("eventType").asText());
        assertEquals(1, root.get("eventVersion").asInt());
        assertEquals(userId.toString(), root.get("subjectId").asText());

        JsonNode payloadNode = root.get("payload");
        assertEquals(userId.toString(), payloadNode.get("userId").asText());
        assertEquals("user@example.com", payloadNode.get("email").asText());
        assertEquals("ACTIVE", payloadNode.get("status").asText());
    }
}
