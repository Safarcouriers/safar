package com.saffaricarrers.saffaricarrers.Controller;

import com.saffaricarrers.saffaricarrers.Dtos.BroadcastNotificationResponse;
import com.saffaricarrers.saffaricarrers.Entity.BroadcastNotificationRequest;
import com.saffaricarrers.saffaricarrers.Services.AdminNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/dashboard/notification")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    /**
     * Send broadcast notification to a user group
     * POST /api/v1/admin/dashboard/notification/send
     *
     * Body: { "title": "...", "body": "...", "userType": "ALL" | "SENDER" | "CARRIER" | "BOTH" }
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendBroadcast(
            @RequestBody BroadcastNotificationRequest request) {
        try {
            log.info("Admin broadcast: type={} title={}", request.getUserType(), request.getTitle());
            BroadcastNotificationResponse result = adminNotificationService.sendBroadcast(request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", result,
                    "message", "Notification sent"
            ));
        } catch (Exception e) {
            log.error("Broadcast failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Preview how many users will receive the notification (dry-run)
     * GET /api/v1/admin/dashboard/notification/preview?userType=ALL
     */
    @GetMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(
            @RequestParam(defaultValue = "ALL") String userType) {
        try {
            long count = adminNotificationService.countTargetUsers(userType);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of("targetCount", count, "userType", userType)
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "error", e.getMessage()));
        }
    }
}