package com.nexaai.subscription;

import com.nexaai.subscription.model.Plan;
import com.nexaai.subscription.model.Subscription;
import com.nexaai.subscription.repo.PaymentRepository;
import com.nexaai.subscription.repo.PlanRepository;
import com.nexaai.subscription.repo.SubscriptionRepository;
import com.nexaai.subscription.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SubscriptionSecurityHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @BeforeEach
    void setup() {
        if (planRepository.findByCode("FREE").isEmpty()) {
            Plan free = new Plan();
            free.setCode("FREE");
            free.setName("Free");
            free.setAmount(BigDecimal.ZERO);
            free.setDurationDays(365);
            free.setType(Plan.PlanType.FREE);
            planRepository.save(free);
        }
    }

    @Test
    @DisplayName("USER cannot access admin subscription endpoints (403 Forbidden)")
    void userCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/subscriptions/user-999")
                        .header("X-User-Id", "user-123")
                        .header("X-User-Roles", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN can access admin subscription endpoints")
    void adminCanAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/subscriptions/user-999")
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Service level check: USER cannot view or modify another user's subscription")
    void userCannotModifyAnotherUserSubscription() {
        String alice = "user-alice";
        String bob = "user-bob";

        // Alice cannot view Bob's subscription
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class, () ->
                subscriptionService.getSubscriptionForUser(alice, bob, false)
        );
        assertEquals(403, ex1.getStatusCode().value());

        // Alice cannot cancel Bob's subscription
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class, () ->
                subscriptionService.cancelSubscription(alice, bob, false)
        );
        assertEquals(403, ex2.getStatusCode().value());

        // Alice cannot upgrade Bob's subscription
        ResponseStatusException ex3 = assertThrows(ResponseStatusException.class, () ->
                subscriptionService.createOrUpgradeSubscription(alice, bob, "PRO", false)
        );
        assertEquals(403, ex3.getStatusCode().value());
    }

    @Test
    @DisplayName("RULES.md §9: Razorpay live keys are strictly rejected")
    void razorpayLiveKeyRejected() {
        SubscriptionService serviceWithLiveKey = new SubscriptionService(planRepository, subscriptionRepository, null);
        org.springframework.test.util.ReflectionTestUtils.setField(serviceWithLiveKey, "keyId", "rzp_live_abc123xyz");
        org.springframework.test.util.ReflectionTestUtils.setField(serviceWithLiveKey, "mode", "test");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                serviceWithLiveKey::validateRazorpaySecurityAndSeedPlans);
        assertTrue(ex.getMessage().contains("Razorpay live keys are strictly prohibited"));
    }
}
