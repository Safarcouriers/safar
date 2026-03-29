package com.saffaricarrers.saffaricarrers.Dtos;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import com.saffaricarrers.saffaricarrers.Responses.PackageSelectionResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageAvailabilityResponse {
    // Route info
    private Long routeId;
    private String fromLocation;
    private String toLocation;
    private LocalDate availableDate;
    private LocalDate deadlineDate;
    private CarrierRoute.TransportType transportType;
    private String availableTime;
    private String deadlineTime;
    // Availability status
    private Boolean hasPackages; // true if packages found, false if empty
    private Integer packageCount;
    private String message; // "No packages available..." or "Found X packages"

    // Matching packages list (empty if none found)
    private List<PackageSelectionResponse> matchingPackages;
}
