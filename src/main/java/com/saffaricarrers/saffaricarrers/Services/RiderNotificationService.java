package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Entity.CarrierProfile;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import com.saffaricarrers.saffaricarrers.Repository.CarrierProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiderNotificationService {

    private final CarrierProfileRepository carrierProfileRepository;
    private final FirebaseNotificationService firebaseNotificationService;

    // Max radius any carrier could ever set (matches our UI cap of 30km)
    private static final double MAX_POSSIBLE_RADIUS_KM = 30.0;

    /**
     * Called immediately after a package is created.
     *
     * SCALING FIX: Instead of loading ALL online carriers into memory,
     * we first do a bounding-box query in the DB to get only carriers
     * within MAX_POSSIBLE_RADIUS_KM of the package. This means at scale
     * with 10,000 online carriers spread across India, only the ~50
     * carriers near Chennai get loaded — not all 10,000.
     *
     * Then we do exact Haversine per carrier to respect their personal radius.
     *
     * Runs @Async so it never delays the sender's createPackage response.
     */
    @Async
    public void notifyNearbyOnlineCarriers(Package pkg) {
        // ✅ FIX: Package lat/lng are primitive double — check for 0.0 not null
        if (pkg.getLatitude() == 0.0 && pkg.getLongitude() == 0.0) {
            log.warn("Package {} has no coordinates — skipping rider notifications", pkg.getPackageId());
            return;
        }

        double pkgLat = pkg.getLatitude();
        double pkgLng = pkg.getLongitude();

        // ✅ SCALING FIX: bounding box in DB, not full table scan
        // Degrees per km: lat ~111.32km, lng varies by latitude
        double latDelta = MAX_POSSIBLE_RADIUS_KM / 111.32;
        double lngDelta = MAX_POSSIBLE_RADIUS_KM / (111.32 * Math.cos(Math.toRadians(pkgLat)));

        double minLat = pkgLat - latDelta;
        double maxLat = pkgLat + latDelta;
        double minLng = pkgLng - lngDelta;
        double maxLng = pkgLng + lngDelta;

        // Only fetches carriers whose last_lat/last_lng fall inside the bounding box
        List<CarrierProfile> nearbyOnlineCarriers =
                carrierProfileRepository.findOnlineCarriersInBoundingBox(minLat, maxLat, minLng, maxLng);

        if (nearbyOnlineCarriers.isEmpty()) {
            log.info("No online carriers near package {} — skipping", pkg.getPackageId());
            return;
        }

        log.info("Package {} — checking {} nearby online carrier(s)", pkg.getPackageId(), nearbyOnlineCarriers.size());

        int notified = 0;

        for (CarrierProfile carrier : nearbyOnlineCarriers) {
            // Carrier location already validated by the DB query (non-null, in bounding box)
            double radius = carrier.getSearchRadiusKm() != null ? carrier.getSearchRadiusKm() : 10.0;
            double dist   = haversineKm(carrier.getLastLat(), carrier.getLastLng(), pkgLat, pkgLng);

            // Exact Haversine check — bounding box is a square, we want a circle
            if (dist > radius) continue;

            String fcmToken = carrier.getUser().getFcmToken();
            if (fcmToken == null || fcmToken.isBlank()) continue;

            Map<String, String> data = Map.of(
                    "type",        "RIDER_REQUEST",
                    "packageId",   String.valueOf(pkg.getPackageId()),
                    "productName", pkg.getProductName()  != null ? pkg.getProductName()  : "",
                    "fromAddress", pkg.getFromAddress()  != null ? pkg.getFromAddress()  : "",
                    "toAddress",   pkg.getToAddress()    != null ? pkg.getToAddress()    : "",
                    "distanceKm",  String.format("%.1f", dist),
                    "tripCharge",  pkg.getTripCharge()   != null ? String.valueOf(pkg.getTripCharge()) : "0"
            );

            firebaseNotificationService.sendNotificationWithData(
                    fcmToken,
                    "📦 New Package Nearby",
                    pkg.getProductName() + " · " + String.format("%.1f", dist) + " km · ₹" + pkg.getTripCharge(),
                    data
            );

            log.info("Notified carrier {} ({} km away) for package {}",
                    carrier.getUser().getUserId(), String.format("%.1f", dist), pkg.getPackageId());
            notified++;
        }

        log.info("Package {} — notified {} carrier(s)", pkg.getPackageId(), notified);
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}