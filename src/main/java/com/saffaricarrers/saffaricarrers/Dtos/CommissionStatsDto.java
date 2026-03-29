package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommissionStatsDto {
    private Double totalPendingCommission;
    private Double totalCarrierEarnings;
    private Long carriersWithPendingCommission;
    private Double dailyCommission;
    private Long activeCarriers;
    private Long inactiveCarriers;
    private Long suspendedCarriers;
}