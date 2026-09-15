package com.saffaricarrers.saffaricarrers.N1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

public class N1SubscriptionDto {
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreatePurchaseOrderRequest {
        @NotBlank private String userId;
        @NotBlank private String planKey;
        @NotBlank private String idempotencyKey;
        private String userEmail;
        private String userName;
        private String paymentMethod;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreatePurchaseOrderResponse {
        private boolean success; private String orderId; private String keyId;
        private long amount; private String currency; private String planKey;
        private String planName; private int validityDays; private String message;
        private String subscriptionDbId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ConfirmPurchaseRequest {
        @NotBlank private String userId;
        @NotBlank private String razorpayOrderId;
        @NotBlank private String razorpayPaymentId;
        @NotBlank private String razorpaySignature;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ConfirmPurchaseResponse {
        private boolean success; private String message; private String status;
        private String planName; private String planKey; private long amountPaid;
        private int validityDays; private String purchaseTime; private String expiryTime;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SubscriptionStatusResponse {
        private boolean hasActiveSubscription; private String status;
        private String planName; private String planKey; private String purchaseTime;
        private String expiryTime; private long amountPaid; private String paymentMethod;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ApiResponse {
        private boolean success; private String message; private Object data;
        public static ApiResponse ok(String message) { return builder().success(true).message(message).build(); }
        public static ApiResponse error(String message) { return builder().success(false).message(message).build(); }
    }
}
