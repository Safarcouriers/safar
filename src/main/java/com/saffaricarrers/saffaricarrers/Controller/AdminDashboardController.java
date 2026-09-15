package com.saffaricarrers.saffaricarrers.Controller;

import com.saffaricarrers.saffaricarrers.Dtos.*;
import com.saffaricarrers.saffaricarrers.Responses.*;
import com.saffaricarrers.saffaricarrers.Services.AdminDashboardService;
import com.saffaricarrers.saffaricarrers.Services.FirebaseNotificationService;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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
                    .body(Map.of("success", false, "error", "Failed to fetch dashboard data", "message", e.getMessage()));
        }
    }

    // ==================== USER ANALYTICS ====================

    @GetMapping("/users/stats")
    public ResponseEntity<Map<String, Object>> getUserStats() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", adminDashboardService.getUserStats());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching user stats: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/users/verified")
    public ResponseEntity<Map<String, Object>> getVerifiedUsers() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", adminDashboardService.getVerifiedUsers());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching verified users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/users/unverified")
    public ResponseEntity<Map<String, Object>> getUnverifiedUsers() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", adminDashboardService.getUnverifiedUsers());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching unverified users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== VERIFICATION ANALYTICS ====================

    @GetMapping("/verification/stats")
    public ResponseEntity<Map<String, Object>> getVerificationStats() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", adminDashboardService.getVerificationStats());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching verification stats: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== PACKAGE ANALYTICS ====================

    @GetMapping("/packages/stats")
    public ResponseEntity<Map<String, Object>> getPackageStats() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", adminDashboardService.getPackageStats());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching package stats: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== DELIVERY REQUEST ANALYTICS ====================

    @GetMapping("/delivery/stats")
    public ResponseEntity<Map<String, Object>> getDeliveryStats() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", adminDashboardService.getDeliveryStats());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching delivery stats: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/delivery/pending")
    public ResponseEntity<Map<String, Object>> getPendingDeliveries() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", adminDashboardService.getPendingDeliveries());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching pending deliveries: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/delivery/completed")
    public ResponseEntity<Map<String, Object>> getCompletedDeliveries() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", adminDashboardService.getCompletedDeliveries());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching completed deliveries: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== REVENUE ANALYTICS ====================

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

    @GetMapping("/commission/stats")
    public ResponseEntity<Map<String, Object>> getCommissionStats() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", adminDashboardService.getCommissionStats());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching commission stats: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== TODAY'S OVERVIEW ====================

    @GetMapping("/today")
    public ResponseEntity<Map<String, Object>> getTodayOverview() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", adminDashboardService.getTodayOverview());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching today's overview: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== DAY-WISE ANALYTICS ====================

    @GetMapping("/analytics/daywise")
    public ResponseEntity<Map<String, Object>> getDayWiseAnalytics(
            @RequestParam(value = "days", defaultValue = "30") int days) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", adminDashboardService.getDayWiseAnalytics(days));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching day-wise analytics: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== SUMMARY ====================

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

    // ==================== ORDERS (with payment info) ====================

    /**
     * GET /api/v1/admin/dashboard/orders
     *
     * Query params:
     *   status  – "all" | delivery status (PENDING, ACCEPTED, PICKED_UP, IN_TRANSIT, DELIVERED,
     *              REJECTED, CANCELLED) | "COMMISSION_PENDING" | "TRANSFER_PENDING"
     *   size    – max records to return (default 500)
     *
     * Each order row includes:
     *   requestId, packageName, senderName/Phone, carrierName/Phone,
     *   fromAddress, toAddress, status, requestedAt, deliveredAt,
     *   totalAmount, platformCommission, carrierAmount,
     *   paymentMethod, paymentStatus, carrierTransferStatus,
     *   razorpayPaymentId, razorpayOrderId, razorpayPayoutId,
     *   paymentCompletedAt, carrierTransferInitiatedAt, carrierTransferCompletedAt,
     *   transferFailureReason, commissionPaid, paymentId
     */
    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> getAllOrders(
            @RequestParam(value = "status", defaultValue = "all") String status,
            @RequestParam(value = "size", defaultValue = "500") int size) {
        try {
            List<OrderSummaryDto> orders = adminDashboardService.getAllOrdersWithPayments(status, size);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "orders", orders,
                            "total", orders.size()
                    )
            ));
        } catch (Exception e) {
            log.error("Error fetching orders: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/admin/dashboard/orders/commission/mark-paid/{paymentId}
     *
     * Admin manually marks the platform commission as collected for a COD order.
     * Body (optional JSON): { "note": "Collected via UPI on 12-Apr-2026" }
     *
     * Response: { success: true, message: "Commission marked as paid", paymentId: 42 }
     */
    @PostMapping("/orders/commission/mark-paid/{paymentId}")
    public ResponseEntity<Map<String, Object>> markCommissionPaid(
            @PathVariable Long paymentId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String note = (body != null) ? body.getOrDefault("note", "") : "";
            adminDashboardService.markCommissionPaid(paymentId, note);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Commission marked as paid successfully",
                    "paymentId", paymentId
            ));
        } catch (IllegalStateException e) {
            // Business-rule violation (already paid, wrong type, etc.)
            log.warn("Mark commission paid rejected | paymentId={} | reason={}", paymentId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", e.getMessage()));
        } catch (RuntimeException e) {
            // Payment not found
            log.error("Mark commission paid error | paymentId={} | {}", paymentId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in markCommissionPaid: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== COMPLETE RIDE HISTORY ====================

    /**
     * Full read-only ride history for the admin panel.
     * Includes parties, addresses, package/proof images, OTPs, payment/settlement
     * data, carrier verification and the complete GPS breadcrumb history.
     */
    @GetMapping("/ride-history/{requestId}")
    public ResponseEntity<Map<String, Object>> getRideHistoryDetail(@PathVariable Long requestId) {
        try {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", adminDashboardService.getRideHistoryDetail(requestId)
            ));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching ride history {}: {}", requestId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== MISC ====================

    @PostMapping("/notification")
    public void testNotification() {
        firebaseNotificationService.sendNotification("f", "FG", "FG");
    }
}