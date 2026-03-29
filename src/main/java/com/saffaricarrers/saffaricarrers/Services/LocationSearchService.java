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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LocationSearchService {

    @Autowired
    private CarrierRouteRepository carrierRouteRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Transactional(readOnly = true)  // ADD THIS
    public LocationSearchResponse searchByRadius(double latitude, double longitude, double radius) {
        LocalDate currentDate = LocalDate.now();

        System.out.println("---- Location Radius Search ----");
        System.out.println("Center: " + latitude + ", " + longitude + " Radius: " + radius);

        List<CarrierRoute> carriers = carrierRouteRepository.findRoutesStartingWithinRadius(
                latitude, longitude, radius, 0.0, currentDate);
        System.out.println("Carrier raw count: " + (carriers != null ? carriers.size() : "null"));

        // CRITICAL: Initialize lazy relationships while still in transaction
        carriers.forEach(route -> {
            if (route.getCarrierProfile() != null) {
                // Access the proxy to initialize it
                route.getCarrierProfile().getCarrierId();
                if (route.getCarrierProfile().getUser() != null) {
                    route.getCarrierProfile().getUser().getFullName();
                }
            }
        });

        List<Package> packages = packageRepository.findPackagesWithinRadius(latitude, longitude, radius);
        System.out.println("Package raw count: " + (packages != null ? packages.size() : "null"));
        LocalDate today = LocalDate.now();
        packages = packages.stream()
                .filter(pkg -> {
                    if (pkg.getPickUpDate() == null || pkg.getPickUpDate().isEmpty()) return false;
                    try {
                        return !LocalDate.parse(pkg.getPickUpDate()).isBefore(today);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());
        // Initialize lazy relationships for packages
        packages.forEach(pkg -> {
            if (pkg.getSender() != null) {
                pkg.getSender().getFullName();
            }
        });

        List<SimpleCarrierDto> carrierDtos = carriers.stream()
                .map(this::convertToSimpleCarrierDto)
                .collect(Collectors.toList());

        List<SimplePackageDto> packageDtos = packages.stream()
                .map(this::convertToSimplePackageDto)
                .collect(Collectors.toList());

        // Log final data for verification
        carrierDtos.forEach(c -> System.out.println("CARRIER: " + c));
        packageDtos.forEach(p -> System.out.println("PACKAGE: " + p));

        LocationSearchResponse response = new LocationSearchResponse();
        response.setCarriersCount(carrierDtos.size());
        response.setSendersCount(packageDtos.size());
        response.setCarriers(carrierDtos);
        response.setPackages(packageDtos);

        System.out.println("---- END SEARCH RESPONSE ----");
        System.out.println("Carrier count: " + carrierDtos.size());
        System.out.println("Package count: " + packageDtos.size());
        System.out.println(">>> FINAL RESPONSE: " + response);

        return response;
    }


    private SimpleCarrierDto convertToSimpleCarrierDto(CarrierRoute route) {
        SimpleCarrierDto dto = new SimpleCarrierDto();
        dto.setRouteId(route.getRouteId());
        dto.setCarrierId(route.getCarrierProfile() != null ? route.getCarrierProfile().getCarrierId() : null);
        dto.setCarrierName(route.getCarrierProfile() != null && route.getCarrierProfile().getUser() != null ?
                route.getCarrierProfile().getUser().getFullName() : null);
        dto.setFromLocation(route.getFromLocation());
        dto.setToLocation(route.getToLocation());
        dto.setTransportType(route.getTransportType());
        dto.setAvailableDate(route.getAvailableDate());
        dto.setLongitude(route.getLongitude());
        dto.setLatitude(route.getLatitude());
        return dto;
    }

    private SimplePackageDto convertToSimplePackageDto(Package pkg) {
        SimplePackageDto dto = new SimplePackageDto();
        dto.setPackageId(pkg.getPackageId());
        dto.setSenderId(pkg.getSender() != null ? pkg.getSender().getUserId() : null);
        dto.setSenderName(pkg.getSender() != null ? pkg.getSender().getFullName() : null);
        dto.setProductName(pkg.getProductName());
        dto.setFromLocation(pkg.getFromAddress());
        dto.setToLocation(pkg.getToAddress());
        dto.setTransportType(pkg.getTransportType());
        dto.setPickUpDate(pkg.getPickUpDate());
        dto.setLongitude(pkg.getLongitude());
        dto.setLatitude(pkg.getLatitude());
        dto.setPackageStatus(pkg.getStatus());
        return dto;
    }
}