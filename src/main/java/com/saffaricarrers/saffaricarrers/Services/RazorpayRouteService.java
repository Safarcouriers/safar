package com.saffaricarrers.saffaricarrers.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saffaricarrers.saffaricarrers.Configaration.RazorpayConfig;
import com.saffaricarrers.saffaricarrers.Entity.*;
import com.saffaricarrers.saffaricarrers.Entity.Payment;
import com.saffaricarrers.saffaricarrers.Repository.BankDetailsRepository;
import com.saffaricarrers.saffaricarrers.Repository.PaymentRepository;
import com.saffaricarrers.saffaricarrers.Repository.UserRepository;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayRouteService {

    private final BankDetailsRepository bankDetailsRepository;
    private final PaymentRepository paymentRepository;
    private final FirebaseNotificationService firebaseNotificationService;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final RazorpayConfig razorpayConfig;


    @Value("${admin.email}")
    private String adminEmail;

    private static final String BASE = "https://api.razorpay.com/v1";

    // =========================================================================
    // BASIC AUTH
    // =========================================================================



    // =========================================================================
    // 1. SETUP CARRIER — creates Contact + Fund Account (once per carrier)
    // =========================================================================

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void createLinkedAccount(User carrier, BankDetails bankDetails) {
        log.info("🏦 Setting up Razorpay Route for carrier: {}", carrier.getUserId());
        try {
            // Step 1 — Create Contact (if not already created)
            String contactId = bankDetails.getRazorpayContactId();
            if (contactId == null || contactId.isBlank()) {
                contactId = createContact(carrier);
                bankDetails.setRazorpayContactId(contactId.trim()); // ✅ trim before saving
                bankDetailsRepository.saveAndFlush(bankDetails);    // ✅ flush immediately
                log.info("✅ Contact created: {} for carrier: {}", contactId, carrier.getUserId());
            } else {
                contactId = contactId.trim();
            }

            // Step 2 — Create Fund Account only if not already set
            String existingFundId = bankDetails.getRazorpayFundAccountId();
            if (existingFundId == null || existingFundId.isBlank()) {
                String fundAccountId = createFundAccount(contactId, bankDetails);
                bankDetails.setRazorpayFundAccountId(fundAccountId.trim()); // ✅ trim before saving
                bankDetailsRepository.saveAndFlush(bankDetails);             // ✅ flush immediately
                log.info("✅ Fund account created: {} for carrier: {}", fundAccountId, carrier.getUserId());
            } else {
                log.info("ℹ️ Fund account already exists: {} — skipping creation", existingFundId.trim());
            }

        } catch (Exception e) {
            log.error("❌ Route setup failed for carrier {}: {}", carrier.getUserId(), e.getMessage());
            throw new RuntimeException("Razorpay Route setup failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // 2. TRANSFER TO CARRIER — fires at delivery OTP verification
    // =========================================================================

    @Transactional
    public void transferToCarrier(Payment payment) {
        DeliveryRequest request = payment.getDeliveryRequest();
        User carrier            = request.getCarrier();
        CarrierProfile profile  = carrier.getCarrierProfile();

        log.info("💸 Route transfer | Carrier: {} | ₹{} | Payment: {}",
                carrier.getUserId(), payment.getCarrierAmount(), payment.getPaymentId());

        // Guard: already processed
        if (payment.getCarrierTransferStatus() == Payment.TransferStatus.COMPLETED
                || payment.getCarrierTransferStatus() == Payment.TransferStatus.INITIATED) {
            log.warn("⚠️ Transfer already {} — skipping", payment.getCarrierTransferStatus());
            return;
        }

        // ✅ Always reload bank details fresh from DB — never trust stale in-memory object
        BankDetails bankDetails = profile != null
                ? bankDetailsRepository.findByCarrierProfile(profile).orElse(null)
                : null;

        // Guard: no verified bank details
        if (bankDetails == null || !Boolean.TRUE.equals(bankDetails.getIsVerified())) {
            failTransfer(payment, carrier, "Carrier has no verified bank details");
            return;
        }

        // Guard: fund account missing — setup on-demand
        String fundAccountId = bankDetails.getRazorpayFundAccountId();
        if (fundAccountId == null || fundAccountId.isBlank()) {
            log.info("🔧 Fund account missing for carrier: {} — setting up now", carrier.getUserId());
            try {
                createLinkedAccount(carrier, bankDetails);
                // ✅ Reload again after setup
                bankDetails = bankDetailsRepository.findByCarrierProfile(profile)
                        .orElse(bankDetails);
                fundAccountId = bankDetails.getRazorpayFundAccountId();
            } catch (Exception e) {
                failTransfer(payment, carrier, "Fund account setup failed: " + e.getMessage());
                return;
            }
        }

        // ✅ Always trim before using
        fundAccountId = fundAccountId != null ? fundAccountId.trim() : null;

        // ✅ Validate fund account ID format (fa_ + 15 chars = 18 total)
// ✅ FIXED
        if (fundAccountId == null || !fundAccountId.startsWith("fa_") || fundAccountId.length() != 17) {            failTransfer(payment, carrier,
                    "Invalid fund account ID: '" + fundAccountId
                            + "' (length=" + (fundAccountId != null ? fundAccountId.length() : 0) + "). "
                            + "Expected fa_XXXXXXXXXXXXXXX (18 chars). Please re-submit bank details.");
            return;
        }

        try {
            int amountInPaise = (int)(payment.getCarrierAmount() * 100);
            // int amountInPaise = 100; // ← uncomment for ₹1 testing

            Map<String, Object> transferPayload = new HashMap<>();
            transferPayload.put("account",  fundAccountId);
            transferPayload.put("amount",   amountInPaise);
            transferPayload.put("currency", "INR");
            transferPayload.put("source",   "balance");
            transferPayload.put("notes", Map.of(
                    "delivery_request_id", String.valueOf(request.getRequestId()),
                    "carrier_id",          carrier.getUserId(),
                    "carrier_name",        carrier.getFullName()
            ));

            log.info("📤 Sending transfer | fundAccountId: {} | amount (paise): {}",
                    fundAccountId, amountInPaise);

            Map<?, ?> response = callRazorpay("POST", "/transfers", transferPayload);

            String transferId = (String) response.get("id");

            payment.setRazorpayPayoutId(transferId);
            payment.setCarrierTransferStatus(Payment.TransferStatus.INITIATED);
            payment.setCarrierTransferInitiatedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            log.info("✅ Transfer initiated: {} | ₹{} → {} | Carrier: {}",
                    transferId, payment.getCarrierAmount(), fundAccountId, carrier.getUserId());

            sendFcm(carrier.getFcmToken(),
                    "💸 Payout Sent!",
                    "₹" + String.format("%.2f", payment.getCarrierAmount())
                            + " is on its way to your bank account.");

        } catch (Exception e) {
            log.error("❌ Transfer failed for carrier {}: {}", carrier.getUserId(), e.getMessage());
            failTransfer(payment, carrier, "Transfer API failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // 3. WEBHOOK — transfer.processed / transfer.failed
    // =========================================================================

    @Transactional
    public void handleTransferWebhook(String transferId, String event) {
        Payment payment = paymentRepository.findByRazorpayPayoutId(transferId).orElse(null);
        if (payment == null) {
            log.warn("⚠️ Webhook for unknown transfer: {}", transferId);
            return;
        }

        User carrier           = payment.getDeliveryRequest().getCarrier();
        CarrierProfile profile = carrier.getCarrierProfile();

        switch (event) {
            case "transfer.processed" -> {
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
                sendFcm(carrier.getFcmToken(),
                        "✅ Money Received!",
                        "₹" + String.format("%.2f", payment.getCarrierAmount())
                                + " confirmed in your bank account.");
            }

            case "transfer.failed" -> {
                payment.setCarrierTransferStatus(Payment.TransferStatus.FAILED);
                payment.setTransferFailureReason("Razorpay: transfer.failed event");
                paymentRepository.save(payment);
                log.error("❌ Transfer FAILED: {} | Carrier: {}", transferId, carrier.getUserId());
                alertAdmin(payment, carrier, "Razorpay returned transfer.failed");
                sendFcm(carrier.getFcmToken(),
                        "⚠️ Payout Issue",
                        "Issue with your payout. Our team will resolve within 24 hours.");
            }

            default -> log.info("ℹ️ Unhandled Razorpay event: {}", event);
        }
    }

    // =========================================================================
    // PRIVATE — API HELPERS
    // =========================================================================

    private String createContact(User carrier) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name",         carrier.getFullName());
        payload.put("email",        carrier.getEmail() != null && !carrier.getEmail().isBlank()
                ? carrier.getEmail() : "noreply@saffari.com");
        payload.put("contact",      carrier.getMobile());
        payload.put("type",         "vendor");
        payload.put("reference_id", carrier.getUserId());

        Map<?, ?> response = callRazorpay("POST", "/contacts", payload);
        String contactId = ((String) response.get("id")).trim(); // ✅ trim immediately

        if (!contactId.startsWith("cont_")) {
            throw new RuntimeException("Unexpected contact ID from Razorpay: " + contactId);
        }

        log.info("📋 Contact created: {}", contactId);
        return contactId;
    }

    private String createFundAccount(String contactId, BankDetails bankDetails) throws Exception {
        String accountNumber = bankDetails.getAccountNumber().trim();
        String ifscCode      = bankDetails.getIfscCode().trim().toUpperCase();
        String holderName    = bankDetails.getAccountHolderName().trim();

        Map<String, Object> bankAccount = new HashMap<>();
        bankAccount.put("name",           holderName);
        bankAccount.put("ifsc",           ifscCode);
        bankAccount.put("account_number", accountNumber);

        Map<String, Object> payload = new HashMap<>();
        payload.put("contact_id",   contactId);
        payload.put("account_type", "bank_account");
        payload.put("bank_account", bankAccount);

        log.info("🏦 Creating fund account | contact: {} | IFSC: {} | account: {}",
                contactId, ifscCode, maskAccount(accountNumber));

        Map<?, ?> response = callRazorpay("POST", "/fund_accounts", payload);
        String fundAccountId = ((String) response.get("id")).trim(); // ✅ trim immediately

        // ✅ Validate: fa_ prefix + exactly 18 chars total
        // ✅ FIXED — Razorpay fa_ IDs are 17 chars, not 18
        if (!fundAccountId.startsWith("fa_") || fundAccountId.length() != 17) {
            throw new RuntimeException(
                    "Unexpected fund account ID from Razorpay: '" + fundAccountId
                            + "' (length=" + fundAccountId.length() + "). Expected fa_XXXXXXXXXXXXXX (17 chars).");
        }

        log.info("✅ Fund account ID: {} (length={})", fundAccountId, fundAccountId.length());
        return fundAccountId;
    }

    private Map<?, ?> callRazorpay(String method, String path,
                                   Map<String, ?> payload) throws Exception {
        String body = objectMapper.writeValueAsString(payload);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("Authorization", basicAuth())
                .header("Content-Type",  "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> res = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString());

        log.info("📨 Razorpay {} {} → HTTP {}", method, path, res.statusCode());
        log.debug("📨 Response body: {}", res.body());

        Map<?, ?> responseBody = objectMapper.readValue(res.body(), Map.class);

        if (res.statusCode() >= 400 || responseBody.containsKey("error")) {
            Map<?, ?> error = (Map<?, ?>) responseBody.get("error");
            String description = error != null
                    ? (String) error.get("description")
                    : "HTTP " + res.statusCode() + " | " + res.body();
            throw new RuntimeException("Razorpay: " + description);
        }

        return responseBody;
    }

    // =========================================================================
    // PRIVATE — FAILURE HANDLING
    // =========================================================================

    private void failTransfer(Payment payment, User carrier, String reason) {
        payment.setCarrierTransferStatus(Payment.TransferStatus.FAILED);
        payment.setTransferFailureReason(reason);
        paymentRepository.save(payment);
        log.error("❌ Transfer failed for carrier {}: {}", carrier.getUserId(), reason);
        alertAdmin(payment, carrier, reason);
        sendFcm(carrier.getFcmToken(),
                "⚠️ Payout Pending",
                "Your earnings are being processed. Our team will transfer within 24 hours.");
    }

    private void alertAdmin(Payment payment, User carrier, String reason) {
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
                </table>
                <br><b>Steps to resolve:</b>
                <ol>
                  <li>Razorpay Dashboard → Route → Manual Transfer → ₹%.2f to carrier</li>
                  <li>Update DB:<br>
                    <code>UPDATE payment SET carrier_transfer_status='COMPLETED',
                    carrier_transfer_completed_at=NOW() WHERE payment_id=%d;</code>
                  </li>
                </ol>
                """,
                    carrier.getFullName(), carrier.getUserId(), carrier.getMobile(),
                    payment.getCarrierAmount(), payment.getPaymentId(),
                    payment.getDeliveryRequest().getRequestId(), reason,
                    payment.getCarrierAmount(), payment.getPaymentId()
            ), true);
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("❌ Admin email failed: {}", e.getMessage());
        }
    }

    private void sendFcm(String token, String title, String body) {
        if (token == null || token.isBlank()) return;
        try {
            firebaseNotificationService.sendNotification(token, title, body);
        } catch (Exception e) {
            log.error("❌ FCM failed: {}", e.getMessage());
        }
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
    private String basicAuth() {
        String creds = razorpayConfig.getSafarcarryKeyId()
                + ":"
                + razorpayConfig.getSafarcarryKeySecret();
        return "Basic " + Base64.getEncoder().encodeToString(
                creds.getBytes(StandardCharsets.UTF_8));
    }
}