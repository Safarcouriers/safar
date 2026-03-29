package com.saffaricarrers.saffaricarrers.Dtos;
import lombok.Data;

@Data
public class CarrierStatsDto {
    private Long totalRoutes;
    private Long activeRoutes;
    private Long completedRoutes;
    private Long totalDeliveries;
    private Long completedDeliveries;
    private Double completionRate;

    public Double getCompletionRate() {
        if (totalDeliveries == 0) return 0.0;
        return ((double) completedDeliveries / totalDeliveries) * 100;
    }
}