package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarrierRouteSimpleDto {
    private Long routeId;
    private Long carrierId;
    private String userId;
    private String carrierName;
    private String fromLocation;
    private String toLocation;
    private LocalDate availableDate;
    private CarrierRoute.TransportType transportType;
    private Double availableWeight;
    private Integer availableQuantity;
    private double latitude;
    private double longitude;
    private double distanceKm;

}