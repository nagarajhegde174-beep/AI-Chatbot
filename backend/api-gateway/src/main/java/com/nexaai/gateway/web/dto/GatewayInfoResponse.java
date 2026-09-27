package com.nexaai.gateway.web.dto;

import java.util.List;

/**
 * Phase 0 boundary descriptor for the edge.
 *
 * <p>Duplicated on purpose instead of being shared through a common library module, see
 * docs/DECISIONS.md ADR-0006.
 *
 * @param service      Always {@code api-gateway}.
 * @param role         Single-sentence statement of what the edge is allowed to do.
 * @param port         Configured HTTP port.
 * @param routedPaths  Public path prefixes the gateway forwards, one entry per service.
 * @param routedTo     The service each prefix is forwarded to, in the same order.
 */
public record GatewayInfoResponse(
        String service,
        String role,
        int port,
        List<String> routedPaths,
        List<String> routedTo) {
}
