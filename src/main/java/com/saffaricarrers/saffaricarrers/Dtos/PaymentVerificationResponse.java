package com.saffaricarrers.saffaricarrers.Dtos;
import lombok.Data;

@Data
public class PaymentVerificationResponse {
    private Long paymentId;
    private String status;
    private String message;
    private Double amount;
    private String razorpayPaymentId;
}