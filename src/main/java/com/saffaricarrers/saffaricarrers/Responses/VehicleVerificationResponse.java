package com.saffaricarrers.saffaricarrers.Responses;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleVerificationResponse {
    private String verificationType; // "RC" or "DL"
    private String status; // "VERIFIED", "FAILED", "PENDING"
    private String message;
    private String verificationId;
    private String referenceId;
    private String verifiedName;
    private Boolean nameMatchWithAadhaar;
    private String aadhaarName;
    private String vehicleNumber; // For RC
    private String vehicleClass; // For RC
    private String dlValidityFrom; // For DL
    private String dlValidityTo; // For DL
    private String dlVehicleClasses; // For DL
    private Boolean canRetry;
    private String overallStatus; // Overall verification status for the carrier
}