package com.saffaricarrers.saffaricarrers.Responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PerformanceMetricsResponse {
    private Double deliverySuccessRate;
    private Double averageDeliveryTime;
    private Double customerSatisfactionScore;
    private Double carrierUtilizationRate;
    private Long totalTripsCompleted;
    private Long totalDeliveriesCompleted;
    private Double platformGrowthRate;
    private LocalDateTime generatedAt;
}