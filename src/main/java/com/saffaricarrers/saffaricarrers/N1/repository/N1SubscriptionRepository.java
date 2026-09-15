package com.saffaricarrers.saffaricarrers.N1.repository;

import com.saffaricarrers.saffaricarrers.N1.model.N1Subscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface N1SubscriptionRepository extends JpaRepository<N1Subscription, Long> {

    Optional<N1Subscription> findByRazorpayOrderId(String orderId);

    Optional<N1Subscription> findByRazorpayPaymentId(String paymentId);

    Optional<N1Subscription> findByUserIdAndIdempotencyKey(
            String userId,
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM N1Subscription s
        WHERE s.userId = :userId
          AND s.status = :status
        ORDER BY s.createdAt DESC
        """)
    List<N1Subscription> findPendingForUpdate(
            @Param("userId") String userId,
            @Param("status") N1Subscription.SubscriptionStatus status
    );

    @Query("""
        SELECT s
        FROM N1Subscription s
        WHERE s.userId = :userId
        ORDER BY s.createdAt DESC
        """)
    List<N1Subscription> findLatestByUserId(
            @Param("userId") String userId
    );

    @Query("""
        SELECT s
        FROM N1Subscription s
        WHERE s.status = :status
          AND s.expiryTime <= :now
        """)
    List<N1Subscription> findExpiredActive(
            @Param("status") N1Subscription.SubscriptionStatus status,
            @Param("now") LocalDateTime now
    );
    long countByInfluencerCodeAndStatus(
            String influencerCode,
            N1Subscription.SubscriptionStatus status
    );
    List<N1Subscription> findByInfluencerCodeOrderByCreatedAtDesc(
            String influencerCode
    );
    @Query("""
    SELECT COALESCE(SUM(s.amountPaid), 0)
    FROM N1Subscription s
    WHERE s.influencerCode = :code
      AND s.status = :status
""")
    Long getRevenueByInfluencer(
            @Param("code") String code,
            @Param("status") N1Subscription.SubscriptionStatus status
    );
}