package com.saffaricarrers.saffaricarrers.Dtos;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RcVerificationRequest {
    private String rcNumber;
}