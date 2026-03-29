package com.saffaricarrers.saffaricarrers.Responses;

import com.saffaricarrers.saffaricarrers.Dtos.CarrierRouteResponse;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DeliveryRequestResponse {
    private Long requestId;
    private Long packageId;
    private Long routeId;
    private String senderName;
    private String carrierName;
    private String status;
    private Double totalAmount;
    private Double platformCommission;
    private Double carrierEarning;
    private String pickupOtp;
    private String deliveryOtp;
    private LocalDateTime requestedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
    private String senderNote;
    private String carrierNote;
    private PackageResponse packageDetails;
    private CarrierRouteResponse routeDetails;
}