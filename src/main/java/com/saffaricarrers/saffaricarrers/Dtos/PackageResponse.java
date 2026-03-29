package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import com.saffaricarrers.saffaricarrers.Entity.RoutePricing;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PackageResponse {
    private Long packageId;
    private String senderName;
    private String productName;
    private String productDescription;
    private Double productValue;
    private RoutePricing.ProductType productType;
    private CarrierRoute.TransportType transportType;
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
