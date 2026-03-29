package com.saffaricarrers.saffaricarrers.Responses;

import com.saffaricarrers.saffaricarrers.Dtos.*;
import com.saffaricarrers.saffaricarrers.Entity.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// ==================== MAIN DASHBOARD DTO ====================
import lombok.*;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminDashboardResponse {
    private UserStatsDto userStats;
    private VerificationStatsDto verificationStats;
    private PackageStatsDto1 packageStats;  // Explicitly specify the Dtos version
    private DeliveryStatsDto deliveryStats;
    private RevenueStatsDto revenueStats;
    private CommissionStatsDto commissionStats;
    private TodayOverviewDto todayOverview;
    private LocalDateTime generatedAt;
}