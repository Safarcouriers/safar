package com.saffaricarrers.saffaricarrers.Monitization.repository;

import com.saffaricarrers.saffaricarrers.Monitization.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    // ── Basic lookups ─────────────────────────────────────────────────────────

    Optional<Subscription> findByUserIdAndStatus(
            String userId,
            Subscription.SubscriptionStatus status);

    List<Subscription> findByUserId(String userId);

    // ── Razorpay ID lookups ───────────────────────────────────────────────────

    Optional<Subscription> findByIntroOrderId(String introOrderId);

    Optional<Subscription> findByIntroPaymentId(String introPaymentId);

    Optional<Subscription> findByRzpSubscriptionId(String rzpSubscriptionId);

    // ── Active subscription (INTRO_PENDING or ACTIVE) ─────────────────────────
    // Used by: create (duplicate check), cancel, status endpoint
    @Query("SELECT s FROM Subscription s WHERE s.userId = :userId " +
            "AND s.status IN ('INTRO_PENDING', 'ACTIVE') " +
            "ORDER BY s.createdAt DESC")
    Optional<Subscription> findActiveSubscriptionByUserId(@Param("userId") String userId);

    // ── Latest subscription regardless of status ──────────────────────────────
    // FIX #1: needed by getSubscriptionStatus so recovery can detect INTRO_PENDING
    @Query("SELECT s FROM Subscription s WHERE s.userId = :userId " +
            "ORDER BY s.createdAt DESC")
    Optional<Subscription> findLatestSubscriptionByUserId(@Param("userId") String userId);

    // ── Count active subscriptions (duplicate guard) ──────────────────────────
    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.userId = :userId " +
            "AND s.status IN ('INTRO_PENDING', 'ACTIVE')")
    long countActiveSubscriptionsByUserId(@Param("userId") String userId);

    // ── Scheduler: subscriptions ready to upgrade ─────────────────────────────
    // intro period ended + not yet upgraded + retry count < 5
    @Query("SELECT s FROM Subscription s WHERE " +
            "s.status = 'ACTIVE' AND " +
            "s.introPhase = true AND " +
            "s.realSubscriptionCreated = true AND " +
            "s.introEndTime <= :now AND " +
            "s.schedulerRetryCount < 5")
    List<Subscription> findSubscriptionsReadyForUpgrade(
            @Param("now") LocalDateTime now);

    // ── FIX: Cleanup query — stale INTRO_PENDING without fetching all rows ────
    // Replaces the dangerous findAll() in the scheduler cleanup job
    @Query("SELECT s FROM Subscription s WHERE " +
            "s.status = 'INTRO_PENDING' AND " +
            "s.createdAt < :cutoff")
    List<Subscription> findStaleIntroPendingBefore(
            @Param("cutoff") LocalDateTime cutoff);

    // ── Webhook idempotency: find by processedWebhookEvent ID ────────────────
    // Used by RazorpayWebhookController to avoid double-processing
    @Query("SELECT COUNT(w) > 0 FROM ProcessedWebhookEvent w WHERE w.eventId = :eventId")
    boolean existsByWebhookEventId(@Param("eventId") String eventId);
}