package com.nexaai.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenIntrospectResponse(
        boolean active,
        String sub,
        List<String> roles,
        String status,
        Long exp
) {
    public static TokenIntrospectResponse inactive() {
        return new TokenIntrospectResponse(false, null, null, null, null);
    }

    public static TokenIntrospectResponse active(String sub, List<String> roles, String status, Long exp) {
        return new TokenIntrospectResponse(true, sub, roles, status, exp);
    }
}
