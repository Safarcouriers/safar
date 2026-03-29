package com.saffaricarrers.saffaricarrers.Entity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "carrier_profiles")
@NamedEntityGraphs({
        @NamedEntityGraph(
                name = "CarrierProfile.withUser",
                attributeNodes = @NamedAttributeNode("user")
        ),
        @NamedEntityGraph(
                name = "CarrierProfile.withBankDetails",
                attributeNodes = {
                        @NamedAttributeNode("user"),
                        @NamedAttributeNode("bankDetails")
                }
        )
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarrierProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long carrierId;

    private String userUid;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Boolean isVerified = false;

    @Enumerated(EnumType.STRING)
    private CarrierStatus status = CarrierStatus.INACTIVE;

    private Integer weeklyOrderCount = 0;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalEarnings = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal pendingCommission = BigDecimal.ZERO;

    // ── Rider Mode fields ──────────────────────────────────────────────────
    @Column(name = "is_online", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isOnline = false;

    @Column(name = "search_radius_km", columnDefinition = "DOUBLE PRECISION DEFAULT 10.0")
    private Double searchRadiusKm = 10.0;
    // ──────────────────────────────────────────────────────────────────────

    @OneToOne(mappedBy = "carrierProfile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private BankDetails bankDetails;

    @OneToMany(mappedBy = "carrierProfile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CarrierRoute> carrierRoutes;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
    @Column(name = "last_lat")
    private Double lastLat;

    @Column(name = "last_lng")
    private Double lastLng;

    @Column(name = "last_location_at")
    private LocalDateTime lastLocationAt;
    public enum CarrierStatus {ACTIVE, INACTIVE, SUSPENDED}
}