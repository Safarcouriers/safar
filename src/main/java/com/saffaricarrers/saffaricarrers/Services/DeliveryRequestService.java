package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Dtos.*;
import com.saffaricarrers.saffaricarrers.Entity.*;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import com.saffaricarrers.saffaricarrers.Repository.*;
import com.saffaricarrers.saffaricarrers.Responses.PackageSelectionResponse;
import com.saffaricarrers.saffaricarrers.Responses.RouteAvailabilityResponse;
import com.saffaricarrers.saffaricarrers.Responses.RouteSelectionResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeliveryRequestService {

    private final DeliveryRequestRepository deliveryRequestRepository;
    private final PackageRepository packageRepository;
    private final CarrierRouteRepository carrierRouteRepository;
    private final UserRepository userRepository;
    private final FirebaseNotificationService firebaseNotificationService;
    private final NotificationRepository notificationRepository;
    private final CarrierRouteService carrierRouteService;
    private final PaymentService paymentService;
    private final CallBackService callBackService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // =====================================================================
    // MY PACKAGES TAB
    // =====================================================================

    public List<MyPackageResponse> getMyPackages(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Package> packages = packageRepository.findBySenderOrderByCreatedAtDesc(user);

        return packages.stream().map(pkg -> {
            MyPackageResponse response = new MyPackageResponse();
            response.setPackageId(pkg.getPackageId());
            response.setProductName(pkg.getProductName());
            response.setProductImage(pkg.getProductImages() != null && !pkg.getProductImages().isEmpty()
                    ? pkg.getProductImages().get(0) : null);
            response.setFromAddress(pkg.getFromAddress());
            response.setToAddress(pkg.getToAddress());
            response.setPrice(pkg.getProductValue());
            response.setStatus(pkg.getStatus());
            response.setEta(pkg.getDropDate());
            response.setCreatedAt(pkg.getCreatedAt());

            Optional<DeliveryRequest> activeRequest = deliveryRequestRepository
                    .findTopByPackageEntityAndStatusInOrderByCreatedAtDesc(pkg, Arrays.asList(
                            DeliveryRequest.RequestStatus.ACCEPTED,
                            DeliveryRequest.RequestStatus.PICKED_UP,
                            DeliveryRequest.RequestStatus.IN_TRANSIT,
                            DeliveryRequest.RequestStatus.DELIVERED
                    ));

            if (activeRequest.isPresent()) {
                DeliveryRequest req = activeRequest.get();
                response.setCarrierName(req.getCarrier().getFullName());
                response.setCarrierPhone(req.getCarrier().getMobile());
                response.setCarrierProfileImage(req.getCarrier().getProfileUrl());
                response.setCarrierVehicleType(req.getCarrierRoute().getTransportType().toString());
                response.setDeliveryRequestId(req.getRequestId());
            }
            return response;
        }).collect(Collectors.toList());
    }

    // =====================================================================
    // MY TRIPS TAB
    // =====================================================================

    public List<MyTripResponse> getMyTrips(String carrierId) {
        User carrier = userRepository.findByUserId(carrierId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        CarrierProfile carrierProfile = carrier.getCarrierProfile();
        if (carrierProfile == null) throw new IllegalArgumentException("User is not a carrier");

        List<CarrierRoute> routes = carrierRouteRepository
                .findByCarrierProfileOrderByCreatedAtDesc(carrierProfile);

        return routes.stream().map(route -> {
            MyTripResponse response = new MyTripResponse();
            response.setRouteId(route.getRouteId());
            response.setFromLocation(route.getFromLocation());
            response.setToLocation(route.getToLocation());
            response.setAvailableDate(route.getAvailableDate());
            response.setAvailableTime(route.getAvailableTime());
            response.setRouteStatus(route.getRouteStatus());
            response.setTransportType(route.getTransportType());
            response.setDeadlineDate(route.getDeadlineDate().toString());
            response.setDeadlineTime(route.getDeadlineTime().toString());
            response.setCurrentWeight(route.getCurrentWeight());
            response.setMaxWeight(route.getMaxWeight());
            response.setCurrentQuantity(route.getCurrentQuantity());
            response.setMaxQuantity(route.getMaxQuantity());

            int packageCount = route.getCurrentQuantity();
            response.setPackageCount(packageCount);
            response.setPackageDisplay(packageCount + "/" + route.getMaxQuantity() + " packages");

            Long pendingCount = deliveryRequestRepository
                    .countByCarrierRouteAndStatus(route, DeliveryRequest.RequestStatus.PENDING);
            response.setPendingRequestsCount(pendingCount.intValue());

            List<DeliveryRequest> acceptedDeliveries = deliveryRequestRepository
                    .findByCarrierRouteAndStatusIn(route, Arrays.asList(
                            DeliveryRequest.RequestStatus.ACCEPTED,
                            DeliveryRequest.RequestStatus.PICKED_UP,
                            DeliveryRequest.RequestStatus.IN_TRANSIT,
                            DeliveryRequest.RequestStatus.DELIVERED
                    ));

            // ✅ Show full trip charge = 140
            Double estimatedEarnings = acceptedDeliveries.stream()
                    .mapToDouble(DeliveryRequest::getTotalAmount)
                    .sum();
            response.setEstimatedEarnings(estimatedEarnings);


            return response;
        }).collect(Collectors.toList());
    }

    // =====================================================================
    // TRIP DETAILS — FIXED: real commission status + photo fields
    // =====================================================================

    public TripDetailResponse getTripDetails(Long routeId, String carrierId) {
        CarrierRoute route = carrierRouteRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));

        if (!route.getCarrierProfile().getUser().getUserId().equals(carrierId)) {
            throw new IllegalArgumentException("Route does not belong to carrier");
        }

        TripDetailResponse response = new TripDetailResponse();
        response.setRouteId(route.getRouteId());
        response.setFromLocation(route.getFromLocation());
        response.setToLocation(route.getToLocation());
        response.setAvailableDate(route.getAvailableDate());
        response.setAvailableTime(route.getAvailableTime());
        response.setRouteStatus(route.getRouteStatus());
        response.setTransportType(route.getTransportType());
        response.setDeadlineTime(route.getDeadlineTime().toString());
        response.setCarrierType(route.getTransportType().toString());
        response.setVehicleType(route.getTransportType().toString());
        response.setTotalCapacity(route.getMaxWeight() + " kg");
        response.setCurrentLoad(route.getCurrentWeight() + " kg");
        response.setPackagesCapacity(route.getCurrentQuantity() + "/" + route.getMaxQuantity());
        response.setWeightCapacity(route.getCurrentWeight() + "/" + route.getMaxWeight() + " kg");

        List<DeliveryRequest> allRequests = deliveryRequestRepository
                .findByCarrierRouteOrderByCreatedAtDesc(route);

        // Package Requests (pending)
        List<PackageRequestSummary> packageRequests = allRequests.stream()
                .filter(req -> req.getStatus() == DeliveryRequest.RequestStatus.PENDING)
                .map(this::mapToPackageRequestSummary)
                .collect(Collectors.toList());
        response.setPackageRequests(packageRequests);
        response.setPackageRequestsCount(packageRequests.size());

        // Active Packages (accepted/pickedUp/inTransit)
        List<ActivePackageSummary> activePackages = allRequests.stream()
                .filter(req -> req.getStatus() == DeliveryRequest.RequestStatus.ACCEPTED
                        || req.getStatus() == DeliveryRequest.RequestStatus.PICKED_UP
                        || req.getStatus() == DeliveryRequest.RequestStatus.IN_TRANSIT)
                .map(this::mapToActivePackageSummary)
                .collect(Collectors.toList());
        response.setActivePackages(activePackages);
        response.setActivePackagesCount(activePackages.size());

        // ✅ Also include delivered packages so frontend can show them with DELIVERED status
        List<ActivePackageSummary> deliveredPackages = allRequests.stream()
                .filter(req -> req.getStatus() == DeliveryRequest.RequestStatus.DELIVERED)
                .map(this::mapToActivePackageSummary)
                .collect(Collectors.toList());
        response.setDeliveredPackages(deliveredPackages);
        response.setDeliveredPackagesCount(deliveredPackages.size());

        // Earnings
        Double totalEarnings = activePackages.stream()
                .mapToDouble(ActivePackageSummary::getEarningAmount).sum()
                + deliveredPackages.stream()
                .mapToDouble(ActivePackageSummary::getEarningAmount).sum();

        Double platformCommission = activePackages.stream()
                .mapToDouble(ActivePackageSummary::getPlatformCommission).sum()
                + deliveredPackages.stream()
                .mapToDouble(ActivePackageSummary::getPlatformCommission).sum();

        response.setTotalEarnings(totalEarnings);
        response.setPlatformCommission(platformCommission);

        // ✅ FIXED Commission Status:
        // Check real payment records — not just delivery status
        boolean allPaymentsSettled = allRequests.stream()
                .filter(req -> req.getStatus() == DeliveryRequest.RequestStatus.ACCEPTED
                        || req.getStatus() == DeliveryRequest.RequestStatus.PICKED_UP
                        || req.getStatus() == DeliveryRequest.RequestStatus.IN_TRANSIT
                        || req.getStatus() == DeliveryRequest.RequestStatus.DELIVERED)
                .allMatch(req -> {
                    Payment payment = req.getPayment();
                    if (payment == null) return false;
                    if (payment.getPaymentStatus() != Payment.PaymentStatus.COMPLETED) return false;
                    // Online: commission already kept by platform — always "paid"
                    if (payment.getPaymentMethod() == Payment.PaymentMethod.ONLINE) return true;
                    // COD: check if carrier paid their commission
                    return Boolean.TRUE.equals(payment.getCommissionPaid());
                });

        boolean hasAnyPayment = allRequests.stream()
                .filter(req -> req.getStatus() == DeliveryRequest.RequestStatus.DELIVERED)
                .anyMatch(req -> req.getPayment() != null
                        && req.getPayment().getPaymentStatus() == Payment.PaymentStatus.COMPLETED);

        if (deliveredPackages.isEmpty() && activePackages.isEmpty()) {
            response.setCommissionStatus("No Deliveries");
        } else if (allPaymentsSettled && hasAnyPayment) {
            response.setCommissionStatus("Paid");
        } else {
            response.setCommissionStatus("Pending");
        }

        // ✅ Also expose individual payment status per active package
        // (frontend uses this to show "Paid" badge on each package card)
        return response;
    }

    // =====================================================================
    // PACKAGE DETAILS — FIXED: includes paymentStatus + paymentCompleted
    // =====================================================================

    public PackageDetailResponse getPackageDetails(Long packageId, String userId) {
        Package packageEntity = packageRepository.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));

        if (!packageEntity.getSender().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Package does not belong to user");
        }

        PackageDetailResponse response = new PackageDetailResponse();
        response.setPackageId(packageEntity.getPackageId());
        response.setProductName(packageEntity.getProductName());
        response.setProductDescription(packageEntity.getProductDescription());
        response.setProductImage(packageEntity.getProductImages() != null && !packageEntity.getProductImages().isEmpty()
                ? packageEntity.getProductImages().get(0) : null);
        response.setWeight(packageEntity.getWeight() + " kg");
        response.setDimensions(packageEntity.getLength() + "x" + packageEntity.getWidth() + "x"
                + packageEntity.getHeight() + " cm");
        response.setValue(packageEntity.getProductValue());
        response.setCreatedDate(packageEntity.getCreatedAt().toLocalDate());
        response.setStatus(packageEntity.getStatus());
        response.setDeadlineTime(packageEntity.getDeadlineTime().toString());
        response.setAvailableTime(packageEntity.getAvailableTime().toString());
        response.setPickupAddress(packageEntity.getFromAddress());
        response.setDeliveryAddress(packageEntity.getToAddress());

        // ✅ Default: no payment
        response.setPaymentStatus("NOT_PAID");
        response.setPaymentCompleted(false);
        response.setPaymentMethod(null);

        Optional<DeliveryRequest> activeRequestOpt = deliveryRequestRepository
                .findTopByPackageEntityAndStatusInOrderByCreatedAtDesc(packageEntity, Arrays.asList(
                        DeliveryRequest.RequestStatus.ACCEPTED,
                        DeliveryRequest.RequestStatus.PICKED_UP,
                        DeliveryRequest.RequestStatus.IN_TRANSIT,
                        DeliveryRequest.RequestStatus.DELIVERED
                ));

        if (activeRequestOpt.isPresent()) {
            DeliveryRequest activeRequest = activeRequestOpt.get();

            User carrier = activeRequest.getCarrier();
            response.setCarrierName(carrier.getFullName());
            response.setCarrierPhone(carrier.getMobile());
            response.setCarrierProfileImage(carrier.getProfileUrl());
            response.setCarrierVehicleType(activeRequest.getCarrierRoute().getTransportType().toString());
            response.setCarrierRating(4.5);

            response.setPickupOtp(activeRequest.getPickupOtp());
            response.setDeliveryOtp(activeRequest.getDeliveryOtp());
            response.setPackagePrice(activeRequest.getTotalAmount());
            response.setInsuranceAmount(packageEntity.getInsurance() ? packageEntity.getProductValue() * 0.05 : 0.0);
            response.setInsuranceEnabled(packageEntity.getInsurance());
            response.setTotalAmount(activeRequest.getTotalAmount() + response.getInsuranceAmount());
            response.setDeliveryRequestId(activeRequest.getRequestId());

            // ✅ FIXED: Set real payment status from payment record
            Payment payment = activeRequest.getPayment();
            if (payment != null) {
                boolean isCompleted = payment.getPaymentStatus() == Payment.PaymentStatus.COMPLETED;
                response.setPaymentStatus(payment.getPaymentStatus().toString());
                response.setPaymentCompleted(isCompleted);
                response.setPaymentMethod(payment.getPaymentMethod().toString());
                response.setRazorpayOrderId(payment.getRazorpayOrderId());

                // ✅ For COD: expose if carrier's commission has been paid
                if (payment.getPaymentMethod() == Payment.PaymentMethod.COD) {
                    response.setCommissionPaid(Boolean.TRUE.equals(payment.getCommissionPaid()));
                } else {
                    // Online payment: commission always handled
                    response.setCommissionPaid(true);
                }
            }

            // Status Timeline
            List<StatusTimelineItem> timeline = new ArrayList<>();
            timeline.add(new StatusTimelineItem("Created", true, packageEntity.getCreatedAt()));
            timeline.add(new StatusTimelineItem("Matched",
                    activeRequest.getStatus() != DeliveryRequest.RequestStatus.ACCEPTED,
                    activeRequest.getAcceptedAt()));
            timeline.add(new StatusTimelineItem("Picked Up",
                    activeRequest.getStatus() == DeliveryRequest.RequestStatus.PICKED_UP
                            || activeRequest.getStatus() == DeliveryRequest.RequestStatus.IN_TRANSIT
                            || activeRequest.getStatus() == DeliveryRequest.RequestStatus.DELIVERED,
                    activeRequest.getPickedUpAt()));
            timeline.add(new StatusTimelineItem("In Transit",
                    activeRequest.getStatus() == DeliveryRequest.RequestStatus.IN_TRANSIT
                            || activeRequest.getStatus() == DeliveryRequest.RequestStatus.DELIVERED,
                    null));
            timeline.add(new StatusTimelineItem("Delivered",
                    activeRequest.getStatus() == DeliveryRequest.RequestStatus.DELIVERED,
                    activeRequest.getDeliveredAt()));
            response.setStatusTimeline(timeline);
        }

        return response;
    }

    // =====================================================================
    // REQUEST ACTIONS
    // =====================================================================

    public DeliveryRequestResponse senderSendRequestToCarrier(String senderUserId, Long packageId,
                                                              Long carrierRouteId, String senderNote) {
        Package packageEntity = packageRepository.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));

        if (!packageEntity.getSender().getUserId().equals(senderUserId)) {
            throw new IllegalArgumentException("Package does not belong to sender");
        }

        CarrierRoute carrierRoute = carrierRouteRepository.findById(carrierRouteId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrier route not found"));

        Optional<DeliveryRequest> existingRequest = deliveryRequestRepository
                .findByPackageEntityAndCarrierRouteAndStatusIn(packageEntity, carrierRoute, Arrays.asList(
                        DeliveryRequest.RequestStatus.PENDING,
                        DeliveryRequest.RequestStatus.ACCEPTED,
                        DeliveryRequest.RequestStatus.PICKED_UP,
                        DeliveryRequest.RequestStatus.IN_TRANSIT
                ));

        if (existingRequest.isPresent()) {
            throw new IllegalStateException("A delivery request already exists for this package and route");
        }

        validateRouteAndDates(packageEntity, carrierRoute);
        return createDeliveryRequest(packageEntity, carrierRoute, senderNote, "SENDER_TO_CARRIER");
    }

    public DeliveryRequestResponse carrierSendRequestToSender(String carrierUserId, Long packageId,
                                                              Long carrierRouteId, String carrierNote) {
        Package packageEntity = packageRepository.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));

        CarrierRoute carrierRoute = carrierRouteRepository.findById(carrierRouteId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrier route not found"));

        if (!carrierRoute.getCarrierProfile().getUser().getUserId().equals(carrierUserId)) {
            throw new IllegalArgumentException("Route does not belong to carrier");
        }

        Optional<DeliveryRequest> existingRequest = deliveryRequestRepository
                .findByPackageEntityAndCarrierRouteAndStatusIn(packageEntity, carrierRoute, Arrays.asList(
                        DeliveryRequest.RequestStatus.PENDING,
                        DeliveryRequest.RequestStatus.ACCEPTED,
                        DeliveryRequest.RequestStatus.PICKED_UP,
                        DeliveryRequest.RequestStatus.IN_TRANSIT
                ));

        if (existingRequest.isPresent()) {
            throw new IllegalStateException("A delivery request already exists for this package and route");
        }

        validateRouteAndDates(packageEntity, carrierRoute);
        return createDeliveryRequest(packageEntity, carrierRoute, carrierNote, "CARRIER_TO_SENDER");
    }

    public DeliveryRequestResponse senderAcceptRequest(String senderUserId, Long requestId, String senderNote) {
        DeliveryRequest deliveryRequest = deliveryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found"));

        String packageOwnerUserId = deliveryRequest.getPackageEntity().getSender().getUserId();
        if (!packageOwnerUserId.equals(senderUserId)) {
            throw new IllegalArgumentException("Not authorized — package does not belong to this user");
        }

        if (deliveryRequest.getStatus() != DeliveryRequest.RequestStatus.PENDING) {
            throw new IllegalArgumentException("Request is not in PENDING state");
        }

        Optional<DeliveryRequest> existingAccepted = deliveryRequestRepository
                .findByPackageEntityAndStatusIn(deliveryRequest.getPackageEntity(), Arrays.asList(
                        DeliveryRequest.RequestStatus.ACCEPTED,
                        DeliveryRequest.RequestStatus.PICKED_UP,
                        DeliveryRequest.RequestStatus.IN_TRANSIT
                )).stream().filter(req -> !req.getRequestId().equals(requestId)).findFirst();

        if (existingAccepted.isPresent()) {
            throw new IllegalStateException("This package already has an active delivery request");
        }

        return acceptRequest(deliveryRequest, senderNote, "SENDER");
    }

    public DeliveryRequestResponse carrierAcceptRequest(String carrierUserId, Long requestId, String carrierNote) {
        DeliveryRequest deliveryRequest = deliveryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found"));

        if (!deliveryRequest.getCarrier().getUserId().equals(carrierUserId)) {
            throw new IllegalArgumentException("Not authorized");
        }

        if (deliveryRequest.getStatus() != DeliveryRequest.RequestStatus.PENDING) {
            throw new IllegalArgumentException("Request is not in PENDING state");
        }

        Optional<DeliveryRequest> existingAccepted = deliveryRequestRepository
                .findByPackageEntityAndStatusIn(deliveryRequest.getPackageEntity(), Arrays.asList(
                        DeliveryRequest.RequestStatus.ACCEPTED,
                        DeliveryRequest.RequestStatus.PICKED_UP,
                        DeliveryRequest.RequestStatus.IN_TRANSIT
                )).stream().filter(req -> !req.getRequestId().equals(requestId)).findFirst();

        if (existingAccepted.isPresent()) {
            throw new IllegalStateException("This package already has an active delivery request");
        }

        return acceptRequest(deliveryRequest, carrierNote, "CARRIER");
    }

    public DeliveryRequestResponse senderRejectRequest(String senderUserId, Long requestId, String reason) {
        DeliveryRequest deliveryRequest = deliveryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found"));

        if (!deliveryRequest.getSender().getUserId().equals(senderUserId)) {
            throw new IllegalArgumentException("Not authorized");
        }

        return rejectRequest(deliveryRequest, reason, "SENDER");
    }

    public DeliveryRequestResponse carrierRejectRequest(String carrierUserId, Long requestId, String reason) {
        DeliveryRequest deliveryRequest = deliveryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found"));

        if (!deliveryRequest.getCarrier().getUserId().equals(carrierUserId)) {
            throw new IllegalArgumentException("Not authorized");
        }

        return rejectRequest(deliveryRequest, reason, "CARRIER");
    }

    // =====================================================================
    // OTP + PHOTO FLOW
    // =====================================================================

    public DeliveryRequestResponse uploadPickupPhoto(String carrierUserId, Long requestId,
                                                     MultipartFile pickupPhoto) {
        DeliveryRequest deliveryRequest = deliveryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found"));

        if (!deliveryRequest.getCarrier().getUserId().equals(carrierUserId)) {
            throw new IllegalArgumentException("Only carrier can upload pickup photo");
        }

        if (deliveryRequest.getStatus() != DeliveryRequest.RequestStatus.ACCEPTED) {
            throw new IllegalArgumentException("Pickup photo can only be uploaded for ACCEPTED requests");
        }

        try {
            String photoUrl = callBackService.uploadDocumentToS3(pickupPhoto, "pickup");
            deliveryRequest.setPickupPhoto(photoUrl);
            DeliveryRequest saved = deliveryRequestRepository.save(deliveryRequest);
            log.info("📷 Pickup photo uploaded for request: {}", requestId);
            return mapToDeliveryRequestResponse(saved);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload pickup photo: " + e.getMessage());
        }
    }

    public DeliveryRequestResponse uploadDeliveryPhoto(String carrierUserId, Long requestId,
                                                       MultipartFile deliveryPhoto) {
        DeliveryRequest deliveryRequest = deliveryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found"));

        if (!deliveryRequest.getCarrier().getUserId().equals(carrierUserId)) {
            throw new IllegalArgumentException("Only carrier can upload delivery photo");
        }

        if (deliveryRequest.getStatus() != DeliveryRequest.RequestStatus.IN_TRANSIT) {
            throw new IllegalArgumentException("Delivery photo can only be uploaded during IN_TRANSIT status");
        }

        try {
            String photoUrl = callBackService.uploadDocumentToS3(deliveryPhoto, "delivery");
            deliveryRequest.setDeliveryPhoto(photoUrl);
            DeliveryRequest saved = deliveryRequestRepository.save(deliveryRequest);
            log.info("📷 Delivery photo uploaded for request: {}", requestId);
            return mapToDeliveryRequestResponse(saved);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload delivery photo: " + e.getMessage());
        }
    }

    public DeliveryRequestResponse verifyPickupOtp(String carrierUserId, Long requestId, String otp) {
        DeliveryRequest deliveryRequest = deliveryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found"));

        if (!deliveryRequest.getCarrier().getUserId().equals(carrierUserId)) {
            throw new IllegalArgumentException("Only carrier can verify OTP");
        }

        if (deliveryRequest.getPickupPhoto() == null || deliveryRequest.getPickupPhoto().isEmpty()) {
            throw new IllegalArgumentException("Please upload package photo before entering pickup OTP");
        }

        if (!deliveryRequest.getPickupOtp().equals(otp)) {
            throw new IllegalArgumentException("Invalid pickup OTP");
        }

        deliveryRequest.setStatus(DeliveryRequest.RequestStatus.PICKED_UP);
        deliveryRequest.setPickedUpAt(LocalDateTime.now());
        DeliveryRequest saved = deliveryRequestRepository.save(deliveryRequest);

        Package pkg = deliveryRequest.getPackageEntity();
        pkg.setStatus(Package.PackageStatus.PICKED_UP);
        packageRepository.save(pkg);

        CarrierRoute route = deliveryRequest.getCarrierRoute();
        updateRouteStatusToHighestLevel(route, CarrierRoute.RouteStatus.PICKED_UP);

        createNotification(
                deliveryRequest.getSender(),
                "Package Picked Up 📦",
                "Your package has been picked up by the carrier.",
                Notification.NotificationType.PICKUP_COMPLETED,
                pkg.getPackageId(),
                "SENDER"
        );

        return mapToDeliveryRequestResponse(saved);
    }

    public DeliveryRequestResponse startTransit(String carrierUserId, Long requestId) {
        DeliveryRequest deliveryRequest = deliveryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found"));

        if (!deliveryRequest.getCarrier().getUserId().equals(carrierUserId)) {
            throw new IllegalArgumentException("Only carrier can start transit");
        }

        if (deliveryRequest.getStatus() != DeliveryRequest.RequestStatus.PICKED_UP) {
            throw new IllegalArgumentException("Package must be in PICKED_UP status to start transit");
        }

        deliveryRequest.setStatus(DeliveryRequest.RequestStatus.IN_TRANSIT);
        DeliveryRequest saved = deliveryRequestRepository.save(deliveryRequest);

        Package pkg = deliveryRequest.getPackageEntity();
        pkg.setStatus(Package.PackageStatus.IN_TRANSIT);
        packageRepository.save(pkg);

        CarrierRoute route = deliveryRequest.getCarrierRoute();
        updateRouteStatusToHighestLevel(route, CarrierRoute.RouteStatus.IN_TRANSIT);

        // ✅ Notify sender that package is in transit
        createNotification(
                deliveryRequest.getSender(),
                "Package In Transit 🚚",
                "Your package is on its way!",
                Notification.NotificationType.DELIVERY_REQUEST,
                pkg.getPackageId(),
                "SENDER"
        );

        return mapToDeliveryRequestResponse(saved);
    }

    public DeliveryRequestResponse verifyDeliveryOtp(String carrierUserId, Long requestId, String otp) {
        DeliveryRequest deliveryRequest = deliveryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found"));

        if (deliveryRequest.getDeliveryPhoto() == null || deliveryRequest.getDeliveryPhoto().isEmpty()) {
            throw new IllegalArgumentException("Please upload delivery photo before entering delivery OTP");
        }

        if (!deliveryRequest.getDeliveryOtp().equals(otp)) {
            throw new IllegalArgumentException("Invalid delivery OTP");
        }

        deliveryRequest.setStatus(DeliveryRequest.RequestStatus.DELIVERED);
        deliveryRequest.setDeliveredAt(LocalDateTime.now());
        DeliveryRequest saved = deliveryRequestRepository.save(deliveryRequest);

        Package pkg = deliveryRequest.getPackageEntity();
        pkg.setStatus(Package.PackageStatus.DELIVERED);
        packageRepository.save(pkg);

        CarrierRoute route = deliveryRequest.getCarrierRoute();
        updateRouteStatusBasedOnAllPackages(route);

        // ✅ FIXED: Handle payment based on actual state
        Payment existingPayment = saved.getPayment();

        if (existingPayment != null
                && existingPayment.getPaymentMethod() == Payment.PaymentMethod.ONLINE
                && existingPayment.getPaymentStatus() == Payment.PaymentStatus.PENDING) {

            // Sender opened Razorpay and paid but network dropped —
            // callback never reached us so payment is stuck PENDING.
            // Verify with Razorpay directly before deciding COD vs Online.
            log.warn("⚠️ Stuck PENDING online payment for request: {}. Verifying with Razorpay...", requestId);

            try {
                com.razorpay.Order razorpayOrder = paymentService.getRazorpayClient()
                        .orders.fetch(existingPayment.getRazorpayOrderId());
                String orderStatus = razorpayOrder.get("status"); // "paid" or "created"

                if ("paid".equals(orderStatus)) {
                    // ✅ Payment DID go through — recover the record
                    log.info("✅ Razorpay confirms payment was made. Recovering for request: {}", requestId);
                    existingPayment.setPaymentStatus(Payment.PaymentStatus.COMPLETED);
                    existingPayment.setPaymentCompletedAt(LocalDateTime.now());
                    existingPayment.setCommissionPaid(true);
                    existingPayment.setCarrierTransferStatus(Payment.TransferStatus.PENDING);
                    existingPayment.setGatewayResponse("Auto-recovered at delivery OTP — Razorpay status: paid");
                    paymentService.getPaymentRepository().save(existingPayment);

                    // Trigger payout since online payment is now confirmed
                    paymentService.triggerCarrierPayoutOnDelivery(requestId);

                } else {
                    // Razorpay says not paid — sender never actually completed payment
                    // Convert stuck PENDING row to COD
                    log.warn("⚠️ Razorpay order not paid (status: {}). Converting to COD for request: {}",
                            orderStatus, requestId);
                    paymentService.handleOfflinePaymentOnDelivery(requestId);
                }

            } catch (Exception e) {
                // Can't reach Razorpay — safe fallback is COD
                log.error("❌ Razorpay verification failed: {}. Falling back to COD for request: {}",
                        e.getMessage(), requestId);
                paymentService.handleOfflinePaymentOnDelivery(requestId);
            }

        } else if (existingPayment == null
                || existingPayment.getPaymentStatus() != Payment.PaymentStatus.COMPLETED) {
            // No payment record at all — normal COD flow
            paymentService.handleOfflinePaymentOnDelivery(requestId);

        } else {
            // Online payment already COMPLETED normally — just trigger payout
            paymentService.triggerCarrierPayoutOnDelivery(requestId);
        }

        createNotification(
                deliveryRequest.getSender(),
                "Package Delivered! 🎉",
                "Your package has been delivered successfully.",
                Notification.NotificationType.DELIVERY_COMPLETED,
                pkg.getPackageId(),
                "SENDER"
        );

        createNotification(
                deliveryRequest.getCarrier(),
                "Delivery Complete ✅",
                "You've successfully delivered " + pkg.getProductName() + ". Payout initiated to your bank.",
                Notification.NotificationType.DELIVERY_COMPLETED,
                pkg.getPackageId(),
                "CARRIER"
        );

        return mapToDeliveryRequestResponse(saved);
    }
    // =====================================================================
    // DELIVERY PROGRESS STATUS
    // =====================================================================

    public DeliveryProgressResponse getDeliveryProgressStatus(String userId, Long requestId) {
        DeliveryRequest deliveryRequest = deliveryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found"));

        boolean isCarrier = deliveryRequest.getCarrier().getUserId().equals(userId);
        boolean isSender = deliveryRequest.getSender().getUserId().equals(userId);

        if (!isCarrier && !isSender) {
            throw new IllegalArgumentException("Not authorized to view this delivery status");
        }

        DeliveryProgressResponse response = new DeliveryProgressResponse();
        response.setRequestId(deliveryRequest.getRequestId());
        response.setStatus(deliveryRequest.getStatus());
        response.setPackageId(deliveryRequest.getPackageEntity().getPackageId());
        response.setPackageName(deliveryRequest.getPackageEntity().getProductName());
        response.setRouteId(deliveryRequest.getCarrierRoute().getRouteId());
        response.setTotalAmount(deliveryRequest.getTotalAmount());
        response.setPlatformCommission(deliveryRequest.getPlatformCommission());
        response.setCarrierEarning(deliveryRequest.getCarrierEarning());

        // ✅ Payment status in progress response
        Payment payment = deliveryRequest.getPayment();
        if (payment != null) {
            response.setPaymentStatus(payment.getPaymentStatus().toString());
            response.setPaymentCompleted(payment.getPaymentStatus() == Payment.PaymentStatus.COMPLETED);
            response.setPaymentMethod(payment.getPaymentMethod().toString());
            response.setCommissionPaid(
                    payment.getPaymentMethod() == Payment.PaymentMethod.ONLINE
                            ? true
                            : Boolean.TRUE.equals(payment.getCommissionPaid())
            );
        } else {
            response.setPaymentStatus("NOT_PAID");
            response.setPaymentCompleted(false);
            response.setCommissionPaid(false);
        }

        User sender = deliveryRequest.getSender();
        SenderDetailsResponse senderDetails = new SenderDetailsResponse();
        senderDetails.setName(sender.getFullName());
        senderDetails.setPhone(sender.getMobile());
        senderDetails.setProfileImage(sender.getProfileUrl());
        response.setSenderDetails(senderDetails);

        if (isCarrier) {
            User carrier = deliveryRequest.getCarrier();
            CarrierDetailsResponse carrierDetails = new CarrierDetailsResponse();
            carrierDetails.setName(carrier.getFullName());
            carrierDetails.setPhone(carrier.getMobile());
            carrierDetails.setProfileImage(carrier.getProfileUrl());
            carrierDetails.setVehicleType(deliveryRequest.getCarrierRoute().getTransportType().toString());
            response.setCarrierDetails(carrierDetails);
        }

        AddressDetailsResponse addressDetails = new AddressDetailsResponse();
        addressDetails.setPickupAddress(deliveryRequest.getPackageEntity().getFromAddress());
        addressDetails.setDeliveryAddress(deliveryRequest.getPackageEntity().getToAddress());
        response.setAddressDetails(addressDetails);

        DocumentsProgressResponse docsProgress = new DocumentsProgressResponse();
        docsProgress.setPickupPhotoUploaded(
                deliveryRequest.getPickupPhoto() != null && !deliveryRequest.getPickupPhoto().isEmpty());
        docsProgress.setPickupPhotoUrl(deliveryRequest.getPickupPhoto());
        docsProgress.setDeliveryPhotoUploaded(
                deliveryRequest.getDeliveryPhoto() != null && !deliveryRequest.getDeliveryPhoto().isEmpty());
        docsProgress.setDeliveryPhotoUrl(deliveryRequest.getDeliveryPhoto());
        response.setDocumentsProgress(docsProgress);

        OtpProgressResponse otpProgress = new OtpProgressResponse();
        otpProgress.setPickupOtpRequired(true);
        otpProgress.setPickupOtpVerified(
                deliveryRequest.getStatus() == DeliveryRequest.RequestStatus.PICKED_UP
                        || deliveryRequest.getStatus() == DeliveryRequest.RequestStatus.IN_TRANSIT
                        || deliveryRequest.getStatus() == DeliveryRequest.RequestStatus.DELIVERED
        );
        otpProgress.setPickupOtp(isCarrier ? deliveryRequest.getPickupOtp() : null);
        otpProgress.setDeliveryOtpRequired(true);
        otpProgress.setDeliveryOtpVerified(
                deliveryRequest.getStatus() == DeliveryRequest.RequestStatus.DELIVERED);
        otpProgress.setDeliveryOtp(isCarrier ? deliveryRequest.getDeliveryOtp() : null);
        response.setOtpProgress(otpProgress);

        List<DeliveryStep> progressSteps = new ArrayList<>();
        progressSteps.add(new DeliveryStep(1, "Request Accepted", "Delivery accepted", true, deliveryRequest.getAcceptedAt()));

        boolean pickupPhotoUploaded = deliveryRequest.getPickupPhoto() != null && !deliveryRequest.getPickupPhoto().isEmpty();
        progressSteps.add(new DeliveryStep(2, "Pickup Photo", "Package photo uploaded", pickupPhotoUploaded, null));

        boolean pickupOtpVerified = deliveryRequest.getStatus() == DeliveryRequest.RequestStatus.PICKED_UP
                || deliveryRequest.getStatus() == DeliveryRequest.RequestStatus.IN_TRANSIT
                || deliveryRequest.getStatus() == DeliveryRequest.RequestStatus.DELIVERED;
        progressSteps.add(new DeliveryStep(3, "Pickup Verified", "Pickup OTP verified", pickupOtpVerified, deliveryRequest.getPickedUpAt()));

        boolean inTransit = deliveryRequest.getStatus() == DeliveryRequest.RequestStatus.IN_TRANSIT
                || deliveryRequest.getStatus() == DeliveryRequest.RequestStatus.DELIVERED;
        progressSteps.add(new DeliveryStep(4, "In Transit", "Package on the way", inTransit, null));

        boolean deliveryPhotoUploaded = deliveryRequest.getDeliveryPhoto() != null && !deliveryRequest.getDeliveryPhoto().isEmpty();
        progressSteps.add(new DeliveryStep(5, "Delivery Photo", "Delivery photo uploaded", deliveryPhotoUploaded, null));

        boolean orderCompleted = deliveryRequest.getStatus() == DeliveryRequest.RequestStatus.DELIVERED;
        progressSteps.add(new DeliveryStep(6, "Order Completed", "Delivered successfully", orderCompleted, deliveryRequest.getDeliveredAt()));

        // ✅ Step 7: Payment status
        boolean paymentDone = payment != null && payment.getPaymentStatus() == Payment.PaymentStatus.COMPLETED;
        progressSteps.add(new DeliveryStep(7, "Payment", "Payment settled", paymentDone,
                payment != null ? payment.getPaymentCompletedAt() : null));

        response.setProgressSteps(progressSteps);

        long completedSteps = progressSteps.stream().filter(DeliveryStep::isCompleted).count();
        response.setProgressPercentage((int) ((completedSteps * 100) / progressSteps.size()));
        response.setTotalSteps(progressSteps.size());
        response.setCompletedSteps((int) completedSteps);
        response.setOrderStatus(getOrderStatusDisplay(deliveryRequest.getStatus()));
        response.setOrderCompleted(orderCompleted);
        response.setSenderNote(deliveryRequest.getSenderNote());
        response.setCarrierNote(deliveryRequest.getCarrierNote());
        response.setCreatedAt(deliveryRequest.getCreatedAt());
        response.setUpdatedAt(deliveryRequest.getUpdatedAt());
        response.setRequestedAt(deliveryRequest.getRequestedAt());
        response.setAcceptedAt(deliveryRequest.getAcceptedAt());
        response.setPickedUpAt(deliveryRequest.getPickedUpAt());
        response.setDeliveredAt(deliveryRequest.getDeliveredAt());
        response.setNextAction(getNextActionRequired(deliveryRequest));

        return response;
    }

    // =====================================================================
    // ALL PENDING REQUESTS TAB
    // =====================================================================

    public RequestsTabResponse getAllPendingRequests(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        RequestsTabResponse response = new RequestsTabResponse();

        List<DeliveryRequest> pendingAsCarrier = deliveryRequestRepository
                .findByCarrierAndStatusOrderByCreatedAtDesc(user, DeliveryRequest.RequestStatus.PENDING);
        response.setRequestsReceivedFromSenders(pendingAsCarrier.stream()
                .map(this::mapToRequestSummary).collect(Collectors.toList()));

        List<DeliveryRequest> pendingAsSender = deliveryRequestRepository
                .findBySenderAndStatusOrderByCreatedAtDesc(user, DeliveryRequest.RequestStatus.PENDING);
        response.setRequestsReceivedFromCarriers(pendingAsSender.stream()
                .map(this::mapToRequestSummary).collect(Collectors.toList()));

        List<DeliveryRequest> activeAsCarrier = deliveryRequestRepository
                .findByCarrierAndStatusInOrderByCreatedAtDesc(user, Arrays.asList(
                        DeliveryRequest.RequestStatus.ACCEPTED,
                        DeliveryRequest.RequestStatus.PICKED_UP,
                        DeliveryRequest.RequestStatus.IN_TRANSIT
                ));
        response.setActiveRequestsAsCarrier(activeAsCarrier.stream()
                .map(this::mapToRequestSummary).collect(Collectors.toList()));

        List<DeliveryRequest> activeAsSender = deliveryRequestRepository
                .findBySenderAndStatusInOrderByCreatedAtDesc(user, Arrays.asList(
                        DeliveryRequest.RequestStatus.ACCEPTED,
                        DeliveryRequest.RequestStatus.PICKED_UP,
                        DeliveryRequest.RequestStatus.IN_TRANSIT
                ));
        response.setActiveRequestsAsSender(activeAsSender.stream()
                .map(this::mapToRequestSummary).collect(Collectors.toList()));

        response.setTotalPendingCount(pendingAsCarrier.size() + pendingAsSender.size());
        response.setTotalActiveCount(activeAsCarrier.size() + activeAsSender.size());

        return response;
    }

    // =====================================================================
    // MATCHING (Home page)
    // =====================================================================

    public PackageAvailabilityResponse getMatchingPackagesForRoute(
            String senderUserId,
            Long routeId,
            Double corridorKm) {

        try {
            User sender = userRepository.findByUserId(senderUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            CarrierRoute carrierRoute = carrierRouteRepository.findById(routeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Route not found"));

            if (carrierRoute.getRouteStatus() != CarrierRoute.RouteStatus.CREATED) {
                throw new IllegalArgumentException("Route is not active");
            }

            LocalDate routeAvailableDate = carrierRoute.getAvailableDate();
            LocalDate routeDeadlineDate = carrierRoute.getDeadlineDate();

            // ✅ Read coords directly from carrierRoute — no frontend params needed
            Double routeFromLat = carrierRoute.getLatitude();
            Double routeFromLng = carrierRoute.getLongitude();
            Double routeToLat   = carrierRoute.getToLatitude();
            Double routeToLng   = carrierRoute.getToLongitude();

            boolean hasCoords = routeFromLat != null && routeFromLng != null
                    && routeToLat != null && routeToLng != null
                    && !(routeFromLat == 0.0 && routeFromLng == 0.0);

            double effectiveCorridor = (corridorKm != null && corridorKm > 0) ? corridorKm : 15.0;

            log.info("🗺️ Geospatial filter active: {} | corridor: {} km", hasCoords, effectiveCorridor);

            List<Package> allPackages = packageRepository.findBySenderAndStatus(
                    sender, Package.PackageStatus.CREATED);

            // Remove packages that already have a pending/active request
            allPackages.removeIf(pkg -> {
                Long pendingCount = deliveryRequestRepository
                        .countByPackageEntityAndStatus(pkg, DeliveryRequest.RequestStatus.PENDING);
                return pendingCount > 0;
            });

            List<PackageSelectionResponse> matchingPackages = allPackages.stream()
                    .map(pkg -> {
                        try {
                            // ── Step 1: Date range check ──────────────────────────
                            LocalDate packagePickupDate = LocalDate.parse(pkg.getPickUpDate(), DATE_FORMATTER);
                            LocalDate packageDropDate = LocalDate.parse(pkg.getDropDate(), DATE_FORMATTER);

                            boolean dateMatch =
                                    isDateInRange(packagePickupDate, routeAvailableDate, routeDeadlineDate)
                                            && isDateInRange(packageDropDate, routeAvailableDate, routeDeadlineDate);

                            if (!dateMatch) {
                                log.info("❌ Package {} filtered out — dates out of route range", pkg.getPackageId());
                                return null;
                            }

                            // ── Step 2: Geospatial corridor check ─────────────────
                            if (hasCoords) {
                                Double pkgFromLat = pkg.getLatitude();
                                Double pkgFromLng = pkg.getLongitude();
                                Double pkgToLat   = pkg.getToLatitude();
                                Double pkgToLng   = pkg.getToLongitude();

                                // Check FROM point
                                if (pkgFromLat == null || pkgFromLng == null
                                        || (pkgFromLat == 0.0 && pkgFromLng == 0.0)) {
                                    log.warn("⚠️ Package {} has no FROM coordinates, skipping geo filter",
                                            pkg.getPackageId());
                                } else {
                                    double fromDistanceToRoute = calculateDistanceToRoute(
                                            routeFromLat, routeFromLng,
                                            routeToLat, routeToLng,
                                            pkgFromLat, pkgFromLng);

                                    log.info("📦 Package {} FROM dist to route: {} km (limit: {} km)",
                                            pkg.getPackageId(),
                                            Math.round(fromDistanceToRoute * 100.0) / 100.0,
                                            effectiveCorridor);

                                    if (fromDistanceToRoute > effectiveCorridor) {
                                        log.info("❌ Package {} FROM point outside corridor — filtered out",
                                                pkg.getPackageId());
                                        return null;
                                    }

                                    // Check TO point (only if coordinates exist)
                                    if (pkgToLat != null && pkgToLng != null
                                            && !(pkgToLat == 0.0 && pkgToLng == 0.0)) {

                                        double toDistanceToRoute = calculateDistanceToRoute(
                                                routeFromLat, routeFromLng,
                                                routeToLat, routeToLng,
                                                pkgToLat, pkgToLng);

                                        log.info("📦 Package {} TO dist to route: {} km (limit: {} km)",
                                                pkg.getPackageId(),
                                                Math.round(toDistanceToRoute * 100.0) / 100.0,
                                                effectiveCorridor);

                                        if (toDistanceToRoute > effectiveCorridor) {
                                            log.info("❌ Package {} TO point outside corridor — filtered out",
                                                    pkg.getPackageId());
                                            return null;
                                        }
                                    } else {
                                        log.warn("⚠️ Package {} has no TO coordinates, skipping TO point check",
                                                pkg.getPackageId());
                                    }
                                }
                            }

                            // ── Step 3: Build selection response ──────────────────
                            log.info("✅ Package {} passed all filters — adding to results", pkg.getPackageId());
                            return mapToPackageSelection(pkg, carrierRoute);

                        } catch (Exception e) {
                            log.warn("⚠️ Error processing package {}: {}", pkg.getPackageId(), e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            PackageAvailabilityResponse response = new PackageAvailabilityResponse();
            response.setRouteId(routeId);
            response.setFromLocation(carrierRoute.getFromLocation());
            response.setToLocation(carrierRoute.getToLocation());
            response.setAvailableDate(carrierRoute.getAvailableDate());
            response.setDeadlineDate(carrierRoute.getDeadlineDate());
            response.setDeadlineTime(carrierRoute.getDeadlineTime().toString());
            response.setAvailableTime(carrierRoute.getAvailableTime().toString());
            response.setTransportType(carrierRoute.getTransportType());
            response.setHasPackages(!matchingPackages.isEmpty());
            response.setPackageCount(matchingPackages.size());
            response.setMatchingPackages(matchingPackages);
            response.setMessage(matchingPackages.isEmpty()
                    ? "No packages available along your route in this date range."
                    : "Found " + matchingPackages.size() + " matching package(s) along your route");

            return response;

        } catch (Exception e) {
            log.error("❌ Error in getMatchingPackagesForRoute: {}", e.getMessage(), e);
            throw e;
        }
    }
    public RouteAvailabilityResponse getMatchingRoutesForPackage(String carrierUserId, Long packageId) {
        User carrier = userRepository.findByUserId(carrierUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        CarrierProfile carrierProfile = carrier.getCarrierProfile();
        if (carrierProfile == null) throw new IllegalArgumentException("User is not a carrier");

        Package packageEntity = packageRepository.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));

        if (packageEntity.getStatus() != Package.PackageStatus.CREATED) {
            throw new IllegalArgumentException("Package is not available for delivery requests");
        }

        LocalDate packagePickupDate = LocalDate.parse(packageEntity.getPickUpDate(), DATE_FORMATTER);
        LocalDate packageDropDate = LocalDate.parse(packageEntity.getDropDate(), DATE_FORMATTER);

        // Package coordinates
        Double pkgFromLat = packageEntity.getLatitude();
        Double pkgFromLng = packageEntity.getLongitude();
        Double pkgToLat   = packageEntity.getToLatitude();
        Double pkgToLng   = packageEntity.getToLongitude();

        boolean pkgHasFromCoords = pkgFromLat != null && pkgFromLng != null
                && !(pkgFromLat == 0.0 && pkgFromLng == 0.0);
        boolean pkgHasToCoords   = pkgToLat != null && pkgToLng != null
                && !(pkgToLat == 0.0 && pkgToLng == 0.0);

        double corridorKm = 15.0;

        List<CarrierRoute> allRoutes = carrierRouteRepository
                .findByCarrierProfileAndRouteStatus(carrierProfile, CarrierRoute.RouteStatus.CREATED);

        List<RouteSelectionResponse> matchingRoutes = allRoutes.stream()
                .filter(route -> {
                    // ── Step 1: Date range check ──────────────────────────────
                    boolean dateMatch =
                            isDateInRange(packagePickupDate, route.getAvailableDate(), route.getDeadlineDate())
                                    && isDateInRange(packageDropDate, route.getAvailableDate(), route.getDeadlineDate());

                    if (!dateMatch) {
                        log.info("❌ Route {} filtered out — dates out of package range", route.getRouteId());
                        return false;
                    }

                    // ── Step 2: Geospatial corridor check ─────────────────────
                    Double routeFromLat = route.getLatitude();
                    Double routeFromLng = route.getLongitude();
                    Double routeToLat   = route.getToLatitude();
                    Double routeToLng   = route.getToLongitude();

                    boolean routeHasCoords = routeFromLat != null && routeFromLng != null
                            && routeToLat != null && routeToLng != null
                            && !(routeFromLat == 0.0 && routeFromLng == 0.0);

                    if (!routeHasCoords || !pkgHasFromCoords) {
                        log.warn("⚠️ Route {} or package {} missing coords — skipping geo filter",
                                route.getRouteId(), packageId);
                        return true; // fall back to date-only match
                    }

                    // Check package FROM point against route corridor
                    double fromDist = calculateDistanceToRoute(
                            routeFromLat, routeFromLng,
                            routeToLat, routeToLng,
                            pkgFromLat, pkgFromLng);

                    log.info("📦 Route {} — pkg FROM dist to route: {} km (limit: {} km)",
                            route.getRouteId(),
                            Math.round(fromDist * 100.0) / 100.0,
                            corridorKm);

                    if (fromDist > corridorKm) {
                        log.info("❌ Route {} filtered out — pkg FROM point outside corridor", route.getRouteId());
                        return false;
                    }

                    // Check package TO point against route corridor
                    if (pkgHasToCoords) {
                        double toDist = calculateDistanceToRoute(
                                routeFromLat, routeFromLng,
                                routeToLat, routeToLng,
                                pkgToLat, pkgToLng);

                        log.info("📦 Route {} — pkg TO dist to route: {} km (limit: {} km)",
                                route.getRouteId(),
                                Math.round(toDist * 100.0) / 100.0,
                                corridorKm);

                        if (toDist > corridorKm) {
                            log.info("❌ Route {} filtered out — pkg TO point outside corridor", route.getRouteId());
                            return false;
                        }
                    } else {
                        log.warn("⚠️ Package {} has no TO coords — skipping TO point check", packageId);
                    }

                    log.info("✅ Route {} passed all filters", route.getRouteId());
                    return true;
                })
                .map(route -> mapToRouteSelection(route, packageEntity))
                .collect(Collectors.toList());

        RouteAvailabilityResponse response = new RouteAvailabilityResponse();
        response.setPackageId(packageId);
        response.setProductName(packageEntity.getProductName());
        response.setFromAddress(packageEntity.getFromAddress());
        response.setToAddress(packageEntity.getToAddress());
        response.setPickUpDate(packageEntity.getPickUpDate());
        response.setDropDate(packageEntity.getDropDate());
        response.setTransportType(packageEntity.getTransportType());
        response.setHasRoutes(!matchingRoutes.isEmpty());
        response.setRouteCount(matchingRoutes.size());
        response.setMatchingRoutes(matchingRoutes);
        response.setMessage(matchingRoutes.isEmpty()
                ? "No routes available in this date range. Please create a route first."
                : "Found " + matchingRoutes.size() + " matching route(s)");

        return response;
    }
    // =====================================================================
    // PRIVATE HELPERS
    // =====================================================================

    private void validateRouteAndDates(Package packageEntity, CarrierRoute carrierRoute) {
        if (carrierRoute.getRouteStatus() != CarrierRoute.RouteStatus.CREATED) {
            throw new IllegalArgumentException("Route is not active");
        }

        LocalDate packagePickupDate = LocalDate.parse(packageEntity.getPickUpDate(), DATE_FORMATTER);
        LocalDate packageDropDate = LocalDate.parse(packageEntity.getDropDate(), DATE_FORMATTER);
        LocalDate routeAvailableDate = carrierRoute.getAvailableDate();
        LocalDate routeDeadlineDate = carrierRoute.getDeadlineDate();

        if (!isDateInRange(packagePickupDate, routeAvailableDate, routeDeadlineDate)
                || !isDateInRange(packageDropDate, routeAvailableDate, routeDeadlineDate)) {
            throw new IllegalArgumentException("Package dates do not fall within the route's date range");
        }

        if ((carrierRoute.getCurrentQuantity() + 1) > carrierRoute.getMaxQuantity()) {
            throw new IllegalArgumentException("Route quantity capacity exceeded");
        }
    }

    private boolean isDateInRange(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    private DeliveryRequestResponse createDeliveryRequest(Package packageEntity, CarrierRoute carrierRoute,
                                                          String note, String requestType) {
        Double totalAmount = calculateDeliveryAmount(packageEntity, carrierRoute);
        // ✅ Use consistent 15% commission rate
        Double platformCommission = totalAmount * 0.15;
        Double carrierEarning = totalAmount - platformCommission;

        DeliveryRequest deliveryRequest = new DeliveryRequest();
        deliveryRequest.setPackageEntity(packageEntity);
        deliveryRequest.setCarrierRoute(carrierRoute);
        deliveryRequest.setSender(packageEntity.getSender());
        deliveryRequest.setCarrier(carrierRoute.getCarrierProfile().getUser());
        deliveryRequest.setStatus(DeliveryRequest.RequestStatus.PENDING);
        deliveryRequest.setTotalAmount(totalAmount);
        deliveryRequest.setPlatformCommission(platformCommission);
        deliveryRequest.setCarrierEarning(carrierEarning);
        deliveryRequest.setPickupOtp(packageEntity.getPickupOtp());
        deliveryRequest.setDeliveryOtp(packageEntity.getDeliveryOtp());
        deliveryRequest.setRequestedAt(LocalDateTime.now());

        if (requestType.equals("SENDER_TO_CARRIER")) {
            deliveryRequest.setSenderNote(note);
        } else {
            deliveryRequest.setCarrierNote(note);
        }

        DeliveryRequest saved = deliveryRequestRepository.save(deliveryRequest);

        packageEntity.setStatus(Package.PackageStatus.REQUEST_SENT);
        packageRepository.save(packageEntity);

        User recipient = requestType.equals("SENDER_TO_CARRIER")
                ? carrierRoute.getCarrierProfile().getUser()
                : packageEntity.getSender();
        String title = requestType.equals("SENDER_TO_CARRIER") ? "New Delivery Request" : "New Carrier Offer";
        String recipientRole = requestType.equals("SENDER_TO_CARRIER") ? "CARRIER" : "SENDER";

        createNotification(recipient, title, "You have a new delivery request",
                Notification.NotificationType.DELIVERY_REQUEST,
                saved.getPackageEntity().getPackageId(), recipientRole);

        return mapToDeliveryRequestResponse(saved);
    }

    private DeliveryRequestResponse acceptRequest(DeliveryRequest deliveryRequest, String note, String acceptedBy) {
        deliveryRequest.setStatus(DeliveryRequest.RequestStatus.ACCEPTED);
        deliveryRequest.setAcceptedAt(LocalDateTime.now());

        if (acceptedBy.equals("CARRIER")) {
            deliveryRequest.setCarrierNote(note);
        } else {
            deliveryRequest.setSenderNote(note);
        }

        DeliveryRequest saved = deliveryRequestRepository.save(deliveryRequest);

        Package packageEntity = deliveryRequest.getPackageEntity();
        packageEntity.setStatus(Package.PackageStatus.MATCHED);
        packageRepository.save(packageEntity);

        CarrierRoute carrierRoute = deliveryRequest.getCarrierRoute();
        if (carrierRoute.getRouteStatus() == CarrierRoute.RouteStatus.CREATED
                || carrierRoute.getRouteStatus() == CarrierRoute.RouteStatus.REQUEST_SENT) {
            carrierRoute.setRouteStatus(CarrierRoute.RouteStatus.MATCHED);
            carrierRouteRepository.save(carrierRoute);
        }

        Double packageWeight = calculatePackageWeight(packageEntity);
        carrierRouteService.updateRouteCapacity(deliveryRequest.getCarrierRoute().getRouteId(), packageWeight, 1);

        User recipient = acceptedBy.equals("CARRIER") ? deliveryRequest.getSender() : deliveryRequest.getCarrier();
        Long referenceId = acceptedBy.equals("CARRIER")
                ? saved.getPackageEntity().getPackageId()
                : saved.getCarrierRoute().getRouteId();
        String recipientRole = acceptedBy.equals("CARRIER") ? "SENDER" : "CARRIER";

        createNotification(recipient, "Request Accepted ✅", "Your delivery request has been accepted.",
                Notification.NotificationType.REQUEST_ACCEPTED, referenceId, recipientRole);

        return mapToDeliveryRequestResponse(saved);
    }

    private DeliveryRequestResponse rejectRequest(DeliveryRequest deliveryRequest, String reason, String rejectedBy) {
        deliveryRequest.setStatus(DeliveryRequest.RequestStatus.REJECTED);

        if (rejectedBy.equals("CARRIER")) {
            deliveryRequest.setCarrierNote(reason);
        } else {
            deliveryRequest.setSenderNote(reason);
        }

        DeliveryRequest saved = deliveryRequestRepository.save(deliveryRequest);

        Package packageEntity = deliveryRequest.getPackageEntity();
        packageEntity.setStatus(Package.PackageStatus.CREATED);
        packageRepository.save(packageEntity);

        User recipient = rejectedBy.equals("CARRIER") ? deliveryRequest.getSender() : deliveryRequest.getCarrier();
        Long referenceId = rejectedBy.equals("CARRIER")
                ? saved.getPackageEntity().getPackageId()
                : saved.getCarrierRoute().getRouteId();
        String recipientRole = rejectedBy.equals("CARRIER") ? "SENDER" : "CARRIER";

        createNotification(recipient, "Request Rejected", reason,
                Notification.NotificationType.REQUEST_REJECTED, referenceId, recipientRole);

        return mapToDeliveryRequestResponse(saved);
    }

    private Double calculatePackageWeight(Package packageEntity) {
        if (packageEntity.getTransportType() == CarrierRoute.TransportType.COMMERCIAL) {
            return packageEntity.getWeight();
        }
        Double dimensionalWeight = (packageEntity.getLength() * packageEntity.getWidth()
                * packageEntity.getHeight()) / 5000.0;
        return packageEntity.getWeight() != null
                ? Math.max(packageEntity.getWeight(), dimensionalWeight)
                : dimensionalWeight;
    }

    private Double calculateDeliveryAmount(Package packageEntity, CarrierRoute carrierRoute) {
        List<RoutePricing> pricingList = carrierRoute.getRoutePricing();

        RoutePricing applicablePricing = pricingList.stream()
                // .filter(p -> p.getProductType() == packageEntity.getProductType()) // ✅ commented — use any available pricing
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No pricing configured for this route"
                ));
        if (carrierRoute.getTransportType() == CarrierRoute.TransportType.COMMERCIAL) {
            Double weightInTons = calculatePackageWeight(packageEntity) / 1000.0;
            return applicablePricing.getPricePerTon() * weightInTons;
        } else {
            Double packageWeight = calculatePackageWeight(packageEntity);
            if (packageWeight > applicablePricing.getWeightLimit()) {
                throw new IllegalArgumentException("Package weight exceeds route limit");
            }
            return applicablePricing.getFixedPrice();
        }
    }

    // =====================================================================
    // ROUTE STATUS HELPERS
    // =====================================================================

    private void updateRouteStatusToHighestLevel(CarrierRoute route, CarrierRoute.RouteStatus newStatus) {
        int currentLevel = getStatusLevel(route.getRouteStatus());
        int newLevel = getStatusLevel(newStatus);
        if (newLevel > currentLevel) {
            route.setRouteStatus(newStatus);
            carrierRouteRepository.save(route);
            log.info("Route {} status → {}", route.getRouteId(), newStatus);
        }
    }

    private void updateRouteStatusBasedOnAllPackages(CarrierRoute route) {
        List<DeliveryRequest> allRequests = deliveryRequestRepository
                .findByCarrierRouteOrderByCreatedAtDesc(route);

        long accepted = allRequests.stream()
                .filter(r -> r.getStatus() == DeliveryRequest.RequestStatus.ACCEPTED)
                .count();
        long pickedUp = allRequests.stream()
                .filter(r -> r.getStatus() == DeliveryRequest.RequestStatus.PICKED_UP)
                .count();
        long inTransit = allRequests.stream()
                .filter(r -> r.getStatus() == DeliveryRequest.RequestStatus.IN_TRANSIT)
                .count();
        long delivered = allRequests.stream()
                .filter(r -> r.getStatus() == DeliveryRequest.RequestStatus.DELIVERED)
                .count();

        // ✅ Only count active statuses — PENDING/REJECTED/CANCELLED are excluded
        long total = accepted + pickedUp + inTransit + delivered;

        CarrierRoute.RouteStatus newStatus;

        if (total == 0) {
            // No active packages at all — route is open
            newStatus = CarrierRoute.RouteStatus.CREATED;
        } else if (delivered > 0 && delivered == total) {
            // All packages delivered — route is fully done
            newStatus = CarrierRoute.RouteStatus.DELIVERED;
        } else if (inTransit > 0) {
            // At least one package in transit
            newStatus = CarrierRoute.RouteStatus.IN_TRANSIT;
        } else if (pickedUp > 0) {
            // At least one package picked up
            newStatus = CarrierRoute.RouteStatus.PICKED_UP;
        } else if (accepted > 0) {
            // Packages accepted but not yet picked up
            newStatus = CarrierRoute.RouteStatus.MATCHED;
        } else {
            // Fallback — no active packages
            newStatus = CarrierRoute.RouteStatus.CREATED;
        }

        if (route.getRouteStatus() != newStatus) {
            route.setRouteStatus(newStatus);
            carrierRouteRepository.save(route);
            log.info("🔄 Route {} status updated → {} (accepted:{}, pickedUp:{}, inTransit:{}, delivered:{})",
                    route.getRouteId(), newStatus, accepted, pickedUp, inTransit, delivered);
        } else {
            log.info("✅ Route {} status unchanged → {} (accepted:{}, pickedUp:{}, inTransit:{}, delivered:{})",
                    route.getRouteId(), newStatus, accepted, pickedUp, inTransit, delivered);
        }
    }

    private int getStatusLevel(CarrierRoute.RouteStatus status) {
        switch (status) {
            case CREATED: return 1;
            case REQUEST_SENT: return 2;
            case ACCEPTED: case MATCHED: return 3;
            case PICKED_UP: return 4;
            case IN_TRANSIT: return 5;
            case DELIVERED: return 6;
            default: return 0;
        }
    }

    // =====================================================================
    // MAPPING HELPERS
    // =====================================================================

    private DeliveryRequestResponse mapToDeliveryRequestResponse(DeliveryRequest req) {
        DeliveryRequestResponse response = new DeliveryRequestResponse();
        response.setRequestId(req.getRequestId());
        response.setPackageId(req.getPackageEntity().getPackageId());
        response.setPackageName(req.getPackageEntity().getProductName());
        response.setRouteId(req.getCarrierRoute().getRouteId());
        response.setSenderName(req.getSender().getFullName());
        response.setCarrierName(req.getCarrier().getFullName());
        response.setStatus(req.getStatus());
        response.setTotalAmount(req.getTotalAmount());
        response.setPlatformCommission(req.getPlatformCommission());
        response.setCarrierEarning(req.getCarrierEarning());
        response.setRequestedAt(req.getRequestedAt());
        response.setAcceptedAt(req.getAcceptedAt());
        response.setSenderNote(req.getSenderNote());
        response.setCarrierNote(req.getCarrierNote());
        // ✅ Include photo URLs in response
        response.setPickupPhoto(req.getPickupPhoto());
        response.setDeliveryPhoto(req.getDeliveryPhoto());
        return response;
    }

    private PackageRequestSummary mapToPackageRequestSummary(DeliveryRequest req) {
        PackageRequestSummary summary = new PackageRequestSummary();
        summary.setRequestId(req.getRequestId());
        summary.setPackageId(req.getPackageEntity().getPackageId());
        summary.setPackageName(req.getPackageEntity().getProductName());
        summary.setProductImage(req.getPackageEntity().getProductImages() != null
                && !req.getPackageEntity().getProductImages().isEmpty()
                ? req.getPackageEntity().getProductImages().get(0) : null);
        summary.setSenderName(req.getSender().getFullName());
        summary.setFromAddress(req.getPackageEntity().getFromAddress());
        summary.setToAddress(req.getPackageEntity().getToAddress());
        summary.setRequestedAt(req.getRequestedAt());
        return summary;
    }

    private ActivePackageSummary mapToActivePackageSummary(DeliveryRequest req) {
        ActivePackageSummary summary = new ActivePackageSummary();
        summary.setRequestId(req.getRequestId());
        summary.setPackageId(req.getPackageEntity().getPackageId());
        summary.setPackageName(req.getPackageEntity().getProductName());
        summary.setProductImage(req.getPackageEntity().getProductImages() != null
                && !req.getPackageEntity().getProductImages().isEmpty()
                ? req.getPackageEntity().getProductImages().get(0) : null);
        summary.setStatus(req.getStatus());
        summary.setEarningAmount(req.getCarrierEarning());
        summary.setPlatformCommission(req.getPlatformCommission());
        summary.setPickupOtp(req.getPickupOtp());
        summary.setDeliveryOtp(req.getDeliveryOtp());
        // ✅ FIXED: Always include photo URLs
        summary.setPickupPhoto(req.getPickupPhoto());
        summary.setDeliveryPhoto(req.getDeliveryPhoto());
        // Sender info
        summary.setSenderName(req.getSender().getFullName());
        summary.setSenderPhone(req.getSender().getMobile());
        summary.setSenderProfileImage(req.getSender().getProfileUrl());
        // Address info
        summary.setPickupAddress(req.getPackageEntity().getFromAddress());
        summary.setDeliveryAddress(req.getPackageEntity().getToAddress());
        // ✅ Payment status on each package summary
        Payment payment = req.getPayment();
        if (payment != null) {
            summary.setPaymentStatus(payment.getPaymentStatus().toString());
            summary.setPaymentCompleted(payment.getPaymentStatus() == Payment.PaymentStatus.COMPLETED);
            summary.setPaymentMethod(payment.getPaymentMethod().toString());
            // ✅ Payout transfer status — frontend shows "Processing" / "Received" / "Failed"
            summary.setCarrierTransferStatus(
                    payment.getCarrierTransferStatus() != null
                            ? payment.getCarrierTransferStatus().toString()
                            : null);
        } else {
            summary.setPaymentStatus("NOT_PAID");
            summary.setPaymentCompleted(false);
            summary.setCarrierTransferStatus(null);
        }
        return summary;
    }

    private RequestSummary mapToRequestSummary(DeliveryRequest req) {
        RequestSummary summary = new RequestSummary();
        summary.setRequestId(req.getRequestId());
        summary.setPackageId(req.getPackageEntity().getPackageId());
        summary.setPackageName(req.getPackageEntity().getProductName());
        summary.setProductImage(req.getPackageEntity().getProductImages() != null
                && !req.getPackageEntity().getProductImages().isEmpty()
                ? req.getPackageEntity().getProductImages().get(0) : null);
        summary.setSenderName(req.getSender().getFullName());
        summary.setCarrierName(req.getCarrier().getFullName());
        summary.setFromAddress(req.getPackageEntity().getFromAddress());
        summary.setToAddress(req.getPackageEntity().getToAddress());
        summary.setAmount(req.getTotalAmount());
        summary.setRequestedAt(req.getRequestedAt());
        summary.setSenderNote(req.getSenderNote());
        summary.setCarrierNote(req.getCarrierNote());
        return summary;
    }

    private PackageSelectionResponse mapToPackageSelection(Package pkg, CarrierRoute carrierRoute) {
        PackageSelectionResponse response = new PackageSelectionResponse();
        response.setPackageId(pkg.getPackageId());
        response.setProductName(pkg.getProductName());
        response.setProductImage(pkg.getProductImages() != null && !pkg.getProductImages().isEmpty()
                ? pkg.getProductImages().get(0) : null);
        response.setProductDescription(pkg.getProductDescription());
        response.setFromAddress(pkg.getFromAddress());
        response.setToAddress(pkg.getToAddress());
        response.setPickUpDate(pkg.getPickUpDate());
        response.setDropDate(pkg.getDropDate());
        response.setWeight(pkg.getWeight());
        response.setProductValue(pkg.getProductValue());
        response.setProductType(pkg.getProductType());
        response.setTransportType(pkg.getTransportType());
        response.setAvailableTime(pkg.getAvailableTime().toString());
        response.setDeadlineTime(pkg.getDeadlineTime().toString());

        boolean transportMatches = pkg.getTransportType() == carrierRoute.getTransportType();
        response.setTransportTypeMatches(transportMatches);

        try {
            Double estimatedCost = calculateDeliveryAmount(pkg, carrierRoute);
            response.setEstimatedCost(estimatedCost);
            Double packageWeight = calculatePackageWeight(pkg);
            boolean weightOk = (carrierRoute.getCurrentWeight() + packageWeight) <= carrierRoute.getMaxWeight();
            boolean quantityOk = (carrierRoute.getCurrentQuantity() + 1) <= carrierRoute.getMaxQuantity();
            response.setCanRequest(transportMatches && weightOk && quantityOk);

            if (!transportMatches) response.setReasonCannotRequest("Transport type does not match route");
            else if (!weightOk) response.setReasonCannotRequest("Route weight capacity exceeded");
            else if (!quantityOk) response.setReasonCannotRequest("Route quantity capacity exceeded");
        } catch (Exception e) {
            response.setEstimatedCost(null);
            response.setCanRequest(false);
            response.setReasonCannotRequest(e.getMessage());
        }
        return response;
    }

    private RouteSelectionResponse mapToRouteSelection(CarrierRoute route, Package packageEntity) {
        RouteSelectionResponse response = new RouteSelectionResponse();
        response.setRouteId(route.getRouteId());
        response.setFromLocation(route.getFromLocation());
        response.setToLocation(route.getToLocation());
        response.setAvailableDate(route.getAvailableDate());
        response.setDeadlineDate(route.getDeadlineDate());
        response.setAvailableTime(route.getAvailableTime());
        response.setTransportType(route.getTransportType());
        response.setRouteStatus(route.getRouteStatus());
        response.setMaxWeight(route.getMaxWeight());
        response.setDeadlineTime(route.getDeadlineTime().toString());
        response.setCurrentWeight(route.getCurrentWeight());
        response.setAvailableWeight(route.getMaxWeight() - route.getCurrentWeight());
        response.setMaxQuantity(route.getMaxQuantity());
        response.setCurrentQuantity(route.getCurrentQuantity());
        response.setAvailableQuantity(route.getMaxQuantity() - route.getCurrentQuantity());

        boolean transportMatches = packageEntity.getTransportType() == route.getTransportType();
        response.setTransportTypeMatches(transportMatches);

        try {
            Double estimatedCost = calculateDeliveryAmount(packageEntity, route);
            Double platformCommission = estimatedCost * 0.15;
            Double carrierEarning = estimatedCost - platformCommission;
            response.setEstimatedCost(estimatedCost);
            response.setPlatformCommission(platformCommission);
            response.setCarrierEarning(carrierEarning);

            Double packageWeight = calculatePackageWeight(packageEntity);
            boolean weightOk = (route.getCurrentWeight() + packageWeight) <= route.getMaxWeight();
            boolean quantityOk = (route.getCurrentQuantity() + 1) <= route.getMaxQuantity();
            response.setCanRequest(transportMatches && weightOk && quantityOk);

            if (!transportMatches) response.setReasonCannotRequest("Transport type does not match package");
            else if (!weightOk) response.setReasonCannotRequest("Route weight capacity exceeded");
            else if (!quantityOk) response.setReasonCannotRequest("Route quantity capacity exceeded");
        } catch (Exception e) {
            response.setEstimatedCost(null);
            response.setCanRequest(false);
            response.setReasonCannotRequest(e.getMessage());
        }
        return response;
    }

    private void createNotification(User user, String title, String message,
                                    Notification.NotificationType type, Long referenceId, String recipientRole) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setReferenceId(referenceId);
        notification.setIsRead(false);
        notification.setType(type);
        notificationRepository.save(notification);

        String fcmToken = user.getFcmToken();
        if (fcmToken != null && !fcmToken.isEmpty()) {
            Map<String, String> data = Map.of(
                    "type", type.name(),
                    "referenceId", String.valueOf(referenceId != null ? referenceId : ""),
                    "recipientRole", recipientRole
            );
            firebaseNotificationService.sendNotificationWithData(fcmToken, title, message, data);
        }
    }

    private String getOrderStatusDisplay(DeliveryRequest.RequestStatus status) {
        switch (status) {
            case PENDING: return "Pending";
            case ACCEPTED: return "Accepted — Ready for Pickup";
            case PICKED_UP: return "Picked Up";
            case IN_TRANSIT: return "In Transit";
            case DELIVERED: return "Delivered";
            case REJECTED: return "Rejected";
            case CANCELLED: return "Cancelled";
            default: return "Unknown";
        }
    }

    private NextActionResponse getNextActionRequired(DeliveryRequest req) {
        NextActionResponse action = new NextActionResponse();
        if (req.getStatus() == DeliveryRequest.RequestStatus.ACCEPTED) {
            if (req.getPickupPhoto() == null || req.getPickupPhoto().isEmpty()) {
                action.setAction("UPLOAD_PICKUP_PHOTO");
                action.setDescription("Upload package pickup photo");
                action.setRequired(true);
            } else {
                action.setAction("VERIFY_PICKUP_OTP");
                action.setDescription("Enter pickup OTP from sender");
                action.setRequired(true);
            }
        } else if (req.getStatus() == DeliveryRequest.RequestStatus.PICKED_UP) {
            action.setAction("START_TRANSIT");
            action.setDescription("Mark package as in transit");
            action.setRequired(true);
        } else if (req.getStatus() == DeliveryRequest.RequestStatus.IN_TRANSIT) {
            if (req.getDeliveryPhoto() == null || req.getDeliveryPhoto().isEmpty()) {
                action.setAction("UPLOAD_DELIVERY_PHOTO");
                action.setDescription("Upload delivery photo");
                action.setRequired(true);
            } else {
                action.setAction("VERIFY_DELIVERY_OTP");
                action.setDescription("Enter delivery OTP from recipient");
                action.setRequired(true);
            }
        } else if (req.getStatus() == DeliveryRequest.RequestStatus.DELIVERED) {
            boolean paymentPending = req.getPayment() == null
                    || req.getPayment().getPaymentStatus() != Payment.PaymentStatus.COMPLETED;
            if (paymentPending) {
                action.setAction("PAYMENT_PENDING");
                action.setDescription("Payment not yet completed");
                action.setRequired(true);
            } else {
                action.setAction("ORDER_COMPLETED");
                action.setDescription("Order successfully completed");
                action.setRequired(false);
            }
        }
        return action;
    }

    // =====================================================================
    // INNER DTO CLASSES
    // =====================================================================

    @Data @NoArgsConstructor @AllArgsConstructor
    public class DeliveryProgressResponse {
        private Long requestId;
        private DeliveryRequest.RequestStatus status;
        private Long packageId;
        private String packageName;
        private Long routeId;
        private Double totalAmount;
        private Double platformCommission;
        private Double carrierEarning;
        private SenderDetailsResponse senderDetails;
        private CarrierDetailsResponse carrierDetails;
        private AddressDetailsResponse addressDetails;
        private DocumentsProgressResponse documentsProgress;
        private OtpProgressResponse otpProgress;
        private List<DeliveryStep> progressSteps;
        private int progressPercentage;
        private int totalSteps;
        private int completedSteps;
        private String orderStatus;
        private boolean orderCompleted;
        // ✅ Payment fields
        private String paymentStatus;
        private boolean paymentCompleted;
        private String paymentMethod;
        private boolean commissionPaid;
        private LocalDateTime requestedAt;
        private LocalDateTime acceptedAt;
        private LocalDateTime pickedUpAt;
        private LocalDateTime deliveredAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String senderNote;
        private String carrierNote;
        private NextActionResponse nextAction;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public class NextActionResponse {
        private String action;
        private String description;
        private boolean isRequired;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public class SenderDetailsResponse {
        private String name;
        private String phone;
        private String profileImage;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public class CarrierDetailsResponse {
        private String name;
        private String phone;
        private String profileImage;
        private String vehicleType;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public class AddressDetailsResponse {
        private String pickupAddress;
        private String deliveryAddress;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public class DocumentsProgressResponse {
        private boolean pickupPhotoUploaded;
        private String pickupPhotoUrl;
        private boolean deliveryPhotoUploaded;
        private String deliveryPhotoUrl;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public class OtpProgressResponse {
        private boolean pickupOtpRequired;
        private boolean pickupOtpVerified;
        private String pickupOtp;
        private boolean deliveryOtpRequired;
        private boolean deliveryOtpVerified;
        private String deliveryOtp;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public class DeliveryStep {
        private int stepNumber;
        private String stepName;
        private String description;
        private boolean completed;
        private LocalDateTime completedAt;
    }
    private double calculateDistanceToRoute(
            double routeLat1, double routeLng1,
            double routeLat2, double routeLng2,
            double pointLat, double pointLng) {

        double R = 6371.0;

        // Distance from route START to the point
        double distToStart = calculateHaversineDistance(routeLat1, routeLng1, pointLat, pointLng);
        // Distance from route END to the point
        double distToEnd = calculateHaversineDistance(routeLat2, routeLng2, pointLat, pointLng);

        double d13 = distToStart / R;
        double brng13 = Math.toRadians(calculateBearing(routeLat1, routeLng1, pointLat, pointLng));
        double brng12 = Math.toRadians(calculateBearing(routeLat1, routeLng1, routeLat2, routeLng2));

        // Cross-track (perpendicular) distance to infinite line
        double crossTrackRad = Math.asin(Math.sin(d13) * Math.sin(brng13 - brng12));
        double crossTrack = Math.abs(crossTrackRad) * R;

        // Along-track distance — how far along the route the closest point falls
        double alongTrack = Math.acos(
                Math.max(-1.0, Math.min(1.0, Math.cos(d13) / Math.cos(crossTrackRad)))
        ) * R;

        double routeLength = calculateHaversineDistance(routeLat1, routeLng1, routeLat2, routeLng2);

        // If closest point falls OUTSIDE the segment → use nearest endpoint distance
        if (alongTrack < 0 || alongTrack > routeLength) {
            return Math.min(distToStart, distToEnd);
        }

        return crossTrack;
    }
    private double calculateHaversineDistance(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double calculateBearing(double lat1, double lng1, double lat2, double lng2) {
        double dLng = Math.toRadians(lng2 - lng1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double y = Math.sin(dLng) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad)
                - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLng);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }
}