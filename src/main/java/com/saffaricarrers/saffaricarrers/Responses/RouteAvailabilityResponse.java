package com.saffaricarrers.saffaricarrers.Responses;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteAvailabilityResponse {
    // Package info
    private Long packageId;
    private String productName;
    private String fromAddress;
    private String toAddress;
    private String pickUpDate;
    private String dropDate;
    private CarrierRoute.TransportType transportType;

    // Availability status
    private Boolean hasRoutes; // true if routes found, false if empty
    private Integer routeCount;
    private String message; // "No routes available..." or "Found X routes"

    // Matching routes list (empty if none found)
    private List<RouteSelectionResponse> matchingRoutes;
}