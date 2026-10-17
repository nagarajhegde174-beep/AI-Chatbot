package com.nexaai.auth.dto.response;

import java.util.List;

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
