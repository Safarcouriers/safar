package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import lombok.Data;

@Data
public class PackageSearchRequest {
    private double fromLatitude;
    private double fromLongitude;
    private double toLatitude;
    private double toLongitude;
    private double radiusKm = 10.0; // default 10km radius
    private double corridorWidthKm = 5.0; // default 5km corridor width
    private CarrierRoute.TransportType transportType;
    private Integer maxResults = 50; // limit results
}