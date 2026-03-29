package com.saffaricarrers.saffaricarrers.Dtos;


import lombok.Data;

@Data
public class LocationUpdateRequest {
    private Double latitude;       // Required
    private Double longitude;      // Required
    private String resolvedAddress; // Reverse-geocoded by frontend
    private Double speed;          // km/h from GPS (optional)
    private Integer batteryLevel;  // 0-100 (optional)
}
