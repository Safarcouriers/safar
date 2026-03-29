package com.saffaricarrers.saffaricarrers.Controller;

import com.saffaricarrers.saffaricarrers.Services.RiderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rider")
@RequiredArgsConstructor
@Slf4j
public class RiderController {

    private final RiderService riderService;

    // ── Online / Offline toggle ───────────────────────────────────────────────
    @PostMapping("/status")
    public ResponseEntity<?> setOnlineStatus(
            @RequestHeader("userId") String userId,
            @RequestBody Map<String, Object> body) {
        try {
            boolean online = Boolean.TRUE.equals(body.get("online"));
            Map<String, Object> result = riderService.setOnlineStatus(userId, online);
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) {
            log.error("setOnlineStatus error for {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ── Get online status ─────────────────────────────────────────────────────
    @GetMapping("/status")
    public ResponseEntity<?> getRiderStatus(
            @RequestHeader("userId") String userId) {
        try {
            Map<String, Object> result = riderService.getRiderStatus(userId);
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) {
            log.error("getRiderStatus error for {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ── Live location update (called every 30s from rider-map.tsx) ────────────
    @PostMapping("/location")
    public ResponseEntity<?> updateLocation(
            @RequestHeader("userId") String userId,
            @RequestBody Map<String, Object> body) {
        try {
            double lat = ((Number) body.get("lat")).doubleValue();
            double lng = ((Number) body.get("lng")).doubleValue();
            Map<String, Object> result = riderService.updateLocation(userId, lat, lng);
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) {
            log.error("updateLocation error for {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ── Nearby package requests ───────────────────────────────────────────────
    @GetMapping("/nearby-requests")
    public ResponseEntity<?> getNearbyRequests(
            @RequestHeader("userId") String userId,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "10.0") double radiusKm) {
        try {
            Map<String, Object> result = riderService.getNearbyRequests(userId, latitude, longitude, radiusKm);
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) {
            log.error("getNearbyRequests error for {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────
    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(
            @RequestHeader("userId") String userId,
            @RequestBody Map<String, Object> body) {
        try {
            double radiusKm = ((Number) body.get("searchRadiusKm")).doubleValue();
            Map<String, Object> result = riderService.updateRiderSettings(userId, radiusKm);
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) {
            log.error("updateSettings error for {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings(
            @RequestHeader("userId") String userId) {
        try {
            Map<String, Object> result = riderService.getRiderSettings(userId);
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) {
            log.error("getSettings error for {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}