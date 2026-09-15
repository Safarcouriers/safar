package com.saffaricarrers.saffaricarrers.Monitization.controller;

import com.saffaricarrers.saffaricarrers.Monitization.dto.SubscriptionDto;
import com.saffaricarrers.saffaricarrers.Monitization.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    // ── POST /api/v1/subscription/intro/create ────────────────────────────────
    // Android calls first — creates Razorpay subscription, returns subscription_id
    // Returns existing subscription_id if user already has INTRO_PENDING (safe to retry)
    @PostMapping("/intro/create")
    public ResponseEntity<SubscriptionDto.CreateIntroOrderResponse> createIntroOrder(
            @Valid @RequestBody SubscriptionDto.CreateIntroOrderRequest request) {

        log.info("📱 Create intro order: user={} plan={}",
                request.getUserId(), request.getPlanKey());

        return ResponseEntity.ok(subscriptionService.createIntroOrder(request));
    }

    // ── POST /api/v1/subscription/intro/confirm ───────────────────────────────
    // Android calls after mandate setup (onPaymentSuccess callback)
    // Verifies Razorpay signature, activates subscription in DB + Firestore
    // Idempotent — safe to call multiple times
    @PostMapping("/intro/confirm")
    public ResponseEntity<SubscriptionDto.ConfirmIntroPaymentResponse> confirmIntroPayment(
            @Valid @RequestBody SubscriptionDto.ConfirmIntroPaymentRequest request) {

        log.info("📱 Confirm payment: user={} order={}",
                request.getUserId(), request.getRazorpayOrderId());

        return ResponseEntity.ok(subscriptionService.confirmIntroPayment(request));
    }

    // ── GET /api/v1/subscription/status/{userId} ──────────────────────────────
    // Android calls on EVERY app resume (onResume)
    // FIX #1: automatically recovers subscriptions where user paid + killed app
    // If INTRO_PENDING + mandate registered on Razorpay → auto-activates
    @GetMapping("/status/{userId}")
    public ResponseEntity<SubscriptionDto.SubscriptionStatusResponse> getStatus(
            @PathVariable String userId) {

        log.debug("📱 Status check: user={}", userId);
        return ResponseEntity.ok(subscriptionService.getSubscriptionStatus(userId));
    }

    // ── POST /api/v1/subscription/cancel/{userId} ─────────────────────────────
    // Android calls when user wants to cancel
    // FIX #3: only marks CANCELLED after Razorpay API confirms — returns error if Razorpay fails
    @PostMapping("/cancel/{userId}")
    public ResponseEntity<SubscriptionDto.ApiResponse> cancelSubscription(
            @PathVariable String userId) {

        log.info("📱 Cancel subscription: user={}", userId);
        return ResponseEntity.ok(subscriptionService.cancelSubscription(userId));
    }

    // ── GET /api/v1/subscription/health ──────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<SubscriptionDto.ApiResponse> health() {
        return ResponseEntity.ok(SubscriptionDto.ApiResponse.ok("Subscription service running ✅"));
    }
}