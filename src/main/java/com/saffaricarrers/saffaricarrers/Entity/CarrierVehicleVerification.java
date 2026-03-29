package com.saffaricarrers.saffaricarrers.Entity;


import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "carrier_vehicle_verification")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarrierVehicleVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String uid;

    // RC Verification Fields
    @Column(name = "rc_number")
    private String rcNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "rc_status")
    private VerificationStatus rcStatus = VerificationStatus.NOT_VERIFIED;

    @Column(name = "rc_verification_id")
    private String rcVerificationId;

    @Column(name = "rc_reference_id")
    private String rcReferenceId;

    @Column(name = "rc_verified_owner_name")
    private String rcVerifiedOwnerName;

    @Column(name = "rc_vehicle_number")
    private String rcVehicleNumber;

    @Column(name = "rc_vehicle_class")
    private String rcVehicleClass;

    @Column(name = "rc_verified_at")
    private LocalDateTime rcVerifiedAt;

    @Column(name = "rc_verification_message", length = 1000)
    private String rcVerificationMessage;

    // Driving License Verification Fields
    @Column(name = "dl_number")
    private String dlNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "dl_status")
    private VerificationStatus dlStatus = VerificationStatus.NOT_VERIFIED;

    @Column(name = "dl_verification_id")
    private String dlVerificationId;

    @Column(name = "dl_reference_id")
    private String dlReferenceId;

    @Column(name = "dl_verified_name")
    private String dlVerifiedName;

    @Column(name = "dl_dob")
    private String dlDob;

    @Column(name = "dl_verified_at")
    private LocalDateTime dlVerifiedAt;

    @Column(name = "dl_verification_message", length = 1000)
    private String dlVerificationMessage;

    @Column(name = "dl_validity_from")
    private String dlValidityFrom;

    @Column(name = "dl_validity_to")
    private String dlValidityTo;

    @Column(name = "dl_vehicle_classes", length = 500)
    private String dlVehicleClasses; // Comma-separated classes

    // Aadhaar matching fields
    @Column(name = "aadhaar_verified_name")
    private String aadhaarVerifiedName; // From DigiLocker

    @Column(name = "name_match_rc")
    private Boolean nameMatchRc;

    @Column(name = "name_match_dl")
    private Boolean nameMatchDl;

    // Overall verification status
    @Enumerated(EnumType.STRING)
    @Column(name = "overall_status")
    private OverallVerificationStatus overallStatus = OverallVerificationStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum VerificationStatus {
        NOT_VERIFIED,
        PENDING,
        VERIFIED,
        FAILED,
        EXPIRED
    }

    public enum OverallVerificationStatus {
        PENDING,           // Nothing verified yet
        RC_VERIFIED,       // Only RC verified
        DL_VERIFIED,       // Only DL verified
        BOTH_VERIFIED,     // Both RC and DL verified
        FAILED             // Verification failed
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getRcNumber() {
        return rcNumber;
    }

    public void setRcNumber(String rcNumber) {
        this.rcNumber = rcNumber;
    }

    public VerificationStatus getRcStatus() {
        return rcStatus;
    }

    public void setRcStatus(VerificationStatus rcStatus) {
        this.rcStatus = rcStatus;
    }

    public String getRcVerificationId() {
        return rcVerificationId;
    }

    public void setRcVerificationId(String rcVerificationId) {
        this.rcVerificationId = rcVerificationId;
    }

    public String getRcReferenceId() {
        return rcReferenceId;
    }

    public void setRcReferenceId(String rcReferenceId) {
        this.rcReferenceId = rcReferenceId;
    }

    public String getRcVerifiedOwnerName() {
        return rcVerifiedOwnerName;
    }

    public void setRcVerifiedOwnerName(String rcVerifiedOwnerName) {
        this.rcVerifiedOwnerName = rcVerifiedOwnerName;
    }

    public String getRcVehicleNumber() {
        return rcVehicleNumber;
    }

    public void setRcVehicleNumber(String rcVehicleNumber) {
        this.rcVehicleNumber = rcVehicleNumber;
    }

    public String getRcVehicleClass() {
        return rcVehicleClass;
    }

    public void setRcVehicleClass(String rcVehicleClass) {
        this.rcVehicleClass = rcVehicleClass;
    }

    public LocalDateTime getRcVerifiedAt() {
        return rcVerifiedAt;
    }

    public void setRcVerifiedAt(LocalDateTime rcVerifiedAt) {
        this.rcVerifiedAt = rcVerifiedAt;
    }

    public String getRcVerificationMessage() {
        return rcVerificationMessage;
    }

    public void setRcVerificationMessage(String rcVerificationMessage) {
        this.rcVerificationMessage = rcVerificationMessage;
    }

    public String getDlNumber() {
        return dlNumber;
    }

    public void setDlNumber(String dlNumber) {
        this.dlNumber = dlNumber;
    }

    public VerificationStatus getDlStatus() {
        return dlStatus;
    }

    public void setDlStatus(VerificationStatus dlStatus) {
        this.dlStatus = dlStatus;
    }

    public String getDlVerificationId() {
        return dlVerificationId;
    }

    public void setDlVerificationId(String dlVerificationId) {
        this.dlVerificationId = dlVerificationId;
    }

    public String getDlReferenceId() {
        return dlReferenceId;
    }

    public void setDlReferenceId(String dlReferenceId) {
        this.dlReferenceId = dlReferenceId;
    }

    public String getDlVerifiedName() {
        return dlVerifiedName;
    }

    public void setDlVerifiedName(String dlVerifiedName) {
        this.dlVerifiedName = dlVerifiedName;
    }

    public String getDlDob() {
        return dlDob;
    }

    public void setDlDob(String dlDob) {
        this.dlDob = dlDob;
    }

    public LocalDateTime getDlVerifiedAt() {
        return dlVerifiedAt;
    }

    public void setDlVerifiedAt(LocalDateTime dlVerifiedAt) {
        this.dlVerifiedAt = dlVerifiedAt;
    }

    public String getDlVerificationMessage() {
        return dlVerificationMessage;
    }

    public void setDlVerificationMessage(String dlVerificationMessage) {
        this.dlVerificationMessage = dlVerificationMessage;
    }

    public String getDlValidityFrom() {
        return dlValidityFrom;
    }

    public void setDlValidityFrom(String dlValidityFrom) {
        this.dlValidityFrom = dlValidityFrom;
    }

    public String getDlValidityTo() {
        return dlValidityTo;
    }

    public void setDlValidityTo(String dlValidityTo) {
        this.dlValidityTo = dlValidityTo;
    }

    public String getDlVehicleClasses() {
        return dlVehicleClasses;
    }

    public void setDlVehicleClasses(String dlVehicleClasses) {
        this.dlVehicleClasses = dlVehicleClasses;
    }

    public String getAadhaarVerifiedName() {
        return aadhaarVerifiedName;
    }

    public void setAadhaarVerifiedName(String aadhaarVerifiedName) {
        this.aadhaarVerifiedName = aadhaarVerifiedName;
    }

    public Boolean getNameMatchRc() {
        return nameMatchRc;
    }

    public void setNameMatchRc(Boolean nameMatchRc) {
        this.nameMatchRc = nameMatchRc;
    }

    public Boolean getNameMatchDl() {
        return nameMatchDl;
    }

    public void setNameMatchDl(Boolean nameMatchDl) {
        this.nameMatchDl = nameMatchDl;
    }

    public OverallVerificationStatus getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(OverallVerificationStatus overallStatus) {
        this.overallStatus = overallStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}