package com.saffaricarrers.saffaricarrers.Dtos;
import lombok.Data;
@Data
public class LocationUpdateDto {
    private Long packageId;
    private String carrierUid;
    private Double latitude;
    private Double longitude;
}