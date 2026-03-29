package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommissionDetailsDto {
    private Long carrierId;
    private String carrierName;
    private Double totalPendingCommission;
    private Integer pendingTripsCount;
    private List<TripCommissionDto> pendingTrips;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TripCommissionDto {
        private Long deliveryRequestId;
        private Long packageId;
        private String packageName;
        private Double deliveryAmount;
        private Double commissionDue;
        private LocalDateTime deliveredAt;
        private String senderName;
        private String fromAddress;
        private String toAddress;
    }
}
