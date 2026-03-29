package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Dtos.LocationUpdateRequest;

import com.saffaricarrers.saffaricarrers.Entity.DeliveryRequest;
import com.saffaricarrers.saffaricarrers.Entity.LocationTracking;
import com.saffaricarrers.saffaricarrers.Entity.User;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import com.saffaricarrers.saffaricarrers.Repository.DeliveryRequestRepository;
import com.saffaricarrers.saffaricarrers.Repository.LocationTrackingRepository;
import com.saffaricarrers.saffaricarrers.Repository.UserRepository;
import com.saffaricarrers.saffaricarrers.Responses.LocationStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LocationTrackingService {

    private final LocationTrackingRepository locationTrackingRepository;
    private final DeliveryRequestRepository deliveryRequestRepository;
    private final UserRepository userRepository;
    private final FirebaseNotificationService firebaseNotificationService;

    // =====================================================================
    // CARRIER: Update location (called every 30 mins from app)
    // =====================================================================

    public LocationStatusResponse updateCarrierLocation(String carrierId, Long requestId,
                                                        LocationUpdateRequest locationRequest) {
        User carrier = userRepository.findByUserId(carrierId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrier not found"));

        DeliveryRequest deliveryRequest = deliveryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found"));

        // ✅ Security: only the assigned carrier can update location
        if (!deliveryRequest.getCarrier().getUserId().equals(carrierId)) {
            throw new IllegalArgumentException("Not authorized to update location for this request");
        }

        // ✅ Only track when in transit
        if (deliveryRequest.getStatus() != DeliveryRequest.RequestStatus.IN_TRANSIT) {
            throw new IllegalArgumentException("Location tracking only available during transit");
        }

        // Save location record
        LocationTracking tracking = new LocationTracking();
        tracking.setDeliveryRequest(deliveryRequest);
        tracking.setCarrier(carrier);
        tracking.setLatitude(locationRequest.getLatitude());
        tracking.setLongitude(locationRequest.getLongitude());
        tracking.setResolvedAddress(locationRequest.getResolvedAddress());
        tracking.setSpeed(locationRequest.getSpeed());
        tracking.setBatteryLevel(locationRequest.getBatteryLevel());
        tracking.setRecordedAt(LocalDateTime.now());

        locationTrackingRepository.save(tracking);
        log.info("📍 Location updated for request {} by carrier {}: ({}, {})",
                requestId, carrierId, locationRequest.getLatitude(), locationRequest.getLongitude());

        // ✅ Notify sender about carrier's current location
        notifySenderWithLocation(deliveryRequest, locationRequest);

        return buildStatusResponse(tracking);
    }

    // =====================================================================
    // SENDER: Get latest carrier location
    // =====================================================================

    @Transactional(readOnly = true)
    public LocationStatusResponse getLatestCarrierLocation(String senderUserId, Long requestId) {
        User sender = userRepository.findByUserId(senderUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        DeliveryRequest deliveryRequest = deliveryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found"));

        // ✅ Security: only the sender or carrier can view location
        boolean isSender = deliveryRequest.getSender().getUserId().equals(senderUserId);
        boolean isCarrier = deliveryRequest.getCarrier().getUserId().equals(senderUserId);
        if (!isSender && !isCarrier) {
            throw new IllegalArgumentException("Not authorized to view location for this request");
        }

        Optional<LocationTracking> latest = locationTrackingRepository
                .findTopByDeliveryRequestOrderByRecordedAtDesc(deliveryRequest);

        if (latest.isEmpty()) {
            LocationStatusResponse empty = new LocationStatusResponse();
            empty.setHasLocation(false);
            empty.setRequestId(requestId);
            empty.setStatus(deliveryRequest.getStatus().toString());
            return empty;
        }

        return buildStatusResponse(latest.get());
    }

    // =====================================================================
    // Get location history (breadcrumb trail)
    // =====================================================================

    @Transactional(readOnly = true)
    public List<LocationStatusResponse> getLocationHistory(String userId, Long requestId) {
        DeliveryRequest deliveryRequest = deliveryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found"));

        boolean isSender = deliveryRequest.getSender().getUserId().equals(userId);
        boolean isCarrier = deliveryRequest.getCarrier().getUserId().equals(userId);
        if (!isSender && !isCarrier) {
            throw new IllegalArgumentException("Not authorized");
        }

        List<LocationTracking> history = locationTrackingRepository
                .findByDeliveryRequestOrderByRecordedAtAsc(deliveryRequest);

        return history.stream().map(this::buildStatusResponse).collect(Collectors.toList());
    }

    // =====================================================================
    // PRIVATE HELPERS
    // =====================================================================

    private void notifySenderWithLocation(DeliveryRequest deliveryRequest, LocationUpdateRequest locationRequest) {
        try {
            User sender = deliveryRequest.getSender();
            String senderFcmToken = sender.getFcmToken();

            if (senderFcmToken == null || senderFcmToken.isEmpty()) {
                log.warn("Sender {} has no FCM token — skipping location notification", sender.getUserId());
                return;
            }

            String packageName = deliveryRequest.getPackageEntity().getProductName();
            String locationText = locationRequest.getResolvedAddress() != null
                    ? locationRequest.getResolvedAddress()
                    : String.format("%.4f, %.4f", locationRequest.getLatitude(), locationRequest.getLongitude());

            String title = "📍 Your package is on its way!";
            String message = packageName + " is currently near: " + locationText;

            Map<String, String> data = new HashMap<>();
            data.put("type", "LOCATION_UPDATE");
            data.put("requestId", deliveryRequest.getRequestId().toString());
            data.put("latitude", String.valueOf(locationRequest.getLatitude()));
            data.put("longitude", String.valueOf(locationRequest.getLongitude()));
            data.put("resolvedAddress", locationText);
            data.put("routeId", deliveryRequest.getCarrierRoute().getRouteId().toString());

            firebaseNotificationService.sendNotificationWithData(senderFcmToken, title, message, data);
            log.info("✅ Location notification sent to sender {} for request {}", sender.getUserId(),
                    deliveryRequest.getRequestId());

        } catch (Exception e) {
            // Never fail the location update just because notification failed
            log.error("❌ Failed to send location notification: {}", e.getMessage());
        }
    }

    private LocationStatusResponse buildStatusResponse(LocationTracking tracking) {
        LocationStatusResponse response = new LocationStatusResponse();
        response.setHasLocation(true);
        response.setRequestId(tracking.getDeliveryRequest().getRequestId());
        response.setLatitude(tracking.getLatitude());
        response.setLongitude(tracking.getLongitude());
        response.setResolvedAddress(tracking.getResolvedAddress());
        response.setSpeed(tracking.getSpeed());
        response.setBatteryLevel(tracking.getBatteryLevel());
        response.setRecordedAt(tracking.getRecordedAt());
        response.setStatus(tracking.getDeliveryRequest().getStatus().toString());
        response.setCarrierName(tracking.getCarrier().getFullName());
        response.setPackageName(tracking.getDeliveryRequest().getPackageEntity().getProductName());
        return response;
    }
}