package com.nexaai.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/healthz")
    public Mono<ResponseEntity<Map<String, String>>> healthz() {
        return Mono.just(ResponseEntity.ok(Map.of("status", "UP")));
    }
}
