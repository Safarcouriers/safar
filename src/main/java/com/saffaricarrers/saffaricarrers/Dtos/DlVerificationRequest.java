package com.saffaricarrers.saffaricarrers.Dtos;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DlVerificationRequest {
    private String dlNumber;
    private String dob; // Format: YYYY-MM-DD
}