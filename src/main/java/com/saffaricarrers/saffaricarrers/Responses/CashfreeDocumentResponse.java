package com.saffaricarrers.saffaricarrers.Responses;

import lombok.Data;

@Data
public class CashfreeDocumentResponse {
    private boolean success;
    private boolean valid;
    private String name;
    private String message;

    // ✅ ADD THESE FIELDS
    private String nameMatch;
    private Double nameMatchScore;
    private String aadhaarSeedingStatus;
    private String aadhaarSeedingStatusDesc;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getNameMatch() {
        return nameMatch;
    }

    public void setNameMatch(String nameMatch) {
        this.nameMatch = nameMatch;
    }

    public Double getNameMatchScore() {
        return nameMatchScore;
    }

    public void setNameMatchScore(Double nameMatchScore) {
        this.nameMatchScore = nameMatchScore;
    }

    public String getAadhaarSeedingStatus() {
        return aadhaarSeedingStatus;
    }

    public void setAadhaarSeedingStatus(String aadhaarSeedingStatus) {
        this.aadhaarSeedingStatus = aadhaarSeedingStatus;
    }

    public String getAadhaarSeedingStatusDesc() {
        return aadhaarSeedingStatusDesc;
    }

    public void setAadhaarSeedingStatusDesc(String aadhaarSeedingStatusDesc) {
        this.aadhaarSeedingStatusDesc = aadhaarSeedingStatusDesc;
    }
}

