package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.RoutePricing;
import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import lombok.Data;

import java.time.LocalDateTime;
import com.saffaricarrers.saffaricarrers.Entity.RoutePricing;
import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class GeospatialPackageResponse {
    private Long packageId;
    private String senderName;
    private String senderId;
    private String senderMobile;
    private String productName;
    private String productDescription;
    private Double productValue;
    private RoutePricing.ProductType productType;
    private CarrierRoute.TransportType transportType;
    private Double weight;
    private Double length;
    private Double width;
    private Double height;
    private String fromAddress;
    private String toAddress;
    private Double latitude;
    private Double longitude;
    private String pickUpDate;
    private String dropDate;
    private Double tripCharge;
    private Double pricePerKg;
    private Double pricePerTon;
    private Boolean insurance;
    private Package.PackageStatus status;
    private LocalDateTime createdAt;
    private String pickupOtp;
    private String deliveryOtp;
    private String availableTime;
    private String deadlineTime;
    // Geospatial specific fields
    private Double distanceFromSearchPoint; // in kilometers
    private Double distanceFromRoute; // distance from the route line
    private String distanceCategory; // NEARBY, FAR, BETWEEN, ON_THE_WAY
    private Boolean isOnRoute; // if package is along the search route
    private String url;
}