package com.saffaricarrers.saffaricarrers.Responses;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileCompletionResponse {
    private String userId;
    private boolean documentVerificationCompleted;
    private boolean documentImagesUploaded;
    private boolean userDetailsCompleted;
    private boolean profileImageUploaded;
    private boolean addressCompleted;
    private boolean carrierProfileCompleted;
    private boolean profileCompleted;
    private int completionPercentage;
    private String nextStep;
    private String nextStepMessage;
}