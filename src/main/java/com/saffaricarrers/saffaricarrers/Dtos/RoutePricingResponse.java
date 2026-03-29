package com.saffaricarrers.saffaricarrers.Dtos;
import com.saffaricarrers.saffaricarrers.Entity.RoutePricing;
import lombok.Data;

@Data
public class RoutePricingResponse {
    private Long pricingId;
    private RoutePricing.ProductType productType;
    private Double weightLimit;
    private Double fixedPrice;
    private Double pricePerTon;
}