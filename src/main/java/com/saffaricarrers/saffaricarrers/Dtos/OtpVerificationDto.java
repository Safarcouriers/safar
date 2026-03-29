package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.Data;

@Data
public class OtpVerificationDto {
    private Long packageId;
    private String otp;
    private String verificationType; // "PICKUP" or "DELIVERY"
}