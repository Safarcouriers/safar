package com.saffaricarrers.saffaricarrers.Responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RevenueBreakdownResponse {
    private Double totalRevenue;
    private Double platformCommission;
    private Double carrierPayouts;
    private Double insuranceRevenue;
    private Double pendingRevenue;
    private LocalDate startDate;
    private LocalDate endDate;
}
