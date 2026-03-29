package com.saffaricarrers.saffaricarrers.Controller;
import com.saffaricarrers.saffaricarrers.Dtos.*;
import com.saffaricarrers.saffaricarrers.Responses.*;
import com.saffaricarrers.saffaricarrers.Services.AdminDashboardService;
import com.saffaricarrers.saffaricarrers.Services.FirebaseNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final FirebaseNotificationService firebaseNotificationService;

    // ==================== MAIN DASHBOARD ====================

    /**
     * Get complete dashboard statistics
     * GET /api/v1/admin/dashboard
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getDashboard() {
        try {
            AdminDashboardResponse dashboardData = adminDashboardService.getDashboardStats();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", dashboardData);
            response.put("message", "Dashboard data retrieved successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching dashboard: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Failed to fetch dashboard data",
                            "message", e.getMessage()
                    ));
        }
    }

    // ==================== USER ANALYTICS ====================

    /**
     * Get user statistics breakdown
     * GET /api/v1/admin/dashboard/users/stats
     */
    @GetMapping("/users/stats")
    public ResponseEntity<Map<String, Object>> getUserStats() {
        try {
            AdminDashboardResponse dashboard = adminDashboardService.getDashboardStats();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", dashboard.getUserStats());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching user stats: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get verified users list
     * GET /api/v1/admin/dashboard/users/verified
     */
    @GetMapping("/users/verified")
    public ResponseEntity<Map<String, Object>> getVerifiedUsers() {
        try {
            UserListResponse verifiedUsers = adminDashboardService.getVerifiedUsers();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", verifiedUsers);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching verified users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get unverified users list
     * GET /api/v1/admin/dashboard/users/unverified
     */
    @GetMapping("/users/unverified")
    public ResponseEntity<Map<String, Object>> getUnverifiedUsers() {
        try {
            UserListResponse unverifiedUsers = adminDashboardService.getUnverifiedUsers();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", unverifiedUsers);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching unverified users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== VERIFICATION ANALYTICS ====================

    /**
     * Get document verification statistics
     * GET /api/v1/admin/dashboard/verification/stats
     */
    @GetMapping("/verification/stats")
    public ResponseEntity<Map<String, Object>> getVerificationStats() {
        try {
            AdminDashboardResponse dashboard = adminDashboardService.getDashboardStats();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", dashboard.getVerificationStats());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching verification stats: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== PACKAGE ANALYTICS ====================

    /**
     * Get package statistics
     * GET /api/v1/admin/dashboard/packages/stats
     */
    @GetMapping("/packages/stats")
    public ResponseEntity<Map<String, Object>> getPackageStats() {
        try {
            AdminDashboardResponse dashboard = adminDashboardService.getDashboardStats();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", dashboard.getPackageStats());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching package stats: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== DELIVERY REQUEST ANALYTICS ====================

    /**
     * Get delivery request statistics
     * GET /api/v1/admin/dashboard/delivery/stats
     */
    @GetMapping("/delivery/stats")
    public ResponseEntity<Map<String, Object>> getDeliveryStats() {
        try {
            AdminDashboardResponse dashboard = adminDashboardService.getDashboardStats();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", dashboard.getDeliveryStats());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching delivery stats: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get pending delivery requests
     * GET /api/v1/admin/dashboard/delivery/pending
     */
    @GetMapping("/delivery/pending")
    public ResponseEntity<Map<String, Object>> getPendingDeliveries() {
        try {
            PendingDeliveriesResponse pending = adminDashboardService.getPendingDeliveries();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", pending);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching pending deliveries: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get completed delivery requests
     * GET /api/v1/admin/dashboard/delivery/completed
     */
    @GetMapping("/delivery/completed")
    public ResponseEntity<Map<String, Object>> getCompletedDeliveries() {
        try {
            CompletedDeliveriesResponse completed = adminDashboardService.getCompletedDeliveries();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", completed);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching completed deliveries: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== REVENUE ANALYTICS ====================

    /**
     * Get revenue statistics
     * GET /api/v1/admin/dashboard/revenue/stats
     */
    @GetMapping("/revenue/stats")
    public ResponseEntity<Map<String, Object>> getRevenueStats() {
        try {
            AdminDashboardResponse dashboard = adminDashboardService.getDashboardStats();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", dashboard.getRevenueStats());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching revenue stats: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== COMMISSION ANALYTICS ====================

    /**
     * Get commission statistics
     * GET /api/v1/admin/dashboard/commission/stats
     */
    @GetMapping("/commission/stats")
    public ResponseEntity<Map<String, Object>> getCommissionStats() {
        try {
            AdminDashboardResponse dashboard = adminDashboardService.getDashboardStats();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", dashboard.getCommissionStats());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching commission stats: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== TODAY'S OVERVIEW ====================

    /**
     * Get today's overview
     * GET /api/v1/admin/dashboard/today
     */
    @GetMapping("/today")
    public ResponseEntity<Map<String, Object>> getTodayOverview() {
        try {
            AdminDashboardResponse dashboard = adminDashboardService.getDashboardStats();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", dashboard.getTodayOverview());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching today's overview: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== DAY-WISE ANALYTICS ====================

    /**
     * Get day-wise analytics for last N days
     * GET /api/v1/admin/dashboard/analytics/daywise?days=30
     */
    @GetMapping("/analytics/daywise")
    public ResponseEntity<Map<String, Object>> getDayWiseAnalytics(
            @RequestParam(value = "days", defaultValue = "30") int days) {
        try {
            DayWiseAnalyticsResponse analytics = adminDashboardService.getDayWiseAnalytics(days);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", analytics);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching day-wise analytics: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== SUMMARY ENDPOINTS ====================

    /**
     * Get quick summary of all key metrics
     * GET /api/v1/admin/dashboard/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        try {
            AdminDashboardResponse dashboard = adminDashboardService.getDashboardStats();

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalUsers", dashboard.getUserStats().getTotalUsers());
            summary.put("verifiedUsers", dashboard.getUserStats().getVerifiedUsers());
            summary.put("unverifiedUsers", dashboard.getUserStats().getUnverifiedUsers());
            summary.put("totalPackages", dashboard.getPackageStats().getTotalPackages());
            summary.put("deliveredPackages", dashboard.getPackageStats().getDeliveredPackages());
            summary.put("totalDeliveryRequests", dashboard.getDeliveryStats().getTotalRequests());
            summary.put("pendingRequests", dashboard.getDeliveryStats().getPendingRequests());
            summary.put("completedDeliveries", dashboard.getDeliveryStats().getDeliveredRequests());
            summary.put("deliverySuccessRate", dashboard.getDeliveryStats().getDeliverySuccessRate());
            summary.put("totalRevenue", dashboard.getRevenueStats().getTotalRevenue());
            summary.put("totalCommission", dashboard.getRevenueStats().getTotalCommission());
            summary.put("pendingCommission", dashboard.getCommissionStats().getTotalPendingCommission());
            summary.put("dailyRevenue", dashboard.getTodayOverview().getRevenueToday());
            summary.put("packagesCreatedToday", dashboard.getTodayOverview().getPackagesCreatedToday());
            summary.put("deliveriesCompletedToday", dashboard.getTodayOverview().getDeliveriesCompletedToday());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", summary);
            response.put("generatedAt", dashboard.getGeneratedAt());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching summary: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    @PostMapping("/notification")
    public void testNotification()
    {
        firebaseNotificationService.sendNotification("f","FG","FG");
    }
}