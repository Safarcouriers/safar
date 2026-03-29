package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import lombok.Data;
import java.time.LocalDate;

@Data
public class RouteSearchRequest {
    private String fromLocation;
    private String toLocation;
    private LocalDate preferredDate;
    private Double packageWeight;
    private String productType;
    private double fromLatitude;
    private double fromLongitude;
    private double toLatitude;
    private double toLongitude;
    private double corridorWidthKm = 5.0; // Default 5km corridor width
    private CarrierRoute.TransportType transportType;
    private Double minCapacityKg = 0.0;
    private Integer maxResults = 50;
    private LocalDate startDate;
    private LocalDate endDate;
    private double nearPointRadiusKm = 10.0;  // Radius around start/end points

    private Boolean includeInsuredOnly = false;
    private Double minPackageValue;
    private Double maxPackageValue;
}
