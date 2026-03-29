package com.saffaricarrers.saffaricarrers.Services;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.razorpay.*;
import com.saffaricarrers.saffaricarrers.Dtos.CommissionDetailsDto;
import com.saffaricarrers.saffaricarrers.Entity.*;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import com.saffaricarrers.saffaricarrers.Entity.Payment;
import com.saffaricarrers.saffaricarrers.Repository.*;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final RazorpayClient razorpayClient;
    private final RazorpayPayoutService razorpayPayoutService;
    private final PaymentRepository paymentRepository;
    private final DeliveryRequestRepository deliveryRequestRepository;
    private final UserRepository userRepository;
    private final PackageRepository packageRepository;
    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Value("${admin.email}")
    private String adminEmail;

    // ✅ Single commission rate used everywhere — no more inconsistency
    public static final double PLATFORM_COMMISSION_RATE = 0.15;

    // =========================================================================
    // 1. CREATE PAYMENT ORDER — sender pays after pickup
    // =========================================================================

    /**
     * Creates a Razorpay order for the sender.
     * Can only be called after carrier has picked up the package.
     * Money stays in YOUR Razorpay account until delivery is confirmed.
     */
    @Transactional
    public Payment createPaymentOrder(Long deliveryRequestId) throws Exception {
        DeliveryRequest request = deliveryRequestRepository.findById(deliveryRequestId)
                .orElseThrow(() -> new RuntimeException("Delivery request not found: " + deliveryRequestId));

        // ✅ Idempotency — return existing pending order
        if (request.getPayment() != null) {
            if (request.getPayment().getPaymentStatus() == Payment.PaymentStatus.COMPLETED) {
                throw new IllegalStateException("Payment already completed for this delivery.");
            }
            log.info("♻️ Returning existing pending order for request: {}", deliveryRequestId);
            return request.getPayment();
        }

        // ✅ Only allow payment after pickup
        if (request.getStatus() != DeliveryRequest.RequestStatus.PICKED_UP
                && request.getStatus() != DeliveryRequest.RequestStatus.IN_TRANSIT) {
            throw new IllegalStateException("Payment can only be made after the carrier picks up the package.");
        }

        Package pkg = request.getPackageEntity();
        User sender = request.getSender();
        User carrier = request.getCarrier();

        double deliveryCharge = request.getTotalAmount();
        double insuranceAmount = 0.0;
        double totalAmount = deliveryCharge + insuranceAmount;

        // ✅ Calculate split upfront — platform keeps 15%, carrier gets 85%
        double platformCommission = deliveryCharge * PLATFORM_COMMISSION_RATE;
        double carrierAmount = deliveryCharge - platformCommission;

        // TEST MODE: ₹1 (100 paise)
        // PRODUCTION: int amountInPaise = (int)(totalAmount * 100);
        int amountInPaise = 100;

        log.info("🧾 Creating order | Request: {} | Total: ₹{} | Carrier gets: ₹{} | Platform keeps: ₹{}",
                deliveryRequestId, totalAmount, carrierAmount, platformCommission);

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountInPaise);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "rcpt_" + deliveryRequestId + "_" + System.currentTimeMillis() % 1000000);
        orderRequest.put("payment_capture", 1);

        JSONObject notes = new JSONObject();
        notes.put("delivery_request_id", deliveryRequestId);
        notes.put("package_id", pkg.getPackageId());
        notes.put("sender_id", sender.getUserId());
        notes.put("carrier_id", carrier.getUserId());
        orderRequest.put("notes", notes);

        Order razorpayOrder = razorpayClient.orders.create(orderRequest);
        String razorpayOrderId = razorpayOrder.get("id");

        Payment payment = new Payment();
        payment.setPackageEntity(pkg);
        payment.setDeliveryRequest(request);
        payment.setTotalAmount(totalAmount);
        payment.setDeliveryCharge(deliveryCharge);
        payment.setInsuranceAmount(insuranceAmount);
        payment.setPlatformCommission(platformCommission);
        payment.setCarrierAmount(carrierAmount);
        payment.setPaymentMethod(Payment.PaymentMethod.ONLINE);
        payment.setPaymentStatus(Payment.PaymentStatus.PENDING);
        // ✅ PENDING — payout only triggered after delivery OTP, not at payment time
        payment.setCarrierTransferStatus(Payment.TransferStatus.PENDING);
        payment.setRazorpayOrderId(razorpayOrderId);
        payment.setReceipt(razorpayOrder.get("receipt"));
        // ✅ Online: commission pre-deducted from carrierAmount — no separate COD commission
        payment.setCommissionPaid(true);

        Payment saved = paymentRepository.save(payment);
        log.info("✅ Payment order created: {} | Request: {}", razorpayOrderId, deliveryRequestId);
        return saved;
    }

    // =========================================================================
    // 2. CONFIRM ONLINE PAYMENT — called after Razorpay success callback
    // =========================================================================

    /**
     * Verifies Razorpay signature and marks payment COMPLETED.
     * Does NOT trigger payout — payout happens at delivery OTP verification.
     * Money sits safely in your account until delivery is confirmed.
     */
    @Transactional
    public void confirmOnlinePayment(String razorpayOrderId, String razorpayPaymentId,
                                     String razorpaySignature) throws Exception {

        // ✅ Always verify signature — prevents tampered/replayed payment confirmations
        String expected = calculateRazorpaySignature(razorpayOrderId, razorpayPaymentId);
        if (!expected.equals(razorpaySignature)) {
            log.error("❌ Signature mismatch | Order: {}", razorpayOrderId);
            throw new RuntimeException("Invalid payment signature — possible tampered request.");
        }

        Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for order: " + razorpayOrderId));

        // ✅ Idempotency — skip already processed payments
        if (payment.getPaymentStatus() == Payment.PaymentStatus.COMPLETED) {
            log.warn("⚠️ Payment already confirmed: {}", razorpayPaymentId);
            return;
        }

        DeliveryRequest request = payment.getDeliveryRequest();
        Package pkg = payment.getPackageEntity();

        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setRazorpaySignature(razorpaySignature);
        payment.setPaymentStatus(Payment.PaymentStatus.COMPLETED);
        payment.setPaymentCompletedAt(LocalDateTime.now());
        payment.setGatewayResponse("Verified via HMAC-SHA256 signature check");
        payment.setCommissionPaid(true);
        // ✅ TransferStatus stays PENDING — payout triggers at delivery, not here
        payment.setCarrierTransferStatus(Payment.TransferStatus.PENDING);

        if (payment.getInsuranceAmount() > 0) {
            pkg.setInsurance(true);
            packageRepository.save(pkg);
            log.info("✅ Insurance activated for package: {}", pkg.getPackageId());
        }

        paymentRepository.save(payment);

        log.info("✅ Online payment confirmed: {} | Amount: ₹{} | Payout queued for delivery.",
                razorpayPaymentId, payment.getTotalAmount());

        // Notify sender — payment successful
        createNotification(request.getSender(),
                "Payment Successful ✅",
                "₹" + String.format("%.2f", payment.getTotalAmount()) + " paid. Carrier earns on delivery.",
                com.saffaricarrers.saffaricarrers.Entity.Notification.NotificationType.PAYMENT_RECEIVED,
                request.getRequestId());

        // Notify carrier — tell them they'll get paid on delivery
        createNotification(request.getCarrier(),
                "Sender Has Paid 💰",
                "₹" + String.format("%.2f", payment.getCarrierAmount()) +
                        " will be transferred to your bank after you complete the delivery.",
                com.saffaricarrers.saffaricarrers.Entity.Notification.NotificationType.PAYMENT_RECEIVED,
                request.getRequestId());

        sendFcm(request.getSender().getFcmToken(),
                "✅ Payment Successful",
                "₹" + String.format("%.2f", payment.getTotalAmount()) + " paid successfully!");

        sendFcm(request.getCarrier().getFcmToken(),
                "Sender Paid 💰",
                "Complete the delivery to receive ₹" +
                        String.format("%.2f", payment.getCarrierAmount()) + " in your bank.");

        sendAdminEmail("Online Payment Confirmed — ₹" + String.format("%.2f", payment.getTotalAmount()),
                buildPaymentEmailHtml(payment, "Payout will trigger when delivery OTP is verified."));
    }

    // =========================================================================
    // 3. TRIGGER CARRIER PAYOUT ON DELIVERY
    // =========================================================================

    /**
     * Called from DeliveryRequestService.verifyDeliveryOtp() AFTER successful OTP verification.
     *
     * This is the correct trigger point because:
     * - Delivery is PROVEN (OTP verified + delivery photo uploaded)
     * - Carrier EARNED the money — no refund risk
     * - Accounting is clean: delivery event = payout event
     * - Two separate timestamps: paymentCompletedAt vs carrierTransferInitiatedAt
     */
    @Transactional
    public void triggerCarrierPayoutOnDelivery(Long deliveryRequestId) {
        DeliveryRequest request = deliveryRequestRepository.findById(deliveryRequestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + deliveryRequestId));

        Payment payment = request.getPayment();

        // No online payment = COD, no payout needed from us
        if (payment == null || payment.getPaymentMethod() == Payment.PaymentMethod.COD) {
            log.info("ℹ️ No online payment for request: {} — COD or unpaid.", deliveryRequestId);
            return;
        }

        if (payment.getPaymentStatus() != Payment.PaymentStatus.COMPLETED) {
            log.warn("⚠️ Online payment not completed for request: {} — cannot payout.", deliveryRequestId);
            return;
        }

        if (payment.getCarrierTransferStatus() == Payment.TransferStatus.COMPLETED
                || payment.getCarrierTransferStatus() == Payment.TransferStatus.INITIATED) {
            log.warn("⚠️ Payout already {} for payment: {}",
                    payment.getCarrierTransferStatus(), payment.getPaymentId());
            return;
        }

        log.info("🚀 Triggering payout at delivery | Request: {} | Carrier: {} | Amount: ₹{}",
                deliveryRequestId, request.getCarrier().getUserId(), payment.getCarrierAmount());

        razorpayPayoutService.initiateCarrierPayout(payment);
    }

    // =========================================================================
    // 4. COD PAYMENT — recorded at delivery OTP verification
    // =========================================================================

    /**
     * Records a COD payment when delivery OTP is verified.
     * Carrier physically collected cash — they owe platform 15%.
     * Called from DeliveryRequestService when no online payment exists.
     */
    @Transactional
    public void handleOfflinePaymentOnDelivery(Long deliveryRequestId) {
        DeliveryRequest request = deliveryRequestRepository.findById(deliveryRequestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + deliveryRequestId));

        // Skip if online payment already COMPLETED
        if (request.getPayment() != null &&
                request.getPayment().getPaymentStatus() == Payment.PaymentStatus.COMPLETED) {
            log.info("✅ Online payment already completed for request: {}. Skipping COD recording.", deliveryRequestId);
            return;
        }

        Package pkg = request.getPackageEntity();
        User carrier = request.getCarrier();
        double totalAmount = request.getTotalAmount();
        double platformCommission = totalAmount * PLATFORM_COMMISSION_RATE;
        double carrierAmount = totalAmount - platformCommission;

        // ✅ UPSERT: reuse existing row if present (stuck PENDING online payment)
        // Never INSERT when a payment row already exists — avoids duplicate key error
        Payment payment = request.getPayment() != null ? request.getPayment() : new Payment();

        payment.setPackageEntity(pkg);
        payment.setDeliveryRequest(request);
        payment.setTotalAmount(totalAmount);
        payment.setDeliveryCharge(totalAmount);
        payment.setInsuranceAmount(0.0);
        payment.setPlatformCommission(platformCommission);
        payment.setCarrierAmount(carrierAmount);
        payment.setPaymentMethod(Payment.PaymentMethod.COD);
        payment.setPaymentStatus(Payment.PaymentStatus.COMPLETED);
        payment.setPaymentCompletedAt(LocalDateTime.now());
        payment.setOfflinePaymentNote("Cash collected on delivery by carrier" +
                (request.getPayment() != null ? " (prior online payment was not completed)" : ""));
        payment.setCompletedBy(carrier.getFullName());
        payment.setCommissionPaid(false);
        payment.setCarrierTransferStatus(Payment.TransferStatus.NA);
        // ✅ Clear stale Razorpay fields if overwriting a stuck PENDING online payment row
        payment.setRazorpayOrderId(null);
        payment.setRazorpayPaymentId(null);
        payment.setRazorpaySignature(null);

        paymentRepository.save(payment);

        // ✅ Track pending commission + update total earnings on carrier profile
        CarrierProfile profile = carrier.getCarrierProfile();
        profile.setPendingCommission(
                profile.getPendingCommission().add(BigDecimal.valueOf(platformCommission)));
        profile.setTotalEarnings(
                profile.getTotalEarnings().add(BigDecimal.valueOf(totalAmount)));

        log.info("💵 COD recorded | Request: {} | Total: ₹{} | Commission owed: ₹{}",
                deliveryRequestId, totalAmount, platformCommission);

        // Notify carrier to pay commission
        sendFcm(carrier.getFcmToken(),
                "💵 Cash Collected — Pay Commission",
                "You collected ₹" + String.format("%.2f", totalAmount) + " cash. Pay ₹"
                        + String.format("%.2f", platformCommission)
                        + " platform commission to keep accepting trips.");

        sendFcm(request.getSender().getFcmToken(),
                "📦 Package Delivered",
                "Your package was delivered. Cash payment of ₹"
                        + String.format("%.2f", totalAmount) + " recorded.");

        sendAdminEmail("COD Payment Recorded — Commission Pending",
                String.format("<p>Carrier: %s | Request: %d | Total: ₹%.2f | Commission owed: ₹%.2f</p>",
                        carrier.getFullName(), request.getRequestId(), totalAmount, platformCommission));
    }
    // =========================================================================
    // 5. COMMISSION PAYMENT (carrier pays COD commission back to platform)
    // =========================================================================

    @Transactional
    public Map<String, Object> createCommissionPaymentOrder(String userId) throws Exception {
        User carrier = userRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Carrier not found: " + userId));

        List<Payment> unpaid = getUnpaidCodPayments(userId);
        double pendingCommission = unpaid.stream().mapToDouble(Payment::getPlatformCommission).sum();

        if (pendingCommission <= 0) {
            throw new RuntimeException("No pending commission for carrier: " + userId);
        }

        // TEST MODE: ₹1
        // PRODUCTION: int amountInPaise = (int)(pendingCommission * 100);
        int amountInPaise = 100;

        String truncId = userId.length() > 8 ? userId.substring(0, 8) : userId;
        String receipt = String.format("comm_%s_%d", truncId, System.currentTimeMillis() % 1000000000L);
        if (receipt.length() > 40) receipt = receipt.substring(0, 40);

        JSONObject orderReq = new JSONObject();
        orderReq.put("amount", amountInPaise);
        orderReq.put("currency", "INR");
        orderReq.put("receipt", receipt);
        orderReq.put("payment_capture", 1);

        JSONObject notes = new JSONObject();
        notes.put("type", "COMMISSION");
        notes.put("carrier_id", userId);
        notes.put("actual_commission", pendingCommission);
        notes.put("trips_count", unpaid.size());
        orderReq.put("notes", notes);

        Order order = razorpayClient.orders.create(orderReq);

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("orderId", order.get("id"));
        res.put("amount", amountInPaise);
        res.put("amountInRupees", 1.0);                      // TEST MODE
        res.put("actualCommissionAmount", pendingCommission); // Real amount for display
        res.put("currency", "INR");
        res.put("carrierId", userId);
        res.put("carrierName", carrier.getFullName());
        res.put("pendingCommission", pendingCommission);
        res.put("pendingTripsCount", unpaid.size());

        log.info("✅ Commission order: {} | Carrier: {} | Actual: ₹{} | Trips: {}",
                order.get("id"), userId, pendingCommission, unpaid.size());
        return res;
    }

    @Transactional
    public void verifyAndConfirmCommissionPayment(String userId, String razorpayOrderId,
                                                  String razorpayPaymentId, String razorpaySignature) throws Exception {
        // ✅ Verify signature
        String expected = calculateRazorpaySignature(razorpayOrderId, razorpayPaymentId);
        if (!expected.equals(razorpaySignature)) {
            throw new RuntimeException("Invalid commission payment signature.");
        }

        User carrier = userRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Carrier not found: " + userId));

        List<Payment> unpaid = getUnpaidCodPayments(userId);
        double totalCleared = unpaid.stream().mapToDouble(Payment::getPlatformCommission).sum();

        if (totalCleared <= 0) {
            throw new RuntimeException("No pending commission to clear for: " + userId);
        }

        // ✅ Mark all COD trips commission paid
        for (Payment p : unpaid) {
            p.setCommissionPaid(true);
            p.setCommissionPaidAt(LocalDateTime.now());
            p.setCommissionPaymentId(razorpayPaymentId);
        }
        paymentRepository.saveAll(unpaid);

        // ✅ Clear pending commission and reactivate carrier
        CarrierProfile profile = carrier.getCarrierProfile();
        profile.setPendingCommission(BigDecimal.ZERO);
        profile.setStatus(CarrierProfile.CarrierStatus.ACTIVE);

        log.info("✅ Commission cleared | Carrier: {} | Amount: ₹{} | Trips: {}",
                userId, totalCleared, unpaid.size());

        sendFcm(carrier.getFcmToken(),
                "✅ Commission Cleared!",
                "₹" + String.format("%.2f", totalCleared) +
                        " commission paid. Your account is active — start accepting trips!");

        createNotification(carrier, "Commission Paid ✅",
                "₹" + String.format("%.2f", totalCleared) + " commission cleared for "
                        + unpaid.size() + " trip(s).",
                com.saffaricarrers.saffaricarrers.Entity.Notification.NotificationType.PAYMENT_RECEIVED,
                null);

        sendAdminEmail("Commission Received — ₹" + String.format("%.2f", totalCleared),
                String.format(
                        "<p>Carrier: %s (ID: %s)</p>" +
                                "<p>Amount: ₹%.2f</p><p>Trips cleared: %d</p>" +
                                "<p>Razorpay Payment ID: %s</p>",
                        carrier.getFullName(), userId, totalCleared, unpaid.size(), razorpayPaymentId));
    }

    // =========================================================================
    // 6. ELIGIBILITY CHECKS
    // =========================================================================

    /**
     * Returns true if carrier can start a new trip.
     * Blocked ONLY if they have unpaid COD commission.
     * Online payment carriers are NEVER blocked.
     */
    @Transactional(readOnly = true)
    public boolean canCarrierStartTrip(String userId) {
        User carrier = userRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Carrier not found: " + userId));

        CarrierProfile profile = carrier.getCarrierProfile();
        if (profile == null) return false;
        if (profile.getStatus() == CarrierProfile.CarrierStatus.SUSPENDED) return false;

        List<Payment> unpaid = getUnpaidCodPayments(userId);
        boolean canStart = unpaid.isEmpty();

        log.info("Trip eligibility for {}: {} | Unpaid COD trips: {}",
                userId, canStart ? "✅ CAN START" : "❌ BLOCKED", unpaid.size());
        return canStart;
    }

    @Transactional(readOnly = true)
    public boolean canInitiatePayment(Long deliveryRequestId) {
        DeliveryRequest request = deliveryRequestRepository.findById(deliveryRequestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + deliveryRequestId));

        boolean pickedUp = (request.getStatus() == DeliveryRequest.RequestStatus.PICKED_UP
                || request.getStatus() == DeliveryRequest.RequestStatus.IN_TRANSIT)
                && request.getPickedUpAt() != null;

        boolean notPaid = request.getPayment() == null
                || request.getPayment().getPaymentStatus() != Payment.PaymentStatus.COMPLETED;

        return pickedUp && notPaid;
    }

    @Transactional(readOnly = true)
    public boolean isInsuranceAvailable(Long deliveryRequestId) {
        DeliveryRequest request = deliveryRequestRepository.findById(deliveryRequestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + deliveryRequestId));
        return request.getStatus() == DeliveryRequest.RequestStatus.PICKED_UP
                && request.getPickedUpAt() != null
                && (request.getPayment() == null
                || request.getPayment().getPaymentMethod() == Payment.PaymentMethod.ONLINE);
    }

    // =========================================================================
    // 7. COMMISSION DETAILS (for UI display)
    // =========================================================================

    @Transactional(readOnly = true)
    public CommissionDetailsDto getCarrierCommissionDetails(String userId) {
        User carrier = userRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Carrier not found: " + userId));

        CarrierProfile profile = carrier.getCarrierProfile();
        if (profile == null) throw new RuntimeException("Carrier profile not found: " + userId);

        List<Payment> unpaid = getUnpaidCodPayments(userId);
        double pending = unpaid.stream().mapToDouble(Payment::getPlatformCommission).sum();

        if (Math.abs(profile.getPendingCommission().doubleValue() - pending) > 0.01) {
            log.warn("⚠️ Commission mismatch — Profile: ₹{} vs Calculated: ₹{}. Using calculated.",
                    profile.getPendingCommission(), pending);
        }

        List<CommissionDetailsDto.TripCommissionDto> trips = unpaid.stream().map(p -> {
            DeliveryRequest req = p.getDeliveryRequest();
            Package pkg = req.getPackageEntity();
            return new CommissionDetailsDto.TripCommissionDto(
                    req.getRequestId(), pkg.getPackageId(), pkg.getProductName(),
                    p.getTotalAmount(), p.getPlatformCommission(), req.getDeliveredAt(),
                    req.getSender().getFullName(), pkg.getFromAddress(), pkg.getToAddress());
        }).toList();

        CommissionDetailsDto dto = new CommissionDetailsDto();
        dto.setCarrierId(profile.getCarrierId());
        dto.setCarrierName(carrier.getFullName());
        dto.setTotalPendingCommission(pending);
        dto.setPendingTripsCount(trips.size());
        dto.setPendingTrips(trips);
        return dto;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCarrierCommissionSummary(String userId) {
        User carrier = userRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Carrier not found: " + userId));
        CarrierProfile profile = carrier.getCarrierProfile();
        if (profile == null) throw new RuntimeException("Carrier profile not found: " + userId);

        List<Payment> unpaid = getUnpaidCodPayments(userId);
        double pending = unpaid.stream().mapToDouble(Payment::getPlatformCommission).sum();

        Map<String, Object> summary = new HashMap<>();
        summary.put("carrierId", userId);
        summary.put("carrierName", carrier.getFullName());
        summary.put("totalPendingCommission", pending);
        summary.put("pendingTripsCount", unpaid.size());
        summary.put("canStartNewTrips", unpaid.isEmpty());
        summary.put("accountStatus", profile.getStatus().toString());
        summary.put("totalEarnings", profile.getTotalEarnings().doubleValue());
        summary.put("commissionStatus", unpaid.isEmpty() ? "Paid" : "Pending");
        return summary;
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private List<Payment> getUnpaidCodPayments(String userId) {
        return paymentRepository
                .findByDeliveryRequest_Carrier_UserIdAndPaymentMethodAndPaymentStatus(
                        userId, Payment.PaymentMethod.COD, Payment.PaymentStatus.COMPLETED)
                .stream()
                .filter(p -> p.getCommissionPaid() == null || !p.getCommissionPaid())
                .toList();
    }

    private String calculateRazorpaySignature(String orderId, String paymentId) throws Exception {
        String payload = orderId + "|" + paymentId;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(razorpayKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void createNotification(User user, String title, String message,
                                    com.saffaricarrers.saffaricarrers.Entity.Notification.NotificationType type,
                                    Long referenceId) {
        com.saffaricarrers.saffaricarrers.Entity.Notification n =
                new com.saffaricarrers.saffaricarrers.Entity.Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setMessage(message);
        n.setType(type);
        n.setReferenceId(referenceId);
        n.setIsRead(false);
        notificationRepository.save(n);
    }

    private void sendFcm(String token, String title, String body) {
        if (token == null || token.isBlank()) return;
        try {
            FirebaseMessaging.getInstance().send(
                    Message.builder()
                            .setToken(token)
                            .setNotification(com.google.firebase.messaging.Notification.builder()
                                    .setTitle(title).setBody(body).build())
                            .build());
        } catch (Exception e) {
            log.error("❌ FCM failed: {}", e.getMessage());
        }
    }

    private String buildPaymentEmailHtml(Payment payment, String note) {
        return String.format("""
            <table border="1" cellpadding="8" style="border-collapse:collapse">
              <tr><td><b>Payment ID</b></td><td>%d</td></tr>
              <tr><td><b>Razorpay Payment ID</b></td><td>%s</td></tr>
              <tr><td><b>Total Amount</b></td><td>₹%.2f</td></tr>
              <tr><td><b>Platform Commission (15%%)</b></td><td>₹%.2f</td></tr>
              <tr><td><b>Carrier Payout (85%%)</b></td><td>₹%.2f</td></tr>
              <tr><td><b>Method</b></td><td>%s</td></tr>
              <tr><td><b>Request ID</b></td><td>%d</td></tr>
              <tr><td><b>Transfer Status</b></td><td>%s</td></tr>
              <tr><td><b>Note</b></td><td>%s</td></tr>
            </table>
            """,
                payment.getPaymentId(),
                payment.getRazorpayPaymentId() != null ? payment.getRazorpayPaymentId() : "N/A",
                payment.getTotalAmount(), payment.getPlatformCommission(), payment.getCarrierAmount(),
                payment.getPaymentMethod(), payment.getDeliveryRequest().getRequestId(),
                payment.getCarrierTransferStatus(), note);
    }

    private void sendAdminEmail(String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true);
            helper.setTo(adminEmail);
            helper.setSubject("[Saffari Carriers] " + subject);
            helper.setText(html, true);
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("❌ Admin email failed: {}", e.getMessage());
        }
    }
    public RazorpayClient getRazorpayClient() {
        return razorpayClient;
    }

    public PaymentRepository getPaymentRepository() {
        return paymentRepository;
    }
}