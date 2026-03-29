package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RevenueStatsDto {
    private Long totalPayments;
    private Long completedPayments;
    private Long pendingPayments;
    private Long failedPayments;
    private Long onlinePayments;
    private Long codePayments;
    private Double totalRevenue;
    private Double totalCommission;
    private Double totalCarrierPayouts;
    private Double totalInsuranceCollected;
    private Double dailyAverageRevenue;
}