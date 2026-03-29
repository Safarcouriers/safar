package com.saffaricarrers.saffaricarrers.Dtos;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoCreateResult {
    private boolean success;
    private String message;

    // Created route (when auto-creating route for carrier)
    private Long createdRouteId;

    // Created package (when auto-creating package for sender)
    private Long createdPackageId;

    // The delivery request that was sent
    private Long requestId;

    // Pricing info for the request
    private Double totalAmount;
    private Double platformCommission;
    private Double carrierEarning;
}