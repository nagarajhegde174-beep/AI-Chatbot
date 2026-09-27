package com.nexaai.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Verifies the API Gateway boots as a standalone Spring Boot application. */
@SpringBootTest
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
        // Fails fast if the gateway context cannot be created in isolation.
    }
}
