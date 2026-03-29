package com.saffaricarrers.saffaricarrers.Entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@NamedEntityGraphs({
        @NamedEntityGraph(
                name = "User.basic",
                attributeNodes = {}
        ),
        @NamedEntityGraph(
                name = "User.withCarrierProfile",
                attributeNodes = {
                        @NamedAttributeNode("carrierProfile")  // ✅ no subgraph, no bankDetails
                }
        )
})

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId; // Firebase UID

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String mobile;

    private Integer age;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    private UserType userType;

    @Column(nullable = false)
    private Boolean isVerified = false;

    private String profileUrl;
    private String aadharFrontUrl;
    private String aadharBackUrl;
    private String panCardUrl;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.INACTIVE;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Address> addresses;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CarrierProfile carrierProfile;
    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Package> packages;
    @CreationTimestamp
    private LocalDateTime createdAt;
    private String FcmToken;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    public enum Gender {MALE, FEMALE, OTHERS}
    public enum UserType {SENDER, CARRIER, BOTH}
    public enum VerificationStatus {PENDING, VERIFIED, REJECTED}
    public enum UserStatus {ACTIVE, INACTIVE, SUSPENDED}
    // Custom getter/setter for isVerified to maintain consistency
    public Boolean getVerified() {
        return isVerified;
    }
    public void setVerified(Boolean verified) {
        this.isVerified = verified;
        if (verified && this.verificationStatus == VerificationStatus.VERIFIED) {
            this.status = UserStatus.ACTIVE;
        }
    }





    public Long getId() {

        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public UserType getUserType() {
        return userType;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
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

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public List<Address> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<Address> addresses) {
        this.addresses = addresses;
    }

    public CarrierProfile getCarrierProfile() {
        return carrierProfile;
    }

    public void setCarrierProfile(CarrierProfile carrierProfile) {
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
}