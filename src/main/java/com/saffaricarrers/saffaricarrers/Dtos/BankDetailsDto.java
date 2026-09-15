package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankDetailsDto {

    // ─── Flat fields (used by CarrierService + CarrierProfileDto) ─────────────

    private Long bankId;
    private String accountHolderName;
    private String accountNumber;
    private String ifscCode;
    private String bankName;
    private String branchName;
    private String accountType;
    private String upiId;
    private Boolean isVerified;
    private String verificationStatus;
    private String verificationNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ─── NESTED: Carrier submits bank details ─────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmitRequest {
        private String accountHolderName;
        private String accountNumber;
        private String confirmAccountNumber;
        private String ifscCode;
        private String bankName;
        private String branchName;
        private String accountType;
        private String upiId;
    }

    // ─── NESTED: What carrier sees (detailed response) ────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long bankId;
        private String accountHolderName;
        private String maskedAccountNumber;
        private String ifscCode;
        private String bankName;
        private String branchName;
        private String accountType;
        private String upiId;
        private Boolean isVerified;
        private String verificationStatus;
        private String verificationNote;
        private LocalDateTime verifiedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Boolean canReceivePayouts;
        private String statusMessage;
        private String statusColor;

        // ✅ Razorpay IDs — so you can verify they're saved correctly
        private String razorpayContactId;
        private String razorpayFundAccountId;
        private String razorpayUpiVpaFundAccountId;

        // ✅ Quick diagnostic flag — tells you immediately if payout will work
        private Boolean razorpaySetupComplete;
    }

    // ─── NESTED: Admin sees full account number ───────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AdminResponse {
        private Long bankId;
        private String carrierId;
        private String carrierName;
        private String carrierPhone;
        private String carrierEmail;
        private String accountHolderName;
        private String accountNumber;
        private String maskedAccountNumber;
        private String ifscCode;
        private String bankName;
        private String branchName;
        private String accountType;
        private String upiId;
        private Boolean isVerified;
        private String verificationStatus;
        private String verificationNote;
        private LocalDateTime verifiedAt;
        private String verifiedBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        // ✅ Razorpay IDs — admin can see and verify these are set correctly
        private String razorpayContactId;
        private String razorpayFundAccountId;
        private String razorpayUpiVpaFundAccountId;

        // ✅ Diagnostic flags
        private Boolean razorpaySetupComplete;
        private String razorpaySetupStatus; // "COMPLETE" | "MISSING_FUND_ACCOUNT" | "MISSING_CONTACT" | "NOT_SETUP"
    }

    // ─── NESTED: Admin approve / reject ──────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifyRequest {
        private String action;   // "APPROVE" | "REJECT"
        private String note;
    }

    // ─── NESTED: IFSC lookup result ──────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IfscInfo {
        private String ifscCode;
        private String bankName;
        private String branchName;
        private String city;
        private String state;
        private String address;
        private Boolean found;
        private String errorMessage;
    }
}