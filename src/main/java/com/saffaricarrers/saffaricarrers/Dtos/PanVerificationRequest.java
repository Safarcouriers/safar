package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class PanVerificationRequest {
    @NotBlank(message = "PAN number is required")
    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "PAN number must be in format: ABCDE1234F")
    private String panNumber;

    public String getPanNumber() {
        return panNumber;
    }

    public void setPanNumber(String panNumber) {
        this.panNumber = panNumber;
    }
}