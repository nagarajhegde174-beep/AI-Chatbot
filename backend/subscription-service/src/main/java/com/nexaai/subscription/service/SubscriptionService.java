package com.nexaai.subscription.service;

import com.nexaai.subscription.model.Payment;
import com.nexaai.subscription.model.Plan;
import com.nexaai.subscription.model.Subscription;
import com.nexaai.subscription.repo.PaymentRepository;
import com.nexaai.subscription.repo.PlanRepository;
import com.nexaai.subscription.repo.SubscriptionRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;

    @Value("${razorpay.key-id:rzp_test_nexaai_placeholder}")
    private String keyId;

    @Value("${razorpay.key-secret:rzp_test_secret_placeholder}")
    private String keySecret;

    @Value("${razorpay.mode:test}")
    private String mode;

    public SubscriptionService(PlanRepository planRepository,
                               SubscriptionRepository subscriptionRepository,
                               PaymentRepository paymentRepository) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
    }

    @PostConstruct
    public void validateRazorpaySecurityAndSeedPlans() {
        // Enforce RULES.md §9: Razorpay TEST MODE ONLY
        if (mode != null && !mode.equalsIgnoreCase("test")) {
            throw new IllegalStateException("CRITICAL SECURITY VIOLATION: Razorpay mode must be 'test'! RULES.md §9.");
        }
        if (keyId != null && keyId.contains("rzp_live_")) {
            throw new IllegalStateException("CRITICAL SECURITY VIOLATION: Razorpay live keys are strictly prohibited! RULES.md §9.");
        }

        // Seed default plans if not present
        if (planRepository.count() == 0) {
            Plan free = new Plan();
            free.setCode("FREE");
            free.setName("Starter Free");
            free.setAmount(BigDecimal.ZERO);
            free.setDurationDays(3650);
            free.setType(Plan.PlanType.FREE);
            planRepository.save(free);

            Plan normal = new Plan();
            normal.setCode("NORMAL");
            normal.setName("Pro Monthly");
            normal.setAmount(new BigDecimal("499.00"));
            normal.setDurationDays(30);
            normal.setType(Plan.PlanType.PAID);
            planRepository.save(normal);

            Plan pro = new Plan();
            pro.setCode("PRO");
            pro.setName("Enterprise Annual");
            pro.setAmount(new BigDecimal("1499.00"));
            pro.setDurationDays(30);
            pro.setType(Plan.PlanType.PAID);
            planRepository.save(pro);

            log.info("Initialized default subscription plans: FREE, NORMAL, PRO");
        }
    }

    public List<Plan> getAllPlans() {
        return planRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Subscription getSubscriptionForUser(String callerUserId, String targetUserId, boolean isAdmin) {
        if (!isAdmin && !callerUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: cannot view another user's subscription.");
        }

        return subscriptionRepository.findTopByUserIdOrderByStartAtDesc(targetUserId)
                .orElseGet(() -> createDefaultFreeSubscription(targetUserId));
    }

    @Transactional
    public Subscription cancelSubscription(String callerUserId, String targetUserId, boolean isAdmin) {
        if (!isAdmin && !callerUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: cannot modify another user's subscription.");
        }

        Subscription sub = subscriptionRepository.findTopByUserIdOrderByStartAtDesc(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active subscription found."));

        sub.setStatus(Subscription.Status.CANCELED);
        sub.setEndsAt(LocalDateTime.now());
        return subscriptionRepository.save(sub);
    }

    @Transactional
    public Subscription createOrUpgradeSubscription(String callerUserId, String targetUserId, String planCode, boolean isAdmin) {
        if (!isAdmin && !callerUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: cannot modify another user's subscription.");
        }

        Plan plan = planRepository.findByCode(planCode.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found: " + planCode));

        Subscription sub = new Subscription();
        sub.setUserId(targetUserId);
        sub.setPlan(plan);
        sub.setStartAt(LocalDateTime.now());
        sub.setEndsAt(LocalDateTime.now().plusDays(plan.getDurationDays()));
        sub.setStatus(Subscription.Status.ACTIVE);

        return subscriptionRepository.save(sub);
    }

    @Transactional
    public Payment createMockRazorpayOrder(String callerUserId, String planCode) {
        Plan plan = planRepository.findByCode(planCode.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found: " + planCode));

        String orderId = "order_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        Payment payment = new Payment();
        payment.setRazorpayOrderId(orderId);
        payment.setAmount(plan.getAmount());
        payment.setPaidAt(LocalDateTime.now());
        payment.setState(Payment.State.CREATED);

        return paymentRepository.save(payment);
    }

    private Subscription createDefaultFreeSubscription(String userId) {
        Plan freePlan = planRepository.findByCode("FREE")
                .orElseThrow(() -> new IllegalStateException("FREE plan missing from database."));

        Subscription sub = new Subscription();
        sub.setUserId(userId);
        sub.setPlan(freePlan);
        sub.setStartAt(LocalDateTime.now());
        sub.setEndsAt(LocalDateTime.now().plusYears(10));
        sub.setStatus(Subscription.Status.ACTIVE);
        return subscriptionRepository.save(sub);
    }
}
