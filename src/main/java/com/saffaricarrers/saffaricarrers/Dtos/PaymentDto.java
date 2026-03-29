package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.Payment;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentDto {
    private Long paymentId;
    private Long packageId;
    private Double totalAmount;
    private Double deliveryCharge;
    private Double insuranceAmount;
    private Double platformCommission;
    private Payment.PaymentMethod paymentMethod;
    private Payment.PaymentStatus paymentStatus;
    private String transactionId;
    private LocalDateTime createdAt;
}