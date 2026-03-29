package com.saffaricarrers.saffaricarrers.Dtos;


import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import lombok.Data;

import java.time.LocalDate;

@Data
public class GeospatialSearchRequest {
    // From/To coordinates...
    private double fromLatitude;
    private double fromLongitude;
    private double toLatitude;
    private double toLongitude;

    // Search parameters
    private double nearbyRadiusKm = 5.0;
    private double farRadiusKm = 25.0;
    private double corridorWidthKm = 10.0;
    private double betweenBufferKm = 15.0;

    // Filters
    private CarrierRoute.TransportType transportType;
    private Integer maxResultsPerCategory = 20;
    private Boolean includeInsuredOnly = false;
    private Double minPackageValue;
    private Double maxPackageValue;

    // Date filters (add these)
    private LocalDate pickUpDate;
    private LocalDate dropDate;
}
