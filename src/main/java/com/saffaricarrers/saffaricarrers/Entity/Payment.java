package com.saffaricarrers.saffaricarrers.Entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    // ─── Relations ────────────────────────────────────────────────────────

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_request_id")
    private DeliveryRequest deliveryRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id")
    private Package packageEntity;

    // ─── Amounts ──────────────────────────────────────────────────────────

    @Column(nullable = false)
    private Double totalAmount = 0.0;

    @Column(nullable = false)
    private Double deliveryCharge = 0.0;

    @Column(nullable = false)
    private Double insuranceAmount = 0.0;

    @Column(nullable = false)
    private Double platformCommission = 0.0;   // 15% of deliveryCharge

    @Column(nullable = false)
    private Double carrierAmount = 0.0;        // 85% of deliveryCharge

    // ─── Payment Method & Status ──────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod = PaymentMethod.ONLINE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column
    private LocalDateTime paymentCompletedAt;

    // ─── Razorpay — Sender Payment ────────────────────────────────────────

    @Column
    private String razorpayOrderId;

    @Column
    private String razorpayPaymentId;

    @Column
    private String razorpaySignature;

    @Column
    private String receipt;

    @Column(length = 1000)
    private String gatewayResponse;

    // ─── Razorpay — Carrier Payout (RazorpayX) ───────────────────────────

    /**
     * Payout ID from RazorpayX — set when payout is triggered at delivery.
     * NOT set at payment time — payout is triggered only after delivery OTP.
     */
    @Column
    private String razorpayPayoutId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus carrierTransferStatus = TransferStatus.PENDING;

    @Column
    private LocalDateTime carrierTransferInitiatedAt;

    @Column
    private LocalDateTime carrierTransferCompletedAt;

    @Column(length = 500)
    private String transferFailureReason;

    // ─── Commission (COD only) ────────────────────────────────────────────

    /**
     * ONLINE: always true (platform keeps 15% before payout — already deducted).
     * COD: false until carrier pays their 15% back to platform separately.
     */
    @Column(nullable = false)
    private Boolean commissionPaid = false;

    @Column
    private LocalDateTime commissionPaidAt;

    @Column
    private String commissionPaymentId; // Razorpay payment ID of carrier's commission payment

    // ─── COD-specific ─────────────────────────────────────────────────────

    @Column(length = 500)
    private String offlinePaymentNote;

    @Column
    private String completedBy;

    // ─── Audit ────────────────────────────────────────────────────────────

    @Column(nullable = false, updatable = false)
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

    // ─── Enums ────────────────────────────────────────────────────────────

    public enum PaymentMethod {
        ONLINE, COD
    }

    public enum PaymentStatus {
        PENDING, COMPLETED, FAILED, REFUNDED
    }

    public enum TransferStatus {
        PENDING,    // Waiting for delivery to be confirmed (payout not yet triggered)
        INITIATED,  // Payout created in RazorpayX — waiting for webhook confirmation
        COMPLETED,  // Webhook confirmed — money in carrier's bank
        FAILED,     // Failed — needs manual intervention
        NA          // Not applicable (COD — carrier got cash directly)
    }
}