package com.nexaai.subscription;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the NexaAI Subscription Service.
 *
 * <p>Owns plans, subscriptions, entitlements, usage counters and the Razorpay TEST-mode order lifecycle. It never trusts an amount or a status sent by the browser.
 *
 * <p>This application is deployed, scaled and versioned on its own. It is not a module of a
 * larger backend and it never connects to a database owned by another service.
 * See docs/SERVICE_CONTRACTS.md for the contracts owned here.
 */
@SpringBootApplication
public class SubscriptionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubscriptionServiceApplication.class, args);
    }
}