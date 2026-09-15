package com.saffaricarrers.saffaricarrers.N1.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class N1SubscriptionExpiryScheduler {
    private final N1SubscriptionService n1SubscriptionService;

    // Backup only. The status endpoint also checks the exact expiry time, so access does not
    // depend on this scheduler running at the exact moment of expiry.
    @Scheduled(cron = "0 */15 * * * *")
    public void expireSubscriptions() {
        try {
            n1SubscriptionService.expireSubscriptions();
        } catch (Exception e) {
            log.error("Subscription expiry job failed", e);
        }
    }
}
