package com.saffaricarrers.saffaricarrers.Entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "carrier_routes")
@NamedEntityGraphs({
        @NamedEntityGraph(
                name = "CarrierRoute.withCarrierProfile",
                attributeNodes = {
                        @NamedAttributeNode(value = "carrierProfile", subgraph = "carrierProfile.user")
                },
                subgraphs = {
                        @NamedSubgraph(
                                name = "carrierProfile.user",
                                attributeNodes = @NamedAttributeNode("user")
                        )
                }
        ),
        @NamedEntityGraph(
                name = "CarrierRoute.withPricing",
                attributeNodes = {
                        @NamedAttributeNode(value = "carrierProfile", subgraph = "carrierProfile.user"),
                        @NamedAttributeNode("routePricing")
                },
                subgraphs = {
                        @NamedSubgraph(
                                name = "carrierProfile.user",
                                attributeNodes = @NamedAttributeNode("user")
                        )
                }
        )
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarrierRoute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long routeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_id", nullable = false)
    private CarrierProfile carrierProfile;

    @Column(nullable = false)
    private String fromLocation;

    @Column(nullable = false)
    private String toLocation;

    @Column(nullable = false)
    private Long longAddressId;

    @Column(nullable = false)
    private LocalDate availableDate;

    @Column(nullable = false)
    private LocalDate deadlineDate;

    @Column(nullable = false)
    private LocalTime availableTime;

    @Column(nullable = false)
    private LocalTime deadlineTime;

    @Enumerated(EnumType.STRING)
    private TransportType transportType;

    private RoutePricing.ProductType productType;

    @Column(nullable = false)
    private Double maxWeight;

    @Column(nullable = false)
    private Integer maxQuantity;

    @Enumerated(EnumType.STRING)
    private RouteStatus routeStatus = RouteStatus.ACTIVE;

    @OneToMany(mappedBy = "carrierRoute", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RoutePricing> routePricing;

    @OneToMany(mappedBy = "carrierRoute", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DeliveryRequest> deliveryRequests;

    private Double currentWeight = 0.0;
    private Integer currentQuantity = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ✅ CHANGED: From primitive double to Double wrapper (better null handling)
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    public enum TransportType {
        CAR, BIKE, TRUCK, BUS, TRAIN, FLIGHT, PRIVATE, PUBLIC, COMMERCIAL, DEFAULT,LORRY,TIPPER,AUTO
    }

    public enum RouteStatus {
        ACTIVE, CLOSED, INACTIVE, COMPLETED, ACCEPTED, CREATED,
        REQUEST_SENT, MATCHED, PICKED_UP, IN_TRANSIT, DELIVERED, CANCELLED
    }

    private String url;

    @Column(name = "is_direct_route")
    private Boolean isDirectRoute = true;

    @Column(name = "intermediate_stops", columnDefinition = "TEXT")
    private String intermediateStops;
    @Column(name = "to_latitude")
    private Double toLatitude;

    @Column(name = "to_longitude")
    private Double toLongitude;
}