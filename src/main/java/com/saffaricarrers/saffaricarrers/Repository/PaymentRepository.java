package com.saffaricarrers.saffaricarrers.Repository;

import com.saffaricarrers.saffaricarrers.Entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);

    Optional<Payment> findByRazorpayPayoutId(String razorpayPayoutId);

    Optional<Payment> findByDeliveryRequest_RequestId(Long requestId);

    List<Payment> findByDeliveryRequest_Carrier_UserIdAndPaymentMethodAndPaymentStatus(
            String carrierId,
            Payment.PaymentMethod method,
            Payment.PaymentStatus status
    );

    // All failed payouts needing retry
    @Query("""
        SELECT p FROM Payment p
        WHERE p.paymentMethod = 'ONLINE'
          AND p.paymentStatus = 'COMPLETED'
          AND p.carrierTransferStatus = 'FAILED'
        ORDER BY p.paymentCompletedAt ASC
    """)
    List<Payment> findFailedPayouts();

    // Payouts initiated but not yet confirmed by webhook (stuck > 30 min)
    @Query("""
        SELECT p FROM Payment p
        WHERE p.paymentMethod = 'ONLINE'
          AND p.paymentStatus = 'COMPLETED'
          AND p.carrierTransferStatus = 'INITIATED'
          AND p.carrierTransferInitiatedAt < :cutoff
    """)
    List<Payment> findStuckPayouts(@Param("cutoff") LocalDateTime cutoff);
    @Query("SELECT COALESCE(SUM(p.totalAmount), 0.0) FROM Payment p " +
            "WHERE p.paymentStatus = :status " +
            "AND p.paymentCompletedAt BETWEEN :from AND :to")
    Double sumTotalAmountByStatusAndCompletedBetween(
            @Param("status") Payment.PaymentStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(p.platformCommission), 0.0) FROM Payment p " +
            "WHERE p.paymentStatus = :status " +
            "AND p.paymentCompletedAt BETWEEN :from AND :to")
    Double sumCommissionByStatusAndCompletedBetween(
            @Param("status") Payment.PaymentStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT CAST(p.paymentCompletedAt AS date), COALESCE(SUM(p.totalAmount), 0.0) " +
            "FROM Payment p WHERE p.paymentStatus = 'COMPLETED' " +
            "AND p.paymentCompletedAt BETWEEN :from AND :to " +
            "GROUP BY CAST(p.paymentCompletedAt AS date)")
    List<Object[]> sumRevenueGroupByDate(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT CAST(p.paymentCompletedAt AS date), COALESCE(SUM(p.platformCommission), 0.0) " +
            "FROM Payment p WHERE p.paymentStatus = 'COMPLETED' " +
            "AND p.paymentCompletedAt BETWEEN :from AND :to " +
            "GROUP BY CAST(p.paymentCompletedAt AS date)")
    List<Object[]> sumCommissionGroupByDate(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

}