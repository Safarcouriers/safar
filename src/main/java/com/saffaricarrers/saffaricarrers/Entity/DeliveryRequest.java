package com.saffaricarrers.saffaricarrers.Entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_requests")
@NamedEntityGraphs({
        @NamedEntityGraph(
                name = "DeliveryRequest.full",
                attributeNodes = {
                        @NamedAttributeNode(value = "packageEntity", subgraph = "package.sender"),
                        @NamedAttributeNode(value = "carrierRoute", subgraph = "route.carrier"),
                        @NamedAttributeNode("sender"),
                        @NamedAttributeNode("carrier"),
                        @NamedAttributeNode("payment")
                },
                subgraphs = {
                        @NamedSubgraph(
                                name = "package.sender",
                                attributeNodes = @NamedAttributeNode("sender")
                        ),
                        @NamedSubgraph(
                                name = "route.carrier",
                                attributeNodes = {
                                        @NamedAttributeNode(value = "carrierProfile", subgraph = "carrier.user")
                                }
                        ),
                        @NamedSubgraph(
                                name = "carrier.user",
                                attributeNodes = @NamedAttributeNode("user")
                        )
                }
        ),
        @NamedEntityGraph(
                name = "DeliveryRequest.withUsers",
                attributeNodes = {
                        @NamedAttributeNode("sender"),
                        @NamedAttributeNode("carrier"),
                        @NamedAttributeNode("packageEntity"),
                        @NamedAttributeNode("carrierRoute")
                }
        )
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id", nullable = false)
    private Package packageEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private CarrierRoute carrierRoute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_id", nullable = false)
    private User carrier;

    // Optional: Keep reference to payment if needed
    @OneToOne(mappedBy = "deliveryRequest", fetch = FetchType.LAZY)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(nullable = false)
    private Double totalAmount;

    @Column(nullable = false)
    private Double platformCommission;

    @Column(nullable = false)
    private Double carrierEarning;

    private String pickupOtp;
    private String deliveryOtp;

    private LocalDateTime requestedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;

    private String senderNote;
    private String carrierNote;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
    private String pickupPhoto;  // Photo taken at pickup
    private String deliveryPhoto; // Photo taken at delivery @Column(name = "request_type", length = 50)
    private String requestType;

    // ... rest of the entity ...

    // ✅ Add getter and setter
    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public enum RequestStatus {
        PENDING, ACCEPTED, REJECTED, PICKED_UP, IN_TRANSIT, DELIVERED, CANCELLED
    }

    // Helper method to access package payment
    public Payment getPackagePayment() {
        return packageEntity != null ? packageEntity.getPayment() : null;
    }
}