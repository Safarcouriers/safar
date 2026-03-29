package com.saffaricarrers.saffaricarrers.Responses;

import lombok.Data;

@Data
public class PackageStatsDto {
    private long totalPackages;
    private long createdPackages;
    private long inTransitPackages;
    private long deliveredPackages;
    private long cancelledPackages;
}