package com.nexaai.subscription.controller;

import com.nexaai.subscription.model.Payment;
import com.nexaai.subscription.model.Plan;
import com.nexaai.subscription.model.Subscription;
import com.nexaai.subscription.security.UserPrincipal;
import com.nexaai.subscription.service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/api/v1/plans")
    public ResponseEntity<List<Plan>> listPlans() {
        return ResponseEntity.ok(subscriptionService.getAllPlans());
    }

    @GetMapping("/api/v1/subscriptions/me")
    public ResponseEntity<Subscription> getOwnSubscription(@AuthenticationPrincipal UserPrincipal principal) {
        Subscription sub = subscriptionService.getSubscriptionForUser(principal.getUserId(), principal.getUserId(), false);
        return ResponseEntity.ok(sub);
    }

    @PostMapping("/api/v1/subscriptions/orders")
    public ResponseEntity<Payment> createOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        String planCode = body.getOrDefault("planCode", "NORMAL");
        Payment payment = subscriptionService.createMockRazorpayOrder(principal.getUserId(), planCode);
        return ResponseEntity.ok(payment);
    }

    @PostMapping("/api/v1/subscriptions/upgrade")
    public ResponseEntity<Subscription> upgradePlan(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        String planCode = body.getOrDefault("planCode", "NORMAL");
        Subscription sub = subscriptionService.createOrUpgradeSubscription(
                principal.getUserId(), principal.getUserId(), planCode, false);
        return ResponseEntity.ok(sub);
    }

    @PostMapping("/api/v1/subscriptions/cancel")
    public ResponseEntity<Subscription> cancelSubscription(@AuthenticationPrincipal UserPrincipal principal) {
        Subscription sub = subscriptionService.cancelSubscription(
                principal.getUserId(), principal.getUserId(), false);
        return ResponseEntity.ok(sub);
    }
}
