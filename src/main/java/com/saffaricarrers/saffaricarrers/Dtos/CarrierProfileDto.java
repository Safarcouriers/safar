package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierProfile;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CarrierProfileDto {
    private Long carrierId;
    private String userUid;
    private Boolean isVerified;
    private CarrierProfile.CarrierStatus status;
    private Integer weeklyOrderCount;
    private BigDecimal totalEarnings;
    private BigDecimal pendingCommission;
    private BankDetailsDto bankDetails;
    private LocalDateTime createdAt;
}