package com.saffaricarrers.saffaricarrers.Services;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.razorpay.*;
import com.saffaricarrers.saffaricarrers.Configaration.RazorpayConfig;
import com.saffaricarrers.saffaricarrers.Dtos.CommissionDetailsDto;
import com.saffaricarrers.saffaricarrers.Entity.*;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import com.saffaricarrers.saffaricarrers.Entity.Payment;
import com.saffaricarrers.saffaricarrers.Repository.*;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.Optional;

@Service
@Slf4j
public class PaymentService {

    private final RazorpayClient razorpayClient;
    private final RazorpayConfig razorpayConfig;
    private final RazorpayRouteService razorpayRouteService;
    private final PaymentRepository paymentRepository;
    private final DeliveryRequestRepository deliveryRequestRepository;
    private final UserRepository userRepository;
    private final PackageRepository packageRepository;
    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    @Value("${admin.email}")
    private String adminEmail;

    /** Razorpay Dashboard -> Webhooks secret. NOT the API key secret. */
    @Value("${razorpay.webhook.secret:}")
    private String razorpayWebhookSecret;

    // ── Manual constructor — required because @Qualifier doesn't work with Lombok ──
    public PaymentService(
            @Qualifier("safarcarryRazorpayClient") RazorpayClient razorpayClient,
            RazorpayConfig razorpayConfig,
            RazorpayRouteService razorpayRouteService,
            PaymentRepository paymentRepository,
            DeliveryRequestRepository deliveryRequestRepository,
            UserRepository userRepository,
            PackageRepository packageRepository,
            NotificationRepository notificationRepository,
            JavaMailSender mailSender) {
        this.razorpayClient = razorpayClient;
        this.razorpayConfig = razorpayConfig;
        this.razorpayRouteService = razorpayRouteService;
        this.paymentRepository = paymentRepository;
        this.deliveryRequestRepository = deliveryRequestRepository;
        this.userRepository = userRepository;
        this.packageRepository = packageRepository;
        this.notificationRepository = notificationRepository;
        this.mailSender = mailSender;
    }

    public static final double PLATFORM_COMMISSION_RATE = 0.15;
    public static final double PLATFORM_FEE_RATE = 0.02;

    // =========================================================================
    // 1. CREATE PAYMENT ORDER
    // =========================================================================

    @Transactional
    public Payment createPaymentOrder(Long deliveryRequestId) throws Exception {
        DeliveryRequest request = deliveryRequestRepository.findById(deliveryRequestId)
                .orElseThrow(() -> new RuntimeException("Delivery request not found: " + deliveryRequestId));

        if (request.getPayment() != null) {
            if (request.getPayment().getPaymentStatus() == Payment.PaymentStatus.COMPLETED) {
                throw new IllegalStateException("Payment already completed for this delivery.");
            }
            log.info("♻️ Returning existing pending order for request: {}", deliveryRequestId);
            return request.getPayment();
        }

        if (request.getStatus() != DeliveryRequest.RequestStatus.PICKED_UP
                && request.getStatus() != DeliveryRequest.RequestStatus.IN_TRANSIT) {
            throw new IllegalStateException("Payment can only be made after the carrier picks up the package.");
        }

        Package pkg = request.getPackageEntity();
        User sender = request.getSender();
        User carrier = request.getCarrier();

        double deliveryCharge = request.getTotalAmount();

        if (deliveryCharge <= 0) {
            throw new IllegalStateException(
                    "Invalid delivery charge: ₹" + deliveryCharge
            );
        }

        double insuranceAmount = 0.0;

// Platform fee paid by sender
        double platformFee =
                round2(deliveryCharge * PLATFORM_FEE_RATE);

// Final amount sender pays
        double totalAmount =
                round2(deliveryCharge + insuranceAmount + platformFee);

// Your internal 15% commission
        double platformCommission =
                round2(deliveryCharge * PLATFORM_COMMISSION_RATE);

// Amount carrier receives
        double carrierAmount =
                round2(deliveryCharge - platformCommission);

// Razorpay ALWAYS receives paise
        int amountInPaise =
                (int) Math.round(totalAmount * 100);
        log.info("🧾 Creating order | Request: {} | Trip: ₹{} | PlatformFee: ₹{} | Total: ₹{} | Carrier: ₹{} | Commission: ₹{}",
                deliveryRequestId, deliveryCharge, platformFee, totalAmount, carrierAmount, platformCommission);

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
        notes.put("platform_fee", platformFee);
        orderRequest.put("notes", notes);

        Order razorpayOrder = razorpayClient.orders.create(orderRequest);
        String razorpayOrderId = razorpayOrder.get("id");

        Payment payment = new Payment();
        payment.setPackageEntity(pkg);
        payment.setDeliveryRequest(request);
        payment.setDeliveryCharge(deliveryCharge);
        payment.setInsuranceAmount(insuranceAmount);
        payment.setPlatformFee(platformFee);
        payment.setTotalAmount(totalAmount);
        payment.setPlatformCommission(platformCommission);
        payment.setCarrierAmount(carrierAmount);
        payment.setPaymentMethod(Payment.PaymentMethod.ONLINE);
        payment.setPaymentStatus(Payment.PaymentStatus.PENDING);
        payment.setCarrierTransferStatus(Payment.TransferStatus.PENDING);
        payment.setRazorpayOrderId(razorpayOrderId);
        payment.setReceipt(razorpayOrder.get("receipt"));
        payment.setCommissionPaid(true);

        Payment saved = paymentRepository.save(payment);
        log.info("✅ Payment order created: {} | Request: {}", razorpayOrderId, deliveryRequestId);
        return saved;
    }

    // =========================================================================
    // 2. CONFIRM ONLINE PAYMENT
    // =========================================================================

    @Transactional
    public void confirmOnlinePayment(String razorpayOrderId, String razorpayPaymentId,
                                     String razorpaySignature) throws Exception {
        String expected = calculateRazorpaySignature(razorpayOrderId, razorpayPaymentId);
        if (!expected.equals(razorpaySignature)) {
            log.error("❌ Signature mismatch | Order: {}", razorpayOrderId);
            throw new RuntimeException("Invalid payment signature — possible tampered request.");
        }

        Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for order: " + razorpayOrderId));

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
        payment.setCarrierTransferStatus(Payment.TransferStatus.PENDING);

        if (payment.getInsuranceAmount() != null && payment.getInsuranceAmount() > 0) {
            pkg.setInsurance(true);
            packageRepository.save(pkg);
            log.info("✅ Insurance activated for package: {}", pkg.getPackageId());
        }

        paymentRepository.save(payment);

        log.info("✅ Online payment confirmed: {} | Total paid: ₹{} (incl. platform fee: ₹{}) | Payout queued for delivery.",
                razorpayPaymentId, payment.getTotalAmount(), payment.getPlatformFee());

        createNotification(request.getSender(),
                "Payment Successful ✅",
                "₹" + fmt(payment.getTotalAmount()) + " paid (incl. ₹" + fmt(payment.getPlatformFee()) + " platform fee). Carrier earns on delivery.",
                com.saffaricarrers.saffaricarrers.Entity.Notification.NotificationType.PAYMENT_RECEIVED,
                request.getRequestId());

        createNotification(request.getCarrier(),
                "Sender Has Paid 💰",
                "₹" + fmt(payment.getCarrierAmount()) + " will be transferred to your bank after you complete the delivery.",
                com.saffaricarrers.saffaricarrers.Entity.Notification.NotificationType.PAYMENT_RECEIVED,
                request.getRequestId());

        sendFcm(request.getSender().getFcmToken(), "✅ Payment Successful",
                "₹" + fmt(payment.getTotalAmount()) + " paid successfully!");
        sendFcm(request.getCarrier().getFcmToken(), "Sender Paid 💰",
                "Complete the delivery to receive ₹" + fmt(payment.getCarrierAmount()) + " in your bank.");
        sendAdminEmail("Online Payment Confirmed — ₹" + fmt(payment.getTotalAmount()),
                buildPaymentEmailHtml(payment, "Payout will trigger when delivery OTP is verified."));
    }

    // =========================================================================
    // 3. TRIGGER CARRIER PAYOUT ON DELIVERY
    // =========================================================================

    @Transactional
    public void triggerCarrierPayoutOnDelivery(Long deliveryRequestId) {
        DeliveryRequest request = deliveryRequestRepository.findById(deliveryRequestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + deliveryRequestId));

        Payment payment = request.getPayment();

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

        razorpayRouteService.transferToCarrier(payment);
    }

    // =========================================================================
    // 4. COD PAYMENT
    // =========================================================================

    @Transactional
    public void handleOfflinePaymentOnDelivery(Long deliveryRequestId) {
        DeliveryRequest request = deliveryRequestRepository.findById(deliveryRequestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + deliveryRequestId));

        if (request.getPayment() != null &&
                request.getPayment().getPaymentStatus() == Payment.PaymentStatus.COMPLETED) {
            log.info("✅ Online payment already completed for request: {}. Skipping COD recording.", deliveryRequestId);
            return;
        }

        Package pkg = request.getPackageEntity();
        User carrier = request.getCarrier();

        double deliveryCharge     = request.getTotalAmount();
        double platformFee        = round2(deliveryCharge * PLATFORM_FEE_RATE);
        double totalAmount        = deliveryCharge + platformFee;
        double platformCommission = round2(deliveryCharge * PLATFORM_COMMISSION_RATE);
        double carrierAmount      = round2(deliveryCharge - platformCommission);

        log.info("💵 COD | Request: {} | Trip: ₹{} | PlatformFee: ₹{} | Total collected: ₹{} | Commission owed: ₹{}",
                deliveryRequestId, deliveryCharge, platformFee, totalAmount, platformCommission);

        Payment payment = request.getPayment() != null ? request.getPayment() : new Payment();
        payment.setPackageEntity(pkg);
        payment.setDeliveryRequest(request);
        payment.setDeliveryCharge(deliveryCharge);
        payment.setInsuranceAmount(0.0);
        payment.setPlatformFee(platformFee);
        payment.setTotalAmount(totalAmount);
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
        payment.setRazorpayOrderId(null);
        payment.setRazorpayPaymentId(null);
        payment.setRazorpaySignature(null);
        paymentRepository.save(payment);

        CarrierProfile profile = carrier.getCarrierProfile();
        profile.setPendingCommission(
                profile.getPendingCommission().add(BigDecimal.valueOf(platformCommission)));
        profile.setTotalEarnings(
                profile.getTotalEarnings().add(BigDecimal.valueOf(totalAmount)));

        sendFcm(carrier.getFcmToken(), "💵 Cash Collected — Pay Commission",
                "You collected ₹" + fmt(totalAmount) + " cash. Pay ₹"
                        + fmt(platformCommission) + " platform commission to keep accepting trips.");
        sendFcm(request.getSender().getFcmToken(), "📦 Package Delivered",
                "Your package was delivered. Cash payment of ₹" + fmt(totalAmount) + " recorded.");
        sendAdminEmail("COD Payment Recorded — Commission Pending",
                String.format("<p>Carrier: %s | Request: %d | Trip: ₹%.2f | PlatformFee: ₹%.2f | Total: ₹%.2f | Commission owed: ₹%.2f</p>",
                        carrier.getFullName(), request.getRequestId(),
                        deliveryCharge, platformFee, totalAmount, platformCommission));
    }

    // =========================================================================
    // 5. COMMISSION PAYMENT
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

        int amountInPaise = (int) (pendingCommission * 100);
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
        res.put("amountInRupees", pendingCommission);
        res.put("actualCommissionAmount", pendingCommission);
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

        for (Payment p : unpaid) {
            p.setCommissionPaid(true);
            p.setCommissionPaidAt(LocalDateTime.now());
            p.setCommissionPaymentId(razorpayPaymentId);
        }
        paymentRepository.saveAll(unpaid);

        CarrierProfile profile = carrier.getCarrierProfile();
        profile.setPendingCommission(BigDecimal.ZERO);
        profile.setStatus(CarrierProfile.CarrierStatus.ACTIVE);

        log.info("✅ Commission cleared | Carrier: {} | Amount: ₹{} | Trips: {}",
                userId, totalCleared, unpaid.size());

        sendFcm(carrier.getFcmToken(), "✅ Commission Cleared!",
                "₹" + fmt(totalCleared) + " commission paid. Your account is active — start accepting trips!");
        createNotification(carrier, "Commission Paid ✅",
                "₹" + fmt(totalCleared) + " commission cleared for " + unpaid.size() + " trip(s).",
                com.saffaricarrers.saffaricarrers.Entity.Notification.NotificationType.PAYMENT_RECEIVED,
                null);
        sendAdminEmail("Commission Received — ₹" + fmt(totalCleared),
                String.format("<p>Carrier: %s (ID: %s)</p><p>Amount: ₹%.2f</p><p>Trips cleared: %d</p><p>Razorpay Payment ID: %s</p>",
                        carrier.getFullName(), userId, totalCleared, unpaid.size(), razorpayPaymentId));
    }

    // =========================================================================
    // 6. ELIGIBILITY CHECKS
    // =========================================================================

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
    // 7. COMMISSION DETAILS
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
    // QR PAYMENT
    // =========================================================================

    @Transactional
    public Map<String, Object> createQrPayment(Long deliveryRequestId) throws Exception {
        DeliveryRequest request = deliveryRequestRepository.findById(deliveryRequestId)
                .orElseThrow(() -> new RuntimeException("Delivery request not found: " + deliveryRequestId));

        if (request.getStatus() != DeliveryRequest.RequestStatus.IN_TRANSIT) {
            throw new IllegalStateException("QR payment can only be generated once the package is in transit.");
        }

        if (request.getPayment() != null) {
            Payment existing = request.getPayment();
            if (existing.getPaymentStatus() == Payment.PaymentStatus.COMPLETED) {
                throw new IllegalStateException("Payment already completed for this delivery.");
            }
            if (existing.getQrId() != null) {
                log.info("♻️ Returning existing QR for request: {}", deliveryRequestId);
                Map<String, Object> result = new HashMap<>();
                result.put("qrId",               existing.getQrId());
                result.put("imageUrl",           existing.getQrImageUrl());
                result.put("shortUrl",           existing.getQrShortUrl());
                result.put("amount",             existing.getTotalAmount());
                result.put("amountInPaise",      (int)(existing.getTotalAmount() * 100));
                result.put("description",        "Delivery payment for " + request.getPackageEntity().getProductName());
                result.put("carrierAmount",      existing.getCarrierAmount());
                result.put("platformFee",        existing.getPlatformFee());
                result.put("platformCommission", existing.getPlatformCommission());
                result.put("expiresInSeconds",   1800);
                return result;
            }
        }

        Package pkg     = request.getPackageEntity();
        User    sender  = request.getSender();
        User    carrier = request.getCarrier();

        double deliveryCharge     = request.getTotalAmount();
        double insuranceAmount    = 0.0;
        double platformFee        = round2(deliveryCharge * PLATFORM_FEE_RATE);
        double totalAmount        = deliveryCharge + insuranceAmount + platformFee;
        double platformCommission = round2(deliveryCharge * PLATFORM_COMMISSION_RATE);
        double carrierAmount      = round2(deliveryCharge - platformCommission);
        int    amountInPaise      = (int)(totalAmount * 100);

        String description = String.format("Delivery payment for %s | Carrier: %s",
                pkg.getProductName(), carrier.getFullName());

        log.info("🔳 Creating QR | Request: {} | Total: ₹{} | Carrier gets: ₹{} | Platform: ₹{}",
                deliveryRequestId, totalAmount, carrierAmount, platformFee + platformCommission);

        JSONObject qrRequest = new JSONObject();
        qrRequest.put("type",           "upi_qr");
        qrRequest.put("name",           "Safar Couriers");
        qrRequest.put("usage",          "single_use");
        qrRequest.put("fixed_amount",   true);
        qrRequest.put("payment_amount", amountInPaise);
        qrRequest.put("description",    description);
        qrRequest.put("close_by",       (System.currentTimeMillis() / 1000) + (30 * 60));

        JSONObject qrNotes = new JSONObject();
        qrNotes.put("delivery_request_id", String.valueOf(deliveryRequestId));
        qrNotes.put("package_id",          String.valueOf(pkg.getPackageId()));
        qrNotes.put("sender_id",           sender.getUserId());
        qrNotes.put("carrier_id",          carrier.getUserId());
        qrRequest.put("notes", qrNotes);

        String credentials = java.util.Base64.getEncoder().encodeToString(
                (razorpayConfig.getSafarcarryKeyId() + ":" + razorpayConfig.getSafarcarryKeySecret())
                        .getBytes(StandardCharsets.UTF_8));

        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest httpReq = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.razorpay.com/v1/payments/qr_codes"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + credentials)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(qrRequest.toString()))
                .build();

        java.net.http.HttpResponse<String> httpResp = httpClient.send(
                httpReq, java.net.http.HttpResponse.BodyHandlers.ofString());

        if (httpResp.statusCode() != 200 && httpResp.statusCode() != 201) {
            log.error("❌ Razorpay QR API error {}: {}", httpResp.statusCode(), httpResp.body());
            throw new RuntimeException("QR creation failed: " + httpResp.body());
        }

        JSONObject qrResponse = new JSONObject(httpResp.body());
        String qrId     = qrResponse.getString("id");
        String imageUrl = qrResponse.getString("image_url");
        String shortUrl = qrResponse.optString("short_url", null);

        log.info("✅ QR created: {} | imageUrl: {}", qrId, imageUrl);

        Payment payment = request.getPayment() != null ? request.getPayment() : new Payment();
        payment.setPackageEntity(pkg);
        payment.setDeliveryRequest(request);
        payment.setDeliveryCharge(deliveryCharge);
        payment.setInsuranceAmount(insuranceAmount);
        payment.setPlatformFee(platformFee);
        payment.setTotalAmount(totalAmount);
        payment.setPlatformCommission(platformCommission);
        payment.setCarrierAmount(carrierAmount);
        payment.setPaymentMethod(Payment.PaymentMethod.ONLINE);
        payment.setPaymentStatus(Payment.PaymentStatus.PENDING);
        payment.setCarrierTransferStatus(Payment.TransferStatus.PENDING);
        payment.setCommissionPaid(false);
        payment.setQrId(qrId);
        payment.setQrImageUrl(imageUrl);
        payment.setQrShortUrl(shortUrl);
        paymentRepository.save(payment);

        Map<String, Object> result = new HashMap<>();
        result.put("qrId",               qrId);
        result.put("imageUrl",           imageUrl);
        result.put("shortUrl",           shortUrl);
        result.put("amount",             totalAmount);
        result.put("amountInPaise",      amountInPaise);
        result.put("description",        description);
        result.put("carrierAmount",      carrierAmount);
        result.put("platformFee",        platformFee);
        result.put("platformCommission", platformCommission);
        result.put("expiresInSeconds",   1800);
        return result;
    }

    /**
     * Checks payment status for a QR code.
     *
     * DB is always checked first because the Razorpay QR webhook can mark the
     * payment COMPLETED before the rider's next polling request arrives.
     * Only when the DB is still pending do we call Razorpay as a fallback.
     */
    @Transactional
    public Map<String, Object> checkQrPaymentStatus(String qrId, Long deliveryRequestId) throws Exception {

        Optional<Payment> dbPayment = paymentRepository.findByQrId(qrId);

        if (dbPayment.isPresent()
                && dbPayment.get().getPaymentStatus() == Payment.PaymentStatus.COMPLETED) {

            Payment payment = dbPayment.get();

            Map<String, Object> already = new HashMap<>();
            already.put("paid", true);
            already.put("paymentId", payment.getRazorpayPaymentId());
            already.put("amount", payment.getTotalAmount());
            already.put("carrierAmount", payment.getCarrierAmount());
            already.put("platformFee", payment.getPlatformFee());
            already.put("platformCommission", payment.getPlatformCommission());
            return already;
        }

        JSONObject fetchOptions = new JSONObject();
        fetchOptions.put("count", 10);

        /*
         * The Razorpay Java SDK version used by this project declares the
         * return type in a way that conflicts with the runtime object type.
         * The actual objects returned here are com.razorpay.Payment.
         * Use List<?> to avoid the generic compile-time mismatch, then cast
         * each runtime object explicitly.
         */
        List<?> qrPayments =
                razorpayClient.qrCode.fetchAllPayments(
                        qrId,
                        fetchOptions
                );

        boolean paid = false;
        String rzpPaymentId = null;

        for (Object rawEntry : qrPayments) {

            com.razorpay.Payment entry =
                    (com.razorpay.Payment) rawEntry;

            String status = entry.get("status");

            if ("captured".equals(status)
                    || "authorized".equals(status)) {

                paid = true;
                rzpPaymentId = entry.get("id");
                break;
            }
        }

        if (paid && dbPayment.isPresent()) {

            Payment payment = dbPayment.get();

            if (payment.getPaymentStatus() != Payment.PaymentStatus.COMPLETED) {

                payment.setPaymentStatus(
                        Payment.PaymentStatus.COMPLETED
                );

                payment.setRazorpayPaymentId(
                        rzpPaymentId
                );

                payment.setPaymentCompletedAt(
                        LocalDateTime.now()
                );

                paymentRepository.save(payment);

                log.info(
                        "✅ QR payment confirmed by fallback polling: qrId={} paymentId={}",
                        qrId,
                        rzpPaymentId
                );

                try {
                    sendAdminSplitNotification(payment);
                } catch (Exception e) {
                    log.error(
                            "Failed to send admin split notification: {}",
                            e.getMessage()
                    );
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("paid", paid);
        result.put("paymentId", rzpPaymentId);

        if (dbPayment.isPresent()) {
            Payment payment = dbPayment.get();
            result.put("amount", payment.getTotalAmount());
            result.put("carrierAmount", payment.getCarrierAmount());
            result.put("platformFee", payment.getPlatformFee());
            result.put("platformCommission", payment.getPlatformCommission());
        }

        return result;
    }

    private void sendAdminSplitNotification(Payment payment) throws Exception {
        DeliveryRequest dr = payment.getDeliveryRequest();
        String subject = String.format(
                "💰 Payment Received — Manual Split Required | Request #%d", dr.getRequestId());

        String html = String.format("""
            <h2>Payment Received — Manual Split Required</h2>
            <table border="1" cellpadding="8" style="border-collapse:collapse">
              <tr><th>Field</th><th>Value</th></tr>
              <tr><td>Delivery Request ID</td><td>%d</td></tr>
              <tr><td>Package</td><td>%s</td></tr>
              <tr><td>Sender</td><td>%s</td></tr>
              <tr><td>Carrier</td><td>%s</td></tr>
              <tr><td><b>Total Paid by Sender</b></td><td><b>₹%.2f</b></td></tr>
              <tr><td>Platform Fee (2%%)</td><td>₹%.2f</td></tr>
              <tr><td>Platform Commission (15%%)</td><td>₹%.2f</td></tr>
              <tr><td style="color:green"><b>Transfer to Carrier</b></td><td style="color:green"><b>₹%.2f</b></td></tr>
              <tr><td>Total Platform Keeps</td><td>₹%.2f</td></tr>
              <tr><td>Razorpay Payment ID</td><td>%s</td></tr>
              <tr><td>QR ID</td><td>%s</td></tr>
            </table>
            <p style="color:red"><b>Action Required:</b> Transfer ₹%.2f to carrier %s manually.</p>
            """,
                dr.getRequestId(),
                payment.getPackageEntity().getProductName(),
                dr.getSender().getFullName(),
                dr.getCarrier().getFullName(),
                payment.getTotalAmount(),
                payment.getPlatformFee(),
                payment.getPlatformCommission(),
                payment.getCarrierAmount(),
                payment.getPlatformFee() + payment.getPlatformCommission(),
                payment.getRazorpayPaymentId(),
                payment.getQrId(),
                payment.getCarrierAmount(),
                dr.getCarrier().getFullName());
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setTo(adminEmail);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(msg);
        log.info("📧 Admin split notification sent for request #{}", dr.getRequestId());
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String fmt(double value) {
        return String.format("%.2f", value);
    }

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
        mac.init(new SecretKeySpec(
                razorpayConfig.getSafarcarryKeySecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
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
              <tr><td><b>Trip / Delivery Charge</b></td><td>₹%.2f</td></tr>
              <tr><td><b>Platform Fee (2%%)</b></td><td>₹%.2f</td></tr>
              <tr><td><b>Total Paid by Sender</b></td><td>₹%.2f</td></tr>
              <tr><td><b>Platform Commission (15%% of trip)</b></td><td>₹%.2f</td></tr>
              <tr><td><b>Carrier Payout</b></td><td>₹%.2f</td></tr>
              <tr><td><b>Method</b></td><td>%s</td></tr>
              <tr><td><b>Request ID</b></td><td>%d</td></tr>
              <tr><td><b>Transfer Status</b></td><td>%s</td></tr>
              <tr><td><b>Note</b></td><td>%s</td></tr>
            </table>
            """,
                payment.getPaymentId(),
                payment.getRazorpayPaymentId() != null ? payment.getRazorpayPaymentId() : "N/A",
                payment.getDeliveryCharge() != null ? payment.getDeliveryCharge() : 0.0,
                payment.getPlatformFee() != null ? payment.getPlatformFee() : 0.0,
                payment.getTotalAmount(),
                payment.getPlatformCommission(),
                payment.getCarrierAmount(),
                payment.getPaymentMethod(),
                payment.getDeliveryRequest().getRequestId(),
                payment.getCarrierTransferStatus(),
                note);
    }

    private void sendAdminEmail(String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true);
            helper.setTo(adminEmail);
            helper.setSubject("[Safar Couriers] " + subject);
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
    /**
     * ================================================================
     * RIDER PAYMENT FLOW — CREATE QR AFTER DELIVERY OTP
     * ================================================================
     *
     * This is separate from createQrPayment().
     *
     * Existing createQrPayment() remains IN_TRANSIT-only.
     *
     * This method allows QR creation after the rider has verified
     * delivery OTP and the request is now DELIVERED.
     */
    @Transactional
    public Map<String, Object> createQrPaymentAfterDeliveryOtp(
            Long deliveryRequestId) throws Exception {

        DeliveryRequest request = deliveryRequestRepository.findById(deliveryRequestId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Delivery request not found: " + deliveryRequestId
                        ));

        // ---------------------------------------------------------------
        // Only allow this new flow after delivery OTP
        // ---------------------------------------------------------------
        if (request.getStatus()
                != DeliveryRequest.RequestStatus.DELIVERED) {

            throw new IllegalStateException(
                    "Delivery OTP must be verified before creating payment QR."
            );
        }

        // ---------------------------------------------------------------
        // If payment already exists
        // ---------------------------------------------------------------
        if (request.getPayment() != null) {

            Payment existing = request.getPayment();

            if (existing.getPaymentStatus()
                    == Payment.PaymentStatus.COMPLETED) {

                log.info(
                        "✅ Payment already completed — returning recovery response | request={}",
                        deliveryRequestId
                );

                Map<String, Object> result = new HashMap<>();
                result.put("paid", true);
                result.put("qrId", existing.getQrId());
                result.put("imageUrl", existing.getQrImageUrl());
                result.put("shortUrl", existing.getQrShortUrl());
                result.put("amount", existing.getTotalAmount());
                result.put("amountInPaise",
                        (int) Math.round(existing.getTotalAmount() * 100));
                result.put("description",
                        "Delivery payment for "
                                + request.getPackageEntity().getProductName());
                result.put("carrierAmount", existing.getCarrierAmount());
                result.put("platformFee", existing.getPlatformFee());
                result.put("platformCommission", existing.getPlatformCommission());
                result.put("expiresInSeconds", 1800);
                result.put("paymentId", existing.getRazorpayPaymentId());
                return result;
            }

            // Re-use existing QR if one exists
            if (existing.getQrId() != null) {

                log.info(
                        "♻️ Returning existing post-delivery QR for request: {}",
                        deliveryRequestId
                );

                Map<String, Object> result = new HashMap<>();

                result.put("qrId", existing.getQrId());
                result.put("imageUrl", existing.getQrImageUrl());
                result.put("shortUrl", existing.getQrShortUrl());
                result.put("amount", existing.getTotalAmount());
                result.put(
                        "amountInPaise",
                        (int) (existing.getTotalAmount() * 100)
                );

                result.put(
                        "description",
                        "Delivery payment for "
                                + request.getPackageEntity().getProductName()
                );

                result.put("carrierAmount", existing.getCarrierAmount());
                result.put("platformFee", existing.getPlatformFee());
                result.put(
                        "platformCommission",
                        existing.getPlatformCommission()
                );

                result.put("expiresInSeconds", 1800);
                result.put("paid", false);

                return result;
            }
        }

        // ---------------------------------------------------------------
        // Amount calculation
        // Same calculation as your existing createQrPayment()
        // ---------------------------------------------------------------
        Package pkg = request.getPackageEntity();

        User sender = request.getSender();
        User carrier = request.getCarrier();

        double deliveryCharge = request.getTotalAmount();

        double insuranceAmount = 0.0;

        double platformFee =
                round2(deliveryCharge * PLATFORM_FEE_RATE);

        double totalAmount =
                deliveryCharge + insuranceAmount + platformFee;

        double platformCommission =
                round2(deliveryCharge * PLATFORM_COMMISSION_RATE);

        double carrierAmount =
                round2(deliveryCharge - platformCommission);

        int amountInPaise =
                (int) (totalAmount * 100);

        String description = String.format(
                "Delivery payment for %s | Carrier: %s",
                pkg.getProductName(),
                carrier.getFullName()
        );

        log.info(
                "🔳 Creating POST-DELIVERY QR | Request: {} | Total: ₹{} | Carrier gets: ₹{}",
                deliveryRequestId,
                totalAmount,
                carrierAmount
        );

        // ---------------------------------------------------------------
        // Create Razorpay QR
        // ---------------------------------------------------------------

        JSONObject qrRequest = new JSONObject();

        qrRequest.put("type", "upi_qr");

        qrRequest.put("name", "Safar");

        qrRequest.put(
                "usage",
                "single_use"
        );

        qrRequest.put(
                "fixed_amount",
                true
        );

        qrRequest.put(
                "payment_amount",
                amountInPaise
        );

        qrRequest.put(
                "description",
                description
        );

        // Use the SAME HTTP/Razorpay configuration you already use
        // in createQrPayment().
        //
        // If your existing method already has:
        //
        // HttpClient httpClient
        // HttpRequest httpReq
        // razorpay credentials
        //
        // COPY THAT EXISTING QR API BLOCK HERE unchanged.

        String credentials = java.util.Base64.getEncoder().encodeToString(
                (razorpayConfig.getSafarcarryKeyId()
                        + ":" +
                        razorpayConfig.getSafarcarryKeySecret())
                        .getBytes(StandardCharsets.UTF_8)
        );

        java.net.http.HttpClient httpClient =
                java.net.http.HttpClient.newHttpClient();

        java.net.http.HttpRequest httpReq =
                java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(
                                "https://api.razorpay.com/v1/payments/qr_codes"
                        ))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Basic " + credentials)
                        .POST(
                                java.net.http.HttpRequest.BodyPublishers.ofString(
                                        qrRequest.toString()
                                )
                        )
                        .build();

        java.net.http.HttpResponse<String> httpResp =
                httpClient.send(
                        httpReq,
                        java.net.http.HttpResponse.BodyHandlers.ofString()
                );

        if (httpResp.statusCode() != 200
                && httpResp.statusCode() != 201) {

            log.error(
                    "❌ Razorpay post-delivery QR API error {}: {}",
                    httpResp.statusCode(),
                    httpResp.body()
            );

            throw new RuntimeException(
                    "QR creation failed: " + httpResp.body()
            );
        }

        JSONObject qrResponse =
                new JSONObject(httpResp.body());

        String qrId =
                qrResponse.getString("id");

        String imageUrl =
                qrResponse.getString("image_url");

        String shortUrl =
                qrResponse.optString(
                        "short_url",
                        null
                );

        log.info(
                "✅ Post-delivery QR created: {}",
                qrId
        );

        // ---------------------------------------------------------------
        // Save Payment
        // ---------------------------------------------------------------
        Payment payment =
                request.getPayment() != null
                        ? request.getPayment()
                        : new Payment();

        payment.setPackageEntity(pkg);
        payment.setDeliveryRequest(request);

        payment.setDeliveryCharge(deliveryCharge);
        payment.setInsuranceAmount(insuranceAmount);
        payment.setPlatformFee(platformFee);
        payment.setTotalAmount(totalAmount);
        payment.setPlatformCommission(platformCommission);
        payment.setCarrierAmount(carrierAmount);

        payment.setPaymentMethod(
                Payment.PaymentMethod.ONLINE
        );

        payment.setPaymentStatus(
                Payment.PaymentStatus.PENDING
        );

        payment.setCarrierTransferStatus(
                Payment.TransferStatus.PENDING
        );

        payment.setCommissionPaid(false);

        payment.setQrId(qrId);
        payment.setQrImageUrl(imageUrl);
        payment.setQrShortUrl(shortUrl);

        paymentRepository.save(payment);

        // ---------------------------------------------------------------
        // Response
        // ---------------------------------------------------------------
        Map<String, Object> result =
                new HashMap<>();

        result.put("qrId", qrId);
        result.put("imageUrl", imageUrl);
        result.put("shortUrl", shortUrl);
        result.put("amount", totalAmount);
        result.put("amountInPaise", amountInPaise);
        result.put("description", description);
        result.put("carrierAmount", carrierAmount);
        result.put("platformFee", platformFee);
        result.put("platformCommission", platformCommission);
        result.put("expiresInSeconds", 1800);

        return result;
    }
    /**
     * Check QR payment specifically for the
     * post-delivery-OTP rider flow.
     *
     * After payment is detected:
     *   - Payment becomes COMPLETED
     *   - Carrier payout is triggered
     */
    @Transactional
    public Map<String, Object> checkQrPaymentStatusAfterDelivery(
            String qrId,
            Long deliveryRequestId) throws Exception {

        DeliveryRequest request =
                deliveryRequestRepository.findById(deliveryRequestId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Delivery request not found: "
                                                + deliveryRequestId
                                ));

        if (request.getStatus()
                != DeliveryRequest.RequestStatus.DELIVERED) {

            throw new IllegalStateException(
                    "Delivery OTP must be verified before checking final payment."
            );
        }

        Map<String, Object> result =
                checkQrPaymentStatus(
                        qrId,
                        deliveryRequestId
                );

        Boolean paid =
                (Boolean) result.get("paid");

        if (Boolean.TRUE.equals(paid)) {

            log.info(
                    "💰 Post-delivery payment confirmed | request={}",
                    deliveryRequestId
            );

            // This method is already idempotent:
            // COMPLETED / INITIATED / etc. will not be paid twice.
            triggerCarrierPayoutOnDelivery(
                    deliveryRequestId
            );
        }

        return result;
    }
    /**
     * Razorpay QR webhook handler.
     *
     * Razorpay's qr_code.credited event contains BOTH the payment entity and
     * the QR-code entity, so the QR id can be mapped directly to our Payment row.
     */
    @Transactional
    public void handleRazorpayQrWebhook(
            String rawBody,
            String signature,
            String eventId) throws Exception {

        if (rawBody == null || rawBody.isBlank()) {
            throw new IllegalArgumentException("Empty Razorpay webhook body");
        }

        if (signature == null || signature.isBlank()) {
            throw new SecurityException("Missing X-Razorpay-Signature");
        }

        if (razorpayWebhookSecret == null
                || razorpayWebhookSecret.isBlank()) {
            throw new IllegalStateException(
                    "razorpay.webhook.secret is not configured"
            );
        }

        // IMPORTANT: verify the exact raw request body.
        Utils.verifyWebhookSignature(
                rawBody,
                signature,
                razorpayWebhookSecret
        );

        JSONObject root = new JSONObject(rawBody);
        String event = root.optString("event", "");

        // We only need the QR payment event for this rider flow.
        if (!"qr_code.credited".equals(event)) {
            log.info("ℹ️ Ignoring Razorpay webhook event: {}", event);
            return;
        }

        JSONObject payload = root.getJSONObject("payload");

        JSONObject paymentEntity =
                payload.getJSONObject("payment")
                        .getJSONObject("entity");

        JSONObject qrEntity =
                payload.getJSONObject("qr_code")
                        .getJSONObject("entity");

        String qrId = qrEntity.getString("id");
        String razorpayPaymentId = paymentEntity.getString("id");
        String status = paymentEntity.optString("status", "");

        log.info(
                "🔔 Razorpay QR webhook | event={} | eventId={} | qrId={} | paymentId={} | status={}",
                event,
                eventId,
                qrId,
                razorpayPaymentId,
                status
        );

        if (!"captured".equals(status)
                && !"authorized".equals(status)) {
            return;
        }

        Optional<Payment> optionalPayment =
                paymentRepository.findByQrId(qrId);

        if (optionalPayment.isEmpty()) {
            log.warn(
                    "⚠️ QR webhook received before local Payment row exists | qrId={} paymentId={} — asking Razorpay to retry",
                    qrId,
                    razorpayPaymentId
            );
            throw new IllegalStateException(
                    "Local payment row not found for QR: " + qrId
            );
        }

        Payment payment = optionalPayment.get();

        // Idempotent: Razorpay can retry the same webhook.
        if (payment.getPaymentStatus()
                == Payment.PaymentStatus.COMPLETED) {

            log.info(
                    "♻️ Duplicate/already-completed QR webhook ignored | qrId={} paymentId={}",
                    qrId,
                    razorpayPaymentId
            );
            return;
        }

        payment.setPaymentStatus(
                Payment.PaymentStatus.COMPLETED
        );

        payment.setRazorpayPaymentId(
                razorpayPaymentId
        );

        payment.setPaymentCompletedAt(
                LocalDateTime.now()
        );

        paymentRepository.save(payment);

        log.info(
                "✅ QR WEBHOOK MARKED PAYMENT COMPLETED | request={} | qrId={} | paymentId={} | amount=₹{}",
                payment.getDeliveryRequest().getRequestId(),
                qrId,
                razorpayPaymentId,
                payment.getTotalAmount()
        );

        /*
         * Keep the webhook response fast. Razorpay requires webhook endpoints
         * to acknowledge quickly. The rider's next status request sees the
         * COMPLETED DB state and triggers the existing idempotent payout flow.
         */
    }

}
