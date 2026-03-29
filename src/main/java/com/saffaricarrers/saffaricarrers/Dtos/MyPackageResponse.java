package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.*;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

// ==================== MY PACKAGES TAB (Screen 4) ====================

/**
 * Response for My Packages list view
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyPackageResponse {
    private Long packageId;
    private String productName;
    private String productImage;
    private String fromAddress;
    private String toAddress;
    private Double price;
    private Package.PackageStatus status;
    private String eta; // Expected delivery date
    private LocalDateTime createdAt;

    // Carrier information (if matched)
    private String carrierName;
    private String carrierPhone;
    private String carrierProfileImage;
    private String carrierVehicleType;
    private Long deliveryRequestId;
}