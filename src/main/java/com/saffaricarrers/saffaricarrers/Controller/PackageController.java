package com.saffaricarrers.saffaricarrers.Controller;


import com.amazonaws.services.dynamodbv2.xspec.S;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saffaricarrers.saffaricarrers.Dtos.*;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import com.saffaricarrers.saffaricarrers.Repository.PackageRepository;
import com.saffaricarrers.saffaricarrers.Responses.PackageStatsDto;
import com.saffaricarrers.saffaricarrers.Services.PackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/packages")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PackageController {
    @Autowired
    private  ObjectMapper objectMapper ;
    private final PackageService packageService;
    @Autowired
    private PackageRepository packageRepository;

    /**
     * Create a new package
     * POST /api/v1/packages
     */
    @PostMapping("/{userId}")
    public ResponseEntity<?> createPackage(
            @PathVariable("userId") String userId,
            @RequestParam(value = "package") String request,
            @RequestParam(value = "productImages", required = false) List<MultipartFile> productImages,
            @RequestParam(value = "invoiceImage", required = false) MultipartFile invoiceImage) {

        log.info("Creating package for user: {}", userId);

        try {
            // Parse the package creation request
            PackageRequest packageRequest = objectMapper.readValue(request, PackageRequest.class);
            System.out.println(request);

            // Create the package
            PackageResponse packageResponse = packageService.createPackage(userId, packageRequest, productImages, invoiceImage);

            // Create a success response wrapper
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("success", true);
            successResponse.put("message", "Package created successfully");
            successResponse.put("data", packageResponse);

            return ResponseEntity.status(HttpStatus.CREATED).body(successResponse);

        } catch (JsonProcessingException e) {
            log.error("Invalid JSON format in package creation request: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "message", "Invalid request format",
                            "error", e.getMessage()
                    ));

        } catch (IllegalArgumentException e) {
            log.error("Invalid package data: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "message", "Invalid package data provided",
                            "error", e.getMessage()
                    ));

        } catch (DataIntegrityViolationException e) {
            log.error("Package data constraint violation: ", e);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "success", false,
                            "message", "Package data constraint violation",
                            "error", "Duplicate data found"
                    ));

        } catch (Exception e) {
            log.error("Unexpected error during package creation for user {}: ", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Internal server error occurred",
                            "error", "Please try again later"
                    ));
        }
    }


    /**
     * Get all packages for the authenticated sender
     * GET /api/v1/packages
     */
    @GetMapping
    public ResponseEntity<List<PackageResponse>> getSenderPackages(
            @RequestHeader("User-Id") String userId) {

        log.info("Fetching packages for user: {}", userId);
        try {
            List<PackageResponse> packages = packageService.getSenderPackages(userId);
            return ResponseEntity.ok(packages);
        } catch (Exception e) {
            log.error("Error fetching packages for user {}: {}", userId, e.getMessage());
            throw e;
        }
    }
    @GetMapping("/all")
    public ResponseEntity<List<PackageResponse>> getAllSenders()
             {

        try {
            List<PackageResponse> packages = packageService.getAll();
            return ResponseEntity.ok(packages);
        } catch (Exception e) {
            log.error("Error fetching packages for user {}: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Get package by ID
     * GET /api/v1/packages/{packageId}
     */
    @GetMapping("/{packageId}")
    public ResponseEntity<PackageResponse> getPackageById(
            @RequestHeader("User-Id") String userId,
            @PathVariable Long packageId) {

        log.info("Fetching package {} for user: {}", packageId, userId);
        try {
            // ✅ Make sure this uses the FULL PackageResponse with all fields
            PackageResponse packageResponse = packageService.getPackageById(userId, packageId);

            // ✅ ADD LOGGING to see what's in the response
            log.info("Response - ProductType: {}, TransportType: {}, PickUpDate: {}, DropDate: {}",
                    packageResponse.getProductType(),
                    packageResponse.getTransportType(),
                    packageResponse.getPickUpDate(),
                    packageResponse.getDropDate());

            System.out.println(packageResponse);
            return ResponseEntity.ok(packageResponse);
        } catch (Exception e) {
            log.error("Error fetching package {} for user {}: {}", packageId, userId, e.getMessage());
            throw e;
        }
    }

    @GetMapping("/single/{packageId}")
    public ResponseEntity<PackageResponse> getPackageByJustId(
            @PathVariable Long packageId) {

        log.info("Fetching package details {} for user: {}", packageId);
        try {
            PackageResponse packageResponse = packageService.getPackageByJustId( packageId);
            log.info("Fetced package details {} for user: {}", packageResponse);
            return ResponseEntity.ok(packageResponse);
        } catch (Exception e) {
            log.error("Error fetching package {}: ", packageId, e); // full stack trace!
            throw e; // or handle as per framework
        }

    }

    /**
     * Update package details
     * PUT /api/v1/packages/{packageId}
     */

    /**
     * Update package details with image uploads
     * PUT /api/v1/packages/{packageId}
     */
    @PutMapping("/{packageId}")
    public ResponseEntity<?> updatePackage(
            @RequestHeader("User-Id") String userId,
            @PathVariable Long packageId,
            @RequestParam(value = "package") String request,
            @RequestParam(value = "productImages", required = false) List<MultipartFile> productImages,
            @RequestParam(value = "invoiceImage", required = false) MultipartFile invoiceImage) {

        log.info("Updating package {} for user: {}", packageId, userId);

        try {
            PackageRequest packageRequest = objectMapper.readValue(request, PackageRequest.class);

            log.info("Parsed package request - ProductType: {}, TransportType: {}",
                    packageRequest.getProductType(),
                    packageRequest.getTransportType());

            // Update the package
            PackageResponse response = packageService.updatePackage(userId, packageId, packageRequest, productImages, invoiceImage);

            log.info("Package service returned response, testing serialization...");

            // ✅ TEST SERIALIZATION EXPLICITLY
            try {
                String testJson = objectMapper.writeValueAsString(response);
                log.info("✅ PackageResponse serialized successfully");
                log.info("JSON length: {} characters", testJson.length());
            } catch (Exception serError) {
                log.error("❌ SERIALIZATION ERROR FOUND:");
                log.error("Error Type: {}", serError.getClass().getName());
                log.error("Error Message: {}", serError.getMessage());
                log.error("Stack Trace:", serError);

                // Return minimal success response
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Package updated successfully (with serialization warning)",
                        "packageId", response.getPackageId() != null ? response.getPackageId() : 0L
                ));
            }

            // Create success response
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("success", true);
            successResponse.put("message", "Package updated successfully");
            successResponse.put("data", response);

            log.info("Returning success response");
            return ResponseEntity.ok(successResponse);

        } catch (JsonProcessingException e) {
            log.error("Invalid JSON format: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Invalid request format", "error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.error("Invalid package data: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", e.getMessage(), "error", "Invalid package data"));

        } catch (Exception e) {
            log.error("❌ UNEXPECTED ERROR:");
            log.error("Error Type: {}", e.getClass().getName());
            log.error("Error Message: {}", e.getMessage());
            log.error("Full Stack Trace:", e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Something went wrong",
                            "error", e.getMessage() != null ? e.getMessage() : "Unknown error"
                    ));
        }
    }


    /**
     * Delete package
     * DELETE /api/v1/packages/{packageId}
     */
    @DeleteMapping("/{packageId}")
    public ResponseEntity<Map<String, String>> deletePackage(
            @RequestHeader("User-Id") String userId,
            @PathVariable Long packageId) {

        log.info("Deleting package {} for user: {}", packageId, userId);
        try {
            packageService.deletePackage(userId, packageId);
            return ResponseEntity.ok(Map.of(
                    "message", "Package deleted successfully",
                    "packageId", packageId.toString()
            ));
        } catch (Exception e) {
            log.error("Error deleting package {} for user {}: {}", packageId, userId, e.getMessage());
            throw e;
        }
    }

    /**
     * Get active packages for sender
     * GET /api/v1/packages/active
     */
    @GetMapping("/active")
    public ResponseEntity<List<PackageResponse>> getActivePackages(
            @RequestHeader("User-Id") String userId) {

        log.info("Fetching active packages for user: {}", userId);
        try {
            List<PackageResponse> packages = packageService.getActivePackages(userId);
            return ResponseEntity.ok(packages);
        } catch (Exception e) {
            log.error("Error fetching active packages for user {}: {}", userId, e.getMessage());
            throw e;
        }
    }

    /**
     * Update package status (Admin/System operation)
     * PATCH /api/v1/packages/{packageId}/status
     */
    @PatchMapping("/{packageId}/status")
    public ResponseEntity<PackageResponse> updatePackageStatus(
            @PathVariable Long packageId,
            @RequestBody Map<String, String> statusRequest) {

        log.info("Updating status for package: {}", packageId);
        try {
            String statusString = statusRequest.get("status");
            Package.PackageStatus status = Package.PackageStatus.valueOf(statusString.toUpperCase());

            PackageResponse response = packageService.updatePackageStatus(packageId, status);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid status provided for package {}: {}", packageId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error updating status for package {}: {}", packageId, e.getMessage());
            throw e;
        }
    }

    /**
     * Verify pickup OTP
     * POST /api/v1/packages/{packageId}/verify-pickup
     */
    @PostMapping("/{packageId}/verify-pickup")
    public ResponseEntity<Map<String, Object>> verifyPickupOtp(
            @PathVariable Long packageId,
            @RequestBody Map<String, String> otpRequest) {

        log.info("Verifying pickup OTP for package: {}", packageId);
        try {
            String otp = otpRequest.get("otp");
            boolean isValid = packageService.verifyPickupOtp(packageId, otp);

            return ResponseEntity.ok(Map.of(
                    "valid", isValid,
                    "message", isValid ? "Pickup OTP verified successfully" : "Invalid pickup OTP",
                    "packageId", packageId
            ));
        } catch (Exception e) {
            log.error("Error verifying pickup OTP for package {}: {}", packageId, e.getMessage());
            throw e;
        }
    }

    /**
     * Verify delivery OTP
     * POST /api/v1/packages/{packageId}/verify-delivery
     */
    @PostMapping("/{packageId}/verify-delivery")
    public ResponseEntity<Map<String, Object>> verifyDeliveryOtp(
            @PathVariable Long packageId,
            @RequestBody Map<String, String> otpRequest) {

        log.info("Verifying delivery OTP for package: {}", packageId);
        try {
            String otp = otpRequest.get("otp");
            boolean isValid = packageService.verifyDeliveryOtp(packageId, otp);

            return ResponseEntity.ok(Map.of(
                    "valid", isValid,
                    "message", isValid ? "Delivery OTP verified successfully" : "Invalid delivery OTP",
                    "packageId", packageId
            ));
        } catch (Exception e) {
            log.error("Error verifying delivery OTP for package {}: {}", packageId, e.getMessage());
            throw e;
        }
    }

    /**
     * Get package statistics for sender
     * GET /api/v1/packages/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<PackageStatsDto> getPackageStats(
            @RequestHeader("User-Id") String userId) {

        log.info("Fetching package statistics for user: {}", userId);
        try {
            PackageStatsDto stats = packageService.getPackageStats(userId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching package statistics for user {}: {}", userId, e.getMessage());
            throw e;
        }
    }

    /**
     * Health check endpoint
     * GET /api/v1/packages/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "PackageService",
                "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
    /**
     * Update package details with image uploads
     * PUT /api/v1/packages/{packageId}
     */
//    @PutMapping("/{packageId}")
//    public ResponseEntity<PackageResponse> updatePackage(
//            @RequestHeader("User-Id") String userId,
//            @PathVariable Long packageId,
//            @RequestParam(value = "package") String request,
//            @RequestParam(value = "productImages", required = false) List<MultipartFile> productImages,
//            @RequestParam(value = "invoiceImage", required = false) MultipartFile invoiceImage) {
//
//        log.info("Updating package {} for user: {}", packageId, userId);
//        try {
//            PackageRequest packageRequest = objectMapper.readValue(request, PackageRequest.class);
//            PackageResponse response = packageService.updatePackage(userId, packageId, packageRequest, productImages, invoiceImage);
//            return ResponseEntity.ok(response);
//        } catch (Exception e) {
//            log.error("Error updating package {} for user {}: {}", packageId, userId, e.getMessage());
//            try {
//                throw e;
//            } catch (JsonProcessingException ex) {
//                throw new RuntimeException(ex);
//            }
//        }
//    }

//    @PostMapping("/search/geospatial")
//    public ResponseEntity<Map<String, Object>> searchPackagesGeospatial(
//            @RequestBody GeospatialSearchRequest request) {
//
////        log.info("Geospatial search for user: {} from [{},{}] to [{},{}]",
////                userId, request.getFromLatitude(), request.getFromLongitude(),
////                request.getToLatitude(), request.getToLongitude());
//
//        try {
//            Map<String, Object> result = packageService.searchPackagesUnified(request);
//            return ResponseEntity.ok(result);
//        } catch (Exception e) {
//         //   log.error("Error in geospatial search for user {}: {}", userId, e.getMessage());
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(Map.of("error", "Search failed: " + e.getMessage()));
//        }
//    }
    @PostMapping("/search/geospatial")
    public ResponseEntity<Map<String, Object>> searchPackagesGeospatial(
            @RequestBody GeospatialSearchRequest request) {
        try {
            System.out.println("📦 Package Search Request: " + request);

            // ✅ Use the method that exists in your service
            Map<String, Object> result = packageService.searchPackagesUnified(request);

            System.out.println("✅ Search completed successfully");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.err.println("❌ Error in package geospatial search:");
            e.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Search failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
                            "type", e.getClass().getSimpleName()
                    ));
        }
    }



    // Additional utility endpoints
    @GetMapping("/search/nearby")
    public ResponseEntity<List<GeospatialPackageResponse>> searchNearbyPackages(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "10") double radius) {

        try {
            List<Package> packages = packageRepository.findPackagesWithinRadius(lat, lng, radius);
            List<GeospatialPackageResponse> response = packages.stream()
                    .map(pkg -> mapToGeospatialResponse(pkg, lat, lng))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in nearby search: {}", e.getMessage());
            throw e;
        }
    }

//    @GetMapping("/search/area")
//    public ResponseEntity<List<GeospatialPackageResponse>> searchInArea(
//            @RequestHeader("User-Id") String userId,
//            @RequestParam double minLat,
//            @RequestParam double maxLat,
//            @RequestParam double minLng,
//            @RequestParam double maxLng) {
//
//        try {
//            List<Package> packages = packageRepository.findPackagesInBoundingBox(
//                    minLat, maxLat, minLng, maxLng);
//
//            double centerLat = (minLat + maxLat) / 2;
//            double centerLng = (minLng + maxLng) / 2;
//
//            List<GeospatialPackageResponse> response = packages.stream()
//                    .map(pkg -> mapToGeospatialResponse(pkg, centerLat, centerLng))
//                    .collect(Collectors.toList());
//            return ResponseEntity.ok(response);
//        } catch (Exception e) {
//            log.error("Error in area search: {}", e.getMessage());
//            throw e;
//        }
//    }
    private GeospatialPackageResponse mapToGeospatialResponse(Package pkg, double searchLat, double searchLng) {
        GeospatialPackageResponse response = new GeospatialPackageResponse();

        // Basic package info
        response.setPackageId(pkg.getPackageId());
        response.setSenderName(pkg.getSender().getFullName());
        response.setSenderId(pkg.getSender().getUserId());
        response.setSenderMobile(pkg.getSender().getMobile());
        response.setProductName(pkg.getProductName());
        response.setProductDescription(pkg.getProductDescription());
        response.setProductValue(pkg.getProductValue());
        response.setProductType(pkg.getProductType());
        response.setTransportType(pkg.getTransportType());
        response.setWeight(pkg.getWeight());
        response.setLength(pkg.getLength());
        response.setWidth(pkg.getWidth());
        response.setHeight(pkg.getHeight());
        response.setFromAddress(pkg.getFromAddress());
        response.setToAddress(pkg.getToAddress());
        response.setLatitude(pkg.getLatitude());
        response.setLongitude(pkg.getLongitude());
        response.setPickUpDate(pkg.getPickUpDate());
        response.setDropDate(pkg.getDropDate());
        response.setTripCharge(pkg.getTripCharge());
        response.setPricePerKg(pkg.getPricePerKg());
        response.setPricePerTon(pkg.getPricePerTon());
        response.setInsurance(pkg.getInsurance());
        response.setStatus(pkg.getStatus());
        response.setCreatedAt(pkg.getCreatedAt());
        response.setPickupOtp(pkg.getPickupOtp());
        response.setDeliveryOtp(pkg.getDeliveryOtp());

        // Calculate distance
        double distance = calculateDistance(searchLat, searchLng, pkg.getLatitude(), pkg.getLongitude());
        response.setDistanceFromSearchPoint(Math.round(distance * 100.0) / 100.0);

        return response;
    }

    /**
     * Calculate distance between two points using Haversine formula
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371; // Radius of the Earth in kilometers

        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c; // Distance in kilometers
    }

    @PostMapping("/search/along-route")
    public ResponseEntity<Map<String, Object>> searchPackagesAlongRoute(
            @RequestBody RouteSearchRequest request) {
        try {
            Map<String, Object> result = packageService.searchPackagesAlongRoute(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in route search: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Route search failed: " + e.getMessage()));
        }
    }

    @PostMapping("/search/route-matching")
    public ResponseEntity<Map<String, Object>> searchRouteMatchingPackages(
            @RequestBody RouteMatchingRequest request) {
        try {
            Map<String, Object> result = packageService.findRouteMatchingPackages(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in route matching search: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Route matching search failed: " + e.getMessage()));
        }
    }
}
