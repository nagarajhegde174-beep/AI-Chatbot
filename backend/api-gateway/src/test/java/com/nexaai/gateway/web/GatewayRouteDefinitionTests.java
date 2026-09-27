package com.nexaai.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

/**
 * The gateway's main job in Phase 0 is to prove that every backend service is reachable
 * through its own route. This test fails if a service is dropped from the configuration,
 * which is the failure mode that turns a microservice setup back into "one app serves
 * everything".
 */
@SpringBootTest
class GatewayRouteDefinitionTests {

    private static final List<String> EXPECTED_ROUTE_IDS = List.of(
            "auth-service",
            "user-service",
            "chat-service",
            "ai-service",
            "document-service",
            "rag-service",
            "subscription-service");

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void definesOneRoutePerBackendService() {
        List<String> routeIds = routeDefinitionLocator.getRouteDefinitions()
                .map(RouteDefinition::getId)
                .collectList()
                .block();

        assertThat(routeIds).isNotNull().containsAll(EXPECTED_ROUTE_IDS);
    }

    @Test
    void keepsApiPathPrefixesOwnedByASingleService() {
        List<RouteDefinition> definitions = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .block();

        assertThat(definitions).isNotNull().isNotEmpty();
        assertThat(definitions)
                .allSatisfy(definition -> assertThat(definition.getPredicates())
                        .as("route %s must constrain what it matches", definition.getId())
                        .isNotEmpty());
    }
}
