package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Dtos.*;
import com.saffaricarrers.saffaricarrers.Entity.*;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import com.saffaricarrers.saffaricarrers.Repository.*;
import com.saffaricarrers.saffaricarrers.Responses.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final CarrierProfileRepository carrierProfileRepository;
    private final PackageRepository packageRepository;
    private final DeliveryRequestRepository deliveryRequestRepository;
    private final PaymentRepository paymentRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final AddressRepository addressRepository;
    private final CarrierVehicleVerificationRepository carrierVehicleVerificationRepository;
    private final CarrierRouteRepository carrierRouteRepository;
    private final DocumentVerificationStatusRepository documentStatusRepository;

    // ==================== MAIN DASHBOARD STATS ====================

    public AdminDashboardResponse getDashboardStats() {
        AdminDashboardResponse response = new AdminDashboardResponse();
        response.setUserStats(getUserStats());
        response.setVerificationStats(getVerificationStats());
        response.setPackageStats(getPackageStats());
        response.setDeliveryStats(getDeliveryStats());
        response.setCommissionStats(getCommissionStats());
        response.setTodayOverview(getTodayOverview());
        response.setGeneratedAt(LocalDateTime.now());
        return response;
    }

    // ==================== USER STATISTICS ====================

    public UserStatsDto getUserStats() {
        long total    = userRepository.count();
        long verified = userRepository.countByVerified(true);
        long active   = userRepository.countByStatus(User.UserStatus.ACTIVE);
        long senders  = userRepository.countByUserType(User.UserType.SENDER);
        long carriers = userRepository.countByUserType(User.UserType.CARRIER);
        long both     = userRepository.countByUserType(User.UserType.BOTH);

        Map<String, Long> genderBreakdown = userRepository.countGroupByGender()
                .stream()
                .collect(Collectors.toMap(
                        row -> row[0] != null ? row[0].toString() : "UNKNOWN",
                        row -> (Long) row[1]
                ));

        UserStatsDto stats = new UserStatsDto();
        stats.setTotalUsers(total);
        stats.setVerifiedUsers(verified);
        stats.setUnverifiedUsers(total - verified);
        stats.setActiveUsers(active);
        stats.setInactiveUsers(total - active);
        stats.setSenderUsers(senders);
        stats.setCarrierUsers(carriers);
        stats.setBothUsers(both);
        stats.setGenderBreakdown(genderBreakdown);
        return stats;
    }

    // ==================== VERIFICATION STATISTICS ====================

    public VerificationStatsDto getVerificationStats() {
        long total    = documentStatusRepository.count();
        long verified = documentStatusRepository.countByStatus(
                DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_VERIFIED);
        long pending  = documentStatusRepository.countByStatus(
                DocumentVerificationStatus.DocumentVerificationStatusEnum.NOT_STARTED);
        long rejected = documentStatusRepository.countByStatus(
                DocumentVerificationStatus.DocumentVerificationStatusEnum.DOCUMENT_REJECTED);
        long aadhaar  = documentStatusRepository.countByDocumentType(OtpVerification.DocumentType.AADHAAR);
        long pan      = documentStatusRepository.countByDocumentType(OtpVerification.DocumentType.PAN);

        VerificationStatsDto stats = new VerificationStatsDto();
        stats.setTotalVerifications(total);
        stats.setVerifiedDocuments(verified);
        stats.setPendingVerifications(pending);
        stats.setRejectedDocuments(rejected);
        stats.setAadhaarVerifications(aadhaar);
        stats.setPanVerifications(pan);
        stats.setUsersStuckAtVerification(getUsersStuckAtVerificationStages());
        return stats;
    }

    private Map<String, Long> getUsersStuckAtVerificationStages() {
        Map<String, Long> stuck = new HashMap<>();
        stuck.put("PENDING_VERIFICATION",
                userRepository.countByVerificationStatus(User.VerificationStatus.PENDING));
        stuck.put("REJECTED",
                userRepository.countByVerificationStatus(User.VerificationStatus.REJECTED));
        stuck.put("VERIFIED_INCOMPLETE_PROFILE",
                userRepository.countVerifiedWithIncompleteProfile());
        return stuck;
    }

    // ==================== PACKAGE STATISTICS ====================

    public PackageStatsDto1 getPackageStats() {
        long total = packageRepository.count();

        Map<String, Long> statusCounts = packageRepository.countGroupByStatus()
                .stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1]
                ));

        Map<String, Long> productTypeCounts = packageRepository.countGroupByProductType()
                .stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1]
                ));

        Map<String, Long> transportTypeCounts = packageRepository.countGroupByTransportType()
                .stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1]
                ));

        long insured    = packageRepository.countByInsurance(true);
        Double totalVal = packageRepository.sumProductValue();

        PackageStatsDto1 stats = new PackageStatsDto1();
        stats.setTotalPackages(total);
        stats.setCreatedPackages(statusCounts.getOrDefault("CREATED", 0L));
        stats.setRequestSentPackages(statusCounts.getOrDefault("REQUEST_SENT", 0L));
        stats.setMatchedPackages(statusCounts.getOrDefault("MATCHED", 0L));
        stats.setPickedUpPackages(statusCounts.getOrDefault("PICKED_UP", 0L));
        stats.setInTransitPackages(statusCounts.getOrDefault("IN_TRANSIT", 0L));
        stats.setDeliveredPackages(statusCounts.getOrDefault("DELIVERED", 0L));
        stats.setCancelledPackages(statusCounts.getOrDefault("CANCELLED", 0L));
        stats.setProductTypeBreakdown(productTypeCounts);
        stats.setTransportTypeBreakdown(transportTypeCounts);
        stats.setInsuredPackages(insured);
        stats.setUninsuredPackages(total - insured);
        stats.setTotalPackageValue(totalVal != null ? totalVal : 0.0);
        return stats;
    }

    // ==================== DELIVERY REQUEST STATISTICS ====================

    public DeliveryStatsDto getDeliveryStats() {
        DeliveryStatsDto stats = new DeliveryStatsDto();
        stats.setTotalRequests(deliveryRequestRepository.count());
        stats.setPendingRequests(deliveryRequestRepository.countByStatus(DeliveryRequest.RequestStatus.PENDING));
        stats.setAcceptedRequests(deliveryRequestRepository.countByStatus(DeliveryRequest.RequestStatus.ACCEPTED));
        stats.setPickedUpRequests(deliveryRequestRepository.countByStatus(DeliveryRequest.RequestStatus.PICKED_UP));
        stats.setInTransitRequests(deliveryRequestRepository.countByStatus(DeliveryRequest.RequestStatus.IN_TRANSIT));
        stats.setDeliveredRequests(deliveryRequestRepository.countByStatus(DeliveryRequest.RequestStatus.DELIVERED));
        stats.setRejectedRequests(deliveryRequestRepository.countByStatus(DeliveryRequest.RequestStatus.REJECTED));
        stats.setCancelledRequests(deliveryRequestRepository.countByStatus(DeliveryRequest.RequestStatus.CANCELLED));

        double successRate = stats.getTotalRequests() > 0
                ? (stats.getDeliveredRequests() * 100.0 / stats.getTotalRequests()) : 0.0;
        stats.setDeliverySuccessRate(Math.round(successRate * 100.0) / 100.0);

        Double avg = deliveryRequestRepository.getAverageTotalAmount();
        stats.setAverageDeliveryAmount(avg != null ? avg : 0.0);
        return stats;
    }

    // ==================== COMMISSION STATISTICS ====================

    public CommissionStatsDto getCommissionStats() {
        Double pendingCommission = carrierProfileRepository.sumPendingCommission();
        Double totalEarnings     = carrierProfileRepository.sumTotalEarnings();

        long carriersWithPending  = carrierProfileRepository.countWithPendingCommission();
        long activeCarriers       = carrierProfileRepository.countByStatus(CarrierProfile.CarrierStatus.ACTIVE);
        long inactiveCarriers     = carrierProfileRepository.countByStatus(CarrierProfile.CarrierStatus.INACTIVE);
        long suspendedCarriers    = carrierProfileRepository.countByStatus(CarrierProfile.CarrierStatus.SUSPENDED);

        double dailyCommission = getCommissionForDate(LocalDate.now());

        CommissionStatsDto stats = new CommissionStatsDto();
        stats.setTotalPendingCommission(pendingCommission != null ? pendingCommission : 0.0);
        stats.setTotalCarrierEarnings(totalEarnings != null ? totalEarnings : 0.0);
        stats.setCarriersWithPendingCommission(carriersWithPending);
        stats.setDailyCommission(dailyCommission);
        stats.setActiveCarriers(activeCarriers);
        stats.setInactiveCarriers(inactiveCarriers);
        stats.setSuspendedCarriers(suspendedCarriers);
        return stats;
    }

    // ==================== TODAY'S OVERVIEW ====================

    public TodayOverviewDto getTodayOverview() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay   = today.atTime(23, 59, 59);

        TodayOverviewDto overview = new TodayOverviewDto();
        overview.setPackagesCreatedToday(
                packageRepository.countByCreatedAtBetween(startOfDay, endOfDay));
        overview.setRequestsCreatedToday(
                deliveryRequestRepository.countByRequestedAtBetween(startOfDay, endOfDay));
        overview.setDeliveriesCompletedToday(
                deliveryRequestRepository.countByStatusAndDeliveredAtBetween(
                        DeliveryRequest.RequestStatus.DELIVERED, startOfDay, endOfDay));
        overview.setRevenueToday(
                paymentRepository.sumTotalAmountByStatusAndCompletedBetween(
                        Payment.PaymentStatus.COMPLETED, startOfDay, endOfDay));
        overview.setCommissionToday(getCommissionForDate(today));
        overview.setNewUsersToday(
                userRepository.countByCreatedAtBetween(startOfDay, endOfDay));
        overview.setActiveTripsToday(
                carrierRouteRepository.countByRouteStatusAndAvailableDate(
                        CarrierRoute.RouteStatus.ACTIVE, today));
        return overview;
    }

    // ==================== DAY-WISE ANALYTICS ====================

    public DayWiseAnalyticsResponse getDayWiseAnalytics(int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1);
        LocalDate to   = LocalDate.now();
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.atTime(23, 59, 59);

        List<Object[]> packagesByDay   = packageRepository.countGroupByCreatedDate(fromDt, toDt);
        List<Object[]> deliveriesByDay = deliveryRequestRepository.countDeliveredGroupByDate(fromDt, toDt);
        List<Object[]> revenueByDay    = paymentRepository.sumRevenueGroupByDate(fromDt, toDt);
        List<Object[]> commissionByDay = paymentRepository.sumCommissionGroupByDate(fromDt, toDt);
        List<Object[]> usersByDay      = userRepository.countGroupByCreatedDate(fromDt, toDt);
        List<Object[]> requestsByDay   = deliveryRequestRepository.countGroupByRequestedDate(fromDt, toDt);

        Map<LocalDate, Long>   pkgMap  = toDateLongMap(packagesByDay);
        Map<LocalDate, Long>   delMap  = toDateLongMap(deliveriesByDay);
        Map<LocalDate, Double> revMap  = toDateDoubleMap(revenueByDay);
        Map<LocalDate, Double> comMap  = toDateDoubleMap(commissionByDay);
        Map<LocalDate, Long>   usrMap  = toDateLongMap(usersByDay);
        Map<LocalDate, Long>   reqMap  = toDateLongMap(requestsByDay);

        List<DayAnalyticsDto> dayAnalytics = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            DayAnalyticsDto d = new DayAnalyticsDto();
            d.setDate(date);
            d.setPackagesCreated(pkgMap.getOrDefault(date, 0L));
            d.setDeliveriesCompleted(delMap.getOrDefault(date, 0L));
            d.setRevenue(revMap.getOrDefault(date, 0.0));
            d.setCommission(comMap.getOrDefault(date, 0.0));
            d.setNewUsers(usrMap.getOrDefault(date, 0L));
            d.setRequestsCreated(reqMap.getOrDefault(date, 0L));
            dayAnalytics.add(d);
        }

        DayWiseAnalyticsResponse response = new DayWiseAnalyticsResponse();
        response.setDayWiseData(dayAnalytics);
        response.setGeneratedAt(LocalDateTime.now());
        return response;
    }

    // ==================== DETAILED LISTS ====================

    public PendingDeliveriesResponse getPendingDeliveries() {
        List<DeliveryRequest> pendingRequests = deliveryRequestRepository
                .findAllByStatusWithDetails(DeliveryRequest.RequestStatus.PENDING);

        List<DeliveryRequestDetailDto> details = pendingRequests.stream()
                .map(this::mapToDeliveryDetail)
                .collect(Collectors.toList());

        return new PendingDeliveriesResponse((long) details.size(), details);
    }

    public CompletedDeliveriesResponse getCompletedDeliveries() {
        List<DeliveryRequest> completedRequests = deliveryRequestRepository
                .findAllByStatusWithDetails(DeliveryRequest.RequestStatus.DELIVERED);

        List<DeliveryRequestDetailDto> details = completedRequests.stream()
                .map(this::mapToDeliveryDetail)
                .collect(Collectors.toList());

        return new CompletedDeliveriesResponse((long) details.size(), details);
    }

    public UserListResponse getUnverifiedUsers() {
        List<User> users = userRepository.findByVerified(false);
        List<UserDetailDto> details = users.stream().map(this::mapToUserDetail).collect(Collectors.toList());
        return new UserListResponse((long) details.size(), details);
    }

    public UserListResponse getVerifiedUsers() {
        List<User> users = userRepository.findByVerified(true);
        List<UserDetailDto> details = users.stream().map(this::mapToUserDetail).collect(Collectors.toList());
        return new UserListResponse((long) details.size(), details);
    }

    // ==================== ORDERS WITH PAYMENTS ====================
    // ✅ NEW: Returns every delivery request joined with its payment for the admin orders table.
    //         Supports filtering by delivery status AND by payment/commission status.

    public List<OrderSummaryDto> getAllOrdersWithPayments(String status, int size) {
        // Fetch delivery requests (with sender, carrier, package eagerly loaded)
        List<DeliveryRequest> requests;

        if ("all".equalsIgnoreCase(status)) {
            requests = deliveryRequestRepository
                    .findAllWithDetailsOrderByRequestedAtDesc(PageRequest.of(0, size));
        } else {
            // Support both delivery-status filter (e.g. "DELIVERED") and
            // payment-side filters ("COMMISSION_PENDING", "TRANSFER_PENDING")
            switch (status.toUpperCase()) {
                case "COMMISSION_PENDING":
                    // COD orders where the carrier has not yet paid commission back
                    requests = deliveryRequestRepository
                            .findAllWithDetailsOrderByRequestedAtDesc(PageRequest.of(0, size))
                            .stream()
                            .filter(r -> r.getPayment() != null
                                    && r.getPayment().getPaymentMethod() == Payment.PaymentMethod.COD
                                    && (r.getPayment().getCommissionPaid() == null
                                    || !r.getPayment().getCommissionPaid()))
                            .collect(Collectors.toList());
                    break;

                case "TRANSFER_PENDING":
                    requests = deliveryRequestRepository
                            .findAllWithDetailsOrderByRequestedAtDesc(PageRequest.of(0, size))
                            .stream()
                            .filter(r -> r.getPayment() != null
                                    && r.getPayment().getCarrierTransferStatus() == Payment.TransferStatus.PENDING)
                            .collect(Collectors.toList());
                    break;

                default:
                    // Treat as DeliveryRequest.RequestStatus
                    try {
                        DeliveryRequest.RequestStatus rs =
                                DeliveryRequest.RequestStatus.valueOf(status.toUpperCase());
                        requests = deliveryRequestRepository
                                .findAllByStatusWithDetailsOrderByRequestedAtDesc(rs, PageRequest.of(0, size));
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown status filter '{}', returning all orders", status);
                        requests = deliveryRequestRepository
                                .findAllWithDetailsOrderByRequestedAtDesc(PageRequest.of(0, size));
                    }
            }
        }

        return requests.stream()
                .map(this::mapToOrderSummary)
                .collect(Collectors.toList());
    }

    // ==================== COMPLETE RIDE HISTORY DETAIL ====================

    /**
     * Admin-only read model for a single delivery/ride. This intentionally aggregates
     * the data already collected by the existing sender/carrier flow: parties,
     * addresses, package images, pickup/delivery proof, OTPs, timestamps, payment,
     * route/vehicle information and the full GPS breadcrumb history.
     */
    public Map<String, Object> getRideHistoryDetail(Long requestId) {
        DeliveryRequest req = deliveryRequestRepository.findByRequestIdWithDetails(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery request not found: " + requestId));

        Package pkg = req.getPackageEntity();
        User sender = req.getSender();
        User carrier = req.getCarrier();
        CarrierRoute route = req.getCarrierRoute();
        Payment payment = req.getPayment();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requestId", req.getRequestId());
        out.put("requestType", req.getRequestType());
        out.put("isRiderDelivery", req.getIsRiderDelivery());
        out.put("status", req.getStatus());
        out.put("requestedAt", req.getRequestedAt());
        out.put("acceptedAt", req.getAcceptedAt());
        out.put("pickedUpAt", req.getPickedUpAt());
        out.put("deliveredAt", req.getDeliveredAt());
        out.put("createdAt", req.getCreatedAt());
        out.put("updatedAt", req.getUpdatedAt());
        out.put("senderNote", req.getSenderNote());
        out.put("carrierNote", req.getCarrierNote());

        Map<String, Object> senderMap = personMap(sender);
        Map<String, Object> carrierMap = personMap(carrier);
        if (sender != null) {
            senderMap.put("profileUrl", sender.getProfileUrl());
            senderMap.put("aadharFrontUrl", sender.getAadharFrontUrl());
            senderMap.put("aadharBackUrl", sender.getAadharBackUrl());
            senderMap.put("panCardUrl", sender.getPanCardUrl());
        }
        if (carrier != null) {
            carrierMap.put("profileUrl", carrier.getProfileUrl());
            carrierMap.put("aadharFrontUrl", carrier.getAadharFrontUrl());
            carrierMap.put("aadharBackUrl", carrier.getAadharBackUrl());
            carrierMap.put("panCardUrl", carrier.getPanCardUrl());
        }
        out.put("sender", senderMap);
        out.put("carrier", carrierMap);
        out.put("senderAddresses", sender != null && sender.getAddresses() != null
                ? sender.getAddresses().stream().map(this::addressMap).collect(Collectors.toList()) : List.of());
        out.put("carrierAddresses", carrier != null && carrier.getAddresses() != null
                ? carrier.getAddresses().stream().map(this::addressMap).collect(Collectors.toList()) : List.of());

        if (pkg != null) {
            Map<String, Object> packageMap = new LinkedHashMap<>();
            packageMap.put("packageId", pkg.getPackageId());
            packageMap.put("productName", pkg.getProductName());
            packageMap.put("productDescription", pkg.getProductDescription());
            packageMap.put("productValue", pkg.getProductValue());
            packageMap.put("productType", pkg.getProductType());
            packageMap.put("transportType", pkg.getTransportType());
            packageMap.put("weight", pkg.getWeight());
            packageMap.put("length", pkg.getLength());
            packageMap.put("width", pkg.getWidth());
            packageMap.put("height", pkg.getHeight());
            packageMap.put("fromAddress", pkg.getFromAddress());
            packageMap.put("toAddress", pkg.getToAddress());
            packageMap.put("addressId", pkg.getAddressId());
            packageMap.put("latitude", pkg.getLatitude());
            packageMap.put("longitude", pkg.getLongitude());
            packageMap.put("toLatitude", pkg.getToLatitude());
            packageMap.put("toLongitude", pkg.getToLongitude());
            packageMap.put("pickUpDate", pkg.getPickUpDate());
            packageMap.put("dropDate", pkg.getDropDate());
            packageMap.put("availableTime", pkg.getAvailableTime());
            packageMap.put("deadlineTime", pkg.getDeadlineTime());
            packageMap.put("tripCharge", pkg.getTripCharge());
            packageMap.put("pricePerKg", pkg.getPricePerKg());
            packageMap.put("pricePerTon", pkg.getPricePerTon());
            packageMap.put("insurance", pkg.getInsurance());
            packageMap.put("pickupOtp", pkg.getPickupOtp());
            packageMap.put("deliveryOtp", pkg.getDeliveryOtp());
            packageMap.put("status", pkg.getStatus());
            packageMap.put("productImages", pkg.getProductImages() != null ? new ArrayList<>(pkg.getProductImages()) : List.of());
            packageMap.put("invoiceImage", pkg.getProductInvoiceImage());
            if (pkg.getInsuranceDetails() != null) {
                Insurance insurance = pkg.getInsuranceDetails();
                Map<String, Object> insuranceMap = new LinkedHashMap<>();
                insuranceMap.put("insuranceId", insurance.getInsuranceId());
                insuranceMap.put("productValue", insurance.getProductValue());
                insuranceMap.put("insuranceAmount", insurance.getInsuranceAmount());
                insuranceMap.put("coveragePercentage", insurance.getCoveragePercentage());
                insuranceMap.put("status", insurance.getStatus());
                insuranceMap.put("policyNumber", insurance.getPolicyNumber());
                insuranceMap.put("validUntil", insurance.getValidUntil());
                insuranceMap.put("createdAt", insurance.getCreatedAt());
                packageMap.put("insuranceDetails", insuranceMap);
            }
            out.put("package", packageMap);

            if (pkg.getAddressId() != null) {
                addressRepository.findById(pkg.getAddressId()).ifPresent(a -> out.put("senderAddress", addressMap(a)));
            }
        }

        if (route != null) {
            Map<String, Object> routeMap = new LinkedHashMap<>();
            routeMap.put("routeId", route.getRouteId());
            routeMap.put("fromLocation", route.getFromLocation());
            routeMap.put("toLocation", route.getToLocation());
            routeMap.put("longAddressId", route.getLongAddressId());
            routeMap.put("availableDate", route.getAvailableDate());
            routeMap.put("deadlineDate", route.getDeadlineDate());
            routeMap.put("availableTime", route.getAvailableTime());
            routeMap.put("deadlineTime", route.getDeadlineTime());
            routeMap.put("transportType", route.getTransportType());
            routeMap.put("maxWeight", route.getMaxWeight());
            routeMap.put("maxQuantity", route.getMaxQuantity());
            routeMap.put("currentWeight", route.getCurrentWeight());
            routeMap.put("currentQuantity", route.getCurrentQuantity());
            routeMap.put("routeStatus", route.getRouteStatus());
            routeMap.put("latitude", route.getLatitude());
            routeMap.put("longitude", route.getLongitude());
            routeMap.put("toLatitude", route.getToLatitude());
            routeMap.put("toLongitude", route.getToLongitude());
            routeMap.put("isDirectRoute", route.getIsDirectRoute());
            routeMap.put("intermediateStops", route.getIntermediateStops());
            out.put("route", routeMap);
            if (route.getLongAddressId() != null) {
                addressRepository.findById(route.getLongAddressId()).ifPresent(a -> out.put("carrierAddress", addressMap(a)));
            }
        }

        Map<String, Object> otp = new LinkedHashMap<>();
        otp.put("pickupOtp", req.getPickupOtp() != null ? req.getPickupOtp() : pkg != null ? pkg.getPickupOtp() : null);
        otp.put("deliveryOtp", req.getDeliveryOtp() != null ? req.getDeliveryOtp() : pkg != null ? pkg.getDeliveryOtp() : null);
        otp.put("pickupVerified", req.getPickedUpAt() != null);
        otp.put("deliveryVerified", req.getDeliveredAt() != null);
        out.put("otps", otp);

        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("pickupPhoto", req.getPickupPhoto());
        proof.put("deliveryPhoto", req.getDeliveryPhoto());
        out.put("proof", proof);

        if (carrier != null) {
            carrierProfileRepository.findByUser(carrier).ifPresent(profile -> {
                Map<String, Object> cp = new LinkedHashMap<>();
                cp.put("carrierId", profile.getCarrierId());
                cp.put("userUid", profile.getUserUid());
                cp.put("isVerified", profile.getIsVerified());
                cp.put("status", profile.getStatus());
                cp.put("isOnline", profile.getIsOnline());
                cp.put("searchRadiusKm", profile.getSearchRadiusKm());
                cp.put("totalEarnings", profile.getTotalEarnings());
                cp.put("pendingCommission", profile.getPendingCommission());
                cp.put("lastLat", profile.getLastLat());
                cp.put("lastLng", profile.getLastLng());
                cp.put("lastLocationAt", profile.getLastLocationAt());
                cp.put("createdAt", profile.getCreatedAt());
                cp.put("updatedAt", profile.getUpdatedAt());
                if (profile.getBankDetails() != null) {
                    BankDetails b = profile.getBankDetails();
                    Map<String, Object> bank = new LinkedHashMap<>();
                    bank.put("accountHolderName", b.getAccountHolderName());
                    bank.put("accountNumber", b.getAccountNumber());
                    bank.put("maskedAccountNumber", b.getMaskedAccountNumber());
                    bank.put("ifscCode", b.getIfscCode());
                    bank.put("bankName", b.getBankName());
                    bank.put("branchName", b.getBranchName());
                    bank.put("accountType", b.getAccountType());
                    bank.put("upiId", b.getUpiId());
                    bank.put("isVerified", b.getIsVerified());
                    bank.put("verificationStatus", b.getVerificationStatus());
                    bank.put("verifiedAt", b.getVerifiedAt());
                    cp.put("bankDetails", bank);
                }
                carrierVehicleVerificationRepository.findByUid(carrier.getUserId()).ifPresent(v -> {
                    Map<String, Object> vehicle = new LinkedHashMap<>();
                    vehicle.put("rcNumber", v.getRcNumber());
                    vehicle.put("rcStatus", v.getRcStatus());
                    vehicle.put("rcVerifiedOwnerName", v.getRcVerifiedOwnerName());
                    vehicle.put("rcVehicleNumber", v.getRcVehicleNumber());
                    vehicle.put("rcVehicleClass", v.getRcVehicleClass());
                    vehicle.put("rcVerifiedAt", v.getRcVerifiedAt());
                    vehicle.put("dlNumber", v.getDlNumber());
                    vehicle.put("dlStatus", v.getDlStatus());
                    vehicle.put("dlVerifiedName", v.getDlVerifiedName());
                    vehicle.put("dlDob", v.getDlDob());
                    vehicle.put("dlValidityFrom", v.getDlValidityFrom());
                    vehicle.put("dlValidityTo", v.getDlValidityTo());
                    vehicle.put("dlVehicleClasses", v.getDlVehicleClasses());
                    vehicle.put("aadhaarVerifiedName", v.getAadhaarVerifiedName());
                    vehicle.put("overallStatus", v.getOverallStatus());
                    cp.put("vehicleVerification", vehicle);
                });
                out.put("carrierProfile", cp);
            });
        }

        if (payment != null) {
            Map<String, Object> pay = new LinkedHashMap<>();
            pay.put("paymentId", payment.getPaymentId());
            pay.put("totalAmount", payment.getTotalAmount());
            pay.put("deliveryCharge", payment.getDeliveryCharge());
            pay.put("insuranceAmount", payment.getInsuranceAmount());
            pay.put("platformCommission", payment.getPlatformCommission());
            pay.put("carrierAmount", payment.getCarrierAmount());
            pay.put("platformFee", payment.getPlatformFee());
            pay.put("paymentMethod", payment.getPaymentMethod());
            pay.put("paymentStatus", payment.getPaymentStatus());
            pay.put("paymentCompletedAt", payment.getPaymentCompletedAt());
            pay.put("razorpayOrderId", payment.getRazorpayOrderId());
            pay.put("razorpayPaymentId", payment.getRazorpayPaymentId());
            pay.put("razorpayPayoutId", payment.getRazorpayPayoutId());
            pay.put("carrierTransferStatus", payment.getCarrierTransferStatus());
            pay.put("carrierTransferInitiatedAt", payment.getCarrierTransferInitiatedAt());
            pay.put("carrierTransferCompletedAt", payment.getCarrierTransferCompletedAt());
            pay.put("transferFailureReason", payment.getTransferFailureReason());
            pay.put("commissionPaid", payment.getCommissionPaid());
            pay.put("commissionPaidAt", payment.getCommissionPaidAt());
            pay.put("commissionPaymentId", payment.getCommissionPaymentId());
            pay.put("offlinePaymentNote", payment.getOfflinePaymentNote());
            pay.put("completedBy", payment.getCompletedBy());
            out.put("payment", pay);
        }

        List<Map<String, Object>> tracking = locationTrackingRepository
                .findByDeliveryRequestOrderByRecordedAtAsc(req)
                .stream().map(t -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("latitude", t.getLatitude());
                    point.put("longitude", t.getLongitude());
                    point.put("resolvedAddress", t.getResolvedAddress());
                    point.put("speed", t.getSpeed());
                    point.put("batteryLevel", t.getBatteryLevel());
                    point.put("recordedAt", t.getRecordedAt());
                    return point;
                }).collect(Collectors.toList());
        out.put("tracking", tracking);
        out.put("trackingCount", tracking.size());
        if (!tracking.isEmpty()) out.put("latestLocation", tracking.get(tracking.size() - 1));

        return out;
    }

    private Map<String, Object> personMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (u == null) return m;
        m.put("userId", u.getUserId());
        m.put("fullName", u.getFullName());
        m.put("email", u.getEmail());
        m.put("mobile", u.getMobile());
        m.put("age", u.getAge());
        m.put("gender", u.getGender());
        m.put("userType", u.getUserType());
        m.put("verified", u.getVerified());
        m.put("verificationStatus", u.getVerificationStatus());
        m.put("status", u.getStatus());
        m.put("createdAt", u.getCreatedAt());
        return m;
    }

    private Map<String, Object> addressMap(Address a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("addressId", a.getAddressId());
        m.put("addressType", a.getAddressType());
        m.put("fullName", a.getFullName());
        m.put("address", a.getAddress());
        m.put("city", a.getCity());
        m.put("state", a.getState());
        m.put("country", a.getCountry());
        m.put("pincode", a.getPincode());
        m.put("mobile", a.getMobile());
        m.put("isDefault", a.getDefault());
        m.put("latitude", a.getLatitude());
        m.put("longitude", a.getLongitude());
        m.put("createdAt", a.getCreatedAt());
        return m;
    }

    // ==================== MARK COMMISSION PAID (Admin manual action) ====================
    // ✅ NEW: Admin manually marks a COD commission as paid after collecting from the carrier.

    @Transactional
    public void markCommissionPaid(Long paymentId, String adminNote) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        if (payment.getPaymentMethod() != Payment.PaymentMethod.COD) {
            throw new IllegalStateException(
                    "Commission 'mark paid' is only applicable to COD orders. " +
                            "Online orders deduct commission automatically.");
        }

        if (Boolean.TRUE.equals(payment.getCommissionPaid())) {
            throw new IllegalStateException("Commission already marked as paid for payment: " + paymentId);
        }

        payment.setCommissionPaid(true);
        payment.setCommissionPaidAt(LocalDateTime.now());

        // Also reduce the pending commission on the carrier's profile
        DeliveryRequest req = payment.getDeliveryRequest();
        if (req != null && req.getCarrier() != null) {
            carrierProfileRepository.findByUser(req.getCarrier()).ifPresent(profile -> {
                BigDecimal pending = profile.getPendingCommission() != null
                        ? profile.getPendingCommission() : BigDecimal.ZERO;
                BigDecimal commission = payment.getPlatformCommission() != null
                        ? BigDecimal.valueOf(payment.getPlatformCommission()) : BigDecimal.ZERO;
                BigDecimal newPending = pending.subtract(commission);
                profile.setPendingCommission(newPending.compareTo(BigDecimal.ZERO) < 0
                        ? BigDecimal.ZERO : newPending);
                carrierProfileRepository.save(profile);
                log.info("✅ Reduced pending commission for carrier {} by ₹{}",
                        req.getCarrier().getUserId(), commission);
            });
        }

        paymentRepository.save(payment);
        log.info("✅ Admin marked commission paid | paymentId={} | note={}", paymentId, adminNote);
    }

    // ==================== PRIVATE HELPERS ====================

    private OrderSummaryDto mapToOrderSummary(DeliveryRequest req) {
        Payment p = req.getPayment();
        Package pkg = req.getPackageEntity();
        User sender  = req.getSender();
        User carrier = req.getCarrier();

        return OrderSummaryDto.builder()
                .requestId(req.getRequestId())
                .packageName(pkg != null ? pkg.getProductName() : "N/A")
                .fromAddress(pkg != null ? pkg.getFromAddress() : "N/A")
                .toAddress(pkg  != null ? pkg.getToAddress()   : "N/A")
                .senderName(sender  != null ? sender.getFullName()  : "N/A")
                .senderPhone(sender != null ? sender.getMobile()    : "N/A")
                .carrierName(carrier  != null ? carrier.getFullName()  : "N/A")
                .carrierPhone(carrier != null ? carrier.getMobile()    : "N/A")
                .status(req.getStatus())
                .requestedAt(req.getRequestedAt())
                .deliveredAt(req.getDeliveredAt())
                // ── Payment fields (null-safe) ──────────────────────────────
                .paymentId(p != null ? p.getPaymentId() : null)
                .totalAmount(p != null && p.getTotalAmount() != null ? p.getTotalAmount() : 0.0)
                .platformCommission(p != null && p.getPlatformCommission() != null ? p.getPlatformCommission() : 0.0)
                .carrierAmount(p != null && p.getCarrierAmount() != null ? p.getCarrierAmount() : 0.0)
                .paymentMethod(p != null ? p.getPaymentMethod() : null)
                .paymentStatus(p != null ? p.getPaymentStatus() : null)
                .carrierTransferStatus(p != null ? p.getCarrierTransferStatus() : null)
                .razorpayPaymentId(p != null ? p.getRazorpayPaymentId() : null)
                .razorpayOrderId(p != null ? p.getRazorpayOrderId() : null)
                .razorpayPayoutId(p != null ? p.getRazorpayPayoutId() : null)
                .paymentCompletedAt(p != null ? p.getPaymentCompletedAt() : null)
                .carrierTransferInitiatedAt(p != null ? p.getCarrierTransferInitiatedAt() : null)
                .carrierTransferCompletedAt(p != null ? p.getCarrierTransferCompletedAt() : null)
                .transferFailureReason(p != null ? p.getTransferFailureReason() : null)
                .commissionPaid(p != null ? p.getCommissionPaid() : null)
                .platformFee(p != null && p.getPlatformFee() != null ? p.getPlatformFee() : 0.0)   // ✅
                .carrierId(req.getCarrier() != null ? req.getCarrier().getUserId() : null)
                .build();
    }

    private double getCommissionForDate(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end   = date.atTime(23, 59, 59);
        Double val = paymentRepository.sumCommissionByStatusAndCompletedBetween(
                Payment.PaymentStatus.COMPLETED, start, end);
        return val != null ? val : 0.0;
    }

    private Map<LocalDate, Long> toDateLongMap(List<Object[]> rows) {
        Map<LocalDate, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                LocalDate date = row[0] instanceof java.sql.Date
                        ? ((java.sql.Date) row[0]).toLocalDate()
                        : (LocalDate) row[0];
                map.put(date, (Long) row[1]);
            }
        }
        return map;
    }

    private Map<LocalDate, Double> toDateDoubleMap(List<Object[]> rows) {
        Map<LocalDate, Double> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                LocalDate date = row[0] instanceof java.sql.Date
                        ? ((java.sql.Date) row[0]).toLocalDate()
                        : (LocalDate) row[0];
                map.put(date, row[1] != null ? ((Number) row[1]).doubleValue() : 0.0);
            }
        }
        return map;
    }

    private DeliveryRequestDetailDto mapToDeliveryDetail(DeliveryRequest request) {
        return DeliveryRequestDetailDto.builder()
                .requestId(request.getRequestId())
                .packageName(request.getPackageEntity() != null
                        ? request.getPackageEntity().getProductName() : "N/A")
                .senderName(request.getSender() != null
                        ? request.getSender().getFullName() : "N/A")
                .carrierName(request.getCarrier() != null
                        ? request.getCarrier().getFullName() : "N/A")
                .fromAddress(request.getPackageEntity() != null
                        ? request.getPackageEntity().getFromAddress() : "N/A")
                .toAddress(request.getPackageEntity() != null
                        ? request.getPackageEntity().getToAddress() : "N/A")
                .amount(request.getTotalAmount() != null
                        ? request.getTotalAmount().doubleValue() : 0.0)
                .status(request.getStatus())
                .requestedAt(request.getRequestedAt())
                .deliveredAt(request.getDeliveredAt())
                .build();
    }

    private UserDetailDto mapToUserDetail(User user) {
        return UserDetailDto.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .mobile(user.getMobile())
                .userType(user.getUserType())
                .verified(user.getVerified())
                .verificationStatus(user.getVerificationStatus())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .profileUrl(user.getProfileUrl())
                .build();
    }
}