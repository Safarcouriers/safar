package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.DeliveryRequest;
import com.saffaricarrers.saffaricarrers.Entity.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryDto {

    // ── Delivery Request fields ──────────────────────────────────────────────
    private Long requestId;
    private String packageName;
    private String senderName;
    private String senderPhone;
    private String carrierName;
    private String carrierPhone;
    private String fromAddress;
    private String toAddress;
    private DeliveryRequest.RequestStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime deliveredAt;

    // ── Payment / financial fields ───────────────────────────────────────────
    private Double totalAmount;
    private Double platformCommission;
    private Double carrierAmount;
    private Payment.PaymentMethod paymentMethod;
    private Payment.PaymentStatus paymentStatus;
    private Payment.TransferStatus carrierTransferStatus;

    // ── Razorpay identifiers ─────────────────────────────────────────────────
    private String razorpayPaymentId;
    private String razorpayOrderId;
    private String razorpayPayoutId;

    // ── Timestamps ───────────────────────────────────────────────────────────
    private LocalDateTime paymentCompletedAt;
    private LocalDateTime carrierTransferInitiatedAt;
    private LocalDateTime carrierTransferCompletedAt;
    private String transferFailureReason;
    private Double platformFee;          // ✅ 2% charged to sender on top of trip charge
    private String carrierId;  // add to OrderSummaryDto

    // ── Commission flag (for COD orders where carrier owes platform) ─────────
    private Boolean commissionPaid;
    private Long paymentId; // internal DB id – handy for the "mark commission paid" action
}