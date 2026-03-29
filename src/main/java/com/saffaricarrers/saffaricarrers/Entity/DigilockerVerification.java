package com.saffaricarrers.saffaricarrers.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "digilocker_verifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DigilockerVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "verification_id", unique = true, nullable = false)
    private String verificationId;

    @Column(name = "aadhaar_number", nullable = false)
    private String aadhaarNumber;

    @Column(name = "digilocker_url", length = 500)
    private String digilockerUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DigilockerStatus status;

    @Column(name = "account_exists")
    private Boolean accountExists;

    @Column(name = "eaadhaar_available")
    private Boolean eAadhaarAvailable;

    @Column(name = "flow_type")
    private String flowType; // SIGN_IN or SIGN_UP

    @Column(name = "verified_name")
    private String verifiedName;

    @Column(name = "expiry_time")
    private LocalDateTime expiryTime;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum DigilockerStatus {
        PENDING,           // URL generated, awaiting user action
        AUTHENTICATED,     // User logged in and gave consent
        DOCUMENT_FETCHED,  // Document successfully retrieved
        EXPIRED,           // URL expired
        CONSENT_DENIED     // User denied consent
    }
}