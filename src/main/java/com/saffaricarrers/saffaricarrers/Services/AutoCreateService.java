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

    // NEW:
    // Used to check whether carrier/rider has pending commission
    // before allowing new trip creation / acceptance.
    private final PaymentService paymentService;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");


    // ============================================================
    // CARRIER
    // ============================================================

    public AutoCreateResult autoCreateRouteAndSendRequest(
            String carrierId,
            Long packageId) {

        log.info(
                "Auto-create route for carrier={} from package={}",
                carrierId,
                packageId
        );

        User carrier = userRepository.findByUserId(carrierId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Carrier not found"));


        // ========================================================
        // COMMISSION CHECK
        // ========================================================
        // Same eligibility rule used by normal trip creation.
        // If carrier has unpaid/pending commission, block the
        // auto-create route flow as well.
        // ========================================================

        if (!paymentService.canCarrierStartTrip(carrierId)) {

            log.warn(
                    "🚫 Auto-create blocked: carrier {} has pending commission",
                    carrierId
            );

            throw new IllegalStateException(
                    "You have pending platform commission. " +
                            "Please pay your commission before creating or accepting new trips."
            );
        }


        CarrierProfile carrierProfile =
                carrierProfileRepository.findByUser(carrier)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "You need to complete your carrier profile before requesting packages."
                                )
                        );

        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Package not found"));


        if (pkg.getSender().getUserId().equals(carrierId)) {

            throw new IllegalStateException(
                    "You cannot send a request for a package you created."
            );
        }


        if (pkg.getStatus() != Package.PackageStatus.CREATED) {

            throw new IllegalStateException(
                    "Package is no longer available for requests."
            );
        }


        boolean alreadyRequested =
                deliveryRequestRepository
                        .findByPackageEntityAndCarrier(pkg, carrier)
                        .stream()
                        .anyMatch(r ->
                                r.getStatus() == DeliveryRequest.RequestStatus.PENDING
                                        || r.getStatus() == DeliveryRequest.RequestStatus.ACCEPTED
                                        || r.getStatus() == DeliveryRequest.RequestStatus.PICKED_UP
                                        || r.getStatus() == DeliveryRequest.RequestStatus.IN_TRANSIT
                        );


        if (alreadyRequested) {

            throw new IllegalStateException(
                    "You already have an active request for this package."
            );
        }


        // ========================================================
        // DATE
        // ========================================================

        LocalDate pickupDate =
                LocalDate.parse(pkg.getPickUpDate(), DATE_FMT);

        LocalDate dropDate =
                LocalDate.parse(pkg.getDropDate(), DATE_FMT);


        // ========================================================
        // CREATE ROUTE
        // ========================================================

        CarrierRoute route = new CarrierRoute();

        route.setCarrierProfile(carrierProfile);

        route.setFromLocation(pkg.getFromAddress());

        route.setToLocation(pkg.getToAddress());

        route.setAvailableDate(pickupDate);

        route.setDeadlineDate(dropDate);


        LocalTime availableTime =
                pkg.getAvailableTime() != null
                        ? pkg.getAvailableTime()
                        : LocalTime.of(8, 0);

        LocalTime deadlineTime =
                pkg.getDeadlineTime() != null
                        ? pkg.getDeadlineTime()
                        : LocalTime.of(20, 0);


        route.setAvailableTime(availableTime);

        route.setDeadlineTime(deadlineTime);

        route.setTransportType(pkg.getTransportType());

        route.setMaxWeight(
                pkg.getWeight() != null
                        ? pkg.getWeight() * 5
                        : 100.0
        );

        route.setMaxQuantity(10);

        route.setCurrentWeight(0.0);

        route.setCurrentQuantity(0);

        route.setRouteStatus(
                CarrierRoute.RouteStatus.CREATED
        );


        // ========================================================
        // LOCATION
        // ========================================================

        route.setLatitude(pkg.getLatitude());

        route.setLongitude(pkg.getLongitude());

        route.setToLatitude(pkg.getToLatitude());

        route.setToLongitude(pkg.getToLongitude());

        route.setLongAddressId(
                pkg.getAddressId() != null
                        ? pkg.getAddressId()
                        : 0L
        );


        // ========================================================
        // SAVE ROUTE
        // ========================================================

        CarrierRoute savedRoute =
                carrierRouteRepository.save(route);

        carrierRouteRepository.flush();


        log.info(
                "Auto-created route id={}",
                savedRoute.getRouteId()
        );


        // ========================================================
        // PRICING
        // ========================================================

        RoutePricing pricing =
                buildDefaultPricing(
                        savedRoute,
                        carrierId,
                        pkg
                );

        pricingRepository.save(pricing);

        pricingRepository.flush();


        // ========================================================
        // AMOUNT
        // ========================================================

        Double totalAmount =
                calculateAmount(
                        pkg,
                        savedRoute,
                        pricing
                );

        Double platformComm =
                totalAmount * 0.15;

        Double carrierEarning =
                totalAmount - platformComm;


        // ========================================================
        // DELIVERY REQUEST
        // ========================================================

        DeliveryRequest req =
                new DeliveryRequest();

        req.setPackageEntity(pkg);

        req.setCarrierRoute(savedRoute);

        req.setSender(pkg.getSender());

        req.setCarrier(carrier);

        req.setStatus(
                DeliveryRequest.RequestStatus.PENDING
        );

        req.setTotalAmount(totalAmount);

        req.setPlatformCommission(platformComm);

        req.setCarrierEarning(carrierEarning);

        req.setPickupOtp(pkg.getPickupOtp());

        req.setDeliveryOtp(pkg.getDeliveryOtp());

        req.setRequestedAt(LocalDateTime.now());

        req.setCarrierNote(
                "Carrier auto-matched to your package route."
        );

        req.setRequestType(
                DeliveryRequest.RequestType.CARRIER_TO_SENDER
        );


        DeliveryRequest savedReq =
                deliveryRequestRepository.save(req);


        // ========================================================
        // PACKAGE STATUS
        // ========================================================

        pkg.setStatus(
                Package.PackageStatus.REQUEST_SENT
        );

        packageRepository.save(pkg);


        // ========================================================
        // NOTIFICATION
        // ========================================================

        notifySender(
                pkg.getSender(),
                pkg.getPackageId(),
                "New Carrier Request",
                carrier.getFullName()
                        + " wants to deliver your package."
        );


        log.info(
                "Auto-create route+request done: routeId={} requestId={}",
                savedRoute.getRouteId(),
                savedReq.getRequestId()
        );


        return AutoCreateResult.builder()
                .success(true)
                .message(
                        "Route created and request sent to sender."
                )
                .createdRouteId(
                        savedRoute.getRouteId()
                )
                .requestId(
                        savedReq.getRequestId()
                )
                .totalAmount(totalAmount)
                .platformCommission(platformComm)
                .carrierEarning(carrierEarning)
                .build();
    }


    // ============================================================
    // SENDER
    // ============================================================

    public AutoCreateResult autoCreatePackageAndSendRequest(
            String senderId,
            AutoCreatePackageRequest body) {

        log.info(
                "Auto-create package for sender={} from route={}",
                senderId,
                body.getRouteId()
        );


        User sender =
                userRepository.findByUserId(senderId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Sender not found"
                                )
                        );


        CarrierRoute route =
                carrierRouteRepository.findById(
                                body.getRouteId()
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Carrier route not found"
                                )
                        );


        if (route.getCarrierProfile()
                .getUser()
                .getUserId()
                .equals(senderId)) {

            throw new IllegalStateException(
                    "You cannot request delivery on a route you created."
            );
        }


        if (route.getRouteStatus()
                != CarrierRoute.RouteStatus.CREATED) {

            throw new IllegalStateException(
                    "This carrier route is no longer available."
            );
        }


        // ========================================================
        // DATES
        // ========================================================

        String pickupDateStr =
                (body.getPickUpDate() != null
                        && !body.getPickUpDate().isEmpty())
                        ? body.getPickUpDate()
                        : route.getAvailableDate()
                        .format(DATE_FMT);


        String dropDateStr =
                (body.getDropDate() != null
                        && !body.getDropDate().isEmpty())
                        ? body.getDropDate()
                        : route.getDeadlineDate()
                        .format(DATE_FMT);


        // ========================================================
        // CREATE PACKAGE
        // ========================================================

        Package pkg = new Package();

        pkg.setSender(sender);


        pkg.setProductName(
                body.getProductName() != null
                        && !body.getProductName().isBlank()
                        ? body.getProductName()
                        : "My Package"
        );


        pkg.setProductDescription(
                body.getProductDescription() != null
                        ? body.getProductDescription()
                        : "Auto-created from carrier route"
        );


        pkg.setProductValue(
                body.getProductValue() != null
                        ? body.getProductValue()
                        : 0.0
        );


        pkg.setWeight(
                body.getWeight() != null
                        ? body.getWeight()
                        : 1.0
        );


        pkg.setLength(0.0);

        pkg.setWidth(0.0);

        pkg.setHeight(0.0);


        // ========================================================
        // ROUTE PRICING
        // ========================================================

        List<RoutePricing> routePricings =
                pricingRepository.findByCarrierRoute(route);


        RoutePricing.ProductType productType =
                routePricings.isEmpty()
                        ? RoutePricing.ProductType.APPAREL
                        : routePricings.get(0).getProductType();


        pkg.setProductType(productType);


        pkg.setTransportType(
                route.getTransportType()
        );


        // ========================================================
        // ADDRESSES
        // ========================================================

        pkg.setFromAddress(
                resolveAddr(
                        body.getFromAddress(),
                        route.getFromLocation()
                )
        );


        pkg.setToAddress(
                resolveAddr(
                        body.getToAddress(),
                        route.getToLocation()
                )
        );


        pkg.setAddressId(
                body.getAddressId() != null
                        ? body.getAddressId()
                        : 0L
        );


        // ========================================================
        // COORDINATES
        // ========================================================

        pkg.setLatitude(
                body.getLatitude() != null
                        ? body.getLatitude()
                        : nvl(route.getLatitude())
        );


        pkg.setLongitude(
                body.getLongitude() != null
                        ? body.getLongitude()
                        : nvl(route.getLongitude())
        );


        pkg.setToLatitude(
                body.getToLatitude() != null
                        ? body.getToLatitude()
                        : nvl(route.getToLatitude())
        );


        pkg.setToLongitude(
                body.getToLongitude() != null
                        ? body.getToLongitude()
                        : nvl(route.getToLongitude())
        );


        // ========================================================
        // DATES & TIMES
        // ========================================================

        pkg.setPickUpDate(pickupDateStr);

        pkg.setDropDate(dropDateStr);

        pkg.setAvailableTime(
                route.getAvailableTime()
        );

        pkg.setDeadlineTime(
                route.getDeadlineTime()
        );


        // ========================================================
        // PRICE
        // ========================================================

        if (pkg.getTripCharge() == null
                || pkg.getTripCharge() == 0) {

            pkg.setTripCharge(
                    estimateTripCharge(
                            route,
                            routePricings
                    )
            );
        }


        pkg.setPricePerKg(
                estimatePricePerKg(routePricings)
        );


        pkg.setPricePerTon(
                estimatePricePerTon(routePricings)
        );


        pkg.setInsurance(false);


        // ========================================================
        // OTP
        // ========================================================

        pkg.setPickupOtp(generateOtp());

        pkg.setDeliveryOtp(generateOtp());


        // ========================================================
        // STATUS
        // ========================================================

        pkg.setStatus(
                Package.PackageStatus.REQUEST_SENT
        );


        pkg.setUrl(
                sender.getProfileUrl()
        );


        // ========================================================
        // SAVE PACKAGE
        // ========================================================

        Package savedPkg =
                packageRepository.save(pkg);

        packageRepository.flush();


        log.info(
                "Auto-created package id={}",
                savedPkg.getPackageId()
        );


        // ========================================================
        // PRICING VALIDATION
        // ========================================================

        if (routePricings.isEmpty()) {

            throw new IllegalArgumentException(
                    "Carrier route has no pricing configured. " +
                            "Cannot auto-create request."
            );
        }


        RoutePricing pricing =
                routePricings.get(0);


        // ========================================================
        // AMOUNT
        // ========================================================

        Double totalAmount =
                calculateAmount(
                        savedPkg,
                        route,
                        pricing
                );


        Double platformComm =
                totalAmount * 0.15;


        Double carrierEarning =
                totalAmount - platformComm;


        // ========================================================
        // DELIVERY REQUEST
        // ========================================================

        DeliveryRequest req =
                new DeliveryRequest();


        req.setPackageEntity(savedPkg);

        req.setCarrierRoute(route);

        req.setSender(sender);

        req.setCarrier(
                route.getCarrierProfile()
                        .getUser()
        );


        req.setStatus(
                DeliveryRequest.RequestStatus.PENDING
        );


        req.setTotalAmount(totalAmount);

        req.setPlatformCommission(platformComm);

        req.setCarrierEarning(carrierEarning);

        req.setPickupOtp(
                savedPkg.getPickupOtp()
        );

        req.setDeliveryOtp(
                savedPkg.getDeliveryOtp()
        );

        req.setRequestedAt(
                LocalDateTime.now()
        );


        req.setSenderNote(
                "Sender auto-matched to your carrier route."
        );


        req.setRequestType(
                DeliveryRequest.RequestType.SENDER_TO_CARRIER
        );


        DeliveryRequest savedReq =
                deliveryRequestRepository.save(req);


        // ========================================================
        // NOTIFY CARRIER
        // ========================================================

        notifyCarrier(
                route.getCarrierProfile().getUser(),
                route.getRouteId(),
                "New Package Request",
                sender.getFullName()
                        + " wants you to deliver a package along your route."
        );


        log.info(
                "Auto-create package+request done: packageId={} requestId={}",
                savedPkg.getPackageId(),
                savedReq.getRequestId()
        );


        return AutoCreateResult.builder()
                .success(true)
                .message(
                        "Package created and request sent to carrier."
                )
                .createdPackageId(
                        savedPkg.getPackageId()
                )
                .requestId(
                        savedReq.getRequestId()
                )
                .totalAmount(totalAmount)
                .platformCommission(platformComm)
                .carrierEarning(carrierEarning)
                .build();
    }


    // ============================================================
    // RIDER
    // ============================================================

    public AutoCreateResult autoCreateRouteAndAcceptForRider(
            String riderId,
            Long packageId) {

        log.info(
                "Rider auto-accept: riderId={} packageId={}",
                riderId,
                packageId
        );


        User rider =
                userRepository.findByUserId(riderId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Rider not found"
                                )
                        );


        // ========================================================
        // COMMISSION CHECK
        // ========================================================
        // Prevent rider from bypassing the normal trip commission
        // restriction through the auto-accept endpoint.
        // ========================================================

        if (!paymentService.canCarrierStartTrip(riderId)) {

            log.warn(
                    "🚫 Rider auto-accept blocked: rider {} has pending commission",
                    riderId
            );

            throw new IllegalStateException(
                    "You have pending platform commission. " +
                            "Please pay your commission before creating or accepting new trips."
            );
        }


        // ========================================================
        // CARRIER PROFILE
        // ========================================================

        CarrierProfile carrierProfile =
                carrierProfileRepository.findByUser(rider)
                        .orElseGet(() -> {

                            log.info(
                                    "No CarrierProfile found for rider {} — creating one automatically",
                                    riderId
                            );

                            CarrierProfile newProfile =
                                    new CarrierProfile();

                            newProfile.setUser(rider);

                            newProfile.setIsOnline(true);

                            newProfile.setIsVerified(false);

                            newProfile.setSearchRadiusKm(10.0);

                            newProfile.setStatus(
                                    CarrierProfile.CarrierStatus.ACTIVE
                            );


                            return carrierProfileRepository.save(
                                    newProfile
                            );
                        });


        // ========================================================
        // PACKAGE
        // ========================================================

        Package pkg =
                packageRepository.findById(packageId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Package not found"
                                )
                        );


        if (pkg.getSender()
                .getUserId()
                .equals(riderId)) {

            throw new IllegalStateException(
                    "You cannot accept your own package."
            );
        }


        if (pkg.getStatus()
                != Package.PackageStatus.CREATED) {

            throw new IllegalStateException(
                    "Package is no longer available."
            );
        }


        // ========================================================
        // CHECK EXISTING REQUEST
        // ========================================================

        boolean alreadyActive =
                deliveryRequestRepository
                        .findByPackageEntityAndCarrier(
                                pkg,
                                rider
                        )
                        .stream()
                        .anyMatch(r ->
                                r.getStatus()
                                        == DeliveryRequest.RequestStatus.PENDING

                                        || r.getStatus()
                                        == DeliveryRequest.RequestStatus.ACCEPTED

                                        || r.getStatus()
                                        == DeliveryRequest.RequestStatus.PICKED_UP

                                        || r.getStatus()
                                        == DeliveryRequest.RequestStatus.IN_TRANSIT
                        );


        if (alreadyActive) {

            throw new IllegalStateException(
                    "You already have an active request for this package."
            );
        }


        // ========================================================
        // DATES
        // ========================================================

        LocalDate pickupDate =
                LocalDate.parse(
                        pkg.getPickUpDate(),
                        DATE_FMT
                );


        LocalDate dropDate =
                LocalDate.parse(
                        pkg.getDropDate(),
                        DATE_FMT
                );


        // ========================================================
        // CREATE ROUTE
        // ========================================================

        CarrierRoute route =
                new CarrierRoute();


        route.setCarrierProfile(
                carrierProfile
        );


        route.setFromLocation(
                pkg.getFromAddress()
        );


        route.setToLocation(
                pkg.getToAddress()
        );


        route.setAvailableDate(
                pickupDate
        );


        route.setDeadlineDate(
                dropDate
        );


        route.setAvailableTime(
                pkg.getAvailableTime() != null
                        ? pkg.getAvailableTime()
                        : LocalTime.of(8, 0)
        );


        route.setDeadlineTime(
                pkg.getDeadlineTime() != null
                        ? pkg.getDeadlineTime()
                        : LocalTime.of(20, 0)
        );


        route.setTransportType(
                pkg.getTransportType()
        );


        route.setMaxWeight(
                pkg.getWeight() != null
                        ? pkg.getWeight() * 5
                        : 100.0
        );


        route.setMaxQuantity(10);


        route.setCurrentWeight(
                pkg.getWeight() != null
                        ? pkg.getWeight()
                        : 0.0
        );


        route.setCurrentQuantity(1);


        route.setRouteStatus(
                CarrierRoute.RouteStatus.MATCHED
        );


        // ========================================================
        // LOCATION
        // ========================================================

        route.setLatitude(
                pkg.getLatitude()
        );


        route.setLongitude(
                pkg.getLongitude()
        );


        route.setToLatitude(
                pkg.getToLatitude()
        );


        route.setToLongitude(
                pkg.getToLongitude()
        );


        route.setLongAddressId(
                pkg.getAddressId() != null
                        ? pkg.getAddressId()
                        : 0L
        );


        // ========================================================
        // SAVE ROUTE
        // ========================================================

        CarrierRoute savedRoute =
                carrierRouteRepository.save(route);

        carrierRouteRepository.flush();


        log.info(
                "Auto-created route id={} for rider",
                savedRoute.getRouteId()
        );


        // ========================================================
        // PRICING
        // ========================================================

        RoutePricing pricing =
                buildDefaultPricing(
                        savedRoute,
                        riderId,
                        pkg
                );


        pricingRepository.save(pricing);

        pricingRepository.flush();


        // ========================================================
        // AMOUNT
        // ========================================================

        Double totalAmount =
                calculateAmount(
                        pkg,
                        savedRoute,
                        pricing
                );


        Double platformComm =
                totalAmount * 0.15;


        Double carrierEarning =
                totalAmount - platformComm;


        // ========================================================
        // DELIVERY REQUEST
        // ========================================================

        DeliveryRequest req =
                new DeliveryRequest();


        req.setPackageEntity(pkg);

        req.setCarrierRoute(savedRoute);

        req.setSender(pkg.getSender());

        req.setCarrier(rider);


        req.setStatus(
                DeliveryRequest.RequestStatus.ACCEPTED
        );


        req.setTotalAmount(totalAmount);

        req.setPlatformCommission(platformComm);

        req.setCarrierEarning(carrierEarning);


        req.setPickupOtp(
                pkg.getPickupOtp()
        );


        req.setDeliveryOtp(
                pkg.getDeliveryOtp()
        );


        req.setRequestedAt(
                LocalDateTime.now()
        );


        req.setAcceptedAt(
                LocalDateTime.now()
        );


        req.setCarrierNote(
                "Rider accepted directly."
        );


        req.setIsRiderDelivery(true);


        req.setRequestType(
                DeliveryRequest.RequestType.CARRIER_TO_SENDER
        );


        DeliveryRequest savedReq =
                deliveryRequestRepository.save(req);


        // ========================================================
        // PACKAGE STATUS
        // ========================================================

        pkg.setStatus(
                Package.PackageStatus.MATCHED
        );


        packageRepository.save(pkg);


        // ========================================================
        // NOTIFICATION
        // ========================================================

        notifySender(
                pkg.getSender(),
                pkg.getPackageId(),
                "Rider Accepted Your Package 🚴",
                rider.getFullName()
                        + " is on the way to pick up your package."
        );


        log.info(
                "Rider auto-accept done: routeId={} requestId={} status=ACCEPTED",
                savedRoute.getRouteId(),
                savedReq.getRequestId()
        );


        return AutoCreateResult.builder()
                .success(true)
                .message(
                        "Package accepted. Proceed to pickup."
                )
                .createdRouteId(
                        savedRoute.getRouteId()
                )
                .requestId(
                        savedReq.getRequestId()
                )
                .totalAmount(totalAmount)
                .platformCommission(platformComm)
                .carrierEarning(carrierEarning)
                .build();
    }


    // ============================================================
    // PRIVATE HELPERS
    // ============================================================

    private RoutePricing buildDefaultPricing(
            CarrierRoute route,
            String carrierId,
            Package pkg) {


        // ========================================================
        // PACKAGE HAS TRIP CHARGE
        // ========================================================

        if (pkg.getTripCharge() != null
                && pkg.getTripCharge() > 0) {

            log.info(
                    "✅ Package has tripCharge={} — skipping existing route pricing copy",
                    pkg.getTripCharge()
            );


            RoutePricing p =
                    new RoutePricing();


            p.setCarrierRoute(route);


            p.setProductType(
                    pkg.getProductType() != null
                            ? pkg.getProductType()
                            : RoutePricing.ProductType.APPAREL
            );


            p.setWeightLimit(100.0);

            p.setFixedPrice(
                    pkg.getTripCharge()
            );

            p.setPricePerTon(null);


            return p;
        }


        // ========================================================
        // COPY EXISTING ROUTE PRICING
        // ========================================================

        List<CarrierRoute> existingRoutes =
                carrierRouteRepository
                        .findByCarrierProfileUserUserId(
                                carrierId
                        );


        for (CarrierRoute existingRoute :
                existingRoutes) {

            List<RoutePricing> pricings =
                    pricingRepository.findByCarrierRoute(
                            existingRoute
                    );


            if (!pricings.isEmpty()) {

                RoutePricing src =
                        pricings.get(0);


                RoutePricing copy =
                        new RoutePricing();


                copy.setCarrierRoute(route);

                copy.setProductType(
                        src.getProductType()
                );

                copy.setWeightLimit(
                        src.getWeightLimit()
                );

                copy.setFixedPrice(
                        src.getFixedPrice()
                );

                copy.setPricePerTon(
                        src.getPricePerTon()
                );


                log.info(
                        "Copied pricing from existing route={}",
                        existingRoute.getRouteId()
                );


                return copy;
            }
        }


        // ========================================================
        // DEFAULT PRICING
        // ========================================================

        RoutePricing p =
                new RoutePricing();


        p.setCarrierRoute(route);


        p.setProductType(
                pkg.getProductType() != null
                        ? pkg.getProductType()
                        : RoutePricing.ProductType.APPAREL
        );


        p.setWeightLimit(100.0);


        boolean isTonBased =
                isTonBasedTransport(
                        route.getTransportType()
                );


        if (isTonBased) {

            p.setPricePerTon(
                    pkg.getPricePerTon() != null
                            ? pkg.getPricePerTon()
                            : 500.0
            );


            p.setFixedPrice(0.0);

        } else {

            p.setFixedPrice(
                    pkg.getTripCharge() != null
                            ? pkg.getTripCharge()
                            : 100.0
            );


            p.setPricePerTon(null);
        }


        log.info(
                "Built default pricing for route (no existing pricing found)"
        );


        return p;
    }


    // ============================================================
    // CALCULATE AMOUNT
    // ============================================================

    private Double calculateAmount(
            Package pkg,
            CarrierRoute route,
            RoutePricing pricing) {

        log.info(
                "🧾 CALCULATING AMOUNT..."
        );


        if (pkg.getTripCharge() != null
                && pkg.getTripCharge() > 0) {

            log.info(
                    "✅ Using PACKAGE tripCharge: {}",
                    pkg.getTripCharge()
            );


            return pkg.getTripCharge();
        }


        if (pricing.getFixedPrice() != null
                && pricing.getFixedPrice() > 0) {

            log.info(
                    "⚠️ Using ROUTE fixedPrice: {}",
                    pricing.getFixedPrice()
            );


            return pricing.getFixedPrice();
        }


        log.info(
                "⚠️ No price found → defaulting to 100"
        );


        return 100.0;
    }


    // ============================================================
    // TON BASED TRANSPORT
    // ============================================================

    private boolean isTonBasedTransport(
            CarrierRoute.TransportType t) {

        return t == CarrierRoute.TransportType.TRUCK
                || t == CarrierRoute.TransportType.LORRY
                || t == CarrierRoute.TransportType.TIPPER;
    }


    // ============================================================
    // ESTIMATE TRIP CHARGE
    // ============================================================

    private Double estimateTripCharge(
            CarrierRoute route,
            List<RoutePricing> pricings) {

        log.info(
                "🧠 ESTIMATING TRIP CHARGE..."
        );


        if (pricings.isEmpty()) {

            log.info(
                    "⚠️ No pricing found → returning 100"
            );

            return 100.0;
        }


        RoutePricing p =
                pricings.get(0);


        if (p.getFixedPrice() != null
                && p.getFixedPrice() > 0) {

            log.info(
                    "💸 USING FIXED PRICE → {}",
                    p.getFixedPrice()
            );


            return p.getFixedPrice();
        }


        log.info(
                "⚠️ fixedPrice is null/zero → returning 100"
        );


        return 100.0;
    }


    // ============================================================
    // PRICE PER KG
    // ============================================================

    private Double estimatePricePerKg(
            List<RoutePricing> pricings) {

        if (pricings.isEmpty()) {
            return null;
        }


        return pricings.get(0).getPricePerTon() != null
                ? pricings.get(0).getPricePerTon() / 1000.0
                : null;
    }


    // ============================================================
    // PRICE PER TON
    // ============================================================

    private Double estimatePricePerTon(
            List<RoutePricing> pricings) {

        if (pricings.isEmpty()) {
            return null;
        }


        return pricings.get(0).getPricePerTon();
    }


    // ============================================================
    // RESOLVE ADDRESS
    // ============================================================

    private String resolveAddr(
            String fromBody,
            String fromRoute) {

        if (fromBody != null
                && !fromBody.isBlank()) {

            return fromBody;
        }


        if (fromRoute != null
                && !fromRoute.isBlank()) {

            return fromRoute;
        }


        return "Not specified";
    }


    // ============================================================
    // NULL VALUE
    // ============================================================

    private double nvl(Double d) {

        return d != null
                ? d
                : 0.0;
    }


    // ============================================================
    // GENERATE OTP
    // ============================================================

    private String generateOtp() {

        return String.valueOf(
                100000
                        + new Random().nextInt(900000)
        );
    }


    // ============================================================
    // NOTIFY SENDER
    // ============================================================

    private void notifySender(
            User sender,
            Long packageId,
            String title,
            String message) {

        createNotification(
                sender,
                title,
                message,
                Notification.NotificationType.DELIVERY_REQUEST,
                packageId
        );
    }


    // ============================================================
    // NOTIFY CARRIER
    // ============================================================

    private void notifyCarrier(
            User carrier,
            Long routeId,
            String title,
            String message) {

        createNotification(
                carrier,
                title,
                message,
                Notification.NotificationType.DELIVERY_REQUEST,
                routeId
        );
    }


    // ============================================================
    // CREATE NOTIFICATION
    // ============================================================

    private void createNotification(
            User user,
            String title,
            String message,
            Notification.NotificationType type,
            Long referenceId) {


        Notification n =
                new Notification();


        n.setUser(user);

        n.setTitle(title);

        n.setMessage(message);

        n.setReferenceId(referenceId);

        n.setIsRead(false);

        n.setType(type);


        notificationRepository.save(n);


        // ========================================================
        // FIREBASE NOTIFICATION
        // ========================================================

        String fcm =
                user.getFcmToken();


        if (fcm != null
                && !fcm.isEmpty()) {

            firebaseNotificationService
                    .sendNotificationWithData(
                            fcm,
                            title,
                            message,
                            java.util.Map.of(
                                    "type",
                                    type.name(),
                                    "referenceId",
                                    String.valueOf(referenceId)
                            )
                    );
        }
    }
}