package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.Data;

@Data
public class PaymentRefundResponse {
    private String refundId;
    private Double amount;
    private String status;
    private String reason;
}