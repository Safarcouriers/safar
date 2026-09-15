package com.saffaricarrers.saffaricarrers.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import com.saffaricarrers.saffaricarrers.Repository.CarrierRouteRepository;
import com.saffaricarrers.saffaricarrers.Repository.PackageRepository;
import com.saffaricarrers.saffaricarrers.Dtos.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LocationSearchService {

    @Autowired
    private CarrierRouteRepository carrierRouteRepository;

    @Autowired
    private PackageRepository packageRepository;


    // ============================================================
    // LOCATION / RADIUS SEARCH
    // ============================================================

    @Transactional(readOnly = true)
    public LocationSearchResponse searchByRadius(
            double latitude,
            double longitude,
            double radius
    ) {

        LocalDate currentDate = LocalDate.now();

        // ============================================================
        // SEARCH START
        // ============================================================

        System.out.println();
        System.out.println("============================================================");
        System.out.println("              🔍 LOCATION RADIUS SEARCH");
        System.out.println("============================================================");
        System.out.println("📍 Search Latitude       : " + latitude);
        System.out.println("📍 Search Longitude      : " + longitude);
        System.out.println("📏 Search Radius         : " + radius + " KM");
        System.out.println("📅 Backend Current Date  : " + currentDate);
        System.out.println("============================================================");


        // ============================================================
        // VALIDATE SEARCH COORDINATES
        // ============================================================

        System.out.println();
        System.out.println("🔎 SEARCH COORDINATE VALIDATION");
        System.out.println("------------------------------------------------------------");

        boolean validSearchCoordinates =
                !Double.isNaN(latitude)
                        && !Double.isNaN(longitude)
                        && !Double.isInfinite(latitude)
                        && !Double.isInfinite(longitude)
                        && latitude >= -90
                        && latitude <= 90
                        && longitude >= -180
                        && longitude <= 180;

        System.out.println(
                "Search coordinates valid = "
                        + (validSearchCoordinates ? "✅ YES" : "❌ NO")
        );

        if (!validSearchCoordinates) {
            System.out.println("❌ INVALID SEARCH COORDINATES");
            System.out.println("Latitude  = " + latitude);
            System.out.println("Longitude = " + longitude);
            System.out.println("============================================================");

            LocationSearchResponse emptyResponse =
                    new LocationSearchResponse();

            emptyResponse.setCarriersCount(0);
            emptyResponse.setSendersCount(0);
            emptyResponse.setCarriers(List.of());
            emptyResponse.setPackages(List.of());

            return emptyResponse;
        }


        // ============================================================
        // CARRIER ROUTE DATABASE QUERY
        // ============================================================

        System.out.println();
        System.out.println("🚚 STARTING CARRIER ROUTE DATABASE QUERY");
        System.out.println("------------------------------------------------------------");

        System.out.println("SQL CONDITIONS:");
        System.out.println("1. route_status = CREATED");
        System.out.println("2. available_date >= " + currentDate);
        System.out.println("3. latitude IS NOT NULL");
        System.out.println("4. longitude IS NOT NULL");
        System.out.println("5. distance <= " + radius + " KM");
        System.out.println("6. max_weight - current_weight >= 0");
        System.out.println();


        List<CarrierRoute> carriers =
                carrierRouteRepository.findRoutesStartingWithinRadius(
                        latitude,
                        longitude,
                        radius,
                        0.0,
                        currentDate
                );


        // ============================================================
        // RAW DATABASE RESULT
        // ============================================================

        System.out.println();
        System.out.println("🚚 CARRIER DATABASE RESULT");
        System.out.println("------------------------------------------------------------");

        System.out.println(
                "🚚 Carrier raw count = "
                        + (carriers == null ? "NULL" : carriers.size())
        );


        if (carriers == null) {
            carriers = List.of();
        }


        // ============================================================
        // PRINT EVERY ROUTE RETURNED BY DATABASE
        // ============================================================

        if (carriers.isEmpty()) {

            System.out.println();
            System.out.println("❌❌❌ NO CARRIER ROUTES RETURNED ❌❌❌");
            System.out.println();

            System.out.println("The database query rejected all carrier routes.");
            System.out.println();

            System.out.println("Possible reasons:");
            System.out.println("❌ route_status is not CREATED");
            System.out.println("❌ available_date is before backend date");
            System.out.println("❌ latitude is NULL");
            System.out.println("❌ longitude is NULL");
            System.out.println("❌ route is outside radius");
            System.out.println("❌ remaining weight is below required capacity");
            System.out.println();

        } else {

            System.out.println();
            System.out.println("✅ CARRIER ROUTES FOUND");
            System.out.println("============================================================");


            for (CarrierRoute route : carriers) {

                System.out.println();
                System.out.println("**************** ROUTE FOUND ****************");

                System.out.println(
                        "🆔 Route ID              : "
                                + route.getRouteId()
                );

                System.out.println(
                        "📌 Route Status          : "
                                + route.getRouteStatus()
                );

                System.out.println(
                        "📍 From Location         : "
                                + route.getFromLocation()
                );

                System.out.println(
                        "📍 To Location           : "
                                + route.getToLocation()
                );

                System.out.println(
                        "📅 Available Date        : "
                                + route.getAvailableDate()
                );

                System.out.println(
                        "📅 Deadline Date         : "
                                + route.getDeadlineDate()
                );

                System.out.println(
                        "⏰ Available Time        : "
                                + route.getAvailableTime()
                );

                System.out.println(
                        "⏰ Deadline Time         : "
                                + route.getDeadlineTime()
                );

                System.out.println(
                        "🌍 Origin Latitude       : "
                                + route.getLatitude()
                );

                System.out.println(
                        "🌍 Origin Longitude      : "
                                + route.getLongitude()
                );

                System.out.println(
                        "🎯 Destination Latitude  : "
                                + route.getToLatitude()
                );

                System.out.println(
                        "🎯 Destination Longitude : "
                                + route.getToLongitude()
                );

                System.out.println(
                        "⚖️ Max Weight            : "
                                + route.getMaxWeight()
                );

                System.out.println(
                        "⚖️ Current Weight        : "
                                + route.getCurrentWeight()
                );

                System.out.println(
                        "📦 Max Quantity          : "
                                + route.getMaxQuantity()
                );

                System.out.println(
                        "📦 Current Quantity      : "
                                + route.getCurrentQuantity()
                );

                System.out.println(
                        "🚛 Transport Type        : "
                                + route.getTransportType()
                );


                // ====================================================
                // REMAINING CAPACITY
                // ====================================================

                if (route.getMaxWeight() != null
                        && route.getCurrentWeight() != null) {

                    double remainingWeight =
                            route.getMaxWeight()
                                    - route.getCurrentWeight();

                    System.out.println(
                            "⚖️ Remaining Weight      : "
                                    + remainingWeight
                    );

                    System.out.println(
                            "⚖️ Capacity Check        : "
                                    + (
                                    remainingWeight >= 0
                                            ? "✅ PASS"
                                            : "❌ FAIL"
                            )
                    );

                } else {

                    System.out.println(
                            "⚠️ Capacity Check        : "
                                    + "❌ INVALID / NULL"
                    );
                }


                // ====================================================
                // DATE CHECK
                // ====================================================

                if (route.getAvailableDate() == null) {

                    System.out.println(
                            "📅 Date Check             : ❌ NULL"
                    );

                } else {

                    boolean dateValid =
                            !route.getAvailableDate()
                                    .isBefore(currentDate);

                    System.out.println(
                            "📅 Date Check             : "
                                    + (
                                    dateValid
                                            ? "✅ PASS"
                                            : "❌ FAIL"
                            )
                    );

                    System.out.println(
                            "📅 Available >= Today    : "
                                    + route.getAvailableDate()
                                    + " >= "
                                    + currentDate
                    );
                }


                // ====================================================
                // COORDINATE CHECK
                // ====================================================

                boolean validRouteCoordinates =
                        route.getLatitude() != null
                                && route.getLongitude() != null
                                && route.getLatitude() != 0.0
                                && route.getLongitude() != 0.0
                                && !Double.isNaN(route.getLatitude())
                                && !Double.isNaN(route.getLongitude())
                                && !Double.isInfinite(route.getLatitude())
                                && !Double.isInfinite(route.getLongitude());

                System.out.println(
                        "🌍 Coordinate Check     : "
                                + (
                                validRouteCoordinates
                                        ? "✅ PASS"
                                        : "❌ FAIL"
                        )
                );


                // ====================================================
                // CARRIER PROFILE
                // ====================================================

                if (route.getCarrierProfile() != null) {

                    System.out.println(
                            "👤 Carrier Profile       : PRESENT"
                    );

                    System.out.println(
                            "👤 Carrier ID            : "
                                    + route.getCarrierProfile()
                                    .getCarrierId()
                    );

                    if (route.getCarrierProfile().getUser() != null) {

                        System.out.println(
                                "👤 Carrier User ID       : "
                                        + route.getCarrierProfile()
                                        .getUser()
                                        .getUserId()
                        );

                        System.out.println(
                                "👤 Carrier Name          : "
                                        + route.getCarrierProfile()
                                        .getUser()
                                        .getFullName()
                        );

                    } else {

                        System.out.println(
                                "⚠️ Carrier User           : NULL"
                        );
                    }

                } else {

                    System.out.println(
                            "⚠️ Carrier Profile        : NULL"
                    );
                }


                System.out.println(
                        "************************************************"
                );
            }
        }


        // ============================================================
        // INITIALIZE CARRIER RELATIONSHIPS
        // ============================================================

        carriers.forEach(route -> {

            if (route.getCarrierProfile() != null) {

                route.getCarrierProfile().getCarrierId();

                if (route.getCarrierProfile().getUser() != null) {

                    route.getCarrierProfile()
                            .getUser()
                            .getFullName();
                }
            }
        });


        // ============================================================
        // PACKAGE SEARCH
        // ============================================================

        System.out.println();
        System.out.println("📦 STARTING PACKAGE DATABASE QUERY");
        System.out.println("------------------------------------------------------------");


        List<Package> packages =
                packageRepository.findPackagesWithinRadius(
                        latitude,
                        longitude,
                        radius
                );


        System.out.println(
                "📦 Package raw count = "
                        + (packages == null
                        ? "NULL"
                        : packages.size())
        );


        if (packages == null) {
            packages = List.of();
        }


        // ============================================================
        // PACKAGE DATE FILTER
        // ============================================================

        LocalDate today = LocalDate.now();

        System.out.println();
        System.out.println("📅 PACKAGE DATE FILTER");
        System.out.println("Today = " + today);
        System.out.println("------------------------------------------------------------");


        packages = packages.stream()
                .filter(pkg -> {

                    String pickupDate =
                            pkg.getPickUpDate();

                    if (pickupDate == null
                            || pickupDate.isEmpty()) {

                        System.out.println(
                                "❌ Package "
                                        + pkg.getPackageId()
                                        + " rejected: pickup date NULL/EMPTY"
                        );

                        return false;
                    }


                    try {

                        LocalDate parsedDate =
                                LocalDate.parse(pickupDate);

                        boolean valid =
                                !parsedDate.isBefore(today);

                        System.out.println(
                                "📦 Package "
                                        + pkg.getPackageId()
                                        + " pickupDate="
                                        + parsedDate
                                        + " → "
                                        + (
                                        valid
                                                ? "✅ PASS"
                                                : "❌ FAIL"
                                )
                        );

                        return valid;

                    } catch (Exception e) {

                        System.out.println(
                                "❌ Package "
                                        + pkg.getPackageId()
                                        + " invalid pickup date = "
                                        + pickupDate
                        );

                        return false;
                    }

                })
                .collect(Collectors.toList());


        System.out.println(
                "📦 Package count after date filter = "
                        + packages.size()
        );


        // ============================================================
        // INITIALIZE PACKAGE RELATIONSHIPS
        // ============================================================

        packages.forEach(pkg -> {

            if (pkg.getSender() != null) {

                pkg.getSender().getFullName();
            }
        });


        // ============================================================
        // CONVERT CARRIERS TO DTO
        // ============================================================

        System.out.println();
        System.out.println("🚚 CONVERTING CARRIER DTOs");
        System.out.println("------------------------------------------------------------");


        List<SimpleCarrierDto> carrierDtos =
                carriers.stream()
                        .map(this::convertToSimpleCarrierDto)
                        .collect(Collectors.toList());


        System.out.println(
                "🚚 Carrier DTO count = "
                        + carrierDtos.size()
        );


        // ============================================================
        // CONVERT PACKAGES TO DTO
        // ============================================================

        System.out.println();
        System.out.println("📦 CONVERTING PACKAGE DTOs");
        System.out.println("------------------------------------------------------------");


        List<SimplePackageDto> packageDtos =
                packages.stream()
                        .map(this::convertToSimplePackageDto)
                        .collect(Collectors.toList());


        System.out.println(
                "📦 Package DTO count = "
                        + packageDtos.size()
        );


        // ============================================================
        // PRINT FINAL CARRIER DTO DATA
        // ============================================================

        System.out.println();
        System.out.println("🚚 FINAL CARRIER DTO DATA");
        System.out.println("------------------------------------------------------------");


        carrierDtos.forEach(c -> {

            System.out.println(
                    "CARRIER:"
                            + " routeId=" + c.getRouteId()
                            + " | carrierId=" + c.getCarrierId()
                            + " | name=" + c.getCarrierName()
                            + " | from=" + c.getFromLocation()
                            + " | to=" + c.getToLocation()
                            + " | transport=" + c.getTransportType()
                            + " | availableDate=" + c.getAvailableDate()
                            + " | latitude=" + c.getLatitude()
                            + " | longitude=" + c.getLongitude()
            );
        });


        // ============================================================
        // PRINT FINAL PACKAGE DTO DATA
        // ============================================================

        System.out.println();
        System.out.println("📦 FINAL PACKAGE DTO DATA");
        System.out.println("------------------------------------------------------------");


        packageDtos.forEach(p ->
                System.out.println(
                        "PACKAGE: " + p
                )
        );


        // ============================================================
        // BUILD FINAL RESPONSE
        // ============================================================

        LocationSearchResponse response =
                new LocationSearchResponse();

        response.setCarriersCount(
                carrierDtos.size()
        );

        response.setSendersCount(
                packageDtos.size()
        );

        response.setCarriers(
                carrierDtos
        );

        response.setPackages(
                packageDtos
        );


        // ============================================================
        // FINAL DEBUG SUMMARY
        // ============================================================

        System.out.println();
        System.out.println("============================================================");
        System.out.println("                 🔥 FINAL SEARCH RESULT");
        System.out.println("============================================================");

        System.out.println(
                "📍 Search coordinates : "
                        + latitude
                        + ", "
                        + longitude
        );

        System.out.println(
                "📏 Search radius     : "
                        + radius
                        + " KM"
        );

        System.out.println(
                "📅 Backend date      : "
                        + currentDate
        );

        System.out.println(
                "🚚 Carrier DB count  : "
                        + carriers.size()
        );

        System.out.println(
                "🚚 Carrier DTO count : "
                        + carrierDtos.size()
        );

        System.out.println(
                "📦 Package DB count  : "
                        + packages.size()
        );

        System.out.println(
                "📦 Package DTO count : "
                        + packageDtos.size()
        );

        System.out.println("============================================================");

        System.out.println(
                ">>> FINAL RESPONSE: "
                        + response
        );

        System.out.println("============================================================");
        System.out.println();


        return response;
    }


    // ============================================================
    // CARRIER DTO CONVERSION
    // ============================================================

    private SimpleCarrierDto convertToSimpleCarrierDto(
            CarrierRoute route
    ) {

        System.out.println();
        System.out.println("🔄 Converting CarrierRoute → SimpleCarrierDto");
        System.out.println(
                "Route ID = " + route.getRouteId()
        );


        SimpleCarrierDto dto =
                new SimpleCarrierDto();


        dto.setRouteId(
                route.getRouteId()
        );


        dto.setCarrierId(
                route.getCarrierProfile() != null
                        ? route.getCarrierProfile().getCarrierId()
                        : null
        );


        dto.setCarrierName(
                route.getCarrierProfile() != null
                        && route.getCarrierProfile().getUser() != null
                        ? route.getCarrierProfile()
                        .getUser()
                        .getFullName()
                        : null
        );


        dto.setFromLocation(
                route.getFromLocation()
        );


        dto.setToLocation(
                route.getToLocation()
        );


        dto.setTransportType(
                route.getTransportType()
        );


        dto.setAvailableDate(
                route.getAvailableDate()
        );


        dto.setLongitude(
                route.getLongitude()
        );


        dto.setLatitude(
                route.getLatitude()
        );


        System.out.println(
                "✅ Carrier DTO created:"
                        + " routeId=" + dto.getRouteId()
                        + " | from=" + dto.getFromLocation()
                        + " | to=" + dto.getToLocation()
                        + " | date=" + dto.getAvailableDate()
                        + " | lat=" + dto.getLatitude()
                        + " | lng=" + dto.getLongitude()
        );


        return dto;
    }


    // ============================================================
    // PACKAGE DTO CONVERSION
    // ============================================================

    private SimplePackageDto convertToSimplePackageDto(
            Package pkg
    ) {

        SimplePackageDto dto =
                new SimplePackageDto();


        dto.setPackageId(
                pkg.getPackageId()
        );


        dto.setSenderId(
                pkg.getSender() != null
                        ? pkg.getSender().getUserId()
                        : null
        );


        dto.setSenderName(
                pkg.getSender() != null
                        ? pkg.getSender().getFullName()
                        : null
        );


        dto.setProductName(
                pkg.getProductName()
        );


        dto.setFromLocation(
                pkg.getFromAddress()
        );


        dto.setToLocation(
                pkg.getToAddress()
        );


        dto.setTransportType(
                pkg.getTransportType()
        );


        dto.setPickUpDate(
                pkg.getPickUpDate()
        );


        dto.setLongitude(
                pkg.getLongitude()
        );


        dto.setLatitude(
                pkg.getLatitude()
        );


        dto.setPackageStatus(
                pkg.getStatus()
        );


        System.out.println(
                "✅ Package DTO created:"
                        + " packageId=" + dto.getPackageId()
                        + " | from=" + dto.getFromLocation()
                        + " | to=" + dto.getToLocation()
                        + " | pickupDate=" + dto.getPickUpDate()
                        + " | lat=" + dto.getLatitude()
                        + " | lng=" + dto.getLongitude()
        );


        return dto;
    }
}