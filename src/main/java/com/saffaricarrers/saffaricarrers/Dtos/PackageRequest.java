package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import com.saffaricarrers.saffaricarrers.Entity.RoutePricing;
import jakarta.persistence.Column;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalTime;
import java.util.List;

@Data
public class PackageRequest {
    private String uid;

    @NotBlank(message = "Product name is required")
    private String productName;

    private String productDescription;

    @NotNull(message = "Product value is required")
    @DecimalMin(value = "0.1", message = "Product value must be greater than 0")
    private Double productValue;

    @NotNull(message = "Product type is required")
    private RoutePricing.ProductType productType;

    @NotNull(message = "Transport type is required")
    private CarrierRoute.TransportType transportType; // PRIVATE, PUBLIC, COMMERCIAL

    /** --- For PRIVATE & PUBLIC → Dimensional weight pricing --- **/
    @DecimalMin(value = "0.1", message = "Weight must be greater than 0")
    private Double weight; // Used only if COMMERCIAL

    @DecimalMin(value = "0.1", message = "Length must be greater than 0")
    private Double length;

    @DecimalMin(value = "0.1", message = "Width must be greater than 0")
    private Double width;

    @DecimalMin(value = "0.1", message = "Height must be greater than 0")
    private Double height;

    /** --- Images and documents --- **/
    private List<String> productImages;
    private String productInvoiceImage;

    /** --- Addresses --- **/
    @NotBlank(message = "From address is required")
    private String fromAddress;

    @NotBlank(message = "To address is required")
    private String toAddress;

    @NotNull(message = "Delivery address ID is required")
    private Long addressId;

    private double latitude;
    private double longitude;

    /** --- Dates --- **/
    @NotBlank(message = "Pickup date is required")
    private String pickUpDate;

    @NotBlank(message = "Drop date is required")
    private String dropDate;

    /** --- Pricing --- **/
    @DecimalMin(value = "0.1", message = "Trip charge must be greater than 0")
    private Double tripCharge;

    // For COMMERCIAL → price per kg and ton (backend validation will enforce)
    @DecimalMin(value = "0.1", message = "Price per kg must be greater than 0")
    private Double pricePerKg;

    @DecimalMin(value = "0.1", message = "Price per ton must be greater than 0")
    private Double pricePerTon;
    @Column(nullable = false)
    private Boolean insurance = false;
    private String url;
    @NotNull(message = "Available time is required")
    private LocalTime availableTime;
    @NotNull(message = "Available time is required")
    private LocalTime deadlineTime;
    private double toLatitude;   // ← ADD THIS
    private double toLongitude;  // ← ADD THIS
}
