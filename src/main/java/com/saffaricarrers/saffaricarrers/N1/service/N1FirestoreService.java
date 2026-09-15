package com.saffaricarrers.saffaricarrers.N1.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
@Service
 @RequiredArgsConstructor @Slf4j
public class N1FirestoreService {
    private final Firestore firestore;

    public void updateOneTimePremium(String userId, String planName, String planKey, LocalDateTime purchaseTime,
                                     LocalDateTime expiryTime, long amountPaid, String paymentId) {
        Map<String,Object> data = new HashMap<>();
        data.put("isPremium", true); data.put("premiumSource", "razorpay_one_time");
        data.put("planName", planName); data.put("planId", planKey); data.put("status", "active");
        data.put("inTrial", false); data.put("purchaseTime", purchaseTime.toInstant(ZoneOffset.UTC).toEpochMilli());
        data.put("premiumExpiryTimestamp", expiryTime.toInstant(ZoneOffset.UTC).toEpochMilli());
        data.put("amountPaid", amountPaid); data.put("razorpayPaymentId", paymentId);
        data.put("lastUpdated", System.currentTimeMillis());
        try {
            firestore.collection("users").document(userId).set(data, SetOptions.merge()).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /** Never disable an AutoPay premium record belonging to the legacy Monitization flow. */
    public void expireOneTimePremium(String userId, LocalDateTime oneTimeExpiry) {
        try {
            DocumentSnapshot doc = firestore.collection("users").document(userId).get().get();
            if (!doc.exists()) return;
            String source = doc.getString("premiumSource");
            Long expiry = doc.getLong("premiumExpiryTimestamp");
            long expected = oneTimeExpiry.toInstant(ZoneOffset.UTC).toEpochMilli();
            if (!"razorpay_one_time".equals(source) || (expiry != null && expiry != expected)) return;
            Map<String,Object> data = new HashMap<>();
            data.put("isPremium", false); data.put("status", "expired"); data.put("inTrial", false);
            data.put("lastUpdated", System.currentTimeMillis());
            firestore.collection("users").document(userId).set(data, SetOptions.merge()).get();
        } catch (Exception e) { throw new IllegalStateException("Firestore expiry update failed", e); }
    }
}
