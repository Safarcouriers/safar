package com.saffaricarrers.saffaricarrers.Responses;

import com.saffaricarrers.saffaricarrers.Entity.User;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserProfileResponse {
    private String userId;
    private String fullName;
    private String email;
    private String mobile;
    private Integer age;
    private String gender;
    private User.UserType userType;
    private Boolean isVerified;
    private User.VerificationStatus verificationStatus;
    private User.UserStatus userStatus;
    private Boolean canAccessFeatures;
    private List<AddressResponse> addresses;
    private CarrierProfileResponse carrierProfile;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String maskedAadharNumber;
    private String maskedPanNumber;

    private String profileUrl;
    private String aadharFrontUrl;
    private String aadharBackUrl;
    private String panCardUrl;
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public User.UserType getUserType() {
        return userType;
    }

    public void setUserType(User.UserType userType) {
        this.userType = userType;
    }

    public Boolean getVerified() {
        return isVerified;
    }

    public void setVerified(Boolean verified) {
        isVerified = verified;
    }

    public User.VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(User.VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public User.UserStatus getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(User.UserStatus userStatus) {
        this.userStatus = userStatus;
    }

    public Boolean getCanAccessFeatures() {
        return canAccessFeatures;
    }

    public void setCanAccessFeatures(Boolean canAccessFeatures) {
        this.canAccessFeatures = canAccessFeatures;
    }

    public List<AddressResponse> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<AddressResponse> addresses) {
        this.addresses = addresses;
    }

    public CarrierProfileResponse getCarrierProfile() {
        return carrierProfile;
    }

    public void setCarrierProfile(CarrierProfileResponse carrierProfile) {
        this.carrierProfile = carrierProfile;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getMaskedAadharNumber() {
        return maskedAadharNumber;
    }

    public void setMaskedAadharNumber(String maskedAadharNumber) {
        this.maskedAadharNumber = maskedAadharNumber;
    }

    public String getMaskedPanNumber() {
        return maskedPanNumber;
    }

    public void setMaskedPanNumber(String maskedPanNumber) {
        this.maskedPanNumber = maskedPanNumber;
    }

    public String getProfileUrl() {
        return profileUrl;
    }

    public void setProfileUrl(String profileUrl) {
        this.profileUrl = profileUrl;
    }

    public String getAadharFrontUrl() {
        return aadharFrontUrl;
    }

    public void setAadharFrontUrl(String aadharFrontUrl) {
        this.aadharFrontUrl = aadharFrontUrl;
    }

    public String getAadharBackUrl() {
        return aadharBackUrl;
    }

    public void setAadharBackUrl(String aadharBackUrl) {
        this.aadharBackUrl = aadharBackUrl;
    }

    public String getPanCardUrl() {
        return panCardUrl;
    }

    public void setPanCardUrl(String panCardUrl) {
        this.panCardUrl = panCardUrl;
    }
}
