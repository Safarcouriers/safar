package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AlertDto {
    private String alertId;
    private String title;
    private String description;
    private String severity; // CRITICAL, WARNING, INFO
    private String type; // PAYMENT_ISSUE, VERIFICATION_STUCK, DELIVERY_DELAY, etc.
    private Long affectedCount;
    private LocalDateTime createdAt;
}