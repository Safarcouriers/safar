package com.saffaricarrers.saffaricarrers.Entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bank_details_id")
    private Long bankDetailsId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_profile_id", nullable = false, unique = true)
    private CarrierProfile carrierProfile;

    // ─── Account Details ──────────────────────────────────────────────────

    @Column(nullable = false)
    private String accountHolderName;

    @Column(nullable = false)
    private String accountNumber;

    @Column(name = "masked_account_number")
    private String maskedAccountNumber;

    @Column(nullable = false)
    private String ifscCode;

    @Column(nullable = false)
    private String bankName;

    @Column
    private String branchName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType = AccountType.SAVINGS;

    @Column
    private String upiId;

    // ─── Verification ─────────────────────────────────────────────────────

    @Column(nullable = false)
    private Boolean isVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(length = 500)
    private String verificationNote;

    @Column
    private LocalDateTime verifiedAt;

    @Column
    private String verifiedBy;

    // ─── RazorpayX Payout Integration ─────────────────────────────────────

    /**
     * RazorpayX Contact ID — created once when admin verifies bank details.
     * Used to link fund account for payouts.
     */
    @Column
    private String razorpayContactId;

    /**
     * RazorpayX Fund Account ID — created once when admin verifies bank details.
     * Used in every payout call: payoutPayload.put("fund_account_id", this).
     */
    @Column
    private String razorpayFundAccountId;

    /**
     * RazorpayX UPI Fund Account ID — created when carrier provides a UPI ID.
     * Used as PRIMARY payout method (instant, 2-5 seconds).
     * Falls back to razorpayFundAccountId (IMPS) if null or failed.
     */
    @Column
    private String razorpayUpiVpaFundAccountId;

    // ─── Audit ────────────────────────────────────────────────────────────

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        maskAccount();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        maskAccount();
    }

    private void maskAccount() {
        if (accountNumber != null && accountNumber.length() >= 4) {
            String last4 = accountNumber.substring(accountNumber.length() - 4);
            maskedAccountNumber = "XXXX XXXX XXXX " + last4;
        }
    }

    public enum AccountType {
        SAVINGS, CURRENT
    }

    public enum VerificationStatus {
        PENDING,       // Just submitted, awaiting admin review
        UNDER_REVIEW,  // Admin is reviewing
        VERIFIED,      // Approved — carrier can receive payouts
        REJECTED       // Rejected — carrier must re-submit with corrections
    }
}