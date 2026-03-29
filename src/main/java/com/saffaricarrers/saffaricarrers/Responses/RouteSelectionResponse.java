package com.saffaricarrers.saffaricarrers.Responses;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteSelectionResponse {
    private Long routeId;
    private String fromLocation;
    private String toLocation;
    private LocalDate availableDate;
    private LocalDate deadlineDate;
    private LocalTime availableTime;
    private CarrierRoute.TransportType transportType;
    private CarrierRoute.RouteStatus routeStatus;

    private String deadlineTime;
    // Capacity info
    private Double maxWeight;
    private Double currentWeight;
    private Double availableWeight;
    private Integer maxQuantity;
    private Integer currentQuantity;
    private Integer availableQuantity;

    // Matching info
    private Boolean transportTypeMatches;
    private Double estimatedCost;
    private Double platformCommission;
    private Double carrierEarning;

    // Can user send request?
    private Boolean canRequest;
    private String reasonCannotRequest;
}