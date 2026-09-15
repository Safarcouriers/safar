package com.saffaricarrers.saffaricarrers.Monitization.scheduler;

import com.saffaricarrers.saffaricarrers.Monitization.model.Subscription;
import com.saffaricarrers.saffaricarrers.Monitization.repository.SubscriptionRepository;
import com.saffaricarrers.saffaricarrers.Monitization.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionUpgradeScheduler {

    private final SubscriptionRepository subscriptionRepo;
    private final SubscriptionService    subscriptionService;

    // ─────────────────────────────────────────────────────────────────────────
    // Every 30 minutes: sync subscriptions whose intro period has ended.
    // The Razorpay subscription already exists (created in step 1) —
    // this just updates our DB/Firestore status to reflect reality.
    // ─────────────────────────────────────────────────────────────────────────
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void upgradeIntroSubscriptions() {
        LocalDateTime now = LocalDateTime.now();

        List<Subscription> readyToUpgrade =
                subscriptionRepo.findSubscriptionsReadyForUpgrade(now);

        if (readyToUpgrade.isEmpty()) {
            log.debug("⏰ Scheduler: no subscriptions ready for upgrade at {}", now);
            return;
        }

        log.info("⏰ Scheduler: {} subscriptions ready for upgrade", readyToUpgrade.size());

        for (Subscription sub : readyToUpgrade) {
            try {
                log.info("🔄 Upgrading user={} plan={}", sub.getUserId(), sub.getPlanKey());
                subscriptionService.createRealSubscription(sub);
                Thread.sleep(2000); // avoid Razorpay rate limit
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Scheduler interrupted");
                break;
            } catch (Exception e) {
                log.error("❌ Upgrade failed for user={}: {}", sub.getUserId(), e.getMessage());
                // Continue with next — each subscription is independent
            }
        }

        log.info("⏰ Scheduler: batch complete");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Daily at 9 AM — expire stale INTRO_PENDING orders older than 24 hours.
    //
    // FIX #6: Replaced findAll() (OOM risk at scale) with a targeted query
    // that only fetches INTRO_PENDING rows older than the cutoff.
    //
    // FIX #9: Also cancels the orphaned subscription object on Razorpay's
    // side (it would otherwise sit in "created" state forever — previously
    // we only updated our own DB and left it dangling on Razorpay).
    // ─────────────────────────────────────────────────────────────────────────
    @Scheduled(cron = "0 0 9 * * *")
    public void cleanupStaleOrders() {
        log.info("🧹 Cleanup: scanning for stale INTRO_PENDING orders...");

        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

        // FIX: use targeted query — NOT findAll()
        List<Subscription> stale =
                subscriptionRepo.findStaleIntroPendingBefore(cutoff);

        if (stale.isEmpty()) {
            log.info("🧹 Cleanup: nothing to expire");
            return;
        }

        int cleaned = 0;
        for (Subscription sub : stale) {
            // FIX #9: cancel the dangling "created" subscription on Razorpay
            // before marking it EXPIRED locally. Failure here is non-fatal —
            // cancelOnRazorpayQuietly() logs and swallows the error so cleanup
            // still proceeds for every row in this batch.
            if (sub.getRzpSubscriptionId() != null) {
                subscriptionService.cancelOnRazorpayQuietly(
                        sub.getRzpSubscriptionId(), sub.getUserId());
            }

            sub.setStatus(Subscription.SubscriptionStatus.EXPIRED);
            subscriptionRepo.save(sub);
            cleaned++;
            log.info("🧹 Expired stale order: user={} plan={} created={}",
                    sub.getUserId(), sub.getPlanKey(), sub.getCreatedAt());
        }

        log.info("🧹 Cleanup: expired {} stale orders", cleaned);
    }
}