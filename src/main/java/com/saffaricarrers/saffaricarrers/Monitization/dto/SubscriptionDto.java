package com.saffaricarrers.saffaricarrers.Monitization.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;

public class SubscriptionDto {

    // ── Request: Android app calls this to create intro order ─────────────────
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateIntroOrderRequest {

        @NotBlank(message = "userId is required")
        private String userId;

        @NotBlank(message = "planKey is required")
        // weekly / monthly / quarterly / biannual / annual
        private String planKey;

        private String userEmail;
        private String userName;
        private String paymentMethod; // phonepe / gpay / upi
    }

    // ── Response: returned to Android app ─────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateIntroOrderResponse {
        private boolean success;
        private String orderId;         // Razorpay order ID → pass to Razorpay SDK
        private String keyId;           // Razorpay key ID → pass to Razorpay SDK
        private long amount;            // 900 (₹9 in paise)
        private String currency;        // INR
        private String planKey;
        private String planName;
        private String message;
        private String subscriptionDbId; // our internal ID
    }

    // ── Request: Android confirms ₹9 payment success ──────────────────────────
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmIntroPaymentRequest {

        @NotBlank
        private String razorpayOrderId;

        @NotBlank
        private String razorpayPaymentId;

        @NotBlank
        private String razorpaySignature;

        @NotBlank
        private String userId;
    }

    // ── Response: confirm payment ─────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmIntroPaymentResponse {
        private boolean success;
        private String message;
        private String status;          // INTRO_PAID
        private String introEndTime;    // ISO datetime when intro ends
        private String planName;
        private long introDays;
    }

    // ── Generic API response ──────────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiResponse {
        private boolean success;
        private String message;
        private Object data;

        public static ApiResponse ok(String message) {
            return ApiResponse.builder().success(true).message(message).build();
        }

        public static ApiResponse ok(String message, Object data) {
            return ApiResponse.builder().success(true).message(message).data(data).build();
        }

        public static ApiResponse error(String message) {
            return ApiResponse.builder().success(false).message(message).build();
        }
    }

    // ── Subscription status (returned to Android app) ─────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionStatusResponse {
        private boolean hasActiveSubscription;
        private String status;          // INTRO_PAID / ACTIVE / CANCELLED / etc.
        private String planName;
        private String planKey;
        private boolean inIntro;
        private String introEndTime;
        private String rzpSubscriptionId;
        private String nextBillingTime;
    }
}
