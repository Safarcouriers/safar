package com.saffaricarrers.saffaricarrers.N1.service;

import com.saffaricarrers.saffaricarrers.N1.model.N1Influencer;
import com.saffaricarrers.saffaricarrers.N1.model.N1InfluencerAttribution;
import com.saffaricarrers.saffaricarrers.N1.model.N1Subscription;
import com.saffaricarrers.saffaricarrers.N1.repository.N1InfluencerAttributionRepository;
import com.saffaricarrers.saffaricarrers.N1.repository.N1InfluencerRepository;
import com.saffaricarrers.saffaricarrers.N1.repository.N1SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class N1InfluencerService {

    private final N1InfluencerRepository influencerRepo;
    private final N1InfluencerAttributionRepository attributionRepo;
    private final N1SubscriptionRepository subscriptionRepo;

    // ============================================================
    // CREATE INFLUENCER
    // ============================================================

    @Transactional
    public N1Influencer createInfluencer(
            String name,
            String code,
            String email,
            Integer commissionPercent
    ) {

        String cleanCode = code.trim().toLowerCase();

        if (influencerRepo.existsByCodeIgnoreCase(cleanCode)) {
            throw new IllegalArgumentException(
                    "Influencer code already exists."
            );
        }

        if (commissionPercent == null) {
            commissionPercent = 20;
        }

        if (commissionPercent < 0 || commissionPercent > 100) {
            throw new IllegalArgumentException(
                    "Commission must be between 0 and 100."
            );
        }

        N1Influencer influencer =
                N1Influencer.builder()
                        .name(name.trim())
                        .code(cleanCode)
                        .email(
                                email == null
                                        ? null
                                        : email.trim()
                        )
                        .commissionPercent(commissionPercent)
                        .active(true)
                        .build();

        return influencerRepo.save(influencer);
    }

    // ============================================================
    // ATTRIBUTE USER
    // ============================================================

    @Transactional
    public void attributeUser(
            String userId,
            String influencerCode,
            String source
    ) {

        String cleanUserId = userId.trim();
        String cleanCode = influencerCode.trim().toLowerCase();

        // First attribution wins.
        if (attributionRepo.existsByUserId(cleanUserId)) {
            log.info(
                    "Influencer attribution already exists user={}",
                    cleanUserId
            );
            return;
        }

        N1Influencer influencer =
                influencerRepo.findByCodeIgnoreCase(cleanCode)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid influencer code."
                                )
                        );

        if (!Boolean.TRUE.equals(influencer.getActive())) {
            throw new IllegalArgumentException(
                    "Influencer is inactive."
            );
        }

        N1InfluencerAttribution attribution =
                N1InfluencerAttribution.builder()
                        .userId(cleanUserId)
                        .influencerCode(cleanCode)
                        .source(
                                source == null || source.isBlank()
                                        ? "unknown"
                                        : source.trim()
                        )
                        .attributedAt(LocalDateTime.now())
                        .build();

        attributionRepo.save(attribution);

        log.info(
                "Influencer attribution created user={} influencer={}",
                cleanUserId,
                cleanCode
        );
    }

    // ============================================================
    // GET ATTRIBUTION
    // ============================================================

    public N1InfluencerAttribution getAttribution(String userId) {

        return attributionRepo.findByUserId(userId.trim())
                .orElse(null);
    }

    // ============================================================
    // LIST INFLUENCERS
    // ============================================================

    public List<N1Influencer> getActiveInfluencers() {

        return influencerRepo
                .findByActiveTrueOrderByCreatedAtDesc();
    }

    // ============================================================
    // STATS
    // ============================================================

    public InfluencerStats getStats(String code) {

        String cleanCode = code.trim().toLowerCase();

        N1Influencer influencer =
                influencerRepo.findByCodeIgnoreCase(cleanCode)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Influencer not found."
                                )
                        );

        long paidUsers =
                subscriptionRepo.countByInfluencerCodeAndStatus(
                        cleanCode,
                        N1Subscription.SubscriptionStatus.ACTIVE
                );

        Long revenuePaise =
                subscriptionRepo.getRevenueByInfluencer(
                        cleanCode,
                        N1Subscription.SubscriptionStatus.ACTIVE
                );

        if (revenuePaise == null) {
            revenuePaise = 0L;
        }

        long commissionPaise =
                revenuePaise
                        * influencer.getCommissionPercent()
                        / 100;

        return new InfluencerStats(
                influencer.getId(),
                influencer.getName(),
                influencer.getCode(),
                paidUsers,
                revenuePaise,
                revenuePaise / 100.0,
                influencer.getCommissionPercent(),
                commissionPaise,
                commissionPaise / 100.0
        );
    }

    // ============================================================
    // STATS DTO
    // ============================================================

    public record InfluencerStats(
            Long influencerId,
            String influencerName,
            String influencerCode,
            long paidUsers,
            long revenuePaise,
            double revenue,
            int commissionPercent,
            long commissionPaise,
            double commission
    ) {
    }
}