package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationSearchRequest {
    private double latitude;
    private double longitude;
    private double radius = 10.0;
    private double minCapacity = 0.0;
    private CarrierRoute.TransportType transportType;
    private LocalDate startDate;
    private LocalDate endDate;
}