package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Dtos.*;
import com.saffaricarrers.saffaricarrers.Entity.*;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import com.saffaricarrers.saffaricarrers.Repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AutoCreateService {

    private final PackageRepository packageRepository;
    private final CarrierRouteRepository carrierRouteRepository;
    private final CarrierProfileRepository carrierProfileRepository;
    private final UserRepository userRepository;
    private final DeliveryRequestRepository deliveryRequestRepository;
    private final RoutePricingRepository pricingRepository;
    private final NotificationRepository notificationRepository;
    private final FirebaseNotificationService firebaseNotificationService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ================================================================
    // CARRIER: auto-create route from package, then send request
    // ================================================================

    /**
     * Called when a carrier views a package (SenderDetailScreen)
     * and has no matching routes.
     *
     * Steps:
     *  1. Load package details
     *  2. Load carrier profile (must exist + have pricing)
     *  3. Create a CarrierRoute mirroring the package's transport type, dates, coords
     *  4. Attach the first pricing entry from any existing route (or create a default)
     *  5. Create a DeliveryRequest (CARRIER_TO_SENDER)
     *  6. Return requestId + routeId
     */
    public AutoCreateResult autoCreateRouteAndSendRequest(String carrierId, Long packageId) {
        log.info("Auto-create route for carrier={} from package={}", carrierId, packageId);

        // 1. Load entities
        User carrier = userRepository.findByUserId(carrierId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrier not found"));

        CarrierProfile carrierProfile = carrierProfileRepository.findByUser(carrier)
                .orElseThrow(() -> new IllegalArgumentException(
                        "You need to complete your carrier profile before requesting packages."));

        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));

        if (pkg.getStatus() != Package.PackageStatus.CREATED) {
            throw new IllegalStateException("Package is no longer available for requests.");
        }

        // 2. Guard: no duplicate pending/active request
        boolean alreadyRequested = deliveryRequestRepository
                .findByPackageEntityAndCarrier(pkg, carrier)
                .stream()
                .anyMatch(r -> r.getStatus() == DeliveryRequest.RequestStatus.PENDING
                        || r.getStatus() == DeliveryRequest.RequestStatus.ACCEPTED
                        || r.getStatus() == DeliveryRequest.RequestStatus.PICKED_UP
                        || r.getStatus() == DeliveryRequest.RequestStatus.IN_TRANSIT);

        if (alreadyRequested) {
            throw new IllegalStateException("You already have an active request for this package.");
        }

        // 3. Parse dates from package
        LocalDate pickupDate = LocalDate.parse(pkg.getPickUpDate(), DATE_FMT);
        LocalDate dropDate   = LocalDate.parse(pkg.getDropDate(), DATE_FMT);

        // 4. Build CarrierRoute mirroring the package
        CarrierRoute route = new CarrierRoute();
        route.setCarrierProfile(carrierProfile);
        route.setFromLocation(pkg.getFromAddress());
        route.setToLocation(pkg.getToAddress());
        route.setAvailableDate(pickupDate);
        route.setDeadlineDate(dropDate);

        LocalTime availableTime = pkg.getAvailableTime() != null
                ? pkg.getAvailableTime() : LocalTime.of(8, 0);
        LocalTime deadlineTime  = pkg.getDeadlineTime() != null
                ? pkg.getDeadlineTime()  : LocalTime.of(20, 0);

        route.setAvailableTime(availableTime);
        route.setDeadlineTime(deadlineTime);
        route.setTransportType(pkg.getTransportType());
        route.setMaxWeight(pkg.getWeight() != null ? pkg.getWeight() * 5 : 100.0);
        route.setMaxQuantity(10);
        route.setCurrentWeight(0.0);
        route.setCurrentQuantity(0);
        route.setRouteStatus(CarrierRoute.RouteStatus.CREATED);

        // Coordinates — from the package
        route.setLatitude(pkg.getLatitude());
        route.setLongitude(pkg.getLongitude());
        route.setToLatitude(pkg.getToLatitude());
        route.setToLongitude(pkg.getToLongitude());

        // LongAddressId — try to copy from package, fallback 0
        route.setLongAddressId(pkg.getAddressId() != null ? pkg.getAddressId() : 0L);

        CarrierRoute savedRoute = carrierRouteRepository.save(route);
        carrierRouteRepository.flush();
        log.info("Auto-created route id={}", savedRoute.getRouteId());

        // 5. Create RoutePricing — copy from any existing carrier route, else use defaults
        RoutePricing pricing = buildDefaultPricing(savedRoute, carrierId, pkg);
        pricingRepository.save(pricing);
        pricingRepository.flush();

        // 6. Build DeliveryRequest
        Double totalAmount      = calculateAmount(pkg, savedRoute, pricing);
        Double platformComm     = totalAmount * 0.15;
        Double carrierEarning   = totalAmount - platformComm;

        DeliveryRequest req = new DeliveryRequest();
        req.setPackageEntity(pkg);
        req.setCarrierRoute(savedRoute);
        req.setSender(pkg.getSender());
        req.setCarrier(carrier);
        req.setStatus(DeliveryRequest.RequestStatus.PENDING);
        req.setTotalAmount(totalAmount);
        req.setPlatformCommission(platformComm);
        req.setCarrierEarning(carrierEarning);
        req.setPickupOtp(pkg.getPickupOtp());
        req.setDeliveryOtp(pkg.getDeliveryOtp());
        req.setRequestedAt(LocalDateTime.now());
        req.setCarrierNote("Carrier auto-matched to your package route.");

        DeliveryRequest savedReq = deliveryRequestRepository.save(req);

        // 7. Update package status
        pkg.setStatus(Package.PackageStatus.REQUEST_SENT);
        packageRepository.save(pkg);

        // 8. Notify sender
        notifySender(pkg.getSender(), pkg.getPackageId(),
                "New Carrier Request",
                carrier.getFullName() + " wants to deliver your package.");

        log.info("Auto-create route+request done: routeId={} requestId={}",
                savedRoute.getRouteId(), savedReq.getRequestId());

        return AutoCreateResult.builder()
                .success(true)
                .message("Route created and request sent to sender.")
                .createdRouteId(savedRoute.getRouteId())
                .requestId(savedReq.getRequestId())
                .totalAmount(totalAmount)
                .platformCommission(platformComm)
                .carrierEarning(carrierEarning)
                .build();
    }

    // ================================================================
    // SENDER: auto-create package from route, then send request
    // ================================================================

    /**
     * Called when a sender views a carrier route (CarrierDetailScreen)
     * and has no matching packages.
     *
     * Steps:
     *  1. Load route details
     *  2. Create a Package mirroring the route's transport type, dates, coords
     *  3. Create a DeliveryRequest (SENDER_TO_CARRIER)
     *  4. Return requestId + packageId
     */
    public AutoCreateResult autoCreatePackageAndSendRequest(
            String senderId, AutoCreatePackageRequest body) {

        log.info("Auto-create package for sender={} from route={}", senderId, body.getRouteId());

        // 1. Load entities
        User sender = userRepository.findByUserId(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));

        CarrierRoute route = carrierRouteRepository.findById(body.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Carrier route not found"));

        if (route.getRouteStatus() != CarrierRoute.RouteStatus.CREATED) {
            throw new IllegalStateException("This carrier route is no longer available.");
        }

        // 2. Resolve dates — use what sender provided, else fall back to route dates
        String pickupDateStr = (body.getPickUpDate() != null && !body.getPickUpDate().isEmpty())
                ? body.getPickUpDate()
                : route.getAvailableDate().format(DATE_FMT);

        String dropDateStr = (body.getDropDate() != null && !body.getDropDate().isEmpty())
                ? body.getDropDate()
                : route.getDeadlineDate().format(DATE_FMT);

        // 3. Build Package
        Package pkg = new Package();
        pkg.setSender(sender);

        pkg.setProductName(body.getProductName() != null && !body.getProductName().isBlank()
                ? body.getProductName() : "My Package");
        pkg.setProductDescription(body.getProductDescription() != null
                ? body.getProductDescription() : "Auto-created from carrier route");
        pkg.setProductValue(body.getProductValue() != null ? body.getProductValue() : 0.0);
        pkg.setWeight(body.getWeight() != null ? body.getWeight() : 1.0);
        pkg.setLength(0.0);
        pkg.setWidth(0.0);
        pkg.setHeight(0.0);

        // Infer productType from any pricing on the route
        List<RoutePricing> routePricings = pricingRepository.findByCarrierRoute(route);
        RoutePricing.ProductType productType = routePricings.isEmpty()
                ? RoutePricing.ProductType.APPAREL
                : routePricings.get(0).getProductType();
        pkg.setProductType(productType);

        pkg.setTransportType(route.getTransportType());

        pkg.setFromAddress(resolveAddr(body.getFromAddress(), route.getFromLocation()));
        pkg.setToAddress(resolveAddr(body.getToAddress(), route.getToLocation()));
        pkg.setAddressId(body.getAddressId() != null ? body.getAddressId() : 0L);

        // Coordinates — prefer what sender passed, else copy from route
        pkg.setLatitude(body.getLatitude() != null ? body.getLatitude() : nvl(route.getLatitude()));
        pkg.setLongitude(body.getLongitude() != null ? body.getLongitude() : nvl(route.getLongitude()));
        pkg.setToLatitude(body.getToLatitude() != null ? body.getToLatitude() : nvl(route.getToLatitude()));
        pkg.setToLongitude(body.getToLongitude() != null ? body.getToLongitude() : nvl(route.getToLongitude()));

        pkg.setPickUpDate(pickupDateStr);
        pkg.setDropDate(dropDateStr);
        pkg.setAvailableTime(route.getAvailableTime());
        pkg.setDeadlineTime(route.getDeadlineTime());

        pkg.setTripCharge(estimateTripCharge(route, routePricings));
        pkg.setPricePerKg(estimatePricePerKg(routePricings));
        pkg.setPricePerTon(estimatePricePerTon(routePricings));
        pkg.setInsurance(false);

        pkg.setPickupOtp(generateOtp());
        pkg.setDeliveryOtp(generateOtp());
        pkg.setStatus(Package.PackageStatus.REQUEST_SENT); // immediately goes to REQUEST_SENT
        pkg.setUrl(sender.getProfileUrl());

        Package savedPkg = packageRepository.save(pkg);
        packageRepository.flush();
        log.info("Auto-created package id={}", savedPkg.getPackageId());

        // 4. Calculate pricing from route
        if (routePricings.isEmpty()) {
            throw new IllegalArgumentException(
                    "Carrier route has no pricing configured. Cannot auto-create request.");
        }
        RoutePricing pricing = routePricings.get(0);
        Double totalAmount  = calculateAmount(savedPkg, route, pricing);
        Double platformComm = totalAmount * 0.15;
        Double carrierEarning = totalAmount - platformComm;

        // 5. Build DeliveryRequest
        DeliveryRequest req = new DeliveryRequest();
        req.setPackageEntity(savedPkg);
        req.setCarrierRoute(route);
        req.setSender(sender);
        req.setCarrier(route.getCarrierProfile().getUser());
        req.setStatus(DeliveryRequest.RequestStatus.PENDING);
        req.setTotalAmount(totalAmount);
        req.setPlatformCommission(platformComm);
        req.setCarrierEarning(carrierEarning);
        req.setPickupOtp(savedPkg.getPickupOtp());
        req.setDeliveryOtp(savedPkg.getDeliveryOtp());
        req.setRequestedAt(LocalDateTime.now());
        req.setSenderNote("Sender auto-matched to your carrier route.");

        DeliveryRequest savedReq = deliveryRequestRepository.save(req);

        // 6. Notify carrier
        notifyCarrier(route.getCarrierProfile().getUser(), route.getRouteId(),
                "New Package Request",
                sender.getFullName() + " wants you to deliver a package along your route.");

        log.info("Auto-create package+request done: packageId={} requestId={}",
                savedPkg.getPackageId(), savedReq.getRequestId());

        return AutoCreateResult.builder()
                .success(true)
                .message("Package created and request sent to carrier.")
                .createdPackageId(savedPkg.getPackageId())
                .requestId(savedReq.getRequestId())
                .totalAmount(totalAmount)
                .platformCommission(platformComm)
                .carrierEarning(carrierEarning)
                .build();
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    private RoutePricing buildDefaultPricing(CarrierRoute route,
                                             String carrierId,
                                             Package pkg) {
        // Try to copy pricing from any existing route by this carrier
        List<CarrierRoute> existingRoutes = carrierRouteRepository
                .findByCarrierProfileUserUserId(carrierId);

        for (CarrierRoute existingRoute : existingRoutes) {
            List<RoutePricing> pricings = pricingRepository.findByCarrierRoute(existingRoute);
            if (!pricings.isEmpty()) {
                RoutePricing src = pricings.get(0);
                RoutePricing copy = new RoutePricing();
                copy.setCarrierRoute(route);
                copy.setProductType(src.getProductType());
                copy.setWeightLimit(src.getWeightLimit());
                copy.setFixedPrice(src.getFixedPrice());
                copy.setPricePerTon(src.getPricePerTon());
                log.info("Copied pricing from existing route={}", existingRoute.getRouteId());
                return copy;
            }
        }

        // Fallback: build a sensible default based on transport type
        RoutePricing p = new RoutePricing();
        p.setCarrierRoute(route);
        p.setProductType(pkg.getProductType() != null
                ? pkg.getProductType() : RoutePricing.ProductType.APPAREL);
        p.setWeightLimit(100.0);

        boolean isTonBased = isTonBasedTransport(route.getTransportType());
        if (isTonBased) {
            p.setPricePerTon(pkg.getPricePerTon() != null ? pkg.getPricePerTon() : 500.0);
            p.setFixedPrice(0.0);
        } else {
            p.setFixedPrice(pkg.getTripCharge() != null ? pkg.getTripCharge() : 100.0);
            p.setPricePerTon(null);
        }

        log.info("Built default pricing for route (no existing pricing found)");
        return p;
    }

    private Double calculateAmount(Package pkg, CarrierRoute route, RoutePricing pricing) {
        boolean isTonBased = isTonBasedTransport(route.getTransportType());
        if (isTonBased && pricing.getPricePerTon() != null && pricing.getPricePerTon() > 0) {
            Double weightTons = (pkg.getWeight() != null ? pkg.getWeight() : 1.0) / 1000.0;
            return pricing.getPricePerTon() * weightTons;
        }
        return pricing.getFixedPrice() != null ? pricing.getFixedPrice() : 100.0;
    }

    private boolean isTonBasedTransport(CarrierRoute.TransportType t) {
        return t == CarrierRoute.TransportType.TRUCK
                || t == CarrierRoute.TransportType.LORRY
                || t == CarrierRoute.TransportType.TIPPER;
    }

    private Double estimateTripCharge(CarrierRoute route, List<RoutePricing> pricings) {
        if (pricings.isEmpty()) return 100.0;
        RoutePricing p = pricings.get(0);
        return p.getFixedPrice() != null ? p.getFixedPrice() : 100.0;
    }

    private Double estimatePricePerKg(List<RoutePricing> pricings) {
        if (pricings.isEmpty()) return null;
        return pricings.get(0).getPricePerTon() != null
                ? pricings.get(0).getPricePerTon() / 1000.0 : null;
    }

    private Double estimatePricePerTon(List<RoutePricing> pricings) {
        if (pricings.isEmpty()) return null;
        return pricings.get(0).getPricePerTon();
    }

    private String resolveAddr(String fromBody, String fromRoute) {
        if (fromBody != null && !fromBody.isBlank()) return fromBody;
        if (fromRoute != null && !fromRoute.isBlank()) return fromRoute;
        return "Not specified";
    }

    private double nvl(Double d) {
        return d != null ? d : 0.0;
    }

    private String generateOtp() {
        return String.valueOf(100000 + new Random().nextInt(900000));
    }

    private void notifySender(User sender, Long packageId, String title, String message) {
        createNotification(sender, title, message,
                Notification.NotificationType.DELIVERY_REQUEST, packageId);
    }

    private void notifyCarrier(User carrier, Long routeId, String title, String message) {
        createNotification(carrier, title, message,
                Notification.NotificationType.DELIVERY_REQUEST, routeId);
    }

    private void createNotification(User user, String title, String message,
                                    Notification.NotificationType type, Long referenceId) {
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setMessage(message);
        n.setReferenceId(referenceId);
        n.setIsRead(false);
        n.setType(type);
        notificationRepository.save(n);

        String fcm = user.getFcmToken();
        if (fcm != null && !fcm.isEmpty()) {
            firebaseNotificationService.sendNotificationWithData(
                    fcm, title, message,
                    java.util.Map.of("type", type.name(),
                            "referenceId", String.valueOf(referenceId)));
        }
    }
}