package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class OtpVerificationRequest {
    @NotBlank(message = "Reference ID is required")
    private String referenceId;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 digits")
    private String otp;

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}