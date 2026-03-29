package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VerificationStatsDto {
    private Long totalVerifications;
    private Long verifiedDocuments;
    private Long pendingVerifications;
    private Long rejectedDocuments;
    private Long aadhaarVerifications;
    private Long panVerifications;
    private Map<String, Long> usersStuckAtVerification;
}