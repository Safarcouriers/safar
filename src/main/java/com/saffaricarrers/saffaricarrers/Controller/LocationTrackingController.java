package com.saffaricarrers.saffaricarrers.Controller;


import com.saffaricarrers.saffaricarrers.Dtos.LocationUpdateRequest;
import com.saffaricarrers.saffaricarrers.Responses.LocationStatusResponse;
import com.saffaricarrers.saffaricarrers.Services.LocationTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
@Slf4j
public class LocationTrackingController {

    private final LocationTrackingService locationTrackingService;

    // =====================================================================
    // CARRIER → POST location update (every 30 mins, background task)
    // =====================================================================

    /**
     * Carrier calls this every 30 minutes while in transit.
     * Header: userId = carrier's Firebase UID
     * Body: { latitude, longitude, resolvedAddress, speed, batteryLevel }
     */
    @PostMapping("/requests/{requestId}/location")
    public ResponseEntity<LocationStatusResponse> updateCarrierLocation(
            @RequestHeader("userId") String carrierId,
            @PathVariable Long requestId,
            @RequestBody LocationUpdateRequest locationRequest) {
        try {
            LocationStatusResponse response = locationTrackingService
                    .updateCarrierLocation(carrierId, requestId, locationRequest);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Location update rejected for request {}: {}", requestId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    // =====================================================================
    // SENDER → GET latest carrier location
    // =====================================================================

    /**
     * Sender (or carrier) polls this to get the latest location.
     * Also called when a push notification arrives to refresh the map.
     */
    @GetMapping("/requests/{requestId}/location")
    public ResponseEntity<LocationStatusResponse> getLatestCarrierLocation(
            @RequestHeader("userId") String userId,
            @PathVariable Long requestId) {
        try {
            LocationStatusResponse response = locationTrackingService
                    .getLatestCarrierLocation(userId, requestId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // =====================================================================
    // SENDER → GET full location history (breadcrumb trail on map)
    // =====================================================================

    @GetMapping("/requests/{requestId}/location/history")
    public ResponseEntity<List<LocationStatusResponse>> getLocationHistory(
            @RequestHeader("userId") String userId,
            @PathVariable Long requestId) {
        try {
            List<LocationStatusResponse> history = locationTrackingService
                    .getLocationHistory(userId, requestId);
            return ResponseEntity.ok(history);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        }
    }
}