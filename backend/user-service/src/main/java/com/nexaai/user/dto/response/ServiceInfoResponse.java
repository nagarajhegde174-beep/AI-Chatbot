package com.nexaai.user.dto.response;

import java.util.List;

/**
 * Service boundary assertion response per SERVICE_CONTRACTS.md §2.
 */
public record ServiceInfoResponse(
        String service,
        String version,
        String role,
        int port,
        String database,
        boolean listensKafka,
        List<String> consumes,
        List<String> produces
) {}
