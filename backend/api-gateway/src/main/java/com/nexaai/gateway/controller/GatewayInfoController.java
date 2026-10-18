package com.nexaai.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/gateway")
public class GatewayInfoController {

    @GetMapping("/info")
    public Mono<ResponseEntity<Map<String, Object>>> getInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", "api-gateway");
        info.put("version", "0.1.0");
        info.put("role", "Public entry point, routing, token validation, edge security");
        info.put("port", 8080);
        info.put("database", null);
        info.put("listensKafka", false);
        info.put("consumes", Collections.emptyList());
        info.put("produces", Collections.emptyList());
        return Mono.just(ResponseEntity.ok(info));
    }
}
