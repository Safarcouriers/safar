package com.saffaricarrers.saffaricarrers.Dtos;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LocationTrackingDto {
    private Long trackingId;
    private Long packageId;
    private String carrierName;
    private Double latitude;
    private Double longitude;
    private String address;
    private LocalDateTime timestamp;
}