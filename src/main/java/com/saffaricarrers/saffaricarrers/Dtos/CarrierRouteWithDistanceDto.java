package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarrierRouteWithDistanceDto {
    private Long routeId;
    private String fromLocation;
    private String toLocation;
    private LocalDate availableDate;
    private LocalDate deadlineDate;
    private LocalTime availableTime;
    private CarrierRoute.TransportType transportType;
    private Double maxWeight;
    private Integer maxQuantity;
    private Double currentWeight;
    private Integer currentQuantity;
    private Double availableWeight;
    private Integer availableQuantity;
    private CarrierRoute.RouteStatus routeStatus;
    private double latitude;
    private double longitude;
    private double distanceKm;
    private CarrierProfileDto carrierProfile;
}
