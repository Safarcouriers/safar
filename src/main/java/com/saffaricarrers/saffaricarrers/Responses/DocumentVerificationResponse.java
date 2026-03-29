package com.saffaricarrers.saffaricarrers.Responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVerificationResponse {

    // Common fields
    private String documentType;        // AADHAAR, PAN, etc.
    private String status;              // PENDING, DOCUMENT_VERIFIED, DOCUMENT_REJECTED, etc.
    private String message;
    private String referenceId;         // For backward compatibility
    private String verificationId;      // DigiLocker verification ID
    private String verifiedName;
    private Boolean canRetry;

    // DigiLocker specific fields
    private String digilockerUrl;       // URL to redirect user to DigiLocker
    private String flowType;            // SIGN_IN or SIGN_UP
    private Integer urlExpiryMinutes;   // How long the URL is valid
    private LocalDateTime expiryTime;   // When the URL expires
    private String additionalInfo;      // Any additional information/warnings

    // Aadhaar details (from DigiLocker document)
    private String address;
    private String dateOfBirth;
    private String gender;
    // Masked Aadhaar image field
    private String maskedImageLink;     // URL of the masked Aadhaar image returned by Cashfree

    // Legacy OTP fields (for backward compatibility if needed)
    private String maskedMobileNumber;
    // ✅ PAN name matching fields (from Cashfree API)
    private String nameMatch;           // DIRECT_MATCH, GOOD_PARTIAL_MATCH, MODERATE_PARTIAL_MATCH, POOR_PARTIAL_MATCH, NO_MATCH
    private Double nameMatchScore;      // Score between 0-100
    private String aadhaarSeedingStatus;       // Y/N - whether PAN is linked with Aadhaar
    private String aadhaarSeedingStatusDesc;   // Description of Aadhaar seeding status

    // Legacy OTP fields (for backward compatibility if needed)
    private Integer otpExpiryMinutes;
    private Integer attemptsRemaining;
}
