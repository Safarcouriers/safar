package com.saffaricarrers.saffaricarrers.Responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL) // ✅ THIS FIXES THE 500 ERROR
public class PackageResponse {
    private Long packageId;
    private String senderName;
    private String productName;
    private String productDescription;
    private Double productValue;
    private String productType;
    private String transportType;
    private Double weight;
    private Double length;
    private Double width;
    private Double height;
    private List<String> productImages;
    private String productInvoiceImage;
    private String fromAddress;
    private String toAddress;
    private String pickUpDate;
    private String dropDate;
    private String availableTime;
    private String deadlineTime;
    private Double tripCharge;
    private Double pricePerKg;
    private Double pricePerTon;
    private Boolean insurance;
    private Package.PackageStatus status;
    private String pickupOtp;
    private String deliveryOtp;
    private LocalDateTime createdAt;
    private String url;
}
