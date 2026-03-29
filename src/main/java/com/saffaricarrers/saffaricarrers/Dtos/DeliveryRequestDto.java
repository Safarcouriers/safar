package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.Data;

@Data
public class DeliveryRequestDto {
    private Long packageId;
    private Long routeId;
    private String senderNote;
}