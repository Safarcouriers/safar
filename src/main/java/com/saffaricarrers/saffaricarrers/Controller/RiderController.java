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

            // Accept both the new names and old names for compatibility
            Object latValue = body.get("latitude") != null
                    ? body.get("latitude")
                    : body.get("lat");

            Object lngValue = body.get("longitude") != null
                    ? body.get("longitude")
                    : body.get("lng");

            Double lat = latValue != null
                    ? ((Number) latValue).doubleValue()
                    : null;

            Double lng = lngValue != null
                    ? ((Number) lngValue).doubleValue()
                    : null;

            Map<String, Object> result =
                    riderService.setOnlineStatus(
                            userId,
                            online,
                            lat,
                            lng
                    );

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "data", result
                    )
            );

        } catch (Exception e) {
            log.error(
                    "setOnlineStatus error for {}: {}",
                    userId,
                    e.getMessage()
            );

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );
        }
    }

    // ── Get online status ─────────────────────────────────────────────────────
    @GetMapping("/status")
    public ResponseEntity<?> getRiderStatus(
            @RequestHeader("userId") String userId) {
        try {

            Map<String, Object> result =
                    riderService.getRiderStatus(userId);

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "data", result
                    )
            );

        } catch (Exception e) {

            log.error(
                    "getRiderStatus error for {}: {}",
                    userId,
                    e.getMessage()
            );

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );
        }
    }

    // ── Live location update ─────────────────────────────────────────────────
    // Called regularly from rider-map.tsx
    @PostMapping("/location")
    public ResponseEntity<?> updateLocation(
            @RequestHeader("userId") String userId,
            @RequestBody Map<String, Object> body) {
        try {

            // App sends latitude / longitude
            Object latValue = body.get("latitude") != null
                    ? body.get("latitude")
                    : body.get("lat");

            Object lngValue = body.get("longitude") != null
                    ? body.get("longitude")
                    : body.get("lng");

            if (latValue == null || lngValue == null) {
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "success", false,
                                "message", "latitude and longitude are required"
                        )
                );
            }

            double lat = ((Number) latValue).doubleValue();
            double lng = ((Number) lngValue).doubleValue();

            Map<String, Object> result =
                    riderService.updateLocation(
                            userId,
                            lat,
                            lng
                    );

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "data", result
                    )
            );

        } catch (Exception e) {

            log.error(
                    "updateLocation error for {}: {}",
                    userId,
                    e.getMessage()
            );

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );
        }
    }

    // ── Rider heartbeat ──────────────────────────────────────────────────────
    // Keeps rider presence alive.
    // Accepts latitude / longitude when the app sends them.
    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(
            @RequestHeader("userId") String userId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {

            if (body == null) {
                body = Map.of();
            }

            Object latValue = body.get("latitude") != null
                    ? body.get("latitude")
                    : body.get("lat");

            Object lngValue = body.get("longitude") != null
                    ? body.get("longitude")
                    : body.get("lng");

            Map<String, Object> result;

            // If heartbeat contains location, update it
            if (latValue != null && lngValue != null) {

                double lat = ((Number) latValue).doubleValue();
                double lng = ((Number) lngValue).doubleValue();

                result = riderService.updateLocation(
                        userId,
                        lat,
                        lng
                );

            } else {

                // No location supplied — just return current rider status
                result = riderService.getRiderStatus(userId);
            }

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "data", result
                    )
            );

        } catch (Exception e) {

            log.error(
                    "heartbeat error for {}: {}",
                    userId,
                    e.getMessage()
            );

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );
        }
    }

    // ── Nearby package requests ──────────────────────────────────────────────
    @GetMapping("/nearby-requests")
    public ResponseEntity<?> getNearbyRequests(
            @RequestHeader("userId") String userId,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "10.0") double radiusKm) {
        try {

            Map<String, Object> result =
                    riderService.getNearbyRequests(
                            userId,
                            latitude,
                            longitude,
                            radiusKm
                    );

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "data", result
                    )
            );

        } catch (Exception e) {

            log.error(
                    "getNearbyRequests error for {}: {}",
                    userId,
                    e.getMessage()
            );

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );
        }
    }

    // ── Settings ─────────────────────────────────────────────────────────────
    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(
            @RequestHeader("userId") String userId,
            @RequestBody Map<String, Object> body) {
        try {

            Object radiusValue = body.get("searchRadiusKm");

            if (radiusValue == null) {
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "success", false,
                                "message", "searchRadiusKm is required"
                        )
                );
            }

            double radiusKm =
                    ((Number) radiusValue).doubleValue();

            Map<String, Object> result =
                    riderService.updateRiderSettings(
                            userId,
                            radiusKm
                    );

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "data", result
                    )
            );

        } catch (Exception e) {

            log.error(
                    "updateSettings error for {}: {}",
                    userId,
                    e.getMessage()
            );

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );
        }
    }

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings(
            @RequestHeader("userId") String userId) {
        try {

            Map<String, Object> result =
                    riderService.getRiderSettings(userId);

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "data", result
                    )
            );

        } catch (Exception e) {

            log.error(
                    "getSettings error for {}: {}",
                    userId,
                    e.getMessage()
            );

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );
        }
    }

    // ── Active delivery check ────────────────────────────────────────────────
    // Returns rider's current in-progress delivery if any exists.
    @GetMapping("/active-delivery")
    public ResponseEntity<?> getActiveDelivery(
            @RequestHeader("userId") String userId) {
        try {

            Map<String, Object> result =
                    riderService.getActiveDelivery(userId);

            return ResponseEntity.ok(result);

        } catch (Exception e) {

            log.error(
                    "getActiveDelivery error for {}: {}",
                    userId,
                    e.getMessage()
            );

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "hasActiveDelivery", false
                    )
            );
        }
    }
}