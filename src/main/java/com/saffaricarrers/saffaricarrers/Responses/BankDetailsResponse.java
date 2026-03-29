package com.saffaricarrers.saffaricarrers.Responses;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BankDetailsResponse {
    private Long bankDetailsId;
    private String accountHolderName;
    private String maskedAccountNumber;
    private String ifscCode;
    private String bankName;
    private String branchName;
    private Boolean isVerified;
    private LocalDateTime createdAt;
}