package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DayAnalyticsDto {
    private LocalDate date;
    private Long packagesCreated;
    private Long deliveriesCompleted;
    private Double revenue;
    private Double commission;
    private Long newUsers;
    private Long requestsCreated;
}