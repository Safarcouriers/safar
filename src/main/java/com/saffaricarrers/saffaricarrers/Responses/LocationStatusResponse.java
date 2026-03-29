package com.saffaricarrers.saffaricarrers.Responses;


import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LocationStatusResponse {
    private Long requestId;
    private boolean hasLocation;
    private Double latitude;
    private Double longitude;
    private String resolvedAddress;
    private Double speed;
    private Integer batteryLevel;
    private LocalDateTime recordedAt;
    private String status;
    private String carrierName;
    private String packageName;
}
