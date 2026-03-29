package com.saffaricarrers.saffaricarrers.Entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "location_tracking1", indexes = {
        @Index(name = "idx_request_id", columnList = "request_id"),
        @Index(name = "idx_recorded_at", columnList = "recorded_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private DeliveryRequest deliveryRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_id", nullable = false)
    private User carrier;

    @Column(nullable = false, precision = 10)
    private Double latitude;

    @Column(nullable = false, precision = 10)
    private Double longitude;

    // Human-readable address resolved by frontend reverse geocoding
    @Column(length = 500)
    private String resolvedAddress;

    // Speed in km/h (optional, from GPS)
    private Double speed;

    // Battery level at time of tracking (0-100)
    private Integer batteryLevel;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        recordedAt = LocalDateTime.now();
    }
}