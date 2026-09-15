package com.saffaricarrers.saffaricarrers.Monitization.service;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.saffaricarrers.saffaricarrers.Configaration.RazorpayConfig;
import com.saffaricarrers.saffaricarrers.Monitization.dto.SubscriptionDto;
import com.saffaricarrers.saffaricarrers.Monitization.model.Subscription;
import com.saffaricarrers.saffaricarrers.Monitization.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepo;
    private final RazorpayConfig razorpayConfig;
    private final FirestoreService firestoreService;

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 1 — Android calls this first: create Razorpay subscription
    // Returns subscription_id so Android opens the AutoPay mandate screen
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public SubscriptionDto.CreateIntroOrderResponse createIntroOrder(
            SubscriptionDto.CreateIntroOrderRequest request) {

        String userId  = request.getUserId();
        String planKey = request.getPlanKey().toLowerCase();

        // ── Block duplicate active subscriptions ──────────────────────────────
        Optional<Subscription> existingOpt =
                subscriptionRepo.findActiveSubscriptionByUserId(userId);

        if (existingOpt.isPresent()) {
            Subscription existing = existingOpt.get();

            // If INTRO_PENDING and same plan — return existing subscription_id
            // so Android can re-open Razorpay without creating a duplicate
            if (existing.getStatus() == Subscription.SubscriptionStatus.INTRO_PENDING
                    && existing.getPlanKey().equals(planKey)
                    && existing.getRzpSubscriptionId() != null) {

                log.info("♻️  Returning existing INTRO_PENDING subscription for user={}", userId);
                return SubscriptionDto.CreateIntroOrderResponse.builder()
                        .success(true)
                        .orderId(existing.getRzpSubscriptionId())
                        .keyId(razorpayConfig.getKeyId())
                        .amount(existing.getPlanAmount())
                        .currency("INR")
                        .planKey(planKey)
                        .planName(existing.getPlanName())
                        .message("Resuming your existing subscription setup.")
                        .subscriptionDbId(String.valueOf(existing.getId()))
                        .build();
            }

            // Any other active state → block
            return SubscriptionDto.CreateIntroOrderResponse.builder()
                    .success(false)
                    .message("You already have an active " + existing.getPlanName()
                            + " subscription. Status: " + existing.getStatus())
                    .build();
        }

        try {
            RazorpayClient client = new RazorpayClient(
                    razorpayConfig.getKeyId(),
                    razorpayConfig.getKeySecret());

            long fullPriceInPaise = razorpayConfig.getPlanAmountPaise(planKey);
            int  introDays        = razorpayConfig.getIntroDays(planKey);

            int totalCount = switch (planKey) {
                case "weekly"    -> 520;
                case "monthly"   -> 120;
                case "quarterly" -> 40;
                case "biannual"  -> 20;
                case "annual"    -> 10;
                default          -> 120;
            };

            // First charge = now + introDays (free period for user)
            long startAt = (System.currentTimeMillis() / 1000L)
                    + ((long) introDays * 24 * 60 * 60);

            JSONObject notes = new JSONObject();
            notes.put("userId",   userId);
            notes.put("planKey",  planKey);
            notes.put("planName", razorpayConfig.getPlanDisplayName(planKey));
            notes.put("type",     "subscription");

            JSONObject subscriptionRequest = new JSONObject();
            subscriptionRequest.put("plan_id",         razorpayConfig.getRegularPlanId(planKey));
            subscriptionRequest.put("total_count",     totalCount);
            subscriptionRequest.put("quantity",        1);
            subscriptionRequest.put("customer_notify", 1);
            subscriptionRequest.put("start_at",        startAt);
            subscriptionRequest.put("notes",           notes);

            com.razorpay.Subscription rzpSub =
                    client.subscriptions.create(subscriptionRequest);

            String rzpSubscriptionId = rzpSub.get("id");
            LocalDateTime firstChargeTime = LocalDateTime.now().plusDays(introDays);

            log.info("✅ Razorpay subscription created: {} for user={} plan={} " +
                            "firstCharge={}",
                    rzpSubscriptionId, userId, planKey, firstChargeTime.toLocalDate());

            // ── Save to DB ────────────────────────────────────────────────────
            Subscription subscription = Subscription.builder()
                    .userId(userId)
                    .userEmail(request.getUserEmail())
                    .userName(request.getUserName())
                    .planKey(planKey)
                    .planName(razorpayConfig.getPlanDisplayName(planKey))
                    .planAmount(fullPriceInPaise)
                    .introPhase(true)
                    .introOrderId(rzpSubscriptionId)        // subscription_id stored here
                    .rzpSubscriptionId(rzpSubscriptionId)
                    .rzpPlanId(razorpayConfig.getRegularPlanId(planKey))
                    .introPlanId(razorpayConfig.getRegularPlanId(planKey))
                    .introEndTime(firstChargeTime)
                    .status(Subscription.SubscriptionStatus.INTRO_PENDING)
                    .realSubscriptionCreated(true)          // subscription already on Razorpay
                    .schedulerRetryCount(0)
                    .build();

            subscription = subscriptionRepo.save(subscription);

            return SubscriptionDto.CreateIntroOrderResponse.builder()
                    .success(true)
                    .orderId(rzpSubscriptionId)
                    .keyId(razorpayConfig.getKeyId())
                    .amount(fullPriceInPaise)
                    .currency("INR")
                    .planKey(planKey)
                    .planName(razorpayConfig.getPlanDisplayName(planKey))
                    .message("Set up AutoPay — Free until "
                            + firstChargeTime.toLocalDate()
                            + ", then ₹" + (fullPriceInPaise / 100)
                            + ". Cancel anytime.")
                    .subscriptionDbId(String.valueOf(subscription.getId()))
                    .build();

        } catch (RazorpayException e) {
            log.error("❌ Razorpay subscription creation failed for user={}: {}",
                    userId, e.getMessage());
            return SubscriptionDto.CreateIntroOrderResponse.builder()
                    .success(false)
                    .message("Payment setup failed. Please try again.")
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 2 — Android calls this after mandate setup completes
    // FIX #1 : signature is NOW verified before activating
    // FIX #2 : idempotent — already-ACTIVE subscriptions return success
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public SubscriptionDto.ConfirmIntroPaymentResponse confirmIntroPayment(
            SubscriptionDto.ConfirmIntroPaymentRequest request) {

        log.info("🔍 Confirm payment: orderId={} paymentId={} userId={}",
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getUserId());

        try {
            // ── Find subscription ─────────────────────────────────────────────
            Optional<Subscription> subOpt =
                    subscriptionRepo.findByRzpSubscriptionId(request.getRazorpayOrderId());

            if (subOpt.isEmpty()) {
                subOpt = subscriptionRepo.findByIntroOrderId(request.getRazorpayOrderId());
            }

            // Last resort: look up by userId
            if (subOpt.isEmpty()) {
                subOpt = subscriptionRepo.findActiveSubscriptionByUserId(request.getUserId());
                log.warn("⚠️  Fell back to userId lookup for user={}", request.getUserId());
            }

            if (subOpt.isEmpty()) {
                log.error("❌ No subscription found for orderId={} userId={}",
                        request.getRazorpayOrderId(), request.getUserId());
                return SubscriptionDto.ConfirmIntroPaymentResponse.builder()
                        .success(false)
                        .message("Subscription not found. Please contact support.")
                        .build();
            }

            Subscription sub = subOpt.get();

            // ── Idempotency guard ─────────────────────────────────────────────
            if (sub.getStatus() == Subscription.SubscriptionStatus.ACTIVE) {
                log.info("ℹ️  Already ACTIVE for user={} — returning success", sub.getUserId());
                return SubscriptionDto.ConfirmIntroPaymentResponse.builder()
                        .success(true)
                        .message("Subscription already active.")
                        .status("ACTIVE")
                        .planName(sub.getPlanName())
                        .introEndTime(sub.getIntroEndTime() != null
                                ? sub.getIntroEndTime()
                                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                : null)
                        .introDays(razorpayConfig.getIntroDays(sub.getPlanKey()))
                        .build();
            }

            // ── FIX #2 — Verify Razorpay signature ───────────────────────────
            // paymentId can be empty for mandate-only setups (no charge yet);
            // only verify when paymentId is actually provided.
            String paymentId  = request.getRazorpayPaymentId();
            String signature  = request.getRazorpaySignature();
            String subId      = request.getRazorpayOrderId();

            boolean signatureValid = false;

            if (paymentId != null && !paymentId.isBlank()
                    && signature != null && !signature.isBlank()) {
                signatureValid = verifySubscriptionSignature(paymentId, subId, signature);
                if (!signatureValid) {
                    log.error("❌ Invalid signature for paymentId={} subId={}", paymentId, subId);
                    // Cross-check with Razorpay API before hard-rejecting
                    // (handles edge case where SDK sent wrong signature format)
                    signatureValid = verifyViaRazorpayApi(sub.getRzpSubscriptionId(), client());
                    if (!signatureValid) {
                        return SubscriptionDto.ConfirmIntroPaymentResponse.builder()
                                .success(false)
                                .message("Payment verification failed. Please contact support.")
                                .build();
                    }
                    log.warn("⚠️  Signature mismatch but Razorpay API confirms active — proceeding");
                }
            } else {
                // No paymentId = mandate registered but first charge is future-dated
                // Verify subscription exists and is in authenticated/active state on Razorpay
                signatureValid = verifyViaRazorpayApi(sub.getRzpSubscriptionId(), client());
                if (!signatureValid) {
                    log.error("❌ Razorpay API says subscription {} is not active",
                            sub.getRzpSubscriptionId());
                    return SubscriptionDto.ConfirmIntroPaymentResponse.builder()
                            .success(false)
                            .message("Mandate not confirmed yet. Please complete the AutoPay setup.")
                            .build();
                }
            }

            // ── Activate subscription ─────────────────────────────────────────
            LocalDateTime now = LocalDateTime.now();
            int introDays = razorpayConfig.getIntroDays(sub.getPlanKey());
            LocalDateTime firstChargeTime = now.plusDays(introDays);

            sub.setIntroPaymentId(paymentId != null ? paymentId : "mandate_registered");
            sub.setIntroPaymentTime(now);
            sub.setIntroEndTime(firstChargeTime);
            sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            sub.setIntroPhase(true);             // still in free period
            sub.setSubscriptionStartTime(now);

            subscriptionRepo.save(sub);
            log.info("✅ DB activated user={} plan={} firstCharge={}",
                    sub.getUserId(), sub.getPlanKey(), firstChargeTime.toLocalDate());

            // ── Sync Firestore ────────────────────────────────────────────────
            try {
                firestoreService.updateUserPremiumStatus(
                        sub.getUserId(), true, "razorpay",
                        sub.getPlanName(), sub.getRzpPlanId(),
                        true,             // inTrial = true during free period
                        firstChargeTime);

                if (sub.getRzpSubscriptionId() != null) {
                    firestoreService.updateSubscriptionId(
                            sub.getUserId(), sub.getRzpSubscriptionId());
                }
            } catch (Exception e) {
                log.error("❌ Firestore update failed (non-fatal): {}", e.getMessage());
                // DB is source of truth — Firestore will self-heal via status endpoint
            }

            return SubscriptionDto.ConfirmIntroPaymentResponse.builder()
                    .success(true)
                    .message("🎉 AutoPay set up! Free access until "
                            + firstChargeTime.toLocalDate()
                            + ", then ₹" + (sub.getPlanAmount() / 100)
                            + " auto-charged.")
                    .status("ACTIVE")
                    .introEndTime(firstChargeTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .planName(sub.getPlanName())
                    .introDays(introDays)
                    .build();

        } catch (Exception e) {
            log.error("❌ FATAL in confirmIntroPayment: {}", e.getMessage(), e);
            return SubscriptionDto.ConfirmIntroPaymentResponse.builder()
                    .success(false)
                    .message("Internal error. Your payment is safe — contact support.")
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FIX #1 — Recovery: called from getSubscriptionStatus when INTRO_PENDING
    // Queries Razorpay API and auto-activates ONLY if mandate is truly
    // authenticated/active. "created" (checkout opened but not completed,
    // including cancelled checkouts) is correctly excluded — see
    // verifyViaRazorpayApi() below.
    // Handles: user paid in PhonePe then killed the app.
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public void recoverPendingSubscriptionIfPaid(Subscription sub) {
        if (sub.getRzpSubscriptionId() == null) return;

        try {
            boolean active = verifyViaRazorpayApi(sub.getRzpSubscriptionId(), client());
            if (!active) {
                log.debug("⏳ Subscription {} still pending on Razorpay",
                        sub.getRzpSubscriptionId());
                return;
            }

            log.info("🔄 Auto-recovering subscription for user={} — Razorpay confirms active",
                    sub.getUserId());

            int introDays = razorpayConfig.getIntroDays(sub.getPlanKey());
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime firstChargeTime = now.plusDays(introDays);

            sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            sub.setIntroPhase(true);
            sub.setIntroPaymentId("recovered_from_razorpay");
            sub.setIntroPaymentTime(now);
            sub.setIntroEndTime(firstChargeTime);
            sub.setSubscriptionStartTime(now);
            subscriptionRepo.save(sub);

            firestoreService.updateUserPremiumStatus(
                    sub.getUserId(), true, "razorpay",
                    sub.getPlanName(), sub.getRzpPlanId(),
                    true, firstChargeTime);

            if (sub.getRzpSubscriptionId() != null) {
                firestoreService.updateSubscriptionId(
                        sub.getUserId(), sub.getRzpSubscriptionId());
            }

            log.info("✅ Auto-recovery complete for user={}", sub.getUserId());

        } catch (Exception e) {
            log.error("❌ Recovery check failed for user={}: {}", sub.getUserId(), e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /status — Android calls on every app resume
    // Triggers recovery if user paid but app was killed
    // ─────────────────────────────────────────────────────────────────────────
    public SubscriptionDto.SubscriptionStatusResponse getSubscriptionStatus(String userId) {

        // Check all subscriptions including PENDING — needed for recovery
        Optional<Subscription> subOpt =
                subscriptionRepo.findLatestSubscriptionByUserId(userId);

        if (subOpt.isEmpty()) {
            return SubscriptionDto.SubscriptionStatusResponse.builder()
                    .hasActiveSubscription(false)
                    .status("NONE")
                    .build();
        }

        Subscription sub = subOpt.get();

        // ── FIX #1 — Auto-recover if user paid and killed app ────────────────
        if (sub.getStatus() == Subscription.SubscriptionStatus.INTRO_PENDING
                && sub.getRzpSubscriptionId() != null) {
            recoverPendingSubscriptionIfPaid(sub);
            // Re-fetch after potential update
            sub = subscriptionRepo.findById(sub.getId()).orElse(sub);
        }

        boolean hasActive = sub.getStatus() == Subscription.SubscriptionStatus.ACTIVE
                || sub.getStatus() == Subscription.SubscriptionStatus.INTRO_PENDING;

        return SubscriptionDto.SubscriptionStatusResponse.builder()
                .hasActiveSubscription(hasActive)
                .status(sub.getStatus().name())
                .planName(sub.getPlanName())
                .planKey(sub.getPlanKey())
                .inIntro(sub.isIntroPhase())
                .introEndTime(sub.getIntroEndTime() != null
                        ? sub.getIntroEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : null)
                .rzpSubscriptionId(sub.getRzpSubscriptionId())
                .nextBillingTime(sub.getNextBillingTime() != null
                        ? sub.getNextBillingTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : null)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CANCEL — FIX #3: only cancel in DB after Razorpay confirms
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public SubscriptionDto.ApiResponse cancelSubscription(String userId) {

        Optional<Subscription> subOpt =
                subscriptionRepo.findActiveSubscriptionByUserId(userId);

        if (subOpt.isEmpty()) {
            return SubscriptionDto.ApiResponse.error("No active subscription found.");
        }

        Subscription sub = subOpt.get();

        // ── Cancel on Razorpay FIRST ──────────────────────────────────────────
        if (sub.getRzpSubscriptionId() != null) {
            try {
                RazorpayClient client = client();
                JSONObject cancelOptions = new JSONObject();
                cancelOptions.put("cancel_at_cycle_end", 1); // cancels at end of period
                client.subscriptions.cancel(sub.getRzpSubscriptionId(), cancelOptions);
                log.info("✅ Razorpay subscription {} cancelled for user={}",
                        sub.getRzpSubscriptionId(), userId);

            } catch (RazorpayException e) {
                log.error("❌ Razorpay cancel failed for user={}: {}", userId, e.getMessage());
                // FIX #3: Do NOT update DB — return error so user can try again
                return SubscriptionDto.ApiResponse.error(
                        "Could not cancel with payment provider. Please try again or contact support.");
            }
        }

        // ── Only mark cancelled after Razorpay succeeds ───────────────────────
        sub.setStatus(Subscription.SubscriptionStatus.CANCELLED);
        sub.setIntroPhase(false);
        subscriptionRepo.save(sub);

        firestoreService.cancelUserSubscription(userId);

        return SubscriptionDto.ApiResponse.ok(
                "Subscription cancelled. Access continues until end of current period.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Called by scheduler when intro period ends — creates real recurring sub
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public void createRealSubscription(Subscription sub) {

        if (sub.isRealSubscriptionCreated()
                && sub.getStatus() == Subscription.SubscriptionStatus.ACTIVE) {
            log.info("⏭ Real subscription already active for user={}", sub.getUserId());
            return;
        }

        log.info("🔄 Scheduler: upgrading subscription for user={} plan={}",
                sub.getUserId(), sub.getPlanKey());

        try {
            RazorpayClient client = client();

            // Fetch current status from Razorpay — it may already be active
            com.razorpay.Subscription rzpSub =
                    client.subscriptions.fetch(sub.getRzpSubscriptionId());
            String rzpStatus = rzpSub.get("status");

            log.info("📡 Razorpay subscription {} status={}", sub.getRzpSubscriptionId(), rzpStatus);

            // If Razorpay already activated it (e.g. webhook arrived) — just sync DB
            if ("active".equals(rzpStatus) || "authenticated".equals(rzpStatus)) {
                sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
                sub.setIntroPhase(false);
                sub.setRealSubscriptionCreated(true);
                sub.setSubscriptionStartTime(LocalDateTime.now());
                subscriptionRepo.save(sub);

                firestoreService.updateUserPremiumStatus(
                        sub.getUserId(), true, "razorpay",
                        sub.getPlanName(), sub.getRzpPlanId(), false, null);
                firestoreService.updateSubscriptionId(
                        sub.getUserId(), sub.getRzpSubscriptionId());

                log.info("✅ Synced already-active Razorpay subscription for user={}",
                        sub.getUserId());
                return;
            }

            // Otherwise resume/reactivate
            client.subscriptions.fetch(sub.getRzpSubscriptionId()); // validate it exists
            sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            sub.setIntroPhase(false);
            sub.setRealSubscriptionCreated(true);
            sub.setSubscriptionStartTime(LocalDateTime.now());
            subscriptionRepo.save(sub);

            firestoreService.updateUserPremiumStatus(
                    sub.getUserId(), true, "razorpay",
                    sub.getPlanName(), sub.getRzpPlanId(), false, null);
            firestoreService.updateSubscriptionId(
                    sub.getUserId(), sub.getRzpSubscriptionId());

        } catch (RazorpayException e) {
            log.error("❌ Scheduler upgrade failed for user={}: {}",
                    sub.getUserId(), e.getMessage());

            sub.setSchedulerRetryCount(sub.getSchedulerRetryCount() + 1);
            if (sub.getSchedulerRetryCount() >= 5) {
                sub.setStatus(Subscription.SubscriptionStatus.FAILED);
                log.error("❌ Giving up on user={} after 5 retries", sub.getUserId());
            }
            subscriptionRepo.save(sub);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NEW — called by SubscriptionUpgradeScheduler.cleanupStaleOrders() so that
    // a stale INTRO_PENDING row doesn't just get marked EXPIRED in our DB while
    // leaving an orphaned "created" subscription sitting on Razorpay forever.
    // Safe to call even if the subscription was already cancelled/expired on
    // Razorpay's side — that case is just logged and swallowed.
    // ─────────────────────────────────────────────────────────────────────────
    public void cancelOnRazorpayQuietly(String rzpSubscriptionId, String userId) {
        if (rzpSubscriptionId == null) return;
        try {
            RazorpayClient client = client();
            JSONObject cancelOptions = new JSONObject();
            cancelOptions.put("cancel_at_cycle_end", 0); // stale/unpaid — cancel immediately
            client.subscriptions.cancel(rzpSubscriptionId, cancelOptions);
            log.info("🧹 Cancelled stale Razorpay subscription {} for user={}",
                    rzpSubscriptionId, userId);
        } catch (RazorpayException e) {
            // Most likely already cancelled/expired on Razorpay's side, or never
            // progressed past "created" — non-fatal, cleanup job continues either way.
            log.warn("🧹 Could not cancel stale Razorpay subscription {} for user={}: {}",
                    rzpSubscriptionId, userId, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private RazorpayClient client() throws RazorpayException {
        return new RazorpayClient(razorpayConfig.getKeyId(), razorpayConfig.getKeySecret());
    }

    /**
     * Verify subscription payment signature (HMAC-SHA256).
     * Used when Android provides paymentId + subscriptionId + signature.
     */
    private boolean verifySubscriptionSignature(String paymentId,
                                                String subscriptionId,
                                                String signature) {
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_payment_id",      paymentId);
            attributes.put("razorpay_subscription_id", subscriptionId);
            attributes.put("razorpay_signature",       signature);
            Utils.verifyPaymentSignature(attributes, razorpayConfig.getKeySecret());
            return true;
        } catch (RazorpayException e) {
            log.warn("Signature verify failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Recovery path — directly query Razorpay API to check subscription state.
     * Returns true ONLY if the user has actually completed the AutoPay mandate
     * (authenticated / active). "created" means the subscription object exists
     * on Razorpay's side but checkout was never finished — including when the
     * user opened Razorpay checkout and then cancelled. That state must NEVER
     * be treated as proof of payment, or every cancelled checkout gets silently
     * auto-activated the next time the app calls /status.
     * Used when Android callback was missed (app killed after paying).
     */
    private boolean verifyViaRazorpayApi(String rzpSubscriptionId, RazorpayClient client) {
        try {
            com.razorpay.Subscription rzpSub = client.subscriptions.fetch(rzpSubscriptionId);
            String status = rzpSub.get("status");
            log.info("📡 Razorpay API check: subId={} status={}", rzpSubscriptionId, status);
            // created       = subscription object exists, checkout not completed
            //                 (includes cancelled checkouts) — NOT confirmed
            // authenticated = mandate registered, first charge pending — confirmed
            // active        = first charge done — confirmed
            return "authenticated".equals(status)
                    || "active".equals(status);
        } catch (RazorpayException e) {
            log.error("❌ Razorpay API fetch failed for {}: {}", rzpSubscriptionId, e.getMessage());
            return false;
        }
    }
}