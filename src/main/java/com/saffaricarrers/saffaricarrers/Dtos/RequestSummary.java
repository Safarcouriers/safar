package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestSummary {
    private Long requestId;
    private Long packageId;
    private String packageName;
    private String productImage;
    private String senderName;
    private String carrierName;
    private String fromAddress;
    private String toAddress;
    private Double amount;
    private LocalDateTime requestedAt;
    private String senderNote;
    private String carrierNote;
}