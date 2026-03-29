package com.saffaricarrers.saffaricarrers.Entity;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "route_pricing")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutePricing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pricingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private CarrierRoute carrierRoute;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType productType;

    // For PRIVATE & PUBLIC → Fixed price with weight limit
    private Double weightLimit;
    private Double fixedPrice;
    @DecimalMin(value = "0.1", message = "Price per kg must be greater than 0")
    private Double pricePerKg;

    // For COMMERCIAL → Price per ton
    private Double pricePerTon;

    public enum ProductType {
        FOOD,
        ELECTRONICS,
        FURNITURE,
        APPAREL,           // Clothing, shoes, accessories
        BOOKS,             // Books, magazines, media
        TOYS,              // Toys and hobby items
        BEAUTY,            // Cosmetics and personal care
        HEALTH,            // Health and wellness products
        SPORTS,            // Sports equipment and fitness
        HOME_DECOR,        // Home decoration items
        AUTOMOTIVE,        // Car parts and accessories
        JEWELRY,           // Jewelry and watches
        PET_SUPPLIES,      // Pet food and care products
        OFFICE_SUPPLIES,   // Office equipment and stationery
        GARDEN,            // Gardening tools and supplies
        APPLIANCES,        // Home appliances
        PHARMACY,          // Medicines and medical supplies
        DIGITAL            // Digital products and services
    }
}