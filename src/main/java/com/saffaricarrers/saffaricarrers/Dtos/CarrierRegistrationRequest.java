package com.saffaricarrers.saffaricarrers.Dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CarrierRegistrationRequest {
    @NotNull(message = "User ID is required")
    private String userId;

    // Additional carrier-specific fields
    private String preferredWorkingHours;
    private String vehicleType;
    private String licenseNumber;
    private Boolean hasVehicle;
    private String emergencyContactName;
    private String emergencyContactNumber;
    private String preferredDeliveryAreas;
}