package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.DeliveryRequest;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeliveryRequestResponse {
    private Long requestId;
    private Long packageId;
    private String packageName;
    private Long routeId;
    private String senderName;
    private String carrierName;
    private DeliveryRequest.RequestStatus status;
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
    private LocalDateTime createdAt;
    private String pickupPhoto;
    private String deliveryPhoto;
}