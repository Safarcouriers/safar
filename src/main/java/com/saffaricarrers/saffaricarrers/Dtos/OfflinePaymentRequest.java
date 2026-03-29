package com.saffaricarrers.saffaricarrers.Dtos;
import com.saffaricarrers.saffaricarrers.Entity.Payment;
import lombok.Data;

@Data
public class OfflinePaymentRequest {
    private Payment.PaymentMethod paymentMethod; // OFFLINE or COD
    private String note;
}