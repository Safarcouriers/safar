package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

/**
 * BankDetailsDto — top-level flat class kept for CarrierProfileDto.bankDetails backward compatibility.
 * Nested static classes used by new BankDetailsService / BankDetailsController endpoints.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankDetailsDto {

    // ─── Flat fields (used by CarrierService + CarrierProfileDto) ─────────────

    private Long bankId;
    private String accountHolderName;
    private String accountNumber;          // Always masked when returned to carrier
    private String ifscCode;
    private String bankName;
    private String branchName;
    private String accountType;
    private String upiId;
    private Boolean isVerified;
    private String verificationStatus;     // PENDING | UNDER_REVIEW | VERIFIED | REJECTED
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
        private String accountType;        // "SAVINGS" | "CURRENT"
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
        private String accountNumber;      // Full number — admin only
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
    }

    // ─── NESTED: Admin approve / reject ──────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifyRequest {
        private String action;   // "APPROVE" | "REJECT"
        private String note;     // Required when action = REJECT
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