package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageSimpleDto {
    private Long packageId;
    private String senderId;
    private String senderName;
    private String productName;
    private Double weight;
    private CarrierRoute.TransportType transportType;
    private String fromAddress;
    private String toAddress;
    private String pickUpDate;
    private double latitude;
    private double longitude;
    private double distanceKm;
}