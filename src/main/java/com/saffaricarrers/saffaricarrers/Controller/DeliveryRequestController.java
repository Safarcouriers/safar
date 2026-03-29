package com.saffaricarrers.saffaricarrers.Controller;
import com.saffaricarrers.saffaricarrers.Dtos.DeliveryRequestDto;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import com.saffaricarrers.saffaricarrers.Responses.RouteAvailabilityResponse;
import com.saffaricarrers.saffaricarrers.Services.DeliveryRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;


import com.saffaricarrers.saffaricarrers.Dtos.*;
import com.saffaricarrers.saffaricarrers.Services.DeliveryRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
public class DeliveryRequestController {

    private final DeliveryRequestService deliveryRequestService;

    // ==================== MY PACKAGES TAB ====================

    /**
     * GET /api/delivery/packages
     * Returns list of user's packages with status
     * Used for "My Packages" tab (Screen 4)
     */
    @GetMapping("/packages")
    public ResponseEntity<List<MyPackageResponse>> getMyPackages(
            @RequestHeader("userId") String userId) {
        return ResponseEntity.ok(deliveryRequestService.getMyPackages(userId));
    }

    // ==================== MY TRIPS TAB ====================

    /**
     * GET /api/delivery/trips
     * Returns list of carrier's routes/trips with status
     * Used for "My Trips" tab (Screen 1)
     */
    @GetMapping("/trips")
    public ResponseEntity<List<MyTripResponse>> getMyTrips(
            @RequestHeader("userId") String userId) {
        return ResponseEntity.ok(deliveryRequestService.getMyTrips(userId));
    }

    // ==================== TRIP DETAILS ====================

    /**
     * GET /api/delivery/trips/{routeId}
     * Returns detailed trip info with package requests and active packages
     * Used when user clicks on a trip card (Screen 2)
     */
    @GetMapping("/trips/{routeId}")
    public ResponseEntity<TripDetailResponse> getTripDetails(
            @PathVariable Long routeId,
            @RequestHeader("userId") String userId) {
        return ResponseEntity.ok(deliveryRequestService.getTripDetails(routeId, userId));
    }

    // ==================== PACKAGE DETAILS ====================

    /**
     * GET /api/delivery/packages/{packageId}
     * Returns detailed package info with carrier, OTP, payment, timeline
     * Used when user clicks on a package card (Screen 3)
     */
    @GetMapping("/packages/{packageId}")
    public ResponseEntity<PackageDetailResponse> getPackageDetails(
            @PathVariable Long packageId,
            @RequestHeader("userId") String userId) {
        return ResponseEntity.ok(deliveryRequestService.getPackageDetails(packageId, userId));
    }

    // ==================== REQUESTS TAB ====================

    /**
     * GET /api/delivery/requests/pending
     * Returns all pending requests (received from senders & carriers)
     * Used for dedicated "Requests" button/tab for accept/reject
     */
    @GetMapping("/requests/pending")
    public ResponseEntity<RequestsTabResponse> getAllPendingRequests(
            @RequestHeader("userId") String userId) {
        System.out.println("userId"+userId);
        return ResponseEntity.ok(deliveryRequestService.getAllPendingRequests(userId));
    }

    // ==================== CREATE REQUESTS ====================

    /**
     * POST /api/delivery/requests/sender
     * Sender sends request to carrier
     */
    @PostMapping("/requests/sender")
    public ResponseEntity<DeliveryRequestResponse> senderSendRequest(
            @RequestHeader("userId") String userId,
            @RequestBody SenderRequestDto requestDto) {
        return ResponseEntity.ok(deliveryRequestService.senderSendRequestToCarrier(
                userId,
                requestDto.getPackageId(),
                requestDto.getRouteId(),
                requestDto.getSenderNote()
        ));
    }

    /**
     * POST /api/delivery/requests/carrier
     * Carrier sends request to sender
     */
    @PostMapping("/requests/carrier")
    public ResponseEntity<DeliveryRequestResponse> carrierSendRequest(
            @RequestHeader("userId") String userId,
            @RequestBody CarrierRequestDto requestDto) {
        return ResponseEntity.ok(deliveryRequestService.carrierSendRequestToSender(
                userId,
                requestDto.getPackageId(),
                requestDto.getRouteId(),
                requestDto.getCarrierNote()
        ));
    }

    // ==================== ACCEPT REQUESTS ====================

    /**
     * PUT /api/delivery/requests/{requestId}/accept/sender
     * Sender accepts carrier's request
     */
    @PutMapping("/requests/{requestId}/accept/sender")
    public ResponseEntity<?> senderAcceptRequest(
            @PathVariable Long requestId,
            @RequestHeader("userId") String userId,
            @RequestBody(required = false) AcceptRequestDto dto) {

        try {
            System.out.println("uiserid"+userId);
            String note = dto != null ? dto.getNote() : null;
            return ResponseEntity.ok(deliveryRequestService.carrierAcceptRequest(userId, requestId, note));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace(); // Log this properly
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
    /**
     * PUT /api/delivery/requests/{requestId}/accept/carrier
     * Carrier accepts sender's request
     */
    @PutMapping("/requests/{requestId}/accept/carrier")
    public ResponseEntity<DeliveryRequestResponse> carrierAcceptRequest(
            @PathVariable Long requestId,
            @RequestHeader("userId") String userId,
            @RequestBody(required = false) AcceptRequestDto dto) {
        String note = dto != null ? dto.getNote() : null;
        return ResponseEntity.ok(deliveryRequestService.senderAcceptRequest(userId, requestId, note));
    }

    // ==================== REJECT REQUESTS ====================

    /**
     * PUT /api/delivery/requests/{requestId}/reject/sender
     * Sender rejects carrier's request
     */
    @PutMapping("/requests/{requestId}/reject/sender")
    public ResponseEntity<DeliveryRequestResponse> senderRejectRequest(
            @PathVariable Long requestId,
            @RequestHeader("userId") String userId,
            @RequestBody RejectRequestDto dto) {
        return ResponseEntity.ok(deliveryRequestService.senderRejectRequest(userId, requestId, dto.getReason()));
    }

    /**
     * PUT /api/delivery/requests/{requestId}/reject/carrier
     * Carrier rejects sender's request
     */
    @PutMapping("/requests/{requestId}/reject/carrier")
    public ResponseEntity<DeliveryRequestResponse> carrierRejectRequest(
            @PathVariable Long requestId,
            @RequestHeader("userId") String userId,
            @RequestBody RejectRequestDto dto) {
        return ResponseEntity.ok(deliveryRequestService.carrierRejectRequest(userId, requestId, dto.getReason()));
    }

    // ==================== OTP VERIFICATION ====================

    /**
     * POST /api/delivery/requests/{requestId}/verify-pickup
     * Carrier verifies pickup OTP
     */
    @PostMapping("/requests/{requestId}/verify-pickup")
    public ResponseEntity<DeliveryRequestResponse> verifyPickupOtp(
            @PathVariable Long requestId,
            @RequestHeader("userId") String userId,
            @RequestBody OtpVerificationDto dto) {
        System.out.println("otp data"+dto.getOtp()+dto.getPackageId()+dto.getVerificationType());
        return ResponseEntity.ok(deliveryRequestService.verifyPickupOtp(userId, requestId, dto.getOtp()));
    }

    /**
     * POST /api/delivery/requests/{requestId}/verify-delivery
     * Carrier verifies delivery OTP
     */
    @PostMapping("/requests/{requestId}/verify-delivery")
    public ResponseEntity<DeliveryRequestResponse> verifyDeliveryOtp(
            @PathVariable Long requestId,
            @RequestHeader("userId") String userId,
            @RequestBody OtpVerificationDto dto) {
        return ResponseEntity.ok(deliveryRequestService.verifyDeliveryOtp(userId, requestId, dto.getOtp()));
    }

    // ==================== TRIP CONTROLS ====================

    /**
     * PUT /api/delivery/requests/{requestId}/start-transit
     * Carrier starts transit
     */
    @PutMapping("/requests/{requestId}/start-transit")
    public ResponseEntity<DeliveryRequestResponse> startTransit(
            @PathVariable Long requestId,
            @RequestHeader("userId") String userId) {
        return ResponseEntity.ok(deliveryRequestService.startTransit(userId, requestId));
    }
    // ==================== HOME PAGE - AVAILABILITY CHECK ====================

    /**
     * GET /api/delivery/routes/{routeId}/matching-packages
     * Check availability and get packages matching route dates
     * Frontend shows "Create Package" if hasPackages = false
     */
    @GetMapping("/routes/{routeId}/matching-packages")
    public ResponseEntity<PackageAvailabilityResponse> getMatchingPackagesForRoute(
            @PathVariable Long routeId,
            @RequestHeader("userId") String userId,
            @RequestParam(required = false, defaultValue = "15.0") Double corridorKm) {  // ← only this remains

        PackageAvailabilityResponse response =
                deliveryRequestService.getMatchingPackagesForRoute(userId, routeId, corridorKm);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/delivery/packages/{packageId}/matching-routes
     * Check availability and get routes matching package dates
     * Frontend shows "Create Route" if hasRoutes = false
     */
    @Operation(summary = "Check Route Availability for Package",
            description = "Returns matching routes or empty list with availability status")
    @GetMapping("/packages/{packageId}/matching-routes")
    public ResponseEntity<RouteAvailabilityResponse> getMatchingRoutesForPackage(
            @PathVariable Long packageId,
            @RequestHeader("userId") String userId) {
        RouteAvailabilityResponse response =
                deliveryRequestService.getMatchingRoutesForPackage(userId, packageId);
        return ResponseEntity.ok(response);
    }
// Add to DeliveryRequestController.java

    @PostMapping("/{requestId}/upload-pickup-photo")
    public ResponseEntity<?> uploadPickupPhoto(
            @RequestHeader("X-User-ID") String carrierId,
            @PathVariable Long requestId,
            @RequestParam("photo") MultipartFile pickupPhoto) {

        DeliveryRequestResponse response = deliveryRequestService
                .uploadPickupPhoto(carrierId, requestId, pickupPhoto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{requestId}/upload-delivery-photo")
    public ResponseEntity<?> uploadDeliveryPhoto(
            @RequestHeader("X-User-ID") String carrierId,
            @PathVariable Long requestId,
            @RequestParam("photo") MultipartFile deliveryPhoto) {

        DeliveryRequestResponse response = deliveryRequestService
                .uploadDeliveryPhoto(carrierId, requestId, deliveryPhoto);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/requests/{requestId}/status")
    public ResponseEntity<DeliveryRequestService.DeliveryProgressResponse> getDeliveryStatus(
            @PathVariable Long requestId,
            @RequestHeader("userId") String userId) {
        return ResponseEntity.ok(deliveryRequestService.getDeliveryProgressStatus(userId, requestId));
    }

}
