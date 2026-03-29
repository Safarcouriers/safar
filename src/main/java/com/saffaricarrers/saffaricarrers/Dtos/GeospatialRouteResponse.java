package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import jakarta.persistence.Column;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class GeospatialRouteResponse {
    // Basic route information
    private Long routeId;
    private String carrierName;
    private String carrierId;
    private String fromLocation;
    private String toLocation;
    private LocalDate availableDate;
    private LocalDate deadlineDate;
    private String availableTime;
    private String deadlineTime;

    private CarrierRoute.TransportType transportType;

    // Capacity information
    private Double maxWeight;
    private Integer maxQuantity;
    private Double currentWeight;
    private Integer currentQuantity;
    private Double availableWeight;
    private Integer availableQuantity;

    // Route status
    private CarrierRoute.RouteStatus routeStatus;

    // Geospatial information
    private Double latitude;
    private Double longitude;
    private Double distanceFromSearchPoint;
    private String distanceCategory; // NEARBY, MEDIUM, FAR, BETWEEN, ON_THE_WAY
    private Boolean isOnRoute = false;

    // Pricing information
    private java.util.List<com.saffaricarrers.saffaricarrers.Responses.RoutePricingResponse> pricing;

    // Additional metadata
    private java.time.LocalDateTime createdAt;
    private Double capacityUtilization; // Percentage of capacity used
    private Boolean isVerifiedCarrier;
    private String url;
    private Boolean isDirectRoute = true; // Default to direct

    /**
     * List of intermediate cities/locations where carrier plans to stop
     * Stored as comma-separated values or JSON
     */
    private String intermediateStops; // e.g., "Hyderabad,Nagpur,Bhopal"

}