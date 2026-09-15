package com.saffaricarrers.saffaricarrers.Monitization.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "subscriptions",
        indexes = {
                @Index(name = "idx_user_status",  columnList = "userId, status"),
                @Index(name = "idx_rzp_sub_id",   columnList = "rzpSubscriptionId", unique = true),
                @Index(name = "idx_intro_order",  columnList = "introOrderId")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── User ──────────────────────────────────────────────────────────────────
    @Column(nullable = false)
    private String userId;       // Firebase UID

    @Column
    private String userEmail;

    @Column
    private String userName;

    // ── Plan ──────────────────────────────────────────────────────────────────
    @Column(nullable = false)
    private String planKey;      // weekly / monthly / quarterly / biannual / annual

    @Column(nullable = false)
    private String planName;     // "Weekly", "Monthly", etc.

    @Column(nullable = false)
    private Long planAmount;     // full price in paise (e.g. 9900 for ₹99)

    // ── Intro Phase ───────────────────────────────────────────────────────────
    @Column(nullable = false)
    private boolean introPhase;  // true = currently in free period

    @Column
    private String introOrderId; // Razorpay subscription ID (stored here for lookup)

    @Column
    private String introPaymentId;

    @Column
    private LocalDateTime introPaymentTime;

    @Column
    private LocalDateTime introEndTime;  // first charge date

    @Column
    private String introPlanId;

    // ── Razorpay Subscription ─────────────────────────────────────────────────
    @Column(unique = true)       // FIX #3: unique constraint prevents duplicate subscriptions
    private String rzpSubscriptionId;

    @Column
    private String rzpPlanId;

    @Column
    private LocalDateTime subscriptionStartTime;

    @Column
    private LocalDateTime nextBillingTime;

    // ── Status ────────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    // ── Scheduler ─────────────────────────────────────────────────────────────
    @Column
    private boolean realSubscriptionCreated;

    @Column
    private int schedulerRetryCount;

    // ── Timestamps ────────────────────────────────────────────────────────────
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum SubscriptionStatus {
        INTRO_PENDING,   // Razorpay subscription created, mandate not yet registered
        ACTIVE,          // Mandate registered — user has access
        CANCELLED,       // User cancelled
        FAILED,          // Payment failed or subscription halted
        EXPIRED          // Stale INTRO_PENDING after 24h, or total_count reached
    }
}