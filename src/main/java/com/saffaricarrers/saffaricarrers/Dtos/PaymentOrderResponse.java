package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.Data;

@Data
public class PaymentOrderResponse {
    private Long paymentId;
    private String razorpayOrderId;
    private Double amount;
    private String currency;
    private String receipt;
    private String razorpayKeyId;
    private Double deliveryAmount;
    private Double insuranceAmount;
    private Double platformCommission;
    private Double carrierAmount;
}