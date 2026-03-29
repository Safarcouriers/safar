package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import com.saffaricarrers.saffaricarrers.Entity.RoutePricing;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SearchRequest {

    @NotBlank(message = "From location is required")
    private String fromLocation;

    @NotBlank(message = "To location is required")
    private String toLocation;

    @NotNull(message = "Available date is required")
    private LocalDate availableDate;

    // Optional filters
    private Double maxWeight;
    private Double requiredWeight;
    private CarrierRoute.TransportType transportType;
    private Double maxPrice;
    private RoutePricing.ProductType productType;
    private String sortBy = "price"; // price, time, rating
    private String sortDirection = "asc"; // asc, desc
}
