package com.saffaricarrers.saffaricarrers.Monitization.webhook;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Stores Razorpay webhook event IDs that have already been processed.
 * Prevents double-processing when Razorpay retries a webhook.
 *
 * FIX #7 from audit: webhook idempotency.
 */
@Entity
@Table(
        name = "processed_webhook_events",
        indexes = @Index(name = "idx_event_id", columnList = "eventId", unique = true)
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Razorpay event ID from the webhook payload root ("id" field).
     * Example: "evt_00000000000001"
     */
    @Column(nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column
    private String subscriptionId;

    @Column
    private String userId;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        processedAt = LocalDateTime.now();
    }
}