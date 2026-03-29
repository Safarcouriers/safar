package com.saffaricarrers.saffaricarrers.Responses;

import com.saffaricarrers.saffaricarrers.Entity.RoutePricing;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RoutePricingResponse {
    private String productType;

    // For PRIVATE & PUBLIC → Fixed price for a weight limit
    private Double weightLimit;

    private Double fixedPrice;

    // For COMMERCIAL → Price per ton
    private Double pricePerTon;

}