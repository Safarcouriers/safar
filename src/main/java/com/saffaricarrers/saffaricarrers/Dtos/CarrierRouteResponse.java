package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import com.saffaricarrers.saffaricarrers.Responses.RoutePricingResponse;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CarrierRouteResponse {
    private Long routeId;
    private String carrierName;
    private String fromLocation;
    private String toLocation;
    private LocalDate availableDate;
    private LocalTime availableTime;

    // ✅ ADDED
    private LocalDate deadlineDate;
    private String deadlineTime;

    private CarrierRoute.TransportType transportType;
    private Double maxWeight;
    private Integer maxQuantity;
    private Double currentWeight;
    private Integer currentQuantity;
    private CarrierRoute.RouteStatus routeStatus;
    private List<RoutePricingResponse> pricing;
    private LocalDateTime createdAt;

    // Destination coordinates
    private double toLatitude;
    private double toLongitude;

    @Override
    public String toString() {
        return "CarrierRouteResponse{" +
                "routeId=" + routeId +
                ", carrierName='" + carrierName + '\'' +
                ", fromLocation='" + fromLocation + '\'' +
                ", toLocation='" + toLocation + '\'' +
                ", availableDate=" + availableDate +
                ", availableTime=" + availableTime +
                ", deadlineDate=" + deadlineDate +
                ", deadlineTime='" + deadlineTime + '\'' +
                ", transportType=" + transportType +
                ", maxWeight=" + maxWeight +
                ", maxQuantity=" + maxQuantity +
                ", currentWeight=" + currentWeight +
                ", currentQuantity=" + currentQuantity +
                ", routeStatus=" + routeStatus +
                ", pricing=" + pricing +
                ", createdAt=" + createdAt +
                ", toLatitude=" + toLatitude +
                ", toLongitude=" + toLongitude +
                '}';
    }
}