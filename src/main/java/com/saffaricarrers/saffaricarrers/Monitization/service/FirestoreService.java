package com.saffaricarrers.saffaricarrers.Monitization.service;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirestoreService {

    private final Firestore firestore;

    // ─────────────────────────────────────────────────────────────────────────
    // Update user premium status in Firestore
    // (Same structure your Android app already reads from)
    // ─────────────────────────────────────────────────────────────────────────

    public void updateUserPremiumStatus(
            String userId,
            boolean isPremium,
            String source,          // "razorpay_intro" or "razorpay"
            String planName,
            String planId,
            boolean inTrial,
            LocalDateTime trialEndTime) {

        Map<String, Object> data = new HashMap<>();
        data.put("isPremium", isPremium);
        data.put("premiumSource", source);
        data.put("planName", planName);
        data.put("planId", planId);
        data.put("status", "active");
        data.put("inTrial", inTrial);
        data.put("lastUpdated", System.currentTimeMillis());

        if (inTrial && trialEndTime != null) {
            data.put("trialDays", 7); // display value
            data.put("trialEndTimestamp",
                    trialEndTime.toInstant(ZoneOffset.UTC).toEpochMilli());
        }

        firestore.collection("users")
                .document(userId)
                .set(data, SetOptions.merge())
                .addListener(() -> log.info("✅ Firestore updated for user {}", userId),
                        Runnable::run);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Update subscription ID after real subscription is created
    // ─────────────────────────────────────────────────────────────────────────

    public void updateSubscriptionId(String userId, String rzpSubscriptionId) {
        Map<String, Object> data = new HashMap<>();
        data.put("subscriptionId", rzpSubscriptionId);
        data.put("inTrial", false);
        data.put("premiumSource", "razorpay");
        data.put("lastUpdated", System.currentTimeMillis());

        firestore.collection("users")
                .document(userId)
                .set(data, SetOptions.merge())
                .addListener(() -> log.info("✅ Firestore subscription ID updated for user {}",
                        userId), Runnable::run);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cancel subscription in Firestore
    // ─────────────────────────────────────────────────────────────────────────

    public void cancelUserSubscription(String userId) {
        Map<String, Object> data = new HashMap<>();
        data.put("isPremium", false);
        data.put("status", "cancelled");
        data.put("inTrial", false);
        data.put("lastUpdated", System.currentTimeMillis());

        firestore.collection("users")
                .document(userId)
                .set(data, SetOptions.merge())
                .addListener(() -> log.info("✅ Firestore subscription cancelled for user {}",
                        userId), Runnable::run);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Save webhook event to Firestore for audit trail
    // ─────────────────────────────────────────────────────────────────────────

    public void saveWebhookEvent(String eventType, String subscriptionId,
                                  String userId, Map<String, Object> payload) {
        Map<String, Object> data = new HashMap<>();
        data.put("eventType", eventType);
        data.put("subscriptionId", subscriptionId);
        data.put("userId", userId);
        data.put("timestamp", System.currentTimeMillis());
        data.put("payload", payload);

        firestore.collection("webhook_events")
                .add(data)
                .addListener(() -> log.debug("Webhook event saved: {}", eventType),
                        Runnable::run);
    }
}
