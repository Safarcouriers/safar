package com.saffaricarrers.saffaricarrers.N1.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * NEW one-time monetization table. Intentionally separate from Monitization.Subscription.
 * Do not merge this entity with the legacy AutoPay entity.
 */
@Entity
@Table(name = "n1_subscriptions", indexes = {
        @Index(name = "idx_n1_user_status", columnList = "userId,status"),
        @Index(name = "idx_n1_user_expiry", columnList = "userId,expiryTime"),
        @Index(name = "idx_n1_order", columnList = "razorpayOrderId", unique = true),
        @Index(name = "idx_n1_payment", columnList = "razorpayPaymentId", unique = true),
        @Index(name = "idx_n1_idempotency", columnList = "userId,idempotencyKey", unique = true)
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class N1Subscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String userId;
    @Column(length = 320) private String userEmail;
    @Column(length = 160) private String userName;

    @Column(nullable = false, length = 32) private String planKey;
    @Column(nullable = false, length = 80) private String planName;
    @Column(nullable = false) private Long planAmount;
    @Column(nullable = false) private boolean introPhase;

    @Column(length = 80) private String razorpayOrderId;
    @Column(length = 80) private String razorpayPaymentId;
    @Column private Long amountPaid;
    @Column(length = 32) private String paymentMethod;
    @Column private LocalDateTime purchaseTime;
    @Column private LocalDateTime expiryTime;

    @Column(nullable = false, length = 100)
    private String idempotencyKey;

    // Reserved legacy/future AutoPay metadata. This entity does not use it.
    @Column(length = 80) private String rzpSubscriptionId;
    @Column(length = 80) private String rzpPlanId;
    @Column private LocalDateTime subscriptionStartTime;
    @Column private LocalDateTime nextBillingTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubscriptionStatus status;

    @Column(nullable = false) private LocalDateTime createdAt;
    @Column private LocalDateTime updatedAt;
    @Column(name = "influencer_code", length = 50)
    private String influencerCode;
    @Column(name = "attribution_source", length = 50)
    private String attributionSource;
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum SubscriptionStatus { PENDING, ACTIVE, CANCELLED, FAILED, EXPIRED }
}
