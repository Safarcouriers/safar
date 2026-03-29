package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TodayOverviewDto {
    private Long packagesCreatedToday;
    private Long requestsCreatedToday;
    private Long deliveriesCompletedToday;
    private Double revenueToday;
    private Double commissionToday;
    private Long newUsersToday;
    private Long activeTripsToday;
}