package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDetailDto {
    private String userId;
    private String fullName;
    private String email;
    private String mobile;
    private User.UserType userType;
    private Boolean verified;
    private User.VerificationStatus verificationStatus;
    private User.UserStatus status;
    private LocalDateTime createdAt;
    private String profileUrl;
}