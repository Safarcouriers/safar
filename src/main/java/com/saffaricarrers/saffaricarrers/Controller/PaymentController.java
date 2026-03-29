package com.saffaricarrers.saffaricarrers.Controller;

import com.saffaricarrers.saffaricarrers.Dtos.CommissionDetailsDto;
import com.saffaricarrers.saffaricarrers.Entity.Payment;
import com.saffaricarrers.saffaricarrers.Services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    // ==================== PAYMENT INITIATION ====================

    /**
     * Check if payment can be initiated (pickup verified, not yet paid)
     * GET /api/payments/can-initiate?deliveryRequestId=123
     */
    @GetMapping("/can-initiate")
    public ResponseEntity<?> canInitiatePayment(@RequestParam Long deliveryRequestId) {
        try {
            boolean canInitiate = paymentService.canInitiatePayment(deliveryRequestId);

            Map<String, Object> response = new HashMap<>();
            response.put("canInitiate", canInitiate);
            response.put("message", canInitiate ?
                    "Payment can be initiated" :
                    "Package must be picked up before payment");

            log.info("✅ Payment initiation check: deliveryRequestId=" + deliveryRequestId + ", canInitiate=" + canInitiate);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error checking payment initiation: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Check if insurance is available for this delivery
     * GET /api/payments/insurance-available?deliveryRequestId=123
     */
    @GetMapping("/insurance-available")
    public ResponseEntity<?> checkInsuranceAvailability(@RequestParam Long deliveryRequestId) {
        try {
            boolean insuranceAvailable = paymentService.isInsuranceAvailable(deliveryRequestId);

            Map<String, Object> response = new HashMap<>();
            response.put("insuranceAvailable", insuranceAvailable);
            response.put("message", insuranceAvailable ?
                    "Insurance can be added to this payment" :
                    "Insurance not available (only for online payments before transit)");

            log.info("✅ Insurance availability check: deliveryRequestId=" + deliveryRequestId + ", available=" + insuranceAvailable);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error checking insurance availability: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Create Razorpay payment order (ONLY AFTER PICKUP OTP)
     * POST /api/payments/create-order?deliveryRequestId=123
     */
//    @PostMapping("/create-order")
//    public ResponseEntity<?> createPaymentOrder(@RequestParam Long deliveryRequestId) {
//        try {
//            Payment payment = paymentService.createPaymentOrder(deliveryRequestId);
//
//            int amountInPaise = (int)(payment.getTotalAmount() * 100);
//
//            Map<String, Object> response = new HashMap<>();
//            response.put("success", true);
//            response.put("orderId", payment.getRazorpayOrderId());
//            response.put("amount", amountInPaise);  // ✅ NOW IN PAISE
//            response.put("amountInRupees", payment.getTotalAmount());
//            response.put("currency", "INR");
//            response.put("paymentId", payment.getPaymentId());
//            response.put("deliveryCharge", payment.getDeliveryCharge());
//            response.put("insuranceAmount", payment.getInsuranceAmount());
//            response.put("insuranceIncluded", payment.getInsuranceAmount() > 0);
//            response.put("platformCommission", payment.getPlatformCommission());
//            response.put("carrierAmount", payment.getCarrierAmount());
//
//
//            log.info("✅ Payment order created: orderId=" + payment.getRazorpayOrderId() +
//                    ", amountInRupees=₹" + payment.getTotalAmount() +
//                    ", amountInPaise=" + amountInPaise);            return ResponseEntity.ok(response);
//        } catch (IllegalStateException e) {
//            log.error("❌ Invalid payment state: " + e.getMessage());
//            Map<String, Object> error = new HashMap<>();
//            error.put("error", "INVALID_STATE");
//            error.put("message", e.getMessage());
//            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
//        } catch (Exception e) {
//            log.error("❌ Order creation failed: " + e.getMessage());
//            Map<String, Object> error = new HashMap<>();
//            error.put("error", "ORDER_CREATION_FAILED");
//            error.put("message", e.getMessage());
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
//        }
//    }

    /**
     * Verify and confirm Razorpay payment after successful payment
     * POST /api/payments/verify
     * Body: { "razorpay_order_id": "...", "razorpay_payment_id": "...", "razorpay_signature": "..." }
     */
//    @PostMapping("/verify")
//    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, String> payload) {
//        try {
//            String orderId = payload.get("razorpay_order_id");
//            String paymentId = payload.get("razorpay_payment_id");
//            String signature = payload.get("razorpay_signature");
//
//            if (orderId == null || paymentId == null || signature == null) {
//                log.warn("❌ Missing payment verification parameters");
//                Map<String, Object> error = new HashMap<>();
//                error.put("error", "MISSING_PARAMETERS");
//                error.put("message", "Order ID, Payment ID, and Signature are required");
//                return ResponseEntity.badRequest().body(error);
//            }
//
//            paymentService.confirmOnlinePayment(orderId, paymentId, signature);
//
//            Map<String, Object> response = new HashMap<>();
//            response.put("success", true);
//            response.put("message", "Payment verified and confirmed successfully");
//            response.put("orderId", orderId);
//            response.put("paymentId", paymentId);
//
//            log.info("✅ Payment verified: orderId=" + orderId + ", paymentId=" + paymentId);
//            return ResponseEntity.ok(response);
//        } catch (Exception e) {
//            log.error("❌ Payment verification failed: " + e.getMessage());
//            Map<String, Object> error = new HashMap<>();
//            error.put("error", "VERIFICATION_FAILED");
//            error.put("message", e.getMessage());
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
//        }
//    }

    // ==================== COMMISSION MANAGEMENT ENDPOINTS ====================

    /**
     * Get carrier's commission summary (quick overview for dashboard)
     * GET /api/payments/commission/summary?userId=abc123
     * ✅ FIXED: Now accepts String userId properly
     */
    @GetMapping("/commission/summary")
    public ResponseEntity<?> getCommissionSummary(@RequestParam String userId) {
        try {
            if (userId == null || userId.trim().isEmpty()) {
                log.warn("❌ UserId parameter is empty");
                Map<String, Object> error = new HashMap<>();
                error.put("error", "MISSING_PARAMETER");
                error.put("message", "userId is required");
                return ResponseEntity.badRequest().body(error);
            }

            Map<String, Object> summary = paymentService.getCarrierCommissionSummary(userId);
            log.info("✅ Commission summary fetched for carrier: " + userId);
            return ResponseEntity.ok(summary);
        } catch (RuntimeException e) {
            log.error("❌ Carrier not found: " + userId);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "CARRIER_NOT_FOUND");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            log.error("❌ Error fetching commission summary: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get carrier's pending commission details (trip-wise breakdown)
     * GET /api/payments/commission/details?userId=abc123
     * ✅ FIXED: Now properly handles String userId conversion
     */
    @GetMapping("/commission/details")
    public ResponseEntity<?> getCommissionDetails(@RequestParam String userId) {
        try {
            if (userId == null || userId.trim().isEmpty()) {
                log.warn("❌ UserId parameter is empty");
                Map<String, Object> error = new HashMap<>();
                error.put("error", "MISSING_PARAMETER");
                error.put("message", "userId is required");
                return ResponseEntity.badRequest().body(error);
            }

            CommissionDetailsDto details = paymentService.getCarrierCommissionDetails(userId);
            log.info("✅ Commission details fetched for carrier: " + userId + ", pending trips: " + details.getPendingTripsCount());
            return ResponseEntity.ok(details);
        } catch (RuntimeException e) {
            log.error("❌ Carrier not found: " + userId);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "CARRIER_NOT_FOUND");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            log.error("❌ Error fetching commission details: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Carrier pays pending commission
     * POST /api/payments/commission/pay
     * Body: { "userId": "abc123", "razorpayPaymentId": "pay_xyz" }
     * ✅ FIXED: Now properly validates and handles String userId
     */
//    @PostMapping("/commission/pay")
//    public ResponseEntity<?> payCommission(@RequestBody Map<String, String> payload) {
//        try {
//            String userId = payload.get("userId");
//            String razorpayPaymentId = payload.get("razorpayPaymentId");
//
//            // ✅ Validate inputs
//            if (userId == null || userId.trim().isEmpty()) {
//                log.warn("❌ Missing userId in commission payment request");
//                Map<String, Object> error = new HashMap<>();
//                error.put("error", "MISSING_PARAMETER");
//                error.put("message", "userId is required");
//                return ResponseEntity.badRequest().body(error);
//            }
//
//            if (razorpayPaymentId == null || razorpayPaymentId.trim().isEmpty()) {
//                log.warn("❌ Missing razorpayPaymentId in commission payment request");
//                Map<String, Object> error = new HashMap<>();
//                error.put("error", "MISSING_PARAMETER");
//                error.put("message", "razorpayPaymentId is required");
//                return ResponseEntity.badRequest().body(error);
//            }
//
//            paymentService.payCarrierCommission(userId, razorpayPaymentId);
//
//            Map<String, Object> response = new HashMap<>();
//            response.put("success", true);
//            response.put("message", "Commission paid successfully");
//            response.put("canStartTrips", true);
//            response.put("userId", userId);
//
//            log.info("✅ Commission paid successfully by carrier: " + userId);
//            return ResponseEntity.ok(response);
//        } catch (RuntimeException e) {
//            log.error("❌ Commission payment failed: " + e.getMessage());
//            Map<String, Object> error = new HashMap<>();
//            error.put("error", "COMMISSION_PAYMENT_FAILED");
//            error.put("message", e.getMessage());
//            return ResponseEntity.badRequest().body(error);
//        } catch (Exception e) {
//            log.error("❌ Internal error during commission payment: " + e.getMessage());
//            Map<String, Object> error = new HashMap<>();
//            error.put("error", "INTERNAL_ERROR");
//            error.put("message", e.getMessage());
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
//        }
//    }

    /**
     * Check if carrier can start new trip (no pending commission)
     * GET /api/payments/can-start-trip?userId=abc123
     * ✅ FIXED: Now properly accepts String userId
     */
    @GetMapping("/can-start-trip")
    public ResponseEntity<?> canStartTrip(@RequestParam String userId) {
        try {
            if (userId == null || userId.trim().isEmpty()) {
                log.warn("❌ UserId parameter is empty");
                Map<String, Object> error = new HashMap<>();
                error.put("error", "MISSING_PARAMETER");
                error.put("message", "userId is required");
                return ResponseEntity.badRequest().body(error);
            }

            boolean canStart = paymentService.canCarrierStartTrip(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("canStartTrip", canStart);
            response.put("message", canStart ?
                    "✅ You can start new trips" :
                    "❌ Please clear pending commission to start trips");
            response.put("userId", userId);

            log.info("✅ Trip eligibility checked for carrier: " + userId + ", canStart=" + canStart);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("❌ Carrier not found: " + userId);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "CARRIER_NOT_FOUND");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            log.error("❌ Error checking trip eligibility: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // ==================== INTERNAL/ADMIN ENDPOINTS ====================

    /**
     * Handle offline payment when delivery OTP is entered (called internally)
     * This is typically called by DeliveryRequestService.verifyDeliveryOtp()
     * POST /api/payments/offline/record?deliveryRequestId=123
     */
    @PostMapping("/offline/record")
    public ResponseEntity<?> recordOfflinePayment(@RequestParam Long deliveryRequestId) {
        try {
            if (deliveryRequestId == null || deliveryRequestId <= 0) {
                log.warn("❌ Invalid deliveryRequestId");
                Map<String, Object> error = new HashMap<>();
                error.put("error", "INVALID_PARAMETER");
                error.put("message", "Valid deliveryRequestId is required");
                return ResponseEntity.badRequest().body(error);
            }

            paymentService.handleOfflinePaymentOnDelivery(deliveryRequestId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Offline payment recorded successfully");
            response.put("paymentMethod", "COD");
            response.put("deliveryRequestId", deliveryRequestId);

            log.info("✅ Offline payment recorded: deliveryRequestId=" + deliveryRequestId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("❌ Delivery request not found: deliveryRequestId=" + deliveryRequestId);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "NOT_FOUND");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            log.error("❌ Offline payment recording failed: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "OFFLINE_PAYMENT_FAILED");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestParam Long deliveryRequestId) {
        try {
            log.info("🧪 Creating test payment order for deliveryRequestId: {}", deliveryRequestId);

            Payment payment = paymentService.createPaymentOrder(deliveryRequestId);

            // ✅ CRITICAL: amount must be in PAISE for Razorpay
            int amountInPaise = (int)(payment.getTotalAmount() * 100);
            double amountInRupees = payment.getTotalAmount();

            log.info("🧪 Order created:");
            log.info("   orderId: {}", payment.getRazorpayOrderId());
            log.info("   amountInRupees: ₹{}", amountInRupees);
            log.info("   amountInPaise: {} paise", amountInPaise);

            // ✅ Build response with PAISE
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("orderId", payment.getRazorpayOrderId());
            response.put("amount", amountInPaise);  // ✅ IN PAISE (100 = ₹1)
            response.put("amountInRupees", amountInRupees);  // ✅ IN RUPEES (1.0)
            response.put("currency", "INR");
            response.put("paymentId", payment.getPaymentId());
            response.put("deliveryCharge", payment.getDeliveryCharge());
            response.put("insuranceAmount", payment.getInsuranceAmount());
            response.put("insuranceIncluded", payment.getInsuranceAmount() > 0);
            response.put("platformCommission", payment.getPlatformCommission());
            response.put("carrierAmount", payment.getCarrierAmount());

            // ✅ Verify amount is correct
            if (amountInPaise == 100) {
                log.info("✅ Correct: 100 paise = ₹1.00");
            } else {
                log.warn("⚠️ WARNING: Expected 100 paise, got: {}", amountInPaise);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error creating payment order: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * ✅ Verify payment
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, String> payload) {
        try {
            String orderId = payload.get("razorpay_order_id");
            String paymentId = payload.get("razorpay_payment_id");
            String signature = payload.get("razorpay_signature");

            log.info("🔍 Verifying payment:");
            log.info("   orderId: {}", orderId);
            log.info("   paymentId: {}", paymentId);

            paymentService.confirmOnlinePayment(orderId, paymentId, signature);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Payment verified successfully"
            ));

        } catch (Exception e) {
            log.error("❌ Payment verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

// ==================== ADD TO PaymentController.java ====================

    /**
     * Create Razorpay order for commission payment
     * POST /api/payments/commission/create-order
     * Body: { "userId": "abc123" }
     */
    @PostMapping("/commission/create-order")
    public ResponseEntity<?> createCommissionOrder(@RequestBody Map<String, String> payload) {
        try {
            String userId = payload.get("userId");

            if (userId == null || userId.trim().isEmpty()) {
                log.warn("❌ Missing userId in commission order request");
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "MISSING_PARAMETER",
                        "message", "userId is required"
                ));
            }

            // Create commission payment order
            Map<String, Object> orderDetails = paymentService.createCommissionPaymentOrder(userId);

            log.info("✅ Commission order created for carrier: " + userId);
            return ResponseEntity.ok(orderDetails);

        } catch (RuntimeException e) {
            log.error("❌ Commission order creation failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "ORDER_CREATION_FAILED",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Internal error creating commission order: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "INTERNAL_ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Verify and confirm commission payment
     * POST /api/payments/commission/verify
     * Body: {
     *   "userId": "abc123",
     *   "razorpay_order_id": "...",
     *   "razorpay_payment_id": "...",
     *   "razorpay_signature": "..."
     * }
     */
    @PostMapping("/commission/verify")
    public ResponseEntity<?> verifyCommissionPayment(@RequestBody Map<String, String> payload) {
        try {
            String userId = payload.get("userId");
            String orderId = payload.get("razorpay_order_id");
            String paymentId = payload.get("razorpay_payment_id");
            String signature = payload.get("razorpay_signature");

            // Validate inputs
            if (userId == null || orderId == null || paymentId == null || signature == null) {
                log.warn("❌ Missing parameters in commission verification");
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "MISSING_PARAMETERS",
                        "message", "All payment details are required"
                ));
            }

            log.info("🔍 Verifying commission payment for carrier: " + userId);

            // Verify and process commission payment
            paymentService.verifyAndConfirmCommissionPayment(userId, orderId, paymentId, signature);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Commission payment verified successfully",
                    "canStartTrips", true,
                    "userId", userId
            ));

        } catch (RuntimeException e) {
            log.error("❌ Commission verification failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "VERIFICATION_FAILED",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Internal error during verification: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "INTERNAL_ERROR",
                    "message", e.getMessage()
            ));
        }
    }
}
