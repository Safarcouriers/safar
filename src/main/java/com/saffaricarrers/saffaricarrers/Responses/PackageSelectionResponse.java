package com.saffaricarrers.saffaricarrers.Responses;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import com.saffaricarrers.saffaricarrers.Entity.RoutePricing;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageSelectionResponse {
    private Long packageId;
    private String productName;
    private String productImage;
    private String productDescription;
    private String fromAddress;
    private String toAddress;
    private String pickUpDate;
    private String dropDate;
    private Double weight;
    private Double productValue;
    private RoutePricing.ProductType productType;
    private CarrierRoute.TransportType transportType;
    private String availableTime;
    private String deadlineTime;
    // Matching info
    private Boolean transportTypeMatches;
    private Double estimatedCost;

    // Can user send request?
    private Boolean canRequest;
    private String reasonCannotRequest;
}