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
    private final PaymentService paymentService;

    // Must match the maximum rider search radius allowed in RiderService.
    private static final double MAX_POSSIBLE_RADIUS_KM = 50.0;

    /**
     * Called immediately after a package is created.
     *
     * A carrier will receive a notification ONLY when:
     *
     * 1. Carrier is online
     * 2. Carrier has a valid location
     * 3. Carrier has an FCM token
     * 4. Carrier has NO pending commission
     * 5. Package is inside the carrier's configured search radius
     *
     * Runs @Async so package creation is not delayed by FCM notifications.
     */
    @Async
    public void notifyNearbyOnlineCarriers(Package pkg) {

        if (pkg == null) {
            log.warn("Cannot notify carriers — package is null");
            return;
        }

        double pkgLat = pkg.getLatitude();
        double pkgLng = pkg.getLongitude();

        if (pkgLat == 0.0 && pkgLng == 0.0) {
            log.warn(
                    "Package {} has invalid coordinates (0,0) — skipping rider notifications",
                    pkg.getPackageId()
            );
            return;
        }

        // Sender's userId — used to prevent self-notification.
        String senderUserId =
                pkg.getSender() != null
                        ? pkg.getSender().getUserId()
                        : null;

        /*
         * Bounding box.
         *
         * This prevents loading every online carrier in the database.
         * Only carriers roughly within MAX_POSSIBLE_RADIUS_KM are loaded.
         */
        double latDelta =
                MAX_POSSIBLE_RADIUS_KM / 111.32;

        double cosLat =
                Math.cos(Math.toRadians(pkgLat));

        // Avoid division by zero at extreme latitudes.
        double lngDelta;

        if (Math.abs(cosLat) < 0.000001) {
            lngDelta = 180.0;
        } else {
            lngDelta =
                    MAX_POSSIBLE_RADIUS_KM / (111.32 * cosLat);
        }

        /*
         * Only carriers with a known location are considered.
         *
         * We intentionally DO NOT notify carriers with no location
         * because their distance from the package cannot be verified.
         */
        List<CarrierProfile> locatedCarriers =
                carrierProfileRepository.findOnlineCarriersInBoundingBox(
                        pkgLat - latDelta,
                        pkgLat + latDelta,
                        pkgLng - lngDelta,
                        pkgLng + lngDelta
                );

        log.info(
                "Package {} — {} online carrier(s) with location found",
                pkg.getPackageId(),
                locatedCarriers.size()
        );

        int notified = 0;
        int commissionBlocked = 0;
        int outsideRadius = 0;

        for (CarrierProfile carrier : locatedCarriers) {

            if (carrier == null || carrier.getUser() == null) {
                continue;
            }

            String carrierId =
                    carrier.getUser().getUserId();

            /*
             * Safety check — carrier should already be online
             * because the repository query filters online carriers.
             */
            if (!Boolean.TRUE.equals(carrier.getIsOnline())) {
                log.debug(
                        "Skipping carrier {} — currently offline",
                        carrierId
                );
                continue;
            }

            /*
             * Prevent a carrier from receiving their own package.
             */
            if (senderUserId != null
                    && senderUserId.equals(carrierId)) {

                log.info(
                        "Skipping notification — carrier {} created this package",
                        carrierId
                );

                continue;
            }

            /*
             * FCM token required.
             */
            String fcmToken =
                    carrier.getUser().getFcmToken();

            if (fcmToken == null || fcmToken.isBlank()) {
                log.debug(
                        "Skipping carrier {} — no FCM token",
                        carrierId
                );
                continue;
            }

            /*
             * IMPORTANT:
             *
             * Existing PaymentService logic already determines
             * whether this carrier has pending COD commission.
             *
             * If commission is pending:
             *
             *     ❌ No new ride
             *     ❌ No FCM notification
             */
            boolean canStartTrip;

            try {
                canStartTrip =
                        paymentService.canCarrierStartTrip(carrierId);
            } catch (Exception e) {
                /*
                 * Fail closed.
                 *
                 * If we cannot verify commission eligibility,
                 * do NOT send a new ride notification.
                 */
                log.error(
                        "Unable to verify commission eligibility for carrier {}. " +
                                "Skipping notification.",
                        carrierId,
                        e
                );

                continue;
            }

            if (!canStartTrip) {

                commissionBlocked++;

                log.info(
                        "Skipping notification — carrier {} has pending commission",
                        carrierId
                );

                continue;
            }

            /*
             * Carrier's personal search radius.
             *
             * Default = 10 km.
             */
            double radius =
                    carrier.getSearchRadiusKm() != null
                            ? carrier.getSearchRadiusKm()
                            : 10.0;

            /*
             * Exact distance calculation.
             */
            Double carrierLat =
                    carrier.getLastLat();

            Double carrierLng =
                    carrier.getLastLng();

            if (carrierLat == null || carrierLng == null) {
                log.debug(
                        "Skipping carrier {} — location missing",
                        carrierId
                );
                continue;
            }

            if (carrierLat == 0.0 && carrierLng == 0.0) {
                log.debug(
                        "Skipping carrier {} — invalid location (0,0)",
                        carrierId
                );
                continue;
            }

            double distance =
                    haversineKm(
                            carrierLat,
                            carrierLng,
                            pkgLat,
                            pkgLng
                    );

            /*
             * IMPORTANT:
             *
             * Exact radius check.
             *
             * Example:
             *
             * Rider radius = 10 km
             * Package distance = 7.5 km
             * => notification
             *
             * Rider radius = 10 km
             * Package distance = 14 km
             * => no notification
             */
            if (distance > radius) {

                outsideRadius++;

                log.info(
                        "Carrier {} is {} km away — outside {} km radius, skipping",
                        carrierId,
                        String.format("%.1f", distance),
                        String.format("%.1f", radius)
                );

                continue;
            }

            /*
             * Everything passed.
             */
            sendNotification(
                    carrier,
                    pkg,
                    String.format("%.1f", distance)
            );

            notified++;
        }

        log.info(
                "Package {} — notified {} carrier(s), " +
                        "{} commission-blocked, {} outside radius",
                pkg.getPackageId(),
                notified,
                commissionBlocked,
                outsideRadius
        );
    }

    /**
     * Build and send rider FCM notification.
     */
    private void sendNotification(
            CarrierProfile carrier,
            Package pkg,
            String distanceLabel
    ) {

        String fcmToken =
                carrier.getUser().getFcmToken();

        Map<String, String> data = Map.of(
                "type",
                "RIDER_REQUEST",

                "packageId",
                String.valueOf(pkg.getPackageId()),

                "productName",
                pkg.getProductName() != null
                        ? pkg.getProductName()
                        : "",

                "fromAddress",
                pkg.getFromAddress() != null
                        ? pkg.getFromAddress()
                        : "",

                "toAddress",
                pkg.getToAddress() != null
                        ? pkg.getToAddress()
                        : "",

                "distanceKm",
                distanceLabel,

                "tripCharge",
                pkg.getTripCharge() != null
                        ? String.valueOf(pkg.getTripCharge())
                        : "0"
        );

        String body =
                (pkg.getProductName() != null
                        ? pkg.getProductName()
                        : "New package")
                        + " · "
                        + distanceLabel
                        + " km · ₹"
                        + (pkg.getTripCharge() != null
                        ? pkg.getTripCharge()
                        : "0");

        firebaseNotificationService.sendNotificationWithData(
                fcmToken,
                "📦 New Package Nearby",
                body,
                data
        );

        log.info(
                "Notified carrier {} ({} km) for package {}",
                carrier.getUser().getUserId(),
                distanceLabel,
                pkg.getPackageId()
        );
    }

    /**
     * Exact Haversine distance in kilometers.
     */
    private double haversineKm(
            double lat1,
            double lng1,
            double lat2,
            double lng2
    ) {

        final int R = 6371;

        double dLat =
                Math.toRadians(lat2 - lat1);

        double dLng =
                Math.toRadians(lng2 - lng1);

        double a =
                Math.sin(dLat / 2)
                        * Math.sin(dLat / 2)
                        +
                        Math.cos(Math.toRadians(lat1))
                                * Math.cos(Math.toRadians(lat2))
                                * Math.sin(dLng / 2)
                                * Math.sin(dLng / 2);

        return R
                * 2
                * Math.atan2(
                Math.sqrt(a),
                Math.sqrt(1 - a)
        );
    }
}