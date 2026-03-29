package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import lombok.Data;

@Data
public  class RouteMatchingRequest {
    private double fromLatitude;
    private double fromLongitude;
    private double toLatitude;
    private double toLongitude;

    // Matching parameters
    private double directionToleranceDegrees = 45.0;  // How much deviation in direction is acceptable
    private double maxDeviationKm = 20.0;             // Max deviation from straight route
    private int maxResults = 30;

    // Filters
    private CarrierRoute.TransportType transportType;
    private Boolean includeInsuredOnly = false;

}