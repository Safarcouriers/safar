package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.DeliveryRequest;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivePackageSummary {

    private Long requestId;
    private Long packageId;
    private String packageName;
    private String productImage;
    private DeliveryRequest.RequestStatus status;

    // ─── Earnings ─────────────────────────────────────────────────────────

    private Double earningAmount;
    private Double platformCommission;

    // ─── OTPs ─────────────────────────────────────────────────────────────

    private String pickupOtp;
    private String deliveryOtp;

    // ✅ Photo URLs — always populated so frontend doesn't lose them on state rebuild

    private String pickupPhoto;
    private String deliveryPhoto;

    // ─── Sender info ──────────────────────────────────────────────────────

    private String senderName;
    private String senderPhone;
    private String senderProfileImage;

    // ─── Addresses ────────────────────────────────────────────────────────

    private String pickupAddress;
    private String deliveryAddress;

    // ✅ Per-package payment status — frontend shows "Online Paid" / "Pending" badge

    /** "NOT_PAID" | "PENDING" | "COMPLETED" */
    private String paymentStatus;

    /** true when payment is COMPLETED */
    private Boolean paymentCompleted;

    /** "ONLINE" | "COD" | null */
    private String paymentMethod;

    /**
     * Payout transfer status for ONLINE payments:
     * PENDING = sender paid, payout queued for delivery
     * INITIATED = IMPS payout sent, waiting for bank confirmation
     * COMPLETED = money confirmed in carrier's bank
     * FAILED = payout failed — support will retry
     * NA = COD (not applicable)
     */
    private String carrierTransferStatus;
}