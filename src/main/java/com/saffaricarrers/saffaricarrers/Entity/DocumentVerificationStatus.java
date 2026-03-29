package com.saffaricarrers.saffaricarrers.Entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_verification_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVerificationStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtpVerification.DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentVerificationStatusEnum status = DocumentVerificationStatusEnum.NOT_STARTED;

    @Column(nullable = false)
    private String documentNumber;

    @Column(length = 1000)
    private String verificationMessage;

    private String thirdPartyReferenceId;
    private String verifiedName;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
    public enum DocumentVerificationStatusEnum {
        NOT_STARTED,
        OTP_SENT,
        OTP_VERIFIED,
        DOCUMENT_VERIFIED,
        DOCUMENT_REJECTED,
        OTP_EXPIRED,
        OTP_FAILED,
        PENDING
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public OtpVerification.DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(OtpVerification.DocumentType documentType) {
        this.documentType = documentType;
    }

    public DocumentVerificationStatusEnum getStatus() {
        return status;
    }

    public void setStatus(DocumentVerificationStatusEnum status) {
        this.status = status;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber(String documentNumber) {
        this.documentNumber = documentNumber;
    }

    public String getVerificationMessage() {
        return verificationMessage;
    }

    public void setVerificationMessage(String verificationMessage) {
        this.verificationMessage = verificationMessage;
    }

    public String getThirdPartyReferenceId() {
        return thirdPartyReferenceId;
    }

    public void setThirdPartyReferenceId(String thirdPartyReferenceId) {
        this.thirdPartyReferenceId = thirdPartyReferenceId;
    }

    public String getVerifiedName() {
        return verifiedName;
    }

    public void setVerifiedName(String verifiedName) {
        this.verifiedName = verifiedName;
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

