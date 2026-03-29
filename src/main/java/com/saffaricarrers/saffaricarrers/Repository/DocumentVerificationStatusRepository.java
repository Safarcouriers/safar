package com.saffaricarrers.saffaricarrers.Repository;

import com.saffaricarrers.saffaricarrers.Entity.DocumentVerificationStatus;
import com.saffaricarrers.saffaricarrers.Entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentVerificationStatusRepository extends JpaRepository<DocumentVerificationStatus, Long> {

    List<DocumentVerificationStatus> findByUserId(String userId);

    Optional<DocumentVerificationStatus> findByUserIdAndDocumentType(String userId, OtpVerification.DocumentType documentType);

    boolean existsByUserIdAndDocumentTypeAndStatus(
            String userId, OtpVerification.DocumentType documentType, DocumentVerificationStatus.DocumentVerificationStatusEnum status);
    boolean existsByDocumentNumberAndDocumentTypeAndStatusAndUserIdNot(
            String documentNumber,
            OtpVerification.DocumentType documentType,
            DocumentVerificationStatus.DocumentVerificationStatusEnum status,
            String userId
    );
    long countByStatus(DocumentVerificationStatus.DocumentVerificationStatusEnum status);
    long countByDocumentType(OtpVerification.DocumentType documentType);


}
