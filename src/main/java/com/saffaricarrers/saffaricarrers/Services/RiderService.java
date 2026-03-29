package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Entity.CarrierProfile;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import com.saffaricarrers.saffaricarrers.Entity.User;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import com.saffaricarrers.saffaricarrers.Repository.CarrierProfileRepository;
import com.saffaricarrers.saffaricarrers.Repository.PackageRepository;
import com.saffaricarrers.saffaricarrers.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RiderService {

    private final UserRepository userRepository;
    private final CarrierProfileRepository carrierProfileRepository;
    private final PackageRepository packageRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ─────────────────────────────────────────────────────────────────────────
    // ONLINE / OFFLINE STATUS
    // ─────────────────────────────────────────────────────────────────────────

    public Map<String, Object> setOnlineStatus(String carrierId, boolean online) {
        // ✅ Go directly to carrier_profiles — avoids loading User + lazy bank_details join
        CarrierProfile profile = carrierProfileRepository.findByUserUserId(carrierId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrier profile not found: " + carrierId));

        profile.setIsOnline(online);

        // When going offline, clear stale location
        if (!online) {
            profile.setLastLat(null);
            profile.setLastLng(null);
            profile.setLastLocationAt(null);
        }

        carrierProfileRepository.save(profile);
        log.info("Carrier {} is now {}", carrierId, online ? "ONLINE" : "OFFLINE");

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
    // NEARBY REQUESTS — date-filtered, same logic as DeliveryRequestService
    // ─────────────────────────────────────────────────────────────────────────

    public Map<String, Object> getNearbyRequests(String carrierId, double latitude, double longitude,
                                                 double radiusKm) {
        userRepository.findByUserId(carrierId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrier not found: " + carrierId));

        LocalDate today = LocalDate.now();

        List<Package> allPackages = packageRepository.findByStatus(Package.PackageStatus.CREATED);

        List<Map<String, Object>> nearby = allPackages.stream()
                .filter(pkg -> {
                    // 1. Must have valid coordinates
                    Double pkgLat = pkg.getLatitude();
                    Double pkgLng = pkg.getLongitude();
                    if (pkgLat == null || pkgLng == null) return false;

                    // 2. Must be within radius
                    double dist = haversineKm(latitude, longitude, pkgLat, pkgLng);
                    if (dist > radiusKm) return false;

                    // 3. Date filter — today must be within [pickUpDate, dropDate]
                    try {
                        LocalDate pickupDate = LocalDate.parse(pkg.getPickUpDate(), DATE_FORMATTER);
                        LocalDate dropDate   = LocalDate.parse(pkg.getDropDate(),   DATE_FORMATTER);
                        boolean pickupOk = !today.isBefore(pickupDate);
                        boolean dropOk   = !today.isAfter(dropDate);
                        if (!pickupOk || !dropOk) {
                            log.debug("Package {} excluded — dates: pickup={} drop={} today={}",
                                    pkg.getPackageId(), pickupDate, dropDate, today);
                            return false;
                        }
                    } catch (Exception e) {
                        log.warn("Package {} has unparseable dates — skipping: {}", pkg.getPackageId(), e.getMessage());
                        return false;
                    }

                    return true;
                })
                .map(pkg -> {
                    double dist = haversineKm(latitude, longitude, pkg.getLatitude(), pkg.getLongitude());
                    return buildPackageCard(pkg, dist);
                })
                .sorted((a, b) -> Double.compare(
                        (Double) a.get("distanceKm"),
                        (Double) b.get("distanceKm")))
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("carrierLat", latitude);
        response.put("carrierLng", longitude);
        response.put("radiusKm", radiusKm);
        response.put("totalFound", nearby.size());
        response.put("requests", nearby);
        return response;
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
        card.put("senderName",         pkg.getSender()         != null ? pkg.getSender().getFullName()  : "Unknown");
        card.put("senderAvatar",       pkg.getSender()         != null ? pkg.getSender().getProfileUrl() : null);
        card.put("status",             pkg.getStatus()         != null ? pkg.getStatus().name()         : null);
        card.put("createdAt",          pkg.getCreatedAt());

        double tripCharge = pkg.getTripCharge() != null ? pkg.getTripCharge() : 0.0;
        card.put("estimatedEarnings",  tripCharge);
        card.put("carrierEarning",     Math.round(tripCharge * 0.85 * 100.0) / 100.0);
        card.put("platformFee",        Math.round(tripCharge * 0.15 * 100.0) / 100.0);
        return card;
    }
}