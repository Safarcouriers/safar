package com.saffaricarrers.saffaricarrers.Dtos;

import jakarta.persistence.Column;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CarrierRouteDto {
    private Long routeId;
    private Long carrierId;
    private String fromLocation;
    private String toLocation;
    private String fromPincode;
    private String toPincode;
    private Boolean isActive;
    private Integer maxWeight;
    private String vehicleType;
    private Double estimatedTime;
    private LocalDateTime createdAt;
    private Boolean isDirectRoute = true; // Default to direct
    private String intermediateStops; // e.g., "Hyderabad,Nagpur,Bhopal"
}