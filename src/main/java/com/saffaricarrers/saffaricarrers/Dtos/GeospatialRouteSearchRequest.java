package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import lombok.Data;

import java.time.LocalDate;
@Data
public class GeospatialRouteSearchRequest {
    private double fromLatitude;
    private double fromLongitude;
    private double toLatitude;
    private double toLongitude;

    // Search radius parameters (in kilometers)
    private double nearbyRadiusKm = 2.0;        // Within 2km = NEARBY
    private double farRadiusKm = 20.0;          // Within 20km = FAR
    private double betweenBufferKm = 10.0;      // 10km buffer for BETWEEN area
    private double corridorWidthKm = 5.0;       // 5km corridor width for ON_THE_WAY

    // Capacity requirements
    private double minCapacityKg = 0.0;         // Minimum available capacity required

    // Transport preferences
    private CarrierRoute.TransportType transportType;

    // Date filters
    private LocalDate startDate;
    private LocalDate endDate;

    // Pricing filters
    private Double maxPricePerTon;

    // Result limits
    private int maxResultsPerCategory = 10;
    private int totalMaxResults = 50;

    // Additional filters
    private Boolean includeFullRoutesOnly = false;  // Only routes with full capacity available
    private Boolean includeVerifiedCarriersOnly = true;

    @Override
    public String toString() {
        return "GeospatialRouteSearchRequest{" +
                "fromLatitude=" + fromLatitude +
                ", fromLongitude=" + fromLongitude +
                ", toLatitude=" + toLatitude +
                ", toLongitude=" + toLongitude +
                ", nearbyRadiusKm=" + nearbyRadiusKm +
                ", farRadiusKm=" + farRadiusKm +
                ", betweenBufferKm=" + betweenBufferKm +
                ", corridorWidthKm=" + corridorWidthKm +
                ", minCapacityKg=" + minCapacityKg +
                ", transportType=" + transportType +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", maxPricePerTon=" + maxPricePerTon +
                ", maxResultsPerCategory=" + maxResultsPerCategory +
                ", totalMaxResults=" + totalMaxResults +
                ", includeFullRoutesOnly=" + includeFullRoutesOnly +
                ", includeVerifiedCarriersOnly=" + includeVerifiedCarriersOnly +
                '}';
    }
}
