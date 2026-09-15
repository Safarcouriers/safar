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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private final RazorpayRouteService razorpayRouteService;

    @Value("${admin.email}")
    private String adminEmail;

    // =====================================================================
    // CARRIER — SUBMIT BANK DETAILS
    // =====================================================================

    @Transactional
    @CacheEvict(value = {"profiles", "users"}, allEntries = true)
    public BankDetailsDto.Response submitBankDetails(String userId, BankDetailsDto.SubmitRequest req) {
        CarrierProfile profile = getCarrierProfileByUserId(userId);
        User carrier = profile.getUser();

        if (req.getAccountNumber() == null || !req.getAccountNumber().equals(req.getConfirmAccountNumber())) {
            throw new IllegalArgumentException("Account numbers do not match. Please re-enter carefully.");
        }

        validateAccountNumber(req.getAccountNumber());
        validateIfscCode(req.getIfscCode());

        boolean accountTaken = bankDetailsRepository
                .existsByAccountNumberAndCarrierProfileNot(req.getAccountNumber().trim(), profile);
        if (accountTaken) {
            throw new IllegalArgumentException(
                    "This account number is already registered with another carrier.");
        }

        BankDetails bankDetails = bankDetailsRepository.findByCarrierProfile(profile)
                .orElse(new BankDetails());

        boolean isResubmission = bankDetails.getBankDetailsId() != null;

        // ✅ Reset Razorpay IDs on resubmission — bank details changed so old IDs invalid
        if (isResubmission) {
            bankDetails.setRazorpayContactId(null);
            bankDetails.setRazorpayFundAccountId(null);
            bankDetails.setRazorpayUpiVpaFundAccountId(null);
            log.info("♻️ Resubmission — cleared old Razorpay IDs for carrier: {}", userId);
        }

        bankDetails.setCarrierProfile(profile);
        bankDetails.setAccountHolderName(req.getAccountHolderName().trim().toUpperCase());
        bankDetails.setAccountNumber(req.getAccountNumber().trim());
        bankDetails.setIfscCode(req.getIfscCode().trim().toUpperCase());
        bankDetails.setBankName(req.getBankName() != null ? req.getBankName().trim() : "");
        bankDetails.setBranchName(req.getBranchName() != null ? req.getBranchName().trim() : null);
        bankDetails.setAccountType(parseAccountType(req.getAccountType()));
        bankDetails.setUpiId(req.getUpiId() != null ? req.getUpiId().trim() : null);

        bankDetails.setIsVerified(true);
        bankDetails.setVerificationStatus(BankDetails.VerificationStatus.VERIFIED);
        bankDetails.setVerificationNote(null);
        bankDetails.setVerifiedAt(LocalDateTime.now());
        bankDetails.setVerifiedBy("AUTO");

        BankDetails saved = bankDetailsRepository.saveAndFlush(bankDetails); // ✅ flush immediately

        profile.setBankDetails(saved);
        profile.setIsVerified(true);
        profile.setStatus(CarrierProfile.CarrierStatus.ACTIVE);
        carrierProfileRepository.save(profile);

        log.info("✅ Bank details {} + carrier activated: {} | Account: {} | Bank: {}",
                isResubmission ? "updated" : "submitted",
                userId, saved.getMaskedAccountNumber(), saved.getBankName());

        // ✅ Create Razorpay linked account — reload fresh from DB after save
        Long bankDetailsId = saved.getBankDetailsId();
        String carrierUserId = carrier.getUserId();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            BankDetails fresh = bankDetailsRepository.findById(bankDetailsId).orElseThrow();
                            User freshCarrier = userRepository.findByUserId(carrierUserId).orElseThrow();
                            razorpayRouteService.createLinkedAccount(freshCarrier, fresh);
                            log.info("✅ Razorpay setup completed after commit for carrier: {}", carrierUserId);
                        } catch (Exception e) {
                            log.warn("⚠️ Razorpay setup failed after commit for carrier {}: {}", carrierUserId, e.getMessage());
                        }
                    }
                }
        );

        // ✅ Reload from DB to return latest data including Razorpay IDs
        BankDetails finalSaved = bankDetailsRepository.findById(saved.getBankDetailsId())
                .orElse(saved);

        sendFcmNotification(
                carrier.getFcmToken(),
                isResubmission ? "Bank Details Updated ✅" : "Account Activated! 🎉",
                isResubmission
                        ? "Your bank details have been updated. Payouts will go to your new account."
                        : "Your bank account is verified. You can now accept deliveries and receive payouts!"
        );

        saveNotification(
                carrier,
                isResubmission ? "Bank Details Updated ✅" : "Account Activated! 🎉",
                isResubmission
                        ? "Your bank details have been updated successfully."
                        : "Your bank account is set up. Start accepting deliveries!",
                Notification.NotificationType.PAYMENT_RECEIVED
        );

        return mapToResponse(finalSaved);
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
        // ✅ Also expose Razorpay setup status in bank status endpoint
        status.put("razorpaySetupComplete", bd != null && isValidFundAccountId(bd.getRazorpayFundAccountId()));
        status.put("razorpaySetupStatus", bd != null ? resolveRazorpaySetupStatus(bd) : "NOT_SETUP");

        String msg = "Not submitted";
        if (bd != null) {
            switch (bd.getVerificationStatus()) {
                case PENDING      -> msg = "Submitted — awaiting setup";
                case UNDER_REVIEW -> msg = "Under review by our team";
                case VERIFIED     -> msg = "Verified ✅ — you can now receive payouts";
                case REJECTED     -> msg = "Rejected: " + (bd.getVerificationNote() != null
                        ? bd.getVerificationNote() : "See details");
            }
        }
        status.put("statusMessage", msg);
        return status;
    }

    // =====================================================================
    // IFSC LOOKUP
    // =====================================================================

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
    // ADMIN — LIST
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

    // ✅ Single source of truth for fund account ID validation — always trims
    private boolean isValidFundAccountId(String fundId) {
        if (fundId == null) return false;
        String trimmed = fundId.trim();
// ✅ FIXED
        boolean valid = trimmed.startsWith("fa_") && trimmed.length() == 17;
        if (!valid) {
            log.warn("⚠️ Invalid fund account ID: '{}' (length={})", trimmed, trimmed.length());
        }
        return valid;
    }

    private String resolveRazorpaySetupStatus(BankDetails bd) {
        if (bd.getRazorpayContactId() == null || bd.getRazorpayContactId().isBlank()) {
            return "NOT_SETUP";
        }
        String fundId = bd.getRazorpayFundAccountId();
        if (fundId == null || fundId.isBlank()) {
            return "MISSING_FUND_ACCOUNT";
        }
        String trimmed = fundId.trim();
        // ✅ FIXED
        if (!trimmed.startsWith("fa_") || trimmed.length() != 17)
            return "INVALID_FUND_ACCOUNT_ID (value='" + trimmed + "', length=" + trimmed.length() + ")";

        return "COMPLETE";
    }

    private BankDetailsDto.Response mapToResponse(BankDetails bd) {
        String statusMsg;
        String statusColor;
        switch (bd.getVerificationStatus()) {
            case PENDING      -> { statusMsg = "Setting up your account..."; statusColor = "orange"; }
            case UNDER_REVIEW -> { statusMsg = "Under review by our team";   statusColor = "blue";   }
            case VERIFIED     -> { statusMsg = "Verified ✅";                statusColor = "green";  }
            case REJECTED     -> { statusMsg = "Rejected — " + (bd.getVerificationNote() != null
                    ? bd.getVerificationNote() : "contact support");         statusColor = "red";    }
            default           -> { statusMsg = "Unknown";                    statusColor = "grey";   }
        }

        // ✅ Always trim Razorpay IDs before returning and validating
        String fundId    = bd.getRazorpayFundAccountId()       != null ? bd.getRazorpayFundAccountId().trim()       : null;
        String contactId = bd.getRazorpayContactId()           != null ? bd.getRazorpayContactId().trim()           : null;
        String upiVpaId  = bd.getRazorpayUpiVpaFundAccountId() != null ? bd.getRazorpayUpiVpaFundAccountId().trim() : null;

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
                .razorpayContactId(contactId)
                .razorpayFundAccountId(fundId)           // ✅ trimmed
                .razorpayUpiVpaFundAccountId(upiVpaId)
                .razorpaySetupComplete(isValidFundAccountId(fundId)) // ✅ uses trimmed value
                .build();
    }

    private BankDetailsDto.AdminResponse mapToAdminResponse(BankDetails bd) {
        User carrier = bd.getCarrierProfile().getUser();

        // ✅ Always trim Razorpay IDs
        String fundId    = bd.getRazorpayFundAccountId()       != null ? bd.getRazorpayFundAccountId().trim()       : null;
        String contactId = bd.getRazorpayContactId()           != null ? bd.getRazorpayContactId().trim()           : null;
        String upiVpaId  = bd.getRazorpayUpiVpaFundAccountId() != null ? bd.getRazorpayUpiVpaFundAccountId().trim() : null;

        return BankDetailsDto.AdminResponse.builder()
                .bankId(bd.getBankDetailsId())
                .carrierId(carrier.getUserId())
                .carrierName(carrier.getFullName())
                .carrierPhone(carrier.getMobile())
                .carrierEmail(carrier.getEmail())
                .accountHolderName(bd.getAccountHolderName())
                .accountNumber(bd.getAccountNumber())
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
                .razorpayContactId(contactId)
                .razorpayFundAccountId(fundId)           // ✅ trimmed
                .razorpayUpiVpaFundAccountId(upiVpaId)
                .razorpaySetupComplete(isValidFundAccountId(fundId)) // ✅ uses trimmed value
                .razorpaySetupStatus(resolveRazorpaySetupStatus(bd))
                .build();
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