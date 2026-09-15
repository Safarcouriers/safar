package com.saffaricarrers.saffaricarrers.Monitization.webhook;

import com.razorpay.Utils;
import com.saffaricarrers.saffaricarrers.Configaration.RazorpayConfig;
import com.saffaricarrers.saffaricarrers.Monitization.model.Subscription;
import com.saffaricarrers.saffaricarrers.Monitization.repository.ProcessedWebhookEventRepository;
import com.saffaricarrers.saffaricarrers.Monitization.repository.SubscriptionRepository;
import com.saffaricarrers.saffaricarrers.Monitization.service.FirestoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookController {

    private final RazorpayConfig                  razorpayConfig;
    private final SubscriptionRepository          subscriptionRepo;
    private final ProcessedWebhookEventRepository webhookEventRepo;
    private final FirestoreService                firestoreService;

    // ─────────────────────────────────────────────────────────────────────────
    // Main webhook endpoint
    // Configure in Razorpay Dashboard → Settings → Webhooks
    // URL: https://your-prod-domain.com/webhook/razorpay
    // Events to enable: subscription.*, payment.failed
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/razorpay")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        // ── 1. Verify HMAC signature ──────────────────────────────────────────
        if (signature == null || !verifyWebhookSignature(payload, signature)) {
            log.warn("❌ Invalid webhook signature — rejecting");
            return ResponseEntity.status(401).body("Invalid signature");
        }

        try {
            JSONObject event    = new JSONObject(payload);
            String eventId      = event.optString("id", "");      // Razorpay event ID
            String eventType    = event.getString("event");

            log.info("📨 Webhook received: type={} id={}", eventType, eventId);

            // ── FIX #7 — Idempotency check ────────────────────────────────────
            // Razorpay retries webhooks on non-200 or timeout.
            // If we already processed this event ID, return 200 immediately.
            if (!eventId.isBlank() && webhookEventRepo.existsByEventId(eventId)) {
                log.info("⏭ Duplicate webhook ignored: id={} type={}", eventId, eventType);
                return ResponseEntity.ok("OK");
            }

            // ── 2. Route to handler ───────────────────────────────────────────
            switch (eventType) {
                case "subscription.activated"  -> handleSubscriptionActivated(event);
                case "subscription.charged"    -> handleSubscriptionCharged(event);
                case "subscription.cancelled"  -> handleSubscriptionCancelled(event);
                case "subscription.completed"  -> handleSubscriptionCompleted(event);
                case "subscription.halted"     -> handleSubscriptionHalted(event);
                case "payment.failed"          -> handlePaymentFailed(event);
                case "subscription.authenticated" -> handleSubscriptionAuthenticated(event);
                default -> log.debug("Unhandled webhook event: {}", eventType);
            }

            // ── 3. Mark as processed ──────────────────────────────────────────
            if (!eventId.isBlank()) {
                try {
                    webhookEventRepo.save(ProcessedWebhookEvent.builder()
                            .eventId(eventId)
                            .eventType(eventType)
                            .build());
                } catch (Exception e) {
                    // Unique constraint violation = race condition, already saved — ignore
                    log.debug("Webhook event already recorded: {}", eventId);
                }
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("❌ Webhook processing error: {}", e.getMessage(), e);
            // Always return 200 to Razorpay — otherwise it retries endlessly
            return ResponseEntity.ok("OK");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // subscription.authenticated — mandate registered, first charge is future
    // This fires when user sets up AutoPay mandate (our main flow)
    // ─────────────────────────────────────────────────────────────────────────
    private void handleSubscriptionAuthenticated(JSONObject event) {
        JSONObject subData = extractSubscriptionEntity(event);
        String rzpSubId    = subData.getString("id");
        String userId      = getUserIdFromNotes(subData);

        log.info("🔐 Subscription authenticated (mandate registered): {} user={}", rzpSubId, userId);

        Optional<Subscription> subOpt = subscriptionRepo.findByRzpSubscriptionId(rzpSubId);
        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();

            if (sub.getStatus() == Subscription.SubscriptionStatus.INTRO_PENDING) {
                sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
                sub.setIntroPhase(true);
                sub.setIntroPaymentId("mandate_authenticated");
                sub.setIntroPaymentTime(LocalDateTime.now());
                subscriptionRepo.save(sub);
                log.info("✅ DB auto-activated via authenticated webhook for user={}", userId);
            }

            if (userId != null) {
                firestoreService.updateUserPremiumStatus(
                        userId, true, "razorpay",
                        sub.getPlanName(), sub.getRzpPlanId(),
                        true, sub.getIntroEndTime());
                firestoreService.updateSubscriptionId(userId, rzpSubId);
            }
        }

        saveEvent("subscription.authenticated", rzpSubId, userId, subData);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // subscription.activated — first charge collected
    // ─────────────────────────────────────────────────────────────────────────
    private void handleSubscriptionActivated(JSONObject event) {
        JSONObject subData = extractSubscriptionEntity(event);
        String rzpSubId    = subData.getString("id");
        String userId      = getUserIdFromNotes(subData);

        log.info("✅ Subscription activated: {} user={}", rzpSubId, userId);

        Optional<Subscription> subOpt = subscriptionRepo.findByRzpSubscriptionId(rzpSubId);
        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();
            sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            sub.setIntroPhase(false);
            sub.setSubscriptionStartTime(LocalDateTime.now());

            // Parse next billing date from webhook if available
            if (subData.has("current_end")) {
                long currentEnd = subData.getLong("current_end");
                sub.setNextBillingTime(
                        LocalDateTime.ofInstant(
                                Instant.ofEpochSecond(currentEnd), ZoneOffset.UTC));
            }

            subscriptionRepo.save(sub);
        }

        if (userId != null) {
            firestoreService.updateUserPremiumStatus(
                    userId, true, "razorpay",
                    getSubPlanName(subOpt), getSubPlanId(subOpt),
                    false, null);
            firestoreService.updateSubscriptionId(userId, rzpSubId);
        }

        saveEvent("subscription.activated", rzpSubId, userId, subData);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // subscription.charged — recurring billing success
    // FIX #5: was only logging, now properly updates Firestore isPremium=true
    // ─────────────────────────────────────────────────────────────────────────
    private void handleSubscriptionCharged(JSONObject event) {
        JSONObject paymentData = event
                .getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");

        JSONObject subData = extractSubscriptionEntity(event);
        String rzpSubId    = subData.getString("id");
        String paymentId   = paymentData.getString("id");
        String userId      = getUserIdFromNotes(subData);

        log.info("💰 Subscription charged: sub={} payment={} user={}",
                rzpSubId, paymentId, userId);

        // ── FIX #5: Update DB next billing time ───────────────────────────────
        Optional<Subscription> subOpt = subscriptionRepo.findByRzpSubscriptionId(rzpSubId);
        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();
            sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            sub.setIntroPhase(false);

            if (subData.has("current_end")) {
                long currentEnd = subData.getLong("current_end");
                sub.setNextBillingTime(
                        LocalDateTime.ofInstant(
                                Instant.ofEpochSecond(currentEnd), ZoneOffset.UTC));
            }

            subscriptionRepo.save(sub);
        }

        // ── FIX #5: Actually update Firestore isPremium=true on charge ────────
        if (userId != null) {
            firestoreService.updateUserPremiumStatus(
                    userId, true, "razorpay",
                    getSubPlanName(subOpt), getSubPlanId(subOpt),
                    false, null);
        }

        saveEvent("subscription.charged", rzpSubId, userId, paymentData);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // subscription.cancelled — user cancelled (from Razorpay or our API)
    // ─────────────────────────────────────────────────────────────────────────
    private void handleSubscriptionCancelled(JSONObject event) {
        JSONObject subData = extractSubscriptionEntity(event);
        String rzpSubId    = subData.getString("id");
        String userId      = getUserIdFromNotes(subData);

        log.info("❌ Subscription cancelled: {} user={}", rzpSubId, userId);

        Optional<Subscription> subOpt = subscriptionRepo.findByRzpSubscriptionId(rzpSubId);
        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();
            sub.setStatus(Subscription.SubscriptionStatus.CANCELLED);
            sub.setIntroPhase(false);
            subscriptionRepo.save(sub);
        }

        if (userId != null) {
            firestoreService.cancelUserSubscription(userId);
        }

        saveEvent("subscription.cancelled", rzpSubId, userId, subData);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // subscription.completed — all billing cycles done (total_count reached)
    // ─────────────────────────────────────────────────────────────────────────
    private void handleSubscriptionCompleted(JSONObject event) {
        JSONObject subData = extractSubscriptionEntity(event);
        String rzpSubId    = subData.getString("id");
        String userId      = getUserIdFromNotes(subData);

        log.info("✅ Subscription completed (all cycles done): {} user={}", rzpSubId, userId);

        Optional<Subscription> subOpt = subscriptionRepo.findByRzpSubscriptionId(rzpSubId);
        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();
            sub.setStatus(Subscription.SubscriptionStatus.EXPIRED);
            sub.setIntroPhase(false);
            subscriptionRepo.save(sub);
        }

        if (userId != null) {
            firestoreService.cancelUserSubscription(userId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // subscription.halted — repeated payment failures
    // FIX: now updates DB status to FAILED (was only Firestore before)
    // ─────────────────────────────────────────────────────────────────────────
    private void handleSubscriptionHalted(JSONObject event) {
        JSONObject subData = extractSubscriptionEntity(event);
        String rzpSubId    = subData.getString("id");
        String userId      = getUserIdFromNotes(subData);

        log.warn("⚠️  Subscription halted (repeated payment failures): {} user={}",
                rzpSubId, userId);

        // FIX: update DB as well
        Optional<Subscription> subOpt = subscriptionRepo.findByRzpSubscriptionId(rzpSubId);
        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();
            sub.setStatus(Subscription.SubscriptionStatus.FAILED);
            sub.setIntroPhase(false);
            subscriptionRepo.save(sub);
        }

        if (userId != null) {
            firestoreService.cancelUserSubscription(userId);
        }

        saveEvent("subscription.halted", rzpSubId, userId, subData);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // payment.failed — a single payment attempt failed
    // ─────────────────────────────────────────────────────────────────────────
    private void handlePaymentFailed(JSONObject event) {
        JSONObject paymentData = event
                .getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");

        String paymentId = paymentData.getString("id");
        String orderId   = paymentData.optString("order_id", "");

        log.warn("❌ Payment failed: paymentId={} orderId={}", paymentId, orderId);

        // Check if this was an intro payment
        if (!orderId.isBlank()) {
            Optional<Subscription> subOpt = subscriptionRepo.findByIntroOrderId(orderId);
            if (subOpt.isEmpty()) {
                subOpt = subscriptionRepo.findByRzpSubscriptionId(orderId);
            }
            if (subOpt.isPresent()) {
                Subscription sub = subOpt.get();
                // Only mark FAILED if still pending (don't override an already-active sub)
                if (sub.getStatus() == Subscription.SubscriptionStatus.INTRO_PENDING) {
                    sub.setStatus(Subscription.SubscriptionStatus.FAILED);
                    subscriptionRepo.save(sub);
                    log.warn("❌ Intro payment failed for user={}", sub.getUserId());
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private JSONObject extractSubscriptionEntity(JSONObject event) {
        return event
                .getJSONObject("payload")
                .getJSONObject("subscription")
                .getJSONObject("entity");
    }

    private String getUserIdFromNotes(JSONObject entity) {
        try {
            if (entity.has("notes")) {
                Object notesObj = entity.get("notes");
                if (notesObj instanceof JSONObject notes) {
                    return notes.optString("userId", null);
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract userId from notes: {}", e.getMessage());
        }
        return null;
    }

    private String getSubPlanName(Optional<Subscription> subOpt) {
        return subOpt.map(Subscription::getPlanName).orElse("Premium");
    }

    private String getSubPlanId(Optional<Subscription> subOpt) {
        return subOpt.map(Subscription::getRzpPlanId).orElse("");
    }

    private void saveEvent(String eventType, String subId,
                           String userId, JSONObject data) {
        try {
            Map<String, Object> map = new HashMap<>();
            data.keys().forEachRemaining(k -> map.put(k, data.opt(k)));
            firestoreService.saveWebhookEvent(eventType, subId, userId, map);
        } catch (Exception e) {
            log.warn("Failed to save webhook event to Firestore: {}", e.getMessage());
        }
    }

    private boolean verifyWebhookSignature(String payload, String signature) {
        try {
            Utils.verifyWebhookSignature(
                    payload, signature, razorpayConfig.getWebhookSecret());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}