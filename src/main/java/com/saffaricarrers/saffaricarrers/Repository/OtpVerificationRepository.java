package com.saffaricarrers.saffaricarrers.Repository;


import com.saffaricarrers.saffaricarrers.Entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.w3c.dom.DocumentType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findByUserIdAndDocumentTypeAndStatusIn(
            String userId, DocumentType documentType, List<OtpVerification.OtpStatus> statuses);

    Optional<OtpVerification> findByReferenceIdAndStatus(String referenceId, OtpVerification.OtpStatus status);

    @Query("SELECT o FROM OtpVerification o WHERE o.userId = :userId AND o.documentType = :documentType " +
            "AND o.status = :status ORDER BY o.createdAt DESC")
    Optional<OtpVerification> findLatestByUserIdAndDocumentTypeAndStatus(
            String userId, DocumentType documentType, OtpVerification.OtpStatus status);

    List<OtpVerification> findByExpiryTimeBeforeAndStatusIn(
            LocalDateTime expiryTime, List<OtpVerification.OtpStatus> statuses);
}
