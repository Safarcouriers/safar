package com.saffaricarrers.saffaricarrers.Responses;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CarrierProfileResponse {
    private Long carrierId;
    private String status;
    private Boolean isVerified;
    private Integer weeklyOrderCount;
    private BigDecimal totalEarnings;
    private BigDecimal pendingCommission;
    private BankDetailsResponse bankDetails;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}