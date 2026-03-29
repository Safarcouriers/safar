package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserStatsDto {
    private Long totalUsers;
    private Long senderUsers;
    private Long carrierUsers;
    private Long bothUsers;
    private Long verifiedUsers;
    private Long unverifiedUsers;
    private Long activeUsers;
    private Long inactiveUsers;
    private Map<String, Long> genderBreakdown;
}
