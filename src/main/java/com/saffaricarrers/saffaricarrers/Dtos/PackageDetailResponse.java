package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.Package;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageDetailResponse {

    private Long packageId;
    private String productName;
    private String productDescription;
    private String productImage;
    private String weight;
    private String dimensions;
    private Double value;
    private LocalDate createdDate;
    private Package.PackageStatus status;
    private String deadlineTime;
    private String availableTime;
    private String pickupAddress;
    private String deliveryAddress;

    // ─── Carrier info (populated when a carrier is matched) ───────────────

    private String carrierName;
    private String carrierPhone;
    private String carrierProfileImage;
    private String carrierVehicleType;
    private Double carrierRating;
    private Double PackagePrice;
    private Boolean InsuranceEnabled = false;



    // ─── OTPs ─────────────────────────────────────────────────────────────

    private String pickupOtp;
    private String deliveryOtp;

    // ─── Pricing ──────────────────────────────────────────────────────────

    private Double totalAmount;
    private Double insuranceAmount;
    private Boolean insurance;

    // ✅ Payment status fields — used by frontend to hide/show Pay button

    /** "NOT_PAID" | "PENDING" | "COMPLETED" | "FAILED" */
    private String paymentStatus;

    /** true when paymentStatus == COMPLETED */
    private Boolean paymentCompleted;

    /** "ONLINE" | "COD" | null */
    private String paymentMethod;

    /** Razorpay order ID — needed to open Razorpay checkout on frontend */
    private String razorpayOrderId;

    /**
     * For ONLINE: always true (commission deducted from carrierAmount).
     * For COD: false until carrier pays commission back to platform.
     */
    private Boolean commissionPaid;

    // ─── Delivery request ID ──────────────────────────────────────────────

    private Long deliveryRequestId;
    private List<StatusTimelineItem> statusTimeline;

}