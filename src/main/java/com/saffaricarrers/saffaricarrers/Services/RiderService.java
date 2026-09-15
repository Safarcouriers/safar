package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Entity.*;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import com.saffaricarrers.saffaricarrers.Repository.CarrierProfileRepository;
import com.saffaricarrers.saffaricarrers.Repository.DeliveryRequestRepository;
import com.saffaricarrers.saffaricarrers.Repository.PackageRepository;
import com.saffaricarrers.saffaricarrers.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RiderService {
    private static final long CACHE_TTL_MS = 30_000; // 30 seconds
    private final Map<String, CachedResult> nearbyCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final UserRepository userRepository;
    private final CarrierProfileRepository carrierProfileRepository;
    private final PackageRepository packageRepository;
    private final DeliveryRequestRepository deliveryRequestRepository; // ✅ NEW
    private final PaymentService paymentService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ─────────────────────────────────────────────────────────────────────────
    // ONLINE / OFFLINE STATUS
    // ─────────────────────────────────────────────────────────────────────────

    public Map<String, Object> setOnlineStatus(String carrierId, boolean online,
                                               Double lat, Double lng) {
        CarrierProfile profile = carrierProfileRepository.findByUserUserId(carrierId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrier profile not found: " + carrierId));

        profile.setIsOnline(online);

        if (!online) {
            profile.setLastLat(null);
            profile.setLastLng(null);
            profile.setLastLocationAt(null);
        } else {
            // ✅ Save location immediately when going online
            if (lat != null && lng != null) {
                profile.setLastLat(lat);
                profile.setLastLng(lng);
                profile.setLastLocationAt(LocalDateTime.now());
            }
        }

        carrierProfileRepository.save(profile);
        log.info("Carrier {} is now {} at ({}, {})", carrierId,
                online ? "ONLINE" : "OFFLINE", lat, lng);

        Map<String, Object> response = new HashMap<>();
        response.put("carrierId", carrierId);
        response.put("online", online);
        response.put("message", online ? "You are now online" : "You are now offline");
        response.put("searchRadiusKm", profile.getSearchRadiusKm() != null ? profile.getSearchRadiusKm() : 10.0);
        return response;
    }

    public Map<String, Object> getRiderStatus(String carrierId) {
        User user = userRepository.findByUserId(carrierId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + carrierId));
        CarrierProfile profile = user.getCarrierProfile();
        if (profile == null) throw new IllegalArgumentException("No carrier profile found");

        Map<String, Object> response = new HashMap<>();
        response.put("carrierId", carrierId);
        response.put("online", Boolean.TRUE.equals(profile.getIsOnline()));
        response.put("searchRadiusKm", profile.getSearchRadiusKm() != null ? profile.getSearchRadiusKm() : 10.0);
        response.put("lastLat", profile.getLastLat());
        response.put("lastLng", profile.getLastLng());
        response.put("lastLocationAt", profile.getLastLocationAt());
        return response;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIVE LOCATION UPDATE
    // Called from rider-map.tsx every 30s while online
    // ─────────────────────────────────────────────────────────────────────────

    public Map<String, Object> updateLocation(String carrierId, double lat, double lng) {
        User user = userRepository.findByUserId(carrierId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + carrierId));
        CarrierProfile profile = user.getCarrierProfile();
        if (profile == null) throw new IllegalArgumentException("No carrier profile found");

        profile.setLastLat(lat);
        profile.setLastLng(lng);
        profile.setLastLocationAt(LocalDateTime.now());
        carrierProfileRepository.save(profile);

        log.debug("Location updated for carrier {}: {},{}", carrierId, lat, lng);

        Map<String, Object> response = new HashMap<>();
        response.put("carrierId", carrierId);
        response.put("lat", lat);
        response.put("lng", lng);
        response.put("updatedAt", profile.getLastLocationAt());
        return response;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NEARBY REQUESTS
    // ─────────────────────────────────────────────────────────────────────────

//    public Map<String, Object> getNearbyRequests(String carrierId,
//                                                 double latitude,
//                                                 double longitude,
//                                                 double radiusKm) {
//
//        CarrierProfile profile = carrierProfileRepository.findByUserUserId(carrierId)
//                .orElseThrow(() -> new ResourceNotFoundException("Carrier profile not found: " + carrierId));
//
//        // ✅ Block offline riders — return empty result, not an error
//        if (!Boolean.TRUE.equals(profile.getIsOnline())) {
//            log.info("Carrier {} is offline — returning empty nearby requests", carrierId);
//            return Map.of(
//                    "carrierLat",  latitude,
//                    "carrierLng",  longitude,
//                    "radiusKm",    radiusKm,
//                    "totalFound",  0,
//                    "requests",    List.of(),
//                    "online",      false
//            );
//        }
//
//        LocalDate today = LocalDate.now();
//
//        List<Package> allPackages = packageRepository.findAll();
//
//        log.warn("🔥 TOTAL PACKAGES IN DB: {}", allPackages.size());
//
//        List<Map<String, Object>> nearby = allPackages.stream()
//                .filter(pkg -> {
//
//                    Long pkgId = pkg.getPackageId();
//
//                    // ✅ STATUS FILTER
//                    if (pkg.getStatus() == null) {
//                        log.warn("❌ Package {} skipped — status null", pkgId);
//                        return false;
//                    }
//
//                    if (!(pkg.getStatus().name().equals("CREATED") ||
//                            pkg.getStatus().name().equals("CONFIRMED"))) {
//                        log.warn("❌ Package {} skipped — status={}", pkgId, pkg.getStatus());
//                        return false;
//                    }
//
//                    // 🚫 Exclude packages created by this carrier themselves
//                    if (pkg.getSender() != null && pkg.getSender().getUserId().equals(carrierId)) {
//                        log.warn("❌ Package {} skipped — created by the requesting carrier", pkgId);
//                        return false;
//                    }
//
//                    Double pkgLat = pkg.getLatitude();
//                    Double pkgLng = pkg.getLongitude();
//
//                    // ❌ Invalid coords
//                    if (pkgLat == null || pkgLng == null) {
//                        log.warn("❌ Package {} skipped — null coordinates", pkgId);
//                        return false;
//                    }
//
//                    if (pkgLat == 0.0 && pkgLng == 0.0) {
//                        log.warn("❌ Package {} skipped — zero coordinates", pkgId);
//                        return false;
//                    }
//
//                    // ✅ Distance check
//                    double dist = haversineKm(latitude, longitude, pkgLat, pkgLng);
//
//                    log.warn("📍 Package {} distance = {} km", pkgId, String.format("%.2f", dist));
//
//                    if (dist > radiusKm) {
//                        log.warn("❌ Package {} skipped — outside radius", pkgId);
//                        return false;
//                    }
//
//                    // ✅ Date check
//                    if (pkg.getPickUpDate() != null && !pkg.getPickUpDate().isBlank()) {
//                        try {
//                            LocalDate dropDate = (pkg.getDropDate() != null && !pkg.getDropDate().isBlank())
//                                    ? LocalDate.parse(pkg.getDropDate(), DATE_FORMATTER)
//                                    : null;
//
//                            if (dropDate != null && today.isAfter(dropDate)) {
//                                log.warn("❌ Package {} skipped — expired drop date {}", pkgId, dropDate);
//                                return false;
//                            }
//
//                            LocalDate pickupDate = LocalDate.parse(pkg.getPickUpDate(), DATE_FORMATTER);
//
//                            if (pickupDate.isAfter(today.plusDays(30))) {
//                                log.warn("❌ Package {} skipped — pickup too far {}", pkgId, pickupDate);
//                                return false;
//                            }
//
//                        } catch (Exception e) {
//                            log.warn("⚠️ Package {} invalid date — including anyway", pkgId);
//                        }
//                    }
//
//                    log.warn("✅ Package {} INCLUDED", pkgId);
//                    return true;
//                })
//                .map(pkg -> {
//                    double dist = haversineKm(latitude, longitude,
//                            pkg.getLatitude(), pkg.getLongitude());
//                    return buildPackageCard(pkg, dist);
//                })
//                .sorted((a, b) -> Double.compare(
//                        (Double) a.get("distanceKm"),
//                        (Double) b.get("distanceKm")))
//                .collect(Collectors.toList());
//
//        log.warn("🎯 FINAL RESULT: {} packages returned", nearby.size());
//
//        Map<String, Object> response = new HashMap<>();
//        response.put("carrierLat", latitude);
//        response.put("carrierLng", longitude);
//        response.put("radiusKm", radiusKm);
//        response.put("totalFound", nearby.size());
//        response.put("requests", nearby);
//
//        return response;
//    }
    // ─────────────────────────────────────────────────────────────────────────
    // ✅ NEW: ACTIVE DELIVERY CHECK
    // Called by GET /api/rider/active-delivery every 15s from rider-map.tsx.
    // Looks for any DeliveryRequest where this user is the carrier AND status
    // is ACCEPTED, PICKED_UP, or IN_TRANSIT.
    // Returns { hasActiveDelivery: true, delivery: {...} } or { hasActiveDelivery: false }
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getActiveDelivery(String carrierId) {

        User rider = userRepository.findByUserId(carrierId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + carrierId
                        )
                );

        // ---------------------------------------------------------------
        // NORMAL ACTIVE DELIVERY STATUSES
        // ---------------------------------------------------------------
        List<DeliveryRequest.RequestStatus> activeStatuses =
                Arrays.asList(
                        DeliveryRequest.RequestStatus.ACCEPTED,
                        DeliveryRequest.RequestStatus.PICKED_UP,
                        DeliveryRequest.RequestStatus.IN_TRANSIT
                );

        // ---------------------------------------------------------------
        // First find normal active delivery
        // ---------------------------------------------------------------
        Optional<DeliveryRequest> activeOpt =
                deliveryRequestRepository
                        .findTopByCarrierAndIsRiderDeliveryTrueAndStatusInOrderByCreatedAtDesc(
                                rider,
                                activeStatuses
                        );

        DeliveryRequest req;

        if (activeOpt.isPresent()) {

            req = activeOpt.get();

        } else {

            // -----------------------------------------------------------
            // IMPORTANT:
            //
            // Delivery OTP changes status -> DELIVERED.
            //
            // But in the NEW flow, payment happens AFTER OTP.
            //
            // Therefore:
            // DELIVERED + payment pending = STILL ACTIVE
            //
            // DELIVERED + payment completed = FINISHED
            // -----------------------------------------------------------

            Optional<DeliveryRequest> deliveredOpt =
                    deliveryRequestRepository
                            .findTopByCarrierAndIsRiderDeliveryTrueAndStatusOrderByCreatedAtDesc(
                                    rider,
                                    DeliveryRequest.RequestStatus.DELIVERED
                            );

            if (deliveredOpt.isEmpty()) {

                log.debug(
                        "No active delivery for rider {}",
                        carrierId
                );

                return Map.of(
                        "hasActiveDelivery",
                        false
                );
            }

            DeliveryRequest deliveredReq =
                    deliveredOpt.get();

            Payment payment =
                    deliveredReq.getPayment();

            boolean paymentCompleted =
                    payment != null
                            && payment.getPaymentStatus()
                            == Payment.PaymentStatus.COMPLETED;

            // Payment is completed -> delivery is REALLY finished.
            if (paymentCompleted) {

                log.debug(
                        "Delivery {} fully completed for rider {}",
                        deliveredReq.getRequestId(),
                        carrierId
                );

                return Map.of(
                        "hasActiveDelivery",
                        false
                );
            }

            // Payment still pending -> keep rider locked.
            req = deliveredReq;

            log.info(
                    "💰 Delivery {} is DELIVERED but payment is still pending. "
                            + "Keeping rider locked.",
                    req.getRequestId()
            );
        }

        // ---------------------------------------------------------------
        // Build delivery response
        // ---------------------------------------------------------------

        Package pkg = req.getPackageEntity();
        User sender = pkg.getSender();

        String stepLabel;

        switch (req.getStatus()) {

            case IN_TRANSIT ->
                    stepLabel = "Go to Drop";

            case PICKED_UP ->
                    stepLabel = "Enter Drop OTP";

            case DELIVERED ->
                    stepLabel = "Complete Payment";

            default ->
                    stepLabel = "Go to Pickup";
        }

        double carrierEarning =
                req.getCarrierEarning() != null
                        ? req.getCarrierEarning()
                        : (
                        req.getTotalAmount() != null
                                ? req.getTotalAmount() * 0.85
                                : 0.0
                );

        Map<String, Object> delivery =
                new HashMap<>();

        delivery.put(
                "requestId",
                req.getRequestId()
        );

        delivery.put(
                "packageId",
                pkg.getPackageId()
        );

        delivery.put(
                "packageName",
                pkg.getProductName()
        );

        delivery.put(
                "status",
                req.getStatus().name()
        );

        delivery.put(
                "fromAddress",
                pkg.getFromAddress()
        );

        delivery.put(
                "toAddress",
                pkg.getToAddress()
        );

        delivery.put(
                "carrierEarning",
                Math.round(
                        carrierEarning * 100.0
                ) / 100.0
        );

        delivery.put(
                "senderName",
                sender != null
                        ? sender.getFullName()
                        : "Sender"
        );

        delivery.put(
                "senderAvatar",
                sender != null
                        ? sender.getProfileUrl()
                        : null
        );

        delivery.put(
                "stepLabel",
                stepLabel
        );

        // Useful for frontend
        delivery.put(
                "paymentPending",
                req.getStatus()
                        == DeliveryRequest.RequestStatus.DELIVERED
                        && (
                        req.getPayment() == null
                                || req.getPayment().getPaymentStatus()
                                != Payment.PaymentStatus.COMPLETED
                )
        );

        log.info(
                "Active delivery found for rider {}: requestId={} status={}",
                carrierId,
                req.getRequestId(),
                req.getStatus()
        );

        return Map.of(
                "hasActiveDelivery",
                true,
                "delivery",
                delivery
        );
    }
    // ─────────────────────────────────────────────────────────────────────────
    // SETTINGS
    // ─────────────────────────────────────────────────────────────────────────

    public Map<String, Object> updateRiderSettings(String carrierId, double radiusKm) {
        User user = userRepository.findByUserId(carrierId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + carrierId));
        CarrierProfile profile = user.getCarrierProfile();
        if (profile == null) throw new IllegalArgumentException("No carrier profile found");

        double clamped = Math.max(1.0, Math.min(50.0, radiusKm));
        profile.setSearchRadiusKm(clamped);
        carrierProfileRepository.save(profile);

        Map<String, Object> response = new HashMap<>();
        response.put("carrierId", carrierId);
        response.put("searchRadiusKm", clamped);
        response.put("message", "Settings updated");
        return response;
    }

    public Map<String, Object> getRiderSettings(String carrierId) {
        User user = userRepository.findByUserId(carrierId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + carrierId));
        CarrierProfile profile = user.getCarrierProfile();
        if (profile == null) throw new IllegalArgumentException("No carrier profile found");

        Map<String, Object> response = new HashMap<>();
        response.put("carrierId", carrierId);
        response.put("searchRadiusKm", profile.getSearchRadiusKm() != null ? profile.getSearchRadiusKm() : 10.0);
        response.put("online", Boolean.TRUE.equals(profile.getIsOnline()));
        return response;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Map<String, Object> buildPackageCard(Package pkg, double distanceKm) {
        Map<String, Object> card = new HashMap<>();
        card.put("packageId",          pkg.getPackageId());
        card.put("productName",        pkg.getProductName());
        card.put("productDescription", pkg.getProductDescription());
        card.put("productType",        pkg.getProductType()    != null ? pkg.getProductType().name()    : "GENERAL");
        card.put("weight",             pkg.getWeight());
        card.put("fromAddress",        pkg.getFromAddress());
        card.put("toAddress",          pkg.getToAddress());
        card.put("latitude",           pkg.getLatitude());
        card.put("longitude",          pkg.getLongitude());
        card.put("toLatitude",         pkg.getToLatitude());
        card.put("toLongitude",        pkg.getToLongitude());
        card.put("distanceKm",         Math.round(distanceKm * 10.0) / 10.0);
        card.put("tripCharge",         pkg.getTripCharge());
        card.put("pricePerKg",         pkg.getPricePerKg());
        card.put("pricePerTon",        pkg.getPricePerTon());
        card.put("pickUpDate",         pkg.getPickUpDate());
        card.put("dropDate",           pkg.getDropDate());
        card.put("availableTime",      pkg.getAvailableTime()  != null ? pkg.getAvailableTime().toString()  : null);
        card.put("deadlineTime",       pkg.getDeadlineTime()   != null ? pkg.getDeadlineTime().toString()   : null);
        card.put("insurance",          pkg.getInsurance());
        card.put("transportType",      pkg.getTransportType()  != null ? pkg.getTransportType().name()  : null);
        card.put("senderName",         pkg.getSender()         != null ? pkg.getSender().getFullName()   : "Unknown");
        card.put("senderAvatar",       pkg.getSender()         != null ? pkg.getSender().getProfileUrl() : null);
        card.put("status",             pkg.getStatus()         != null ? pkg.getStatus().name()          : null);
        card.put("createdAt",          pkg.getCreatedAt());

        double tripCharge = pkg.getTripCharge() != null ? pkg.getTripCharge() : 0.0;
        card.put("estimatedEarnings",  tripCharge);
        card.put("carrierEarning",     Math.round(tripCharge * 0.85 * 100.0) / 100.0);
        card.put("platformFee",        Math.round(tripCharge * 0.15 * 100.0) / 100.0);
        return card;
    }
    private static class CachedResult {
        final Map<String, Object> data;
        final long cachedAt;
        CachedResult(Map<String, Object> data) {
            this.data     = data;
            this.cachedAt = System.currentTimeMillis();
        }
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > CACHE_TTL_MS;
        }
    }
    public Map<String, Object> getNearbyRequests(String carrierId,
                                                 double latitude,
                                                 double longitude,
                                                 double radiusKm) {

        CarrierProfile profile = carrierProfileRepository.findByUserUserId(carrierId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Carrier profile not found: " + carrierId));

        // 1. Rider must be online
        if (!Boolean.TRUE.equals(profile.getIsOnline())) {
            log.info("Carrier {} is offline — returning empty nearby requests", carrierId);

            return Map.of(
                    "carrierLat", latitude,
                    "carrierLng", longitude,
                    "radiusKm", radiusKm,
                    "totalFound", 0,
                    "requests", List.of(),
                    "online", false,
                    "commissionBlocked", false
            );
        }

        // 2. Rider must have cleared commission
        boolean canStartTrip = paymentService.canCarrierStartTrip(carrierId);

        if (!canStartTrip) {
            log.info("Carrier {} has pending commission — blocking nearby rides",
                    carrierId);

            return Map.of(
                    "carrierLat", latitude,
                    "carrierLng", longitude,
                    "radiusKm", radiusKm,
                    "totalFound", 0,
                    "requests", List.of(),
                    "online", true,
                    "commissionBlocked", true
            );
        }

        // 3. Only check cache AFTER commission validation
        String cacheKey = String.format(
                "%s_%.2f_%.2f_%.1f",
                carrierId,
                latitude,
                longitude,
                radiusKm
        );

        CachedResult cached = nearbyCache.get(cacheKey);

        if (cached != null && !cached.isExpired()) {
            log.debug("Cache HIT for carrier {} — skipping DB query", carrierId);
            return cached.data;
        }

        LocalDate today = LocalDate.now();

        // 4. Bounding box
        double latDelta = radiusKm / 111.32;

        double cosLat = Math.cos(Math.toRadians(latitude));

        // Prevent division by zero near the poles
        double lngDelta = cosLat == 0
                ? radiusKm / 111.32
                : radiusKm / (111.32 * cosLat);

        List<Package> candidates =
                packageRepository.findActivePackagesInBoundingBox(
                        latitude - latDelta,
                        latitude + latDelta,
                        longitude - lngDelta,
                        longitude + lngDelta,
                        carrierId
                );

        // 5. Exact distance + date filtering
        List<Map<String, Object>> nearby = candidates.stream()
                .filter(pkg -> {

                    Long pkgId = pkg.getPackageId();

                    // Never show rider's own package
                    if (pkg.getSender() != null
                            && carrierId.equals(pkg.getSender().getUserId())) {
                        return false;
                    }

                    // Package must have valid coordinates
                   

                    // Exact Haversine distance
                    double dist = haversineKm(
                            latitude,
                            longitude,
                            pkg.getLatitude(),
                            pkg.getLongitude()
                    );

                    if (dist > radiusKm) {
                        return false;
                    }

                    // Date filtering
                    if (pkg.getPickUpDate() != null
                            && !pkg.getPickUpDate().isBlank()) {

                        try {

                            LocalDate dropDate =
                                    (pkg.getDropDate() != null
                                            && !pkg.getDropDate().isBlank())
                                            ? LocalDate.parse(
                                            pkg.getDropDate(),
                                            DATE_FORMATTER
                                    )
                                            : null;

                            if (dropDate != null
                                    && today.isAfter(dropDate)) {
                                return false;
                            }

                            LocalDate pickupDate =
                                    LocalDate.parse(
                                            pkg.getPickUpDate(),
                                            DATE_FORMATTER
                                    );

                            if (pickupDate.isAfter(today.plusDays(30))) {
                                return false;
                            }

                        } catch (Exception e) {

                            log.warn(
                                    "Package {} has invalid date — including anyway",
                                    pkgId
                            );
                        }
                    }

                    return true;
                })
                .map(pkg -> {

                    double dist = haversineKm(
                            latitude,
                            longitude,
                            pkg.getLatitude(),
                            pkg.getLongitude()
                    );

                    return buildPackageCard(pkg, dist);
                })
                .sorted((a, b) ->
                        Double.compare(
                                (Double) a.get("distanceKm"),
                                (Double) b.get("distanceKm")
                        )
                )
                .collect(Collectors.toList());

        // 6. Response
        Map<String, Object> response = new HashMap<>();

        response.put("carrierLat", latitude);
        response.put("carrierLng", longitude);
        response.put("radiusKm", radiusKm);
        response.put("totalFound", nearby.size());
        response.put("requests", nearby);
        response.put("online", true);
        response.put("commissionBlocked", false);

        // 7. Cache only eligible rider results
        nearbyCache.put(
                cacheKey,
                new CachedResult(response)
        );

        nearbyCache.entrySet()
                .removeIf(e -> e.getValue().isExpired());

        return response;
    }
    public void invalidateNearbyCache(String carrierId) {
        nearbyCache.entrySet().removeIf(e -> e.getKey().startsWith(carrierId + "_"));
        log.info("Cache invalidated for carrier {}", carrierId);
    }
}