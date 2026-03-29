package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.Data;

@Data
public class RouteCapacityDto {
    private Long routeId;
    private Double maxWeight;
    private Double currentWeight;
    private Double availableWeight;
    private Integer maxQuantity;
    private Integer currentQuantity;
    private Integer availableQuantity;
    private Double weightUtilization; // Percentage
    private Double quantityUtilization; // Percentage
}