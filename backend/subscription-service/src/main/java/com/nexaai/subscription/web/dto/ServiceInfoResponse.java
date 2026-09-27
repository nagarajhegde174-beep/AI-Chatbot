package com.nexaai.subscription.web.dto;

import java.util.List;

/**
 * Phase 0 boundary descriptor.
 *
 * <p>Not part of the public product API. It exists so that a running service can prove
 * which responsibility it owns, which port it listens on and which data it is allowed to
 * touch. Every service exposes an equivalent record with its own values; the shapes are
 * intentionally duplicated rather than shared through a common library module, see
 * docs/DECISIONS.md ADR-0006.
 *
 * @param service       Service name, must equal {@code spring.application.name}.
 * @param role          Single-sentence business responsibility.
 * @param port          Configured HTTP port.
 * @param ownedDatabase The only database this service may connect to.
 * @param ownedTopics   Kafka topics this service produces.
 */
public record ServiceInfoResponse(
        String service,
        String role,
        int port,
        String ownedDatabase,
        List<String> ownedTopics) {
}