package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Dtos.BankDetailsDto;
import com.saffaricarrers.saffaricarrers.Entity.BankDetails;
import com.saffaricarrers.saffaricarrers.Entity.CarrierProfile;
import com.saffaricarrers.saffaricarrers.Entity.Notification;
import com.saffaricarrers.saffaricarrers.Entity.User;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import com.saffaricarrers.saffaricarrers.Repository.BankDetailsRepository;
import com.saffaricarrers.saffaricarrers.Repository.CarrierProfileRepository;
import com.saffaricarrers.saffaricarrers.Repository.NotificationRepository;
import com.saffaricarrers.saffaricarrers.Repository.UserRepository;
import com.saffaricarrers.saffaricarrers.Services.RazorpayPayoutService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class Bankdetailsservice {

    private final BankDetailsRepository bankDetailsRepository;
    private final CarrierProfileRepository carrierProfileRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final FirebaseNotificationService firebaseNotificationService;
    private final RazorpayPayoutService razorpayPayoutService;

    @Value("${admin.email}")
    private String adminEmail;

    // =====================================================================
    // CARRIER — SUBMIT BANK DETAILS (first time)
    // =====================================================================

    @Transactional
    @CacheEvict(value = {"profiles", "users"}, allEntries = true) // ← ADD THIS
    public BankDetailsDto.Response submitBankDetails(String userId, BankDetailsDto.SubmitRequest req) {
        CarrierProfile profile = getCarrierProfileByUserId(userId);
        User carrier = profile.getUser();

        // ✅ Account number must match confirm field
        if (req.getAccountNumber() == null || !req.getAccountNumber().equals(req.getConfirmAccountNumber())) {
            throw new IllegalArgumentException("Account numbers do not match. Please re-enter carefully.");
        }

        // ✅ Basic format validations
        validateAccountNumber(req.getAccountNumber());
        validateIfscCode(req.getIfscCode());

        // ✅ Duplicate account check across OTHER carriers
        boolean accountTaken = bankDetailsRepository
                .existsByAccountNumberAndCarrierProfileNot(req.getAccountNumber().trim(), profile);
        if (accountTaken) {
            throw new IllegalArgumentException(
                    "This account number is already registered with another carrier.");
        }

        // ✅ If existing bank details exist, UPDATE them (re-submission resets verification)
        BankDetails bankDetails = bankDetailsRepository.findByCarrierProfile(profile)
                .orElse(new BankDetails());

        boolean isResubmission = bankDetails.getBankDetailsId() != null;

        bankDetails.setCarrierProfile(profile);
        bankDetails.setAccountHolderName(req.getAccountHolderName().trim().toUpperCase());
        bankDetails.setAccountNumber(req.getAccountNumber().trim());
        bankDetails.setIfscCode(req.getIfscCode().trim().toUpperCase());
        bankDetails.setBankName(req.getBankName() != null ? req.getBankName().trim() : "");
        bankDetails.setBranchName(req.getBranchName() != null ? req.getBranchName().trim() : null);
        bankDetails.setAccountType(parseAccountType(req.getAccountType()));
        bankDetails.setUpiId(req.getUpiId() != null ? req.getUpiId().trim() : null);

        // ✅ Always reset verification on any submission/re-submission
        bankDetails.setIsVerified(false);
        bankDetails.setVerificationStatus(BankDetails.VerificationStatus.PENDING);
        bankDetails.setVerificationNote(null);
        bankDetails.setVerifiedAt(null);
        bankDetails.setVerifiedBy(null);

        BankDetails saved = bankDetailsRepository.save(bankDetails);

        // ✅ Link to carrier profile
        profile.setBankDetails(saved);
        // Keep carrier INACTIVE until admin verifies
        profile.setIsVerified(false);
        profile.setStatus(CarrierProfile.CarrierStatus.INACTIVE);
        carrierProfileRepository.save(profile);

        log.info("✅ Bank details {} by carrier: {} | Account: {} | Bank: {}",
                isResubmission ? "re-submitted" : "submitted",
                userId, saved.getMaskedAccountNumber(), saved.getBankName());

        // Notify admin
        notifyAdminNewBankSubmission(carrier, saved, isResubmission);

        // In-app notification
        saveNotification(
                carrier,
                isResubmission ? "Bank Details Updated 🔄" : "Bank Details Submitted ✅",
                "Your bank details have been submitted for review. We'll verify them within 1–2 business days.",
                Notification.NotificationType.PAYMENT_RECEIVED
        );

        return mapToResponse(saved);
    }

    // =====================================================================
    // CARRIER — GET OWN BANK DETAILS
    // =====================================================================

    @Transactional(readOnly = true)
    public BankDetailsDto.Response getBankDetails(String userId) {
        CarrierProfile profile = getCarrierProfileByUserId(userId);
        BankDetails bankDetails = bankDetailsRepository.findByCarrierProfile(profile).orElse(null);
        if (bankDetails == null) return null;
        return mapToResponse(bankDetails);
    }

    // =====================================================================
    // CARRIER — BANK STATUS SUMMARY
    // =====================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> getBankStatus(String userId) {
        CarrierProfile profile = getCarrierProfileByUserId(userId);
        BankDetails bd = bankDetailsRepository.findByCarrierProfile(profile).orElse(null);

        Map<String, Object> status = new HashMap<>();
        status.put("hasBankDetails", bd != null);
        status.put("isVerified", bd != null && Boolean.TRUE.equals(bd.getIsVerified()));
        status.put("verificationStatus", bd != null ? bd.getVerificationStatus().toString() : "NOT_SUBMITTED");
        status.put("maskedAccountNumber", bd != null ? bd.getMaskedAccountNumber() : null);
        status.put("bankName", bd != null ? bd.getBankName() : null);
        status.put("accountType", bd != null ? bd.getAccountType().toString() : null);
        status.put("verificationNote", bd != null ? bd.getVerificationNote() : null);
        status.put("canReceivePayouts", bd != null && Boolean.TRUE.equals(bd.getIsVerified()));

        String msg = "Not submitted";
        if (bd != null) {
            switch (bd.getVerificationStatus()) {
                case PENDING:      msg = "Submitted — awaiting review (1–2 business days)"; break;
                case UNDER_REVIEW: msg = "Under review by our team"; break;
                case VERIFIED:     msg = "Verified ✅ — you can now receive payouts"; break;
                case REJECTED:     msg = "Rejected: " + (bd.getVerificationNote() != null ? bd.getVerificationNote() : "See details"); break;
            }
        }
        status.put("statusMessage", msg);
        return status;
    }

    // =====================================================================
    // IFSC LOOKUP — Auto-fill bank name and branch
    // =====================================================================

    /**
     * Razorpay hosts a free public IFSC API: https://ifsc.razorpay.com/{IFSC}
     * No auth required.
     */
    @Transactional(readOnly = true)
    public BankDetailsDto.IfscInfo lookupIfsc(String ifscCode) {
        if (ifscCode == null || ifscCode.length() != 11) {
            return BankDetailsDto.IfscInfo.builder()
                    .ifscCode(ifscCode)
                    .found(false)
                    .errorMessage("IFSC must be exactly 11 characters")
                    .build();
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ifsc.razorpay.com/" + ifscCode.toUpperCase()))
                    .GET()
                    .build();

            HttpResponse<String> httpResponse = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() == 200) {
                // Simple JSON parse without Jackson dependency on reactive
                Map<?, ?> result = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(httpResponse.body(), Map.class);
                return BankDetailsDto.IfscInfo.builder()
                        .ifscCode(ifscCode.toUpperCase())
                        .bankName(getString(result, "BANK"))
                        .branchName(getString(result, "BRANCH"))
                        .city(getString(result, "CITY"))
                        .state(getString(result, "STATE"))
                        .address(getString(result, "ADDRESS"))
                        .found(true)
                        .build();
            }
        } catch (Exception e) {
            log.warn("⚠️ IFSC lookup failed for {}: {}", ifscCode, e.getMessage());
        }

        return BankDetailsDto.IfscInfo.builder()
                .ifscCode(ifscCode)
                .found(false)
                .errorMessage("IFSC not found. Please enter bank name manually.")
                .build();
    }

    // =====================================================================
    // ADMIN — LIST ALL PENDING SUBMISSIONS
    // =====================================================================

    @Transactional(readOnly = true)
    public List<BankDetailsDto.AdminResponse> getPendingVerifications() {
        return bankDetailsRepository
                .findByVerificationStatusOrderByCreatedAtAsc(BankDetails.VerificationStatus.PENDING)
                .stream()
                .map(this::mapToAdminResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BankDetailsDto.AdminResponse> getAllBankDetails() {
        return bankDetailsRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToAdminResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BankDetailsDto.AdminResponse getAdminBankDetails(Long bankId) {
        BankDetails bd = bankDetailsRepository.findById(bankId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank details not found: " + bankId));
        return mapToAdminResponse(bd);
    }

    // =====================================================================
    // ADMIN — VERIFY OR REJECT
    // =====================================================================

    @Transactional
    @CacheEvict(value = {"profiles", "users"}, allEntries = true) // ← ADD THIS
    public BankDetailsDto.Response adminVerifyBankDetails(Long bankId, String adminUserId,
                                                          BankDetailsDto.VerifyRequest req) {
        BankDetails bd = bankDetailsRepository.findById(bankId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank details not found: " + bankId));

        if (bd.getVerificationStatus() == BankDetails.VerificationStatus.VERIFIED) {
            throw new IllegalStateException("Bank details are already verified.");
        }

        String action = req.getAction() != null ? req.getAction().toUpperCase() : "";

        if ("APPROVE".equals(action)) {
            bd.setIsVerified(true);
            bd.setVerificationStatus(BankDetails.VerificationStatus.VERIFIED);
            bd.setVerificationNote(null);
            bd.setVerifiedAt(LocalDateTime.now());
            bd.setVerifiedBy(adminUserId);

            // ✅ Activate carrier profile
            CarrierProfile profile = bd.getCarrierProfile();
            profile.setIsVerified(true);
            profile.setStatus(CarrierProfile.CarrierStatus.ACTIVE);
            carrierProfileRepository.save(profile);

            User carrier = profile.getUser();
            log.info("✅ Bank details APPROVED for carrier: {} by admin: {}",
                    carrier.getUserId(), adminUserId);

            // ✅ Register carrier's bank with RazorpayX for future payouts
            // This creates Contact + Fund Account so payout is instant at delivery time
            razorpayPayoutService.createFundAccountForCarrier(carrier, bd);

            // Notify carrier
            sendFcmNotification(
                    carrier.getFcmToken(),
                    "Bank Details Verified ✅",
                    "Your bank account has been verified! You can now receive payouts for deliveries."
            );
            saveNotification(
                    carrier,
                    "Bank Account Verified ✅",
                    "Your bank details have been verified by our team. You can now receive payouts.",
                    Notification.NotificationType.PAYMENT_RECEIVED
            );

        } else if ("REJECT".equals(action)) {
            if (req.getNote() == null || req.getNote().trim().isEmpty()) {
                throw new IllegalArgumentException("Rejection reason (note) is required.");
            }

            bd.setIsVerified(false);
            bd.setVerificationStatus(BankDetails.VerificationStatus.REJECTED);
            bd.setVerificationNote(req.getNote().trim());
            bd.setVerifiedAt(null);
            bd.setVerifiedBy(adminUserId);

            // Keep carrier inactive
            CarrierProfile profile = bd.getCarrierProfile();
            profile.setIsVerified(false);
            profile.setStatus(CarrierProfile.CarrierStatus.INACTIVE);
            carrierProfileRepository.save(profile);

            User carrier = profile.getUser();
            log.info("❌ Bank details REJECTED for carrier: {} | Reason: {}", carrier.getUserId(), req.getNote());

            // Notify carrier with reason
            sendFcmNotification(
                    carrier.getFcmToken(),
                    "Bank Details Rejected ❌",
                    "Your bank details were rejected: " + req.getNote() + ". Please update and resubmit."
            );
            saveNotification(
                    carrier,
                    "Bank Details Rejected ❌",
                    "Reason: " + req.getNote() + ". Please update your bank details and resubmit.",
                    Notification.NotificationType.PAYMENT_RECEIVED
            );

        } else {
            throw new IllegalArgumentException("Invalid action: " + action + ". Must be APPROVE or REJECT.");
        }

        BankDetails saved = bankDetailsRepository.save(bd);
        return mapToResponse(saved);
    }

    // =====================================================================
    // PRIVATE HELPERS
    // =====================================================================

    private CarrierProfile getCarrierProfileByUserId(String userId) {
        return carrierProfileRepository.findByUserUid(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Carrier profile not found for user: " + userId));
    }

    private void validateAccountNumber(String accountNumber) {
        if (accountNumber == null) throw new IllegalArgumentException("Account number is required.");
        String clean = accountNumber.trim();
        if (!clean.matches("\\d{9,18}")) {
            throw new IllegalArgumentException(
                    "Account number must be 9–18 digits with no spaces or special characters.");
        }
    }

    private void validateIfscCode(String ifsc) {
        if (ifsc == null) throw new IllegalArgumentException("IFSC code is required.");
        String clean = ifsc.trim().toUpperCase();
        if (!clean.matches("[A-Z]{4}0[A-Z0-9]{6}")) {
            throw new IllegalArgumentException(
                    "Invalid IFSC code format. It must be 11 characters: 4 letters + 0 + 6 alphanumeric.");
        }
    }

    private BankDetails.AccountType parseAccountType(String type) {
        try {
            return BankDetails.AccountType.valueOf(type.toUpperCase());
        } catch (Exception e) {
            return BankDetails.AccountType.SAVINGS;
        }
    }

    private String getString(Map<?, ?> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private BankDetailsDto.Response mapToResponse(BankDetails bd) {
        String statusMsg;
        String statusColor;
        switch (bd.getVerificationStatus()) {
            case PENDING:      statusMsg = "Submitted — under review (1–2 business days)"; statusColor = "orange"; break;
            case UNDER_REVIEW: statusMsg = "Under review by our team"; statusColor = "blue"; break;
            case VERIFIED:     statusMsg = "Verified ✅"; statusColor = "green"; break;
            case REJECTED:     statusMsg = "Rejected — " + (bd.getVerificationNote() != null ? bd.getVerificationNote() : "contact support"); statusColor = "red"; break;
            default:           statusMsg = "Unknown"; statusColor = "grey";
        }

        return BankDetailsDto.Response.builder()
                .bankId(bd.getBankDetailsId())
                .accountHolderName(bd.getAccountHolderName())
                .maskedAccountNumber(bd.getMaskedAccountNumber())
                .ifscCode(bd.getIfscCode())
                .bankName(bd.getBankName())
                .branchName(bd.getBranchName())
                .accountType(bd.getAccountType().toString())
                .upiId(bd.getUpiId())
                .isVerified(bd.getIsVerified())
                .verificationStatus(bd.getVerificationStatus().toString())
                .verificationNote(bd.getVerificationNote())
                .verifiedAt(bd.getVerifiedAt())
                .createdAt(bd.getCreatedAt())
                .updatedAt(bd.getUpdatedAt())
                .canReceivePayouts(Boolean.TRUE.equals(bd.getIsVerified()))
                .statusMessage(statusMsg)
                .statusColor(statusColor)
                .build();
    }

    private BankDetailsDto.AdminResponse mapToAdminResponse(BankDetails bd) {
        User carrier = bd.getCarrierProfile().getUser();
        return BankDetailsDto.AdminResponse.builder()
                .bankId(bd.getBankDetailsId())
                .carrierId(carrier.getUserId())
                .carrierName(carrier.getFullName())
                .carrierPhone(carrier.getMobile())
                .carrierEmail(carrier.getEmail())
                .accountHolderName(bd.getAccountHolderName())
                .accountNumber(bd.getAccountNumber())   // Full number for admin
                .maskedAccountNumber(bd.getMaskedAccountNumber())
                .ifscCode(bd.getIfscCode())
                .bankName(bd.getBankName())
                .branchName(bd.getBranchName())
                .accountType(bd.getAccountType().toString())
                .upiId(bd.getUpiId())
                .isVerified(bd.getIsVerified())
                .verificationStatus(bd.getVerificationStatus().toString())
                .verificationNote(bd.getVerificationNote())
                .verifiedAt(bd.getVerifiedAt())
                .verifiedBy(bd.getVerifiedBy())
                .createdAt(bd.getCreatedAt())
                .updatedAt(bd.getUpdatedAt())
                .build();
    }

    private void notifyAdminNewBankSubmission(User carrier, BankDetails bd, boolean isResubmission) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(adminEmail);
            helper.setSubject("[Saffari Carriers] " +
                    (isResubmission ? "Bank Details Re-submitted" : "New Bank Details — Verification Required"));

            String html = String.format("""
                <h2>%s</h2>
                <table border="1" cellpadding="8" style="border-collapse:collapse;">
                  <tr><td><b>Carrier Name</b></td><td>%s</td></tr>
                  <tr><td><b>Carrier ID</b></td><td>%s</td></tr>
                  <tr><td><b>Phone</b></td><td>%s</td></tr>
                  <tr><td><b>Account Holder</b></td><td>%s</td></tr>
                  <tr><td><b>Account Number</b></td><td>%s</td></tr>
                  <tr><td><b>IFSC</b></td><td>%s</td></tr>
                  <tr><td><b>Bank</b></td><td>%s</td></tr>
                  <tr><td><b>Branch</b></td><td>%s</td></tr>
                  <tr><td><b>Account Type</b></td><td>%s</td></tr>
                  <tr><td><b>UPI ID</b></td><td>%s</td></tr>
                  <tr><td><b>Submitted At</b></td><td>%s</td></tr>
                </table>
                <br/>
                <p>Please review and verify/reject in the admin panel.</p>
                """,
                    isResubmission ? "Re-submitted Bank Details" : "New Bank Details Submission",
                    carrier.getFullName(), carrier.getUserId(), carrier.getMobile(),
                    bd.getAccountHolderName(), bd.getAccountNumber(),
                    bd.getIfscCode(), bd.getBankName(), bd.getBranchName(),
                    bd.getAccountType(), bd.getUpiId() != null ? bd.getUpiId() : "N/A",
                    bd.getCreatedAt()
            );
            helper.setText(html, true);
            mailSender.send(message);
            log.info("✉️ Admin notified of bank details submission for carrier: {}", carrier.getUserId());
        } catch (Exception e) {
            log.error("❌ Failed to send admin email: {}", e.getMessage());
        }
    }

    private void sendFcmNotification(String fcmToken, String title, String body) {
        if (fcmToken == null || fcmToken.isBlank()) return;
        try {
            firebaseNotificationService.sendNotification(fcmToken, title, body);
        } catch (Exception e) {
            log.error("❌ FCM failed: {}", e.getMessage());
        }
    }

    private void saveNotification(User user, String title, String message,
                                  Notification.NotificationType type) {
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setMessage(message);
        n.setType(type);
        n.setIsRead(false);
        notificationRepository.save(n);
    }
}