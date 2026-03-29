package com.saffaricarrers.saffaricarrers.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "packages")
@NamedEntityGraphs({
        @NamedEntityGraph(
                name = "Package.withSender",
                attributeNodes = @NamedAttributeNode("sender")
        ),
        @NamedEntityGraph(
                name = "Package.withDeliveryRequests",
                attributeNodes = {
                        @NamedAttributeNode("sender"),
                        @NamedAttributeNode(value = "deliveryRequests", subgraph = "deliveryRequests.details")
                },
                subgraphs = {
                        @NamedSubgraph(
                                name = "deliveryRequests.details",
                                attributeNodes = {
                                        @NamedAttributeNode("carrier"),
                                        @NamedAttributeNode("carrierRoute")
                                }
                        )
                }
        )
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Package {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long packageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false)
    private String productName;

    private String productDescription;

    @Column(nullable = false)
    private Double productValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoutePricing.ProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CarrierRoute.TransportType transportType;

    private Double weight;

    @Column(nullable = true)
    private Double length;

    @Column(nullable = true)
    private Double width;

    @Column(nullable = true)
    private Double height;

    @ElementCollection
    @CollectionTable(name = "package_images", joinColumns = @JoinColumn(name = "package_id"))
    @Column(name = "image_url")
    private List<String> productImages;

    private String productInvoiceImage;

    @Column(nullable = false)
    private String fromAddress;

    @Column(nullable = false)
    private String toAddress;

    @Column(nullable = false)
    private Long addressId;

    private double latitude = 0.0;
    private double longitude = 0.0;

    @Column(nullable = false)
    private String pickUpDate;

    @Column(nullable = false)
    private String dropDate;

    private Double tripCharge;
    private Double pricePerKg;
    private Double pricePerTon;

    @Column(nullable = false)
    private Boolean insurance = false;

    private String pickupOtp;
    private String deliveryOtp;

    @Enumerated(EnumType.STRING)
    private PackageStatus status = PackageStatus.CREATED;

    private LocalTime availableTime;
    private LocalTime deadlineTime;

    @OneToMany(mappedBy = "packageEntity", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<DeliveryRequest> deliveryRequests;

    @OneToOne(mappedBy = "packageEntity", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private Payment payment;

    @OneToOne(mappedBy = "packageEntity", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private Insurance insuranceDetails;

    // ✅ REMOVED: locationTrackings — access via DeliveryRequest instead
    // Use LocationTrackingRepository.findByPackageId(packageId) for queries

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private String url;
    private double toLatitude = 0.0;  // ← ADD THIS
    private double toLongitude = 0.0; // ← ADD THIS
    public enum PackageStatus {
        CREATED, REQUEST_SENT, MATCHED, PICKED_UP, IN_TRANSIT, DELIVERED, CANCELLED
    }

    public boolean isPaymentCompleted() {
        return payment != null && payment.getPaymentStatus() == Payment.PaymentStatus.COMPLETED;
    }

    public Double getTotalPaymentAmount() {
        return payment != null ? payment.getTotalAmount() : null;
    }

    public Payment.PaymentStatus getPaymentStatus() {
        return payment != null ? payment.getPaymentStatus() : null;
    }
}
