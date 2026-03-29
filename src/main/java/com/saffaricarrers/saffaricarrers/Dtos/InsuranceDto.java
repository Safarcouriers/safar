package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.Insurance;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InsuranceDto {
    private Long insuranceId;
    private Long packageId;
    private Double productValue;
    private Double insuranceAmount;
    private Double coveragePercentage;
    private Insurance.InsuranceStatus status;
    private String policyNumber;
    private LocalDateTime validUntil;
    private LocalDateTime createdAt;
}
