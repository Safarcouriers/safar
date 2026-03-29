package com.saffaricarrers.saffaricarrers.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saffaricarrers.saffaricarrers.Entity.*;
import com.saffaricarrers.saffaricarrers.Entity.Payment;
import com.saffaricarrers.saffaricarrers.Repository.BankDetailsRepository;
import com.saffaricarrers.saffaricarrers.Repository.PaymentRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * CARRIER PAYOUT SERVICE — Cashfree Payouts API
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * FLOW (100% automatic — no manual work after setup):
 *   1. Sender pays ₹100 → lands in YOUR account via Razorpay Checkout
 *   2. Carrier enters delivery OTP in app
 *   3. THIS SERVICE fires automatically → ₹85 sent to carrier UPI/bank
 *   4. ₹15 stays in your account (platform commission)
 *   5. Cashfree webhook confirms → DB updated, carrier notified
 *
 * PAYOUT STRATEGY:
 *   → UPI first  (2–5 seconds, instant)
 *   → IMPS fallback (under 2 minutes)
 *   → Both fail → admin gets email with manual transfer steps
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * SANDBOX TESTING (do this now):
 * ═══════════════════════════════════════════════════════════════════════════
 *  1. Cashfree dashboard → Payouts → "Try Test Environment"
 *  2. Get sandbox App ID + Secret Key
 *  3. Add to application.properties (see below)
 *  4. Test UPI IDs:
 *       success@cashfree  → payout succeeds ✅
 *       failure@cashfree  → payout fails ❌ (tests admin email alert)
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * GOING LIVE (when Cashfree activates your account — zero code changes):
 * ═══════════════════════════════════════════════════════════════════════════
 *  1. Replace sandbox keys with live keys in application.properties
 *  2. Set cashfree.sandbox=false
 *  3. Uncomment production amount line in attemptPayout() below
 *  4. Add webhook in Cashfree dashboard → Payouts → Settings → Webhooks
 *     URL: https://yourdomain.com/api/webhooks/payout
 *  Done — nothing else changes.
 *
 * application.properties:
 *   cashfree.app.id=YOUR_APP_ID
 *   cashfree.secret.key=YOUR_SECRET_KEY
 *   cashfree.sandbox=true            ← set false when going live
 *   cashfree.webhook.secret=YOUR_WEBHOOK_SECRET
 *   admin.email=your@email.com
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayPayoutService {

    private final PaymentRepository paymentRepository;
    private final BankDetailsRepository bankDetailsRepository;
    private final FirebaseNotificationService firebaseNotificationService;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;

    @Value("${cashfree.app.id}")
    private String cashfreeAppId;

    @Value("${cashfree.secret.key}")
    private String cashfreeSecretKey;

    @Value("${cashfree.sandbox:true}")
    private boolean sandbox;

    @Value("${admin.email}")
    private String adminEmail;

    // Sandbox vs Production URLs
    private static final String CASHFREE_SANDBOX    = "https://payout-gamma.cashfree.com";
    private static final String CASHFREE_PRODUCTION = "https://payout-api.cashfree.com";

    private String baseUrl() {
        return sandbox ? CASHFREE_SANDBOX : CASHFREE_PRODUCTION;
    }

    // =========================================================================
    // AUTH — Cashfree uses short-lived Bearer tokens (expires every 30 min)
    // =========================================================================

    private String getAuthToken() throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("appId", cashfreeAppId);
        payload.put("secretKey", cashfreeSecretKey);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/payout/v1/authorize"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> res = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString());
        Map<?, ?> body = objectMapper.readValue(res.body(), Map.class);

        if (!"SUCCESS".equals(body.get("status"))) {
            throw new RuntimeException("Cashfree auth failed: " + body.get("message"));
        }

        Map<?, ?> data = (Map<?, ?>) body.get("data");
        return (String) data.get("token");
    }

    // =========================================================================
    // 1. REGISTER BENEFICIARY
    //    Called once when admin approves carrier bank details.
    //    Registers UPI + bank with Cashfree so payouts can be sent to them.
    // =========================================================================

    @Transactional
    public void createFundAccountForCarrier(User carrier, BankDetails bankDetails) {
        log.info("🏦 Registering Cashfree beneficiary for carrier: {}", carrier.getUserId());
        try {
            String token = getAuthToken();
            // Clean userId for use as beneId (alphanumeric only, max 20 chars)
            String cleanId = carrier.getUserId().replaceAll("[^a-zA-Z0-9]", "")
                    .substring(0, Math.min(carrier.getUserId().replaceAll("[^a-zA-Z0-9]", "").length(), 14));

            // ── Register UPI beneficiary (if carrier provided UPI ID) ─────────
            if (bankDetails.getUpiId() != null && !bankDetails.getUpiId().isBlank()) {
                try {
                    String upiBeneId = "upi_" + cleanId;
                    Map<String, Object> upiPayload = new HashMap<>();
                    upiPayload.put("beneId",   upiBeneId);
                    upiPayload.put("name",     carrier.getFullName());
                    upiPayload.put("email",    carrier.getEmail() != null ? carrier.getEmail() : "noreply@saffari.com");
                    upiPayload.put("phone",    carrier.getMobile());
                    upiPayload.put("vpa",      bankDetails.getUpiId().trim()); // e.g. "9876543210@upi"
                    upiPayload.put("address1", "India");

                    callCashfree("POST", "/payout/v1/addBeneficiary", upiPayload, token);
                    bankDetails.setRazorpayUpiVpaFundAccountId(upiBeneId);
                    log.info("✅ UPI beneficiary registered: {}", upiBeneId);
                } catch (Exception e) {
                    log.warn("⚠️ UPI beneficiary failed (IMPS fallback will be used): {}", e.getMessage());
                }
            }

            // ── Register Bank (IMPS) beneficiary — always ─────────────────────
            String bankBeneId = "bank_" + cleanId;
            Map<String, Object> bankPayload = new HashMap<>();
            bankPayload.put("beneId",       bankBeneId);
            bankPayload.put("name",         carrier.getFullName());
            bankPayload.put("email",        carrier.getEmail() != null ? carrier.getEmail() : "noreply@saffari.com");
            bankPayload.put("phone",        carrier.getMobile());
            bankPayload.put("bankAccount",  bankDetails.getAccountNumber());
            bankPayload.put("ifsc",         bankDetails.getIfscCode());
            bankPayload.put("address1",     "India");

            callCashfree("POST", "/payout/v1/addBeneficiary", bankPayload, token);
            bankDetails.setRazorpayFundAccountId(bankBeneId);
            log.info("✅ Bank beneficiary registered: {}", bankBeneId);

            bankDetailsRepository.save(bankDetails);
            log.info("✅ Cashfree setup done for carrier: {} | UPI: {} | Bank: ✅",
                    carrier.getUserId(),
                    bankDetails.getRazorpayUpiVpaFundAccountId() != null ? "✅" : "not provided");

        } catch (Exception e) {
            // Non-fatal — will retry on first payout attempt
            log.error("❌ Cashfree setup failed for carrier {}: {}", carrier.getUserId(), e.getMessage());
        }
    }

    // =========================================================================
    // 2. INITIATE PAYOUT — fires automatically at delivery OTP verification
    //    YOU NEVER CALL THIS MANUALLY.
    //
    //    Call chain:
    //      verifyDeliveryOtp()
    //        → PaymentService.triggerCarrierPayoutOnDelivery()
    //          → THIS METHOD
    // =========================================================================

    @Transactional
    public void initiateCarrierPayout(Payment payment) {
        DeliveryRequest request = payment.getDeliveryRequest();
        User carrier            = request.getCarrier();
        CarrierProfile profile  = carrier.getCarrierProfile();
        BankDetails bankDetails = profile != null ? profile.getBankDetails() : null;

        log.info("💸 Auto-payout | Carrier: {} | ₹{} | Payment: {}",
                carrier.getUserId(), payment.getCarrierAmount(), payment.getPaymentId());

        // ── Already processed ─────────────────────────────────────────────────
        if (payment.getCarrierTransferStatus() == Payment.TransferStatus.COMPLETED
                || payment.getCarrierTransferStatus() == Payment.TransferStatus.INITIATED) {
            log.warn("⚠️ Payout already {} — skipping", payment.getCarrierTransferStatus());
            return;
        }

        // ── No verified bank details ──────────────────────────────────────────
        if (bankDetails == null || !Boolean.TRUE.equals(bankDetails.getIsVerified())) {
            failPayout(payment, carrier, "Carrier has no verified bank details");
            return;
        }

        // ── Register beneficiary on-demand if not done yet ────────────────────
        if (bankDetails.getRazorpayFundAccountId() == null) {
            log.info("🔧 Beneficiary not set up for carrier: {} — registering now", carrier.getUserId());
            try {
                createFundAccountForCarrier(carrier, bankDetails);
                bankDetails = bankDetailsRepository.findByCarrierProfile(profile).orElse(bankDetails);
            } catch (Exception e) {
                failPayout(payment, carrier, "Beneficiary setup failed: " + e.getMessage());
                return;
            }
        }

        // ── Try UPI first (instant) ───────────────────────────────────────────
        if (bankDetails.getRazorpayUpiVpaFundAccountId() != null) {
            log.info("🔄 Trying UPI → {}", bankDetails.getUpiId());
            if (attemptPayout(payment, carrier, profile,
                    bankDetails.getRazorpayUpiVpaFundAccountId(), "UPI",
                    bankDetails.getUpiId())) return;
            log.warn("⚠️ UPI failed — trying IMPS");
        }

        // ── IMPS fallback ─────────────────────────────────────────────────────
        if (bankDetails.getRazorpayFundAccountId() != null) {
            log.info("🔄 Trying IMPS → bank account");
            String last4 = bankDetails.getAccountNumber()
                    .substring(Math.max(0, bankDetails.getAccountNumber().length() - 4));
            if (attemptPayout(payment, carrier, profile,
                    bankDetails.getRazorpayFundAccountId(), "IMPS",
                    (bankDetails.getBankName() != null ? bankDetails.getBankName() : "Bank") + " ••••" + last4)) return;
        }

        // ── Both failed ───────────────────────────────────────────────────────
        failPayout(payment, carrier, "Both UPI and IMPS failed. Manual transfer needed.");
    }

    private boolean attemptPayout(Payment payment, User carrier, CarrierProfile profile,
                                  String beneId, String mode, String destination) {
        try {
            String token = getAuthToken();

            // ─────────────────────────────────────────────────────────────────
            // WHEN GOING LIVE — make these two changes:
            //   1. Comment out the TEST line
            //   2. Uncomment the PRODUCTION line
            //   3. Set cashfree.sandbox=false in application.properties
            // ─────────────────────────────────────────────────────────────────
            // PRODUCTION → uncomment this:
            // double amount = payment.getCarrierAmount();
            // TEST → remove this when going live:
            double amount = 1.0;

            String transferId = "TXN" + payment.getPaymentId() + "_" + System.currentTimeMillis();

            Map<String, Object> payload = new HashMap<>();
            payload.put("beneId",       beneId);
            payload.put("amount",       String.format("%.2f", amount));
            payload.put("transferId",   transferId);
            payload.put("transferMode", mode);         // "UPI" or "IMPS"
            payload.put("remarks",      "Trip #" + payment.getDeliveryRequest().getRequestId()
                    + " | " + carrier.getFullName());

            Map<?, ?> res = callCashfree("POST", "/payout/v1/requestTransfer", payload, token);

            String status = (String) res.get("status");
            if (!"SUCCESS".equals(status)) {
                log.error("❌ Cashfree {} returned status {}: {}", mode, status, res.get("message"));
                return false;
            }

            // Store Cashfree's transfer ID for webhook matching
            Map<?, ?> data = res.get("data") instanceof Map ? (Map<?, ?>) res.get("data") : new HashMap<>();
            String cfTransferId = data.containsKey("transferId")
                    ? (String) data.get("transferId") : transferId;

            payment.setRazorpayPayoutId(cfTransferId);
            payment.setCarrierTransferInitiatedAt(LocalDateTime.now());
            payment.setCarrierTransferStatus(Payment.TransferStatus.INITIATED);
            paymentRepository.save(payment);

            log.info("✅ {} payout initiated | ID: {} | ₹{} → {} | Carrier: {}",
                    mode, cfTransferId, amount, destination, carrier.getUserId());

            String eta = "UPI".equals(mode) ? "in seconds" : "within 2 minutes";
            sendFcm(carrier.getFcmToken(), "💸 Payout Sent",
                    "₹" + String.format("%.2f", payment.getCarrierAmount())
                            + " is on its way via " + mode + " — expect it " + eta + ".");
            return true;

        } catch (Exception e) {
            log.error("❌ {} payout failed for carrier {}: {}", mode, carrier.getUserId(), e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // 3. WEBHOOK — Cashfree calls this when transfer status changes
    //    Called by PayoutWebhookController → /api/webhooks/payout
    // =========================================================================

    @Transactional
    public void handlePayoutWebhook(String transferId, String event) {
        Payment payment = paymentRepository.findByRazorpayPayoutId(transferId).orElse(null);
        if (payment == null) {
            log.warn("⚠️ Webhook for unknown transfer: {}", transferId);
            return;
        }

        User carrier           = payment.getDeliveryRequest().getCarrier();
        CarrierProfile profile = carrier.getCarrierProfile();

        switch (event) {
            case "TRANSFER_SUCCESS" -> {
                if (payment.getCarrierTransferStatus() == Payment.TransferStatus.COMPLETED) return;
                payment.setCarrierTransferStatus(Payment.TransferStatus.COMPLETED);
                payment.setCarrierTransferCompletedAt(LocalDateTime.now());
                if (profile != null) {
                    profile.setTotalEarnings(profile.getTotalEarnings()
                            .add(BigDecimal.valueOf(payment.getCarrierAmount())));
                }
                paymentRepository.save(payment);
                log.info("✅ Transfer CONFIRMED: {} | ₹{} | Carrier: {}",
                        transferId, payment.getCarrierAmount(), carrier.getUserId());
                sendFcm(carrier.getFcmToken(), "✅ Money Received!",
                        "₹" + String.format("%.2f", payment.getCarrierAmount())
                                + " confirmed in your account.");
            }
            case "TRANSFER_FAILED" -> {
                payment.setCarrierTransferStatus(Payment.TransferStatus.FAILED);
                payment.setTransferFailureReason("Cashfree TRANSFER_FAILED");
                paymentRepository.save(payment);
                log.error("❌ Transfer FAILED: {} | Carrier: {}", transferId, carrier.getUserId());
                alertAdminManualPayout(payment, carrier, "Cashfree returned TRANSFER_FAILED");
                sendFcm(carrier.getFcmToken(), "⚠️ Payout Issue",
                        "Issue with your payout. Our team will resolve within 24 hours.");
            }
            case "TRANSFER_REVERSED" -> {
                payment.setCarrierTransferStatus(Payment.TransferStatus.FAILED);
                payment.setTransferFailureReason("Transfer reversed by bank");
                paymentRepository.save(payment);
                log.error("❌ Transfer REVERSED: {} | Carrier: {}", transferId, carrier.getUserId());
                alertAdminManualPayout(payment, carrier, "Transfer reversed by bank");
                sendFcm(carrier.getFcmToken(), "⚠️ Payout Reversed",
                        "Your payout was returned. Our team will resolve within 24 hours.");
            }
            default -> log.info("ℹ️ Unhandled Cashfree event: {}", event);
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private void failPayout(Payment payment, User carrier, String reason) {
        payment.setCarrierTransferStatus(Payment.TransferStatus.FAILED);
        payment.setTransferFailureReason(reason);
        paymentRepository.save(payment);
        log.error("❌ Payout failed for carrier {}: {}", carrier.getUserId(), reason);
        alertAdminManualPayout(payment, carrier, reason);
        sendFcm(carrier.getFcmToken(), "⚠️ Payout Pending",
                "Your earnings are being processed. Our team will transfer within 24 hours.");
    }

    private Map<?, ?> callCashfree(String method, String path,
                                   Map<String, Object> payload,
                                   String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> res = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString());
        Map<?, ?> body = objectMapper.readValue(res.body(), Map.class);

        if ("ERROR".equals(body.get("status"))) {
            throw new RuntimeException("Cashfree: " + body.get("message"));
        }
        return body;
    }

    private void alertAdminManualPayout(Payment payment, User carrier, String reason) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true);
            h.setTo(adminEmail);
            h.setSubject("[ACTION REQUIRED] Manual Payout ₹"
                    + String.format("%.2f", payment.getCarrierAmount())
                    + " → " + carrier.getFullName());
            h.setText(String.format("""
                <h3>⚠️ Automatic payout failed — please transfer manually</h3>
                <table border="1" cellpadding="8" style="border-collapse:collapse">
                  <tr><td><b>Carrier</b></td><td>%s (ID: %s)</td></tr>
                  <tr><td><b>Phone</b></td><td>%s</td></tr>
                  <tr><td><b>Amount</b></td><td><b style="color:red">₹%.2f</b></td></tr>
                  <tr><td><b>DB Payment ID</b></td><td>%d</td></tr>
                  <tr><td><b>Delivery Request</b></td><td>%d</td></tr>
                  <tr><td><b>Reason</b></td><td>%s</td></tr>
                  <tr><td><b>Time</b></td><td>%s</td></tr>
                </table>
                <br><b>Steps to resolve:</b>
                <ol>
                  <li>Cashfree dashboard → Payouts → Create Manual Transfer → send ₹%.2f</li>
                  <li>Then run in DB:<br>
                    <code>UPDATE payment SET carrier_transfer_status='COMPLETED',
                    carrier_transfer_completed_at=NOW() WHERE payment_id=%d;</code>
                  </li>
                </ol>
                """,
                    carrier.getFullName(), carrier.getUserId(), carrier.getMobile(),
                    payment.getCarrierAmount(), payment.getPaymentId(),
                    payment.getDeliveryRequest().getRequestId(), reason, LocalDateTime.now(),
                    payment.getCarrierAmount(), payment.getPaymentId()
            ), true);
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("❌ Admin alert email failed: {}", e.getMessage());
        }
    }

    private void sendFcm(String token, String title, String body) {
        if (token == null || token.isBlank()) return;
        try { firebaseNotificationService.sendNotification(token, title, body); }
        catch (Exception e) { log.error("❌ FCM failed: {}", e.getMessage()); }
    }
}