package com.saffaricarrers.saffaricarrers.N1.service;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.saffaricarrers.saffaricarrers.N1.config.N1RazorpayConfig;
import com.saffaricarrers.saffaricarrers.N1.dto.N1SubscriptionDto;
import com.saffaricarrers.saffaricarrers.N1.model.N1InfluencerAttribution;
import com.saffaricarrers.saffaricarrers.N1.model.N1MonetizationPlan;
import com.saffaricarrers.saffaricarrers.N1.model.N1Subscription;
import com.saffaricarrers.saffaricarrers.N1.repository.N1InfluencerAttributionRepository;
import com.saffaricarrers.saffaricarrers.N1.repository.N1SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class N1SubscriptionService {

    private final N1SubscriptionRepository repo;

    private final N1RazorpayConfig razorpayConfig;

    @Qualifier("n1FirestoreService")
    private final N1FirestoreService n1FirestoreService;

    /*
     * Optional influencer attribution.
     *
     * IMPORTANT:
     * Existing users/payments work even when no attribution exists.
     */
    private final N1InfluencerAttributionRepository attributionRepo;


    // =========================================================================
    // RAZORPAY CLIENT
    // =========================================================================

    private RazorpayClient client() throws RazorpayException {

        return new RazorpayClient(
                razorpayConfig.getKeyId(),
                razorpayConfig.getKeySecret()
        );
    }


    // =========================================================================
    // CREATE ONE-TIME PURCHASE ORDER
    // =========================================================================

    @Transactional
    public N1SubscriptionDto.CreatePurchaseOrderResponse createPurchaseOrder(
            N1SubscriptionDto.CreatePurchaseOrderRequest request
    ) {

        String userId = request.getUserId().trim();
        String idem = request.getIdempotencyKey().trim();

        N1MonetizationPlan plan;

        try {

            plan = N1MonetizationPlan.fromKey(
                    request.getPlanKey()
            );

        } catch (IllegalArgumentException e) {

            return failureCreate(e.getMessage());
        }


        // =========================================================================
        // IDEMPOTENCY
        // =========================================================================

        Optional<N1Subscription> existingByKey =
                repo.findByUserIdAndIdempotencyKey(
                        userId,
                        idem
                );

        if (existingByKey.isPresent()) {

            log.info(
                    "♻️ Reusing idempotent N1 purchase user={} key={}",
                    userId,
                    idem
            );

            return orderResponse(
                    existingByKey.get()
            );
        }


        LocalDateTime now =
                LocalDateTime.now();


        // =========================================================================
        // CHECK EXISTING ACTIVE SUBSCRIPTION
        // =========================================================================

        for (N1Subscription s :
                repo.findLatestByUserId(userId)) {

            if (s.getStatus() ==
                    N1Subscription.SubscriptionStatus.ACTIVE) {

                if (s.getExpiryTime() != null
                        && s.getExpiryTime().isAfter(now)) {

                    return failureCreate(
                            "You already have an active "
                                    + s.getPlanName()
                                    + " plan until "
                                    + format(s.getExpiryTime())
                                    + "."
                    );
                }

                // Existing subscription expired.
                expireIfNeeded(
                        s,
                        now
                );
            }
        }


        // =========================================================================
        // HANDLE OLD PENDING ORDERS
        //
        // Same plan:
        //     Reuse existing Razorpay order.
        //
        // Different plan:
        //     Mark old pending order as FAILED.
        //     Create fresh order.
        // =========================================================================

        List<N1Subscription> pending =
                repo.findPendingForUpdate(
                        userId,
                        N1Subscription.SubscriptionStatus.PENDING
                );


        for (N1Subscription s :
                pending) {

            // -----------------------------------------------------------------
            // SAME PLAN
            // -----------------------------------------------------------------

            if (s.getPlanKey() != null
                    && s.getPlanKey()
                    .equalsIgnoreCase(plan.getKey())
                    && s.getRazorpayOrderId() != null) {

                log.info(
                        "♻️ Reusing existing pending N1 order user={} plan={} orderId={}",
                        userId,
                        plan.getKey(),
                        s.getRazorpayOrderId()
                );

                return orderResponse(s);
            }


            // -----------------------------------------------------------------
            // DIFFERENT PLAN
            // -----------------------------------------------------------------

            log.info(
                    "🔄 User changed N1 plan user={} oldPlan={} newPlan={}. " +
                            "Closing old pending order.",
                    userId,
                    s.getPlanKey(),
                    plan.getKey()
            );

            s.setStatus(
                    N1Subscription.SubscriptionStatus.FAILED
            );

            s.setUpdatedAt(now);

            repo.save(s);
        }


        // =========================================================================
        // CREATE NEW N1 SUBSCRIPTION RECORD
        // =========================================================================

        N1Subscription sub =
                N1Subscription.builder()
                        .userId(userId)
                        .userEmail(request.getUserEmail())
                        .userName(request.getUserName())
                        .planKey(plan.getKey())
                        .planName(plan.getDisplayName())
                        .planAmount(plan.getAmountPaise())
                        .introPhase(false)
                        .paymentMethod(request.getPaymentMethod())
                        .idempotencyKey(idem)
                        .status(
                                N1Subscription.SubscriptionStatus.PENDING
                        )
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

        /*
         * NOTE:
         *
         * We intentionally do NOT require influencerCode here.
         *
         * The current frontend doesn't send it yet.
         *
         * Once Android Install Referrer is integrated,
         * attribution will already exist for the Firebase user
         * and activate() will automatically attach it.
         */

        sub = repo.saveAndFlush(sub);


        // =========================================================================
        // CREATE RAZORPAY ORDER
        // =========================================================================

        try {

            JSONObject notes =
                    new JSONObject();

            notes.put(
                    "userId",
                    userId
            );

            notes.put(
                    "planKey",
                    plan.getKey()
            );

            notes.put(
                    "paymentType",
                    "one_time"
            );

            notes.put(
                    "subscriptionDbId",
                    String.valueOf(sub.getId())
            );


            JSONObject orderRequest =
                    new JSONObject();

            orderRequest.put(
                    "amount",
                    plan.getAmountPaise()
            );

            orderRequest.put(
                    "currency",
                    "INR"
            );

            orderRequest.put(
                    "receipt",
                    "n1_"
                            + sub.getId()
                            + "_"
                            + UUID.randomUUID()
                            .toString()
                            .replace("-", "")
            );

            orderRequest.put(
                    "notes",
                    notes
            );


            log.info(
                    "➡️ Creating Razorpay N1 order user={} plan={} amountPaise={}",
                    userId,
                    plan.getKey(),
                    plan.getAmountPaise()
            );


            com.razorpay.Order order =
                    client().orders.create(
                            orderRequest
                    );


            // =========================================================================
            // GET RAZORPAY ORDER ID
            // =========================================================================

            Object razorpayOrderIdObject =
                    order.get("id");

            String razorpayOrderId;

            if (razorpayOrderIdObject == null) {

                razorpayOrderId = null;

            } else {

                razorpayOrderId =
                        razorpayOrderIdObject.toString();
            }


            if (razorpayOrderId == null
                    || razorpayOrderId.trim().isEmpty()) {

                throw new IllegalStateException(
                        "Razorpay returned an empty order ID"
                );
            }


            sub.setRazorpayOrderId(
                    razorpayOrderId
            );

            sub.setUpdatedAt(
                    LocalDateTime.now()
            );

            repo.save(sub);


            log.info(
                    "✅ N1 Razorpay order created user={} plan={} amount={} orderId={}",
                    userId,
                    plan.getKey(),
                    plan.getAmountPaise(),
                    razorpayOrderId
            );


            return orderResponse(sub);


        } catch (Exception e) {

            sub.setStatus(
                    N1Subscription.SubscriptionStatus.FAILED
            );

            sub.setUpdatedAt(
                    LocalDateTime.now()
            );

            repo.save(sub);


            log.error(
                    "❌ N1 Razorpay order creation failed user={} plan={} dbId={}",
                    userId,
                    plan.getKey(),
                    sub.getId(),
                    e
            );


            return failureCreate(
                    "We could not start payment. Please try again."
            );
        }
    }


    // =========================================================================
    // CONFIRM PAYMENT
    // =========================================================================

    @Transactional
    public N1SubscriptionDto.ConfirmPurchaseResponse confirmPurchase(
            N1SubscriptionDto.ConfirmPurchaseRequest request
    ) {

        Optional<N1Subscription> opt =
                repo.findByRazorpayOrderId(
                        request.getRazorpayOrderId().trim()
                );


        if (opt.isEmpty()) {

            return errorConfirm(
                    "Payment order not found. Please contact support."
            );
        }


        N1Subscription sub =
                opt.get();


        // =========================================================================
        // VERIFY USER
        // =========================================================================

        if (!sub.getUserId().equals(
                request.getUserId().trim()
        )) {

            return errorConfirm(
                    "Payment does not belong to this account."
            );
        }


        // =========================================================================
        // IDEMPOTENCY
        // =========================================================================

        if (sub.getStatus()
                == N1Subscription.SubscriptionStatus.ACTIVE
                && sub.getRazorpayPaymentId() != null) {

            return successConfirm(
                    sub,
                    "Payment already confirmed. Premium is active."
            );
        }


        // =========================================================================
        // PAYMENT MUST STILL BE PENDING
        // =========================================================================

        if (sub.getStatus()
                != N1Subscription.SubscriptionStatus.PENDING) {

            return errorConfirm(
                    "This payment is no longer pending. "
                            + "Please check your subscription status."
            );
        }


        try {

            // =========================================================================
            // VERIFY RAZORPAY SIGNATURE
            // =========================================================================

            JSONObject attrs =
                    new JSONObject();

            attrs.put(
                    "razorpay_order_id",
                    request.getRazorpayOrderId()
            );

            attrs.put(
                    "razorpay_payment_id",
                    request.getRazorpayPaymentId()
            );

            attrs.put(
                    "razorpay_signature",
                    request.getRazorpaySignature()
            );


            Utils.verifyPaymentSignature(
                    attrs,
                    razorpayConfig.getKeySecret()
            );


            // =========================================================================
            // FETCH ORDER
            // =========================================================================

            RazorpayClient c =
                    client();

            com.razorpay.Order order =
                    c.orders.fetch(
                            request.getRazorpayOrderId()
                    );


            long expected =
                    sub.getPlanAmount();


            // =========================================================================
            // ORDER AMOUNT
            // =========================================================================

            Object orderAmountObject =
                    order.get("amount");

            if (orderAmountObject == null) {

                return errorConfirm(
                        "Razorpay order amount could not be verified."
                );
            }


            long orderAmount =
                    Long.parseLong(
                            orderAmountObject.toString()
                    );


            // =========================================================================
            // ORDER CURRENCY
            // =========================================================================

            Object orderCurrencyObject =
                    order.get("currency");

            String orderCurrency =
                    orderCurrencyObject == null
                            ? ""
                            : orderCurrencyObject.toString();


            // =========================================================================
            // VERIFY ORDER
            // =========================================================================

            if (orderAmount != expected
                    || !"INR".equalsIgnoreCase(
                    orderCurrency
            )) {

                return errorConfirm(
                        "Payment amount could not be verified. "
                                + "Please contact support."
                );
            }


            // =========================================================================
            // FETCH PAYMENT
            // =========================================================================

            com.razorpay.Payment payment =
                    c.payments.fetch(
                            request.getRazorpayPaymentId()
                    );


            // =========================================================================
            // PAYMENT AMOUNT
            // =========================================================================

            Object paidObject =
                    payment.get("amount");

            if (paidObject == null) {

                return errorConfirm(
                        "Payment amount could not be verified."
                );
            }


            long paid =
                    Long.parseLong(
                            paidObject.toString()
                    );


            // =========================================================================
            // PAYMENT ORDER ID
            // =========================================================================

            Object paymentOrderIdObject =
                    payment.get("order_id");

            String paymentOrderId =
                    paymentOrderIdObject == null
                            ? ""
                            : paymentOrderIdObject.toString();


            // =========================================================================
            // PAYMENT CURRENCY
            // =========================================================================

            Object paymentCurrencyObject =
                    payment.get("currency");

            String paymentCurrency =
                    paymentCurrencyObject == null
                            ? ""
                            : paymentCurrencyObject.toString();


            // =========================================================================
            // PAYMENT STATUS
            // =========================================================================

            Object paymentStatusObject =
                    payment.get("status");

            String paymentStatus =
                    paymentStatusObject == null
                            ? ""
                            : paymentStatusObject.toString();


            // =========================================================================
            // VERIFY PAYMENT
            // =========================================================================

            if (!request.getRazorpayOrderId()
                    .equals(paymentOrderId)
                    || paid != expected
                    || !"INR".equalsIgnoreCase(
                    paymentCurrency
            )) {

                return errorConfirm(
                        "Payment verification failed. "
                                + "Please contact support."
                );
            }


            // =========================================================================
            // MUST BE CAPTURED
            // =========================================================================

            if (!"captured".equalsIgnoreCase(
                    paymentStatus
            )) {

                return errorConfirm(
                        "Payment is still being processed. "
                                + "Please reopen the app shortly."
                );
            }


            // =========================================================================
            // ACTIVATE
            // =========================================================================

            activate(
                    sub,
                    request.getRazorpayPaymentId(),
                    paid,
                    resolveMethod(payment),
                    LocalDateTime.now()
            );


            return successConfirm(
                    sub,
                    "Payment verified. Your premium plan is active."
            );


        } catch (Exception e) {

            log.error(
                    "❌ N1 payment confirmation failed order={} payment={}",
                    request.getRazorpayOrderId(),
                    request.getRazorpayPaymentId(),
                    e
            );


            return errorConfirm(
                    "Payment was received but verification is "
                            + "temporarily delayed. Please reopen "
                            + "the app in a moment."
            );
        }
    }


    // =========================================================================
    // WEBHOOK PAYMENT RECOVERY
    // =========================================================================

    /**
     * Called by webhook after payment.captured/order.paid.
     *
     * Server verifies payment directly with Razorpay API.
     */
    @Transactional
    public void recoverPayment(
            String orderId,
            String paymentId
    ) {

        Optional<N1Subscription> opt =
                repo.findByRazorpayOrderId(
                        orderId
                );


        if (opt.isEmpty()) {

            log.warn(
                    "N1 webhook order not found order={} payment={}",
                    orderId,
                    paymentId
            );

            return;
        }


        N1Subscription sub =
                opt.get();


        if (sub.getStatus()
                == N1Subscription.SubscriptionStatus.ACTIVE) {

            return;
        }


        try {

            com.razorpay.Payment payment =
                    client().payments.fetch(
                            paymentId
                    );


            // =========================================================================
            // PAYMENT AMOUNT
            // =========================================================================

            Object amountObject =
                    payment.get("amount");

            if (amountObject == null) {

                log.warn(
                        "N1 webhook payment has no amount order={} payment={}",
                        orderId,
                        paymentId
                );

                return;
            }


            long amount =
                    Long.parseLong(
                            amountObject.toString()
                    );


            // =========================================================================
            // PAYMENT ORDER ID
            // =========================================================================

            Object paymentOrderIdObject =
                    payment.get("order_id");

            String paymentOrderId =
                    paymentOrderIdObject == null
                            ? ""
                            : paymentOrderIdObject.toString();


            // =========================================================================
            // PAYMENT CURRENCY
            // =========================================================================

            Object paymentCurrencyObject =
                    payment.get("currency");

            String paymentCurrency =
                    paymentCurrencyObject == null
                            ? ""
                            : paymentCurrencyObject.toString();


            // =========================================================================
            // PAYMENT STATUS
            // =========================================================================

            Object paymentStatusObject =
                    payment.get("status");

            String paymentStatus =
                    paymentStatusObject == null
                            ? ""
                            : paymentStatusObject.toString();


            // =========================================================================
            // VERIFY WEBHOOK PAYMENT
            // =========================================================================

            if (orderId.equals(paymentOrderId)
                    && amount == sub.getPlanAmount()
                    && "INR".equalsIgnoreCase(
                    paymentCurrency
            )
                    && "captured".equalsIgnoreCase(
                    paymentStatus
            )) {

                activate(
                        sub,
                        paymentId,
                        amount,
                        resolveMethod(payment),
                        LocalDateTime.now()
                );
            }


        } catch (Exception e) {

            log.error(
                    "❌ N1 webhook recovery failed order={} payment={}",
                    orderId,
                    paymentId,
                    e
            );


            throw new IllegalStateException(
                    "Razorpay verification temporarily unavailable",
                    e
            );
        }
    }


    // =========================================================================
    // ACTIVATE SUBSCRIPTION
    // =========================================================================

    private void activate(
            N1Subscription sub,
            String paymentId,
            long amount,
            String method,
            LocalDateTime purchaseTime
    ) {

        // =========================================================================
        // IDEMPOTENCY
        // =========================================================================

        if (sub.getStatus()
                == N1Subscription.SubscriptionStatus.ACTIVE) {

            return;
        }


        // =========================================================================
        // PLAN
        // =========================================================================

        N1MonetizationPlan plan =
                N1MonetizationPlan.fromKey(
                        sub.getPlanKey()
                );


        // =========================================================================
        // EXPIRY
        // =========================================================================

        LocalDateTime expiry =
                plan.calculateExpiry(
                        purchaseTime
                );


        // =========================================================================
        // PAYMENT DATA
        // =========================================================================

        sub.setRazorpayPaymentId(
                paymentId
        );

        sub.setAmountPaid(
                amount
        );

        sub.setPaymentMethod(
                method
        );

        sub.setPurchaseTime(
                purchaseTime
        );

        sub.setExpiryTime(
                expiry
        );


        // =========================================================================
        // INFLUENCER ATTRIBUTION
        // =========================================================================
        //
        // This is intentionally optional.
        //
        // If the user came from an influencer link:
        //
        //     Firebase UID
        //          ↓
        //     N1InfluencerAttribution
        //          ↓
        //     influencerCode
        //
        // then we copy the influencer onto THIS paid subscription.
        //
        // If no attribution exists, subscription works normally.
        // =========================================================================

        try {

            Optional<N1InfluencerAttribution> attributionOpt =
                    attributionRepo.findByUserId(
                            sub.getUserId()
                    );

            if (attributionOpt.isPresent()) {

                N1InfluencerAttribution attribution =
                        attributionOpt.get();

                sub.setInfluencerCode(
                        attribution.getInfluencerCode()
                );

                sub.setAttributionSource(
                        attribution.getSource()
                );

                log.info(
                        "🎯 N1 influencer conversion user={} influencer={} source={} payment={}",
                        sub.getUserId(),
                        attribution.getInfluencerCode(),
                        attribution.getSource(),
                        paymentId
                );

            } else {

                log.info(
                        "N1 payment has no influencer attribution user={} payment={}",
                        sub.getUserId(),
                        paymentId
                );
            }

        } catch (Exception e) {

            /*
             * IMPORTANT:
             *
             * Influencer tracking must NEVER prevent a successful
             * customer payment from becoming premium.
             *
             * Therefore attribution failure is logged but payment
             * activation continues.
             */

            log.warn(
                    "⚠️ N1 influencer attribution lookup failed user={} payment={}",
                    sub.getUserId(),
                    paymentId,
                    e
            );
        }


        // =========================================================================
        // MARK ACTIVE
        // =========================================================================

        sub.setStatus(
                N1Subscription.SubscriptionStatus.ACTIVE
        );

        sub.setUpdatedAt(
                LocalDateTime.now()
        );


        // =========================================================================
        // SAVE SUBSCRIPTION
        // =========================================================================

        repo.save(sub);


        // =========================================================================
        // FIRESTORE
        // =========================================================================

        try {

            n1FirestoreService.updateOneTimePremium(
                    sub.getUserId(),
                    sub.getPlanName(),
                    sub.getPlanKey(),
                    purchaseTime,
                    expiry,
                    amount,
                    paymentId
            );


        } catch (Exception e) {

            log.error(
                    "N1 Firestore sync failed user={}",
                    sub.getUserId(),
                    e
            );
        }
    }


    // =========================================================================
    // GET SUBSCRIPTION STATUS
    // =========================================================================

    public N1SubscriptionDto.SubscriptionStatusResponse getSubscriptionStatus(
            String userId
    ) {

        LocalDateTime now =
                LocalDateTime.now();


        for (N1Subscription s :
                repo.findLatestByUserId(
                        userId.trim()
                )) {


            if (s.getStatus()
                    == N1Subscription.SubscriptionStatus.ACTIVE) {


                // =============================================================
                // ACTIVE + NOT EXPIRED
                // =============================================================

                if (s.getExpiryTime() != null
                        && s.getExpiryTime().isAfter(now)) {

                    try {

                        n1FirestoreService.updateOneTimePremium(
                                s.getUserId(),
                                s.getPlanName(),
                                s.getPlanKey(),
                                s.getPurchaseTime(),
                                s.getExpiryTime(),
                                s.getAmountPaid(),
                                s.getRazorpayPaymentId()
                        );


                    } catch (Exception e) {

                        log.warn(
                                "N1 Firestore self-heal failed user={}",
                                userId,
                                e
                        );
                    }


                    return status(s);
                }


                // =============================================================
                // EXPIRED
                // =============================================================

                expireIfNeeded(
                        s,
                        now
                );
            }
        }


        return N1SubscriptionDto.SubscriptionStatusResponse
                .builder()
                .hasActiveSubscription(false)
                .status("NONE")
                .build();
    }


    // =========================================================================
    // EXPIRE SUBSCRIPTIONS
    // =========================================================================

    @Transactional
    public void expireSubscriptions() {

        LocalDateTime now =
                LocalDateTime.now();


        List<N1Subscription> expired =
                repo.findExpiredActive(
                        N1Subscription.SubscriptionStatus.ACTIVE,
                        now
                );


        for (N1Subscription s :
                expired) {

            expireIfNeeded(
                    s,
                    now
            );
        }
    }


    // =========================================================================
    // EXPIRE SINGLE SUBSCRIPTION
    // =========================================================================

    private void expireIfNeeded(
            N1Subscription s,
            LocalDateTime now
    ) {

        if (s.getStatus()
                == N1Subscription.SubscriptionStatus.ACTIVE
                && s.getExpiryTime() != null
                && !s.getExpiryTime().isAfter(now)) {


            s.setStatus(
                    N1Subscription.SubscriptionStatus.EXPIRED
            );

            s.setUpdatedAt(now);

            repo.save(s);


            try {

                n1FirestoreService.expireOneTimePremium(
                        s.getUserId(),
                        s.getExpiryTime()
                );


            } catch (Exception e) {

                log.warn(
                        "N1 Firestore expiry sync failed user={}",
                        s.getUserId(),
                        e
                );
            }
        }
    }


    // =========================================================================
    // ORDER RESPONSE
    // =========================================================================

    private N1SubscriptionDto.CreatePurchaseOrderResponse orderResponse(
            N1Subscription s
    ) {

        N1MonetizationPlan p =
                N1MonetizationPlan.fromKey(
                        s.getPlanKey()
                );


        return N1SubscriptionDto.CreatePurchaseOrderResponse
                .builder()
                .success(true)
                .orderId(
                        s.getRazorpayOrderId()
                )
                .keyId(
                        razorpayConfig.getKeyId()
                )
                .amount(
                        s.getPlanAmount()
                )
                .currency(
                        "INR"
                )
                .planKey(
                        s.getPlanKey()
                )
                .planName(
                        s.getPlanName()
                )
                .validityDays(
                        p.getValidityDays()
                )
                .message(
                        "Continue the secure payment checkout."
                )
                .subscriptionDbId(
                        String.valueOf(
                                s.getId()
                        )
                )
                .build();
    }


    // =========================================================================
    // CREATE FAILURE
    // =========================================================================

    private N1SubscriptionDto.CreatePurchaseOrderResponse failureCreate(
            String message
    ) {

        return N1SubscriptionDto.CreatePurchaseOrderResponse
                .builder()
                .success(false)
                .message(message)
                .build();
    }


    // =========================================================================
    // CONFIRM FAILURE
    // =========================================================================

    private N1SubscriptionDto.ConfirmPurchaseResponse errorConfirm(
            String message
    ) {

        return N1SubscriptionDto.ConfirmPurchaseResponse
                .builder()
                .success(false)
                .message(message)
                .build();
    }


    // =========================================================================
    // CONFIRM SUCCESS
    // =========================================================================

    private N1SubscriptionDto.ConfirmPurchaseResponse successConfirm(
            N1Subscription s,
            String message
    ) {

        return N1SubscriptionDto.ConfirmPurchaseResponse
                .builder()
                .success(true)
                .message(message)
                .status("ACTIVE")
                .planName(
                        s.getPlanName()
                )
                .planKey(
                        s.getPlanKey()
                )
                .amountPaid(
                        s.getAmountPaid()
                )
                .validityDays(
                        N1MonetizationPlan
                                .fromKey(
                                        s.getPlanKey()
                                )
                                .getValidityDays()
                )
                .purchaseTime(
                        format(
                                s.getPurchaseTime()
                        )
                )
                .expiryTime(
                        format(
                                s.getExpiryTime()
                        )
                )
                .build();
    }


    // =========================================================================
    // STATUS RESPONSE
    // =========================================================================

    private N1SubscriptionDto.SubscriptionStatusResponse status(
            N1Subscription s
    ) {

        return N1SubscriptionDto.SubscriptionStatusResponse
                .builder()
                .hasActiveSubscription(true)
                .status("ACTIVE")
                .planName(
                        s.getPlanName()
                )
                .planKey(
                        s.getPlanKey()
                )
                .purchaseTime(
                        format(
                                s.getPurchaseTime()
                        )
                )
                .expiryTime(
                        format(
                                s.getExpiryTime()
                        )
                )
                .amountPaid(
                        s.getAmountPaid()
                )
                .paymentMethod(
                        s.getPaymentMethod()
                )
                .build();
    }


    // =========================================================================
    // DATE FORMAT
    // =========================================================================

    private String format(
            LocalDateTime value
    ) {

        return value == null
                ? null
                : value.format(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
        );
    }


    // =========================================================================
    // PAYMENT METHOD
    // =========================================================================

    private String resolveMethod(
            com.razorpay.Payment payment
    ) {

        try {

            Object value =
                    payment.get("method");

            if (value == null) {

                return "razorpay";
            }

            return value.toString();

        } catch (Exception e) {

            return "razorpay";
        }
    }
}