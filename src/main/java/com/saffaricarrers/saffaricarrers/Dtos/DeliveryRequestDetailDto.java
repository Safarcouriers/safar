package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.DeliveryRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryRequestDetailDto {
    private Long requestId;
    private String packageName;
    private String senderName;
    private String carrierName;
    private String fromAddress;
    private String toAddress;
    private Double amount;
    private DeliveryRequest.RequestStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime deliveredAt;
}