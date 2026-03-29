package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.Data;

@Data
public class PaymentCreateRequest {
    private Boolean includeInsurance = false;
    private String paymentNote;
}