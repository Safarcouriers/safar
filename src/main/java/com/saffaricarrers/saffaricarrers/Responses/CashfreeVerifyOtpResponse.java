package com.saffaricarrers.saffaricarrers.Responses;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashfreeVerifyOtpResponse {
    private boolean success;
    private boolean otpValid;
    private String message;
    private String verifiedName;
    private String address;
    private String dateOfBirth;
    private String gender;
    private String email;

    // Additional constructors for backward compatibility
    public CashfreeVerifyOtpResponse(boolean success, boolean otpValid, String message) {
        this.success = success;
        this.otpValid = otpValid;
        this.message = message;
    }

    // Getters and Setters (if not using Lombok @Data)
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isOtpValid() {
        return otpValid;
    }

    public void setOtpValid(boolean otpValid) {
        this.otpValid = otpValid;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getVerifiedName() {
        return verifiedName;
    }

    public void setVerifiedName(String verifiedName) {
        this.verifiedName = verifiedName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}