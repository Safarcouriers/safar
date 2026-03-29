package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryStatsDto {
    private Long totalRequests;
    private Long pendingRequests;
    private Long acceptedRequests;
    private Long pickedUpRequests;
    private Long inTransitRequests;
    private Long deliveredRequests;
    private Long rejectedRequests;
    private Long cancelledRequests;
    private Double deliverySuccessRate;
    private Double averageDeliveryAmount;
}