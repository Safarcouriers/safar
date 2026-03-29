package com.saffaricarrers.saffaricarrers.Repository;

import com.saffaricarrers.saffaricarrers.Entity.DigilockerVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DigilockerVerificationRepository extends JpaRepository<DigilockerVerification, Long> {

    Optional<DigilockerVerification> findByVerificationId(String verificationId);

    Optional<DigilockerVerification> findByUserIdAndStatus(String userId, DigilockerVerification.DigilockerStatus status);

    List<DigilockerVerification> findByUserId(String userId);

    List<DigilockerVerification> findByExpiryTimeBeforeAndStatus(
            LocalDateTime expiryTime,
            DigilockerVerification.DigilockerStatus status
    );

    // ✅ ADD THIS METHOD - Required for RC/DL verification to get Aadhaar verified name
    Optional<DigilockerVerification> findTopByUserIdAndStatusOrderByVerifiedAtDesc(
            String userId,
            DigilockerVerification.DigilockerStatus status
    );
}