package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripDetailResponse {

    private Long routeId;
    private String fromLocation;
    private String toLocation;
    private LocalDate availableDate;
    private LocalTime availableTime;
    private CarrierRoute.RouteStatus routeStatus;
    private CarrierRoute.TransportType transportType;
    private String deadlineTime;
    private String carrierType;
    private String vehicleType;
    private String totalCapacity;
    private String currentLoad;
    private String packagesCapacity;
    private String weightCapacity;

    // ─── Package Requests (pending) ───────────────────────────────────────

    private List<PackageRequestSummary> packageRequests;
    private int packageRequestsCount;

    // ─── Active Packages ──────────────────────────────────────────────────

    private List<ActivePackageSummary> activePackages;
    private int activePackagesCount;

    // ✅ Delivered packages — so frontend keeps delivered state, not lose it
    private List<ActivePackageSummary> deliveredPackages;
    private int deliveredPackagesCount;

    // ─── Earnings ─────────────────────────────────────────────────────────

    private Double totalEarnings;
    private Double platformCommission;

    // ─── Commission Status ────────────────────────────────────────────────

    /**
     * "Paid"         — all deliveries have settled payments
     * "Pending"      — some COD commissions still unpaid by carrier
     * "No Deliveries"— no delivered packages yet
     */
    private String commissionStatus;
}