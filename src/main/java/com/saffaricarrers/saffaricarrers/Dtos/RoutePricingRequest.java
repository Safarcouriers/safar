package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.RoutePricing;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RoutePricingRequest {

    @NotNull(message = "Product type is required")
    private RoutePricing.ProductType productType;

    // For PRIVATE & PUBLIC → Fixed price for a weight limit
    @DecimalMin(value = "0.1", message = "Weight limit must be greater than 0")
    private Double weightLimit;

    @DecimalMin(value = "0.1", message = "Fixed price must be greater than 0")
    private Double fixedPrice;

    // For COMMERCIAL → Price per ton
    @DecimalMin(value = "0.1", message = "Price per ton must be greater than 0")
    private Double pricePerTon;
}
