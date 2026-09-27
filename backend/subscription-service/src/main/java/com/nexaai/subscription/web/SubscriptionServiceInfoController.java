package com.nexaai.subscription.web;

import com.nexaai.subscription.web.dto.ServiceInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal boundary endpoint. Proves the Subscription Service is deployed as its own
 * application on its own port and knows only its own data. The real public API is
 * introduced in a later phase, see docs/TASKS.md.
 */
@RestController
@RequestMapping("/internal/v1/subscription")
@Tag(name = "internal", description = "Infrastructure and boundary endpoints, not for end users")
public class SubscriptionServiceInfoController {

    private final int port;

    public SubscriptionServiceInfoController(
            @Value("${server.port}") int port) {
        this.port = port;
    }

    @GetMapping("/info")
    @Operation(summary = "Describe this service's boundary")
    public ServiceInfoResponse info() {
        return new ServiceInfoResponse(
                "subscription-service",
                "Plans, entitlements, usage metering and Razorpay TEST-mode order lifecycle.",
                port,
                "nexa_subscription",
                List.of("payment.order.created.v1", "payment.succeeded.v1", "payment.failed.v1"));
    }
}