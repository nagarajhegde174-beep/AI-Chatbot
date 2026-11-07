package com.nexaai.subscription.controller;

import com.nexaai.subscription.model.Subscription;
import com.nexaai.subscription.security.UserPrincipal;
import com.nexaai.subscription.service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/subscriptions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubscriptionController {

    private final SubscriptionService subscriptionService;

    public AdminSubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Subscription> getUserSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("userId") String userId) {
        Subscription sub = subscriptionService.getSubscriptionForUser(principal.getUserId(), userId, true);
        return ResponseEntity.ok(sub);
    }

    @PostMapping("/{userId}/cancel")
    public ResponseEntity<Subscription> cancelUserSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("userId") String userId) {
        Subscription sub = subscriptionService.cancelSubscription(principal.getUserId(), userId, true);
        return ResponseEntity.ok(sub);
    }

    @PostMapping("/{userId}/modify")
    public ResponseEntity<Subscription> modifyUserSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("userId") String userId,
            @RequestBody Map<String, String> body) {
        String planCode = body.getOrDefault("planCode", "PRO");
        Subscription sub = subscriptionService.createOrUpgradeSubscription(
                principal.getUserId(), userId, planCode, true);
        return ResponseEntity.ok(sub);
    }
}
