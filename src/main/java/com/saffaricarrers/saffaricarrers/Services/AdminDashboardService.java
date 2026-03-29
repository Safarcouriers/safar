package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Dtos.*;
import com.saffaricarrers.saffaricarrers.Entity.*;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import com.saffaricarrers.saffaricarrers.Repository.*;
import com.saffaricarrers.saffaricarrers.Responses.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final CarrierRouteRepository carrierRouteRepository;
    private final DocumentVerificationStatusRepository documentStatusRepository;

    // ==================== MAIN DASHBOARD STATS ====================
    // ✅ FIX: Single method, called ONCE by the controller. Sub-endpoints now call
    //    dedicated lightweight methods below rather than re-fetching the full dashboard.

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
        // ✅ FIX: Use COUNT queries — zero object loading
        long total    = userRepository.count();
        long verified = userRepository.countByVerified(true);
        long active   = userRepository.countByStatus(User.UserStatus.ACTIVE);
        long senders  = userRepository.countByUserType(User.UserType.SENDER);
        long carriers = userRepository.countByUserType(User.UserType.CARRIER);
        long both     = userRepository.countByUserType(User.UserType.BOTH);

        // Gender breakdown — one aggregation query
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
        // ✅ FIX: All count queries, no findAll()
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
        // ✅ FIX: Three targeted COUNT queries instead of findAll() + in-memory filter
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
        // ✅ FIX: One aggregation query for status counts + count queries for the rest
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
        // ✅ Already uses countByStatus — kept as-is, it was fine
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
        // ✅ FIX: Aggregation queries instead of findAll() + stream
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

        // ✅ FIX: All targeted date-range COUNT / SUM queries — no findAll()
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
    // ✅ FIX: Load all data in BULK, then partition in memory — NOT N queries per day

    public DayWiseAnalyticsResponse getDayWiseAnalytics(int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1);
        LocalDate to   = LocalDate.now();
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.atTime(23, 59, 59);

        // ✅ 5 queries total regardless of `days` value
        List<Object[]> packagesByDay   = packageRepository.countGroupByCreatedDate(fromDt, toDt);
        List<Object[]> deliveriesByDay = deliveryRequestRepository.countDeliveredGroupByDate(fromDt, toDt);
        List<Object[]> revenueByDay    = paymentRepository.sumRevenueGroupByDate(fromDt, toDt);
        List<Object[]> commissionByDay = paymentRepository.sumCommissionGroupByDate(fromDt, toDt);
        List<Object[]> usersByDay      = userRepository.countGroupByCreatedDate(fromDt, toDt);
        List<Object[]> requestsByDay   = deliveryRequestRepository.countGroupByRequestedDate(fromDt, toDt);

        // Convert to maps keyed by LocalDate for O(1) lookup
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
        // These still load entities — acceptable for list views
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

    // ==================== PRIVATE HELPERS ====================

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