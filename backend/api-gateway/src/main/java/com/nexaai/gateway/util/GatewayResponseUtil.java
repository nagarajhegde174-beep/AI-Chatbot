package com.nexaai.gateway.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GatewayResponseUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private GatewayResponseUtil() {}

    public static Mono<Void> writeErrorResponse(
            ServerWebExchange exchange,
            HttpStatus status,
            String errorCode,
            String message,
            String correlationId) {

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            response.getHeaders().set("X-Correlation-Id", correlationId);
        }

        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("code", errorCode);
        errorBody.put("message", message);
        errorBody.put("correlationId", correlationId);
        errorBody.put("timestamp", Instant.now().toString());
        errorBody.put("details", null);

        Map<String, Object> root = Map.of("error", errorBody);

        try {
            byte[] bytes = OBJECT_MAPPER.writeValueAsString(root).getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            DataBuffer buffer = response.bufferFactory().wrap(
                    ("{\"error\":{\"code\":\"" + errorCode + "\",\"message\":\"" + message + "\"}}")
                            .getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }
    }
}
