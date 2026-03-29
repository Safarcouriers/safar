package com.saffaricarrers.saffaricarrers.Controller;


import com.saffaricarrers.saffaricarrers.Dtos.*;
import com.saffaricarrers.saffaricarrers.Services.AutoCreateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auto-create")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AutoCreateController {

    private final AutoCreateService autoCreateService;

    /**
     * Carrier has no matching routes for a package.
     * Auto-create a carrier route from the package's details
     * and immediately send a delivery request to the sender.
     *
     * POST /api/auto-create/route-and-request
     * Header: userId (carrier's firebase uid)
     * Body:   { packageId }
     */
    @PostMapping("/route-and-request")
    public ResponseEntity<?> autoCreateRouteAndRequest(
            @RequestHeader("userId") String carrierId,
            @RequestBody Map<String, Long> body) {

        Long packageId = body.get("packageId");
        if (packageId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "packageId is required"));
        }

        try {
            AutoCreateResult result = autoCreateService.autoCreateRouteAndSendRequest(
                    carrierId, packageId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Auto-create route error for carrier {} package {}: {}",
                    carrierId, packageId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to auto-create route: " + e.getMessage()));
        }
    }

    /**
     * Sender has no matching packages for a route.
     * Auto-create a package from the route's details
     * and immediately send a delivery request to the carrier.
     *
     * POST /api/auto-create/package-and-request
     * Header: userId (sender's firebase uid)
     * Body:   { routeId, fromAddress, toAddress, productName?, productValue?, weight? }
     */
    @PostMapping("/package-and-request")
    public ResponseEntity<?> autoCreatePackageAndRequest(
            @RequestHeader("userId") String senderId,
            @RequestBody AutoCreatePackageRequest body) {

        if (body.getRouteId() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "routeId is required"));
        }

        try {
            AutoCreateResult result = autoCreateService.autoCreatePackageAndSendRequest(
                    senderId, body);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Auto-create package error for sender {} route {}: {}",
                    senderId, body.getRouteId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to auto-create package: " + e.getMessage()));
        }
    }
}