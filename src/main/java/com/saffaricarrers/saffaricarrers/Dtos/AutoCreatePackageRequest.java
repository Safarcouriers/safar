package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.Data;

@Data
public class AutoCreatePackageRequest {
    private Long routeId;

    // Sender fills these in the UI (minimal info needed)
    private String fromAddress;
    private String toAddress;
    private Double latitude;
    private Double longitude;
    private Double toLatitude;
    private Double toLongitude;
    private Long addressId;

    // Optional product info — defaults applied in service if missing
    private String productName;
    private String productDescription;
    private Double productValue;
    private Double weight;

    // Optional — will be inferred from route if absent
    private String pickUpDate;  // yyyy-MM-dd
    private String dropDate;    // yyyy-MM-dd
}