package com.saffaricarrers.saffaricarrers.Responses;

import com.saffaricarrers.saffaricarrers.Entity.User;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class VerificationStatusResponse {
    private String userId;
    private User.VerificationStatus verificationStatus;
    private Boolean isVerified;
    private Boolean canAccessFeatures;
    private String message;
    private String estimatedWaitTime;
    private LocalDateTime verificationStartedAt;
    private LocalDateTime lastUpdatedAt;
    private Boolean canRetry;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public User.VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(User.VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public Boolean getVerified() {
        return isVerified;
    }

    public void setVerified(Boolean verified) {
        isVerified = verified;
    }

    public Boolean getCanAccessFeatures() {
        return canAccessFeatures;
    }

    public void setCanAccessFeatures(Boolean canAccessFeatures) {
        this.canAccessFeatures = canAccessFeatures;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getEstimatedWaitTime() {
        return estimatedWaitTime;
    }

    public void setEstimatedWaitTime(String estimatedWaitTime) {
        this.estimatedWaitTime = estimatedWaitTime;
    }

    public LocalDateTime getVerificationStartedAt() {
        return verificationStartedAt;
    }

    public void setVerificationStartedAt(LocalDateTime verificationStartedAt) {
        this.verificationStartedAt = verificationStartedAt;
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public Boolean getCanRetry() {
        return canRetry;
    }

    public void setCanRetry(Boolean canRetry) {
        this.canRetry = canRetry;
    }
}