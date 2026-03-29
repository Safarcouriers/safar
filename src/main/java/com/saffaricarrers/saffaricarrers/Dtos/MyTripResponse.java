package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyTripResponse {
    private Long routeId;
    private String fromLocation;
    private String toLocation;
    private LocalDate availableDate;
    private LocalTime availableTime;
    private String deadlineTime;
    private String  deadlineDate;
    private CarrierRoute.RouteStatus routeStatus;
    private CarrierRoute.TransportType transportType;

    // Capacity information
    private Double currentWeight;
    private Double maxWeight;
    private Integer currentQuantity;
    private Integer maxQuantity;
    private Integer packageCount;
    private String packageDisplay; // e.g., "3/5 packages"

    // Request and earnings info
    private Integer pendingRequestsCount;
    private Double estimatedEarnings;
}