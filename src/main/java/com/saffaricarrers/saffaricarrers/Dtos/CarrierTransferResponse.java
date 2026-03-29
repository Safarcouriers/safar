package com.saffaricarrers.saffaricarrers.Dtos;


import lombok.Data;

@Data
public class CarrierTransferResponse {
    private String transferId;
    private Double amount;
    private String status;
    private String carrierName;
    private String accountNumber;
}