package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Dtos.*;
import com.saffaricarrers.saffaricarrers.Entity.*;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import com.saffaricarrers.saffaricarrers.Repository.*;
import com.saffaricarrers.saffaricarrers.Responses.PackageStatsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PackageService {

    private final PackageRepository packageRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PaymentRepository paymentRepository;
    private final InsuranceRepository insuranceRepository;
    private final CallBackService callBackService;
    private final RiderNotificationService riderNotificationService; // ✅ NEW

    // ==================== CREATE PACKAGE ====================

    @CacheEvict(value = {"senderPackages", "packageStats", "geospatialPackages"}, allEntries = true)
    public PackageResponse createPackage(String userId, PackageRequest request,
                                         List<MultipartFile> productImages,
                                         MultipartFile invoiceImage) {
        log.info("Creating package for user: {} with {} product images and invoice image: {}",
                userId,
                productImages != null ? productImages.size() : 0,
                invoiceImage != null ? "provided" : "not provided");

        try {
            User sender = userRepository.findByUserId(request.getUid())
                    .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));

            // Upload product images to S3
            List<String> productImageUrls = new ArrayList<>();
            if (productImages != null && !productImages.isEmpty()) {
                for (MultipartFile image : productImages) {
                    if (image != null && !image.isEmpty()) {
                        try {
                            String imageUrl = callBackService.uploadToS3(
                                    image.getInputStream(),
                                    image.getContentType(),
                                    image.getSize()
                            );
                            productImageUrls.add(imageUrl);
                            log.info("Uploaded product image: {}", imageUrl);
                        } catch (IOException e) {
                            log.error("Failed to upload product image: {}", e.getMessage());
                            throw new RuntimeException("Failed to upload product image: " + e.getMessage());
                        }
                    }
                }
            }

            // Upload invoice image to S3
            String invoiceImageUrl = null;
            if (invoiceImage != null && !invoiceImage.isEmpty()) {
                try {
                    invoiceImageUrl = callBackService.uploadDocumentToS3(invoiceImage, "invoice");
                    log.info("Uploaded invoice image: {}", invoiceImageUrl);
                } catch (IOException e) {
                    log.error("Failed to upload invoice image: {}", e.getMessage());
                    throw new RuntimeException("Failed to upload invoice image: " + e.getMessage());
                }
            }

            // Create package entity
            Package packageEntity = new Package();
            packageEntity.setSender(sender);
            packageEntity.setProductName(request.getProductName());
            packageEntity.setProductDescription(request.getProductDescription());
            packageEntity.setProductValue(request.getProductValue());
            packageEntity.setProductType(request.getProductType());
            packageEntity.setTransportType(request.getTransportType());
            packageEntity.setWeight(request.getWeight());
            packageEntity.setLength(request.getLength());
            packageEntity.setWidth(request.getWidth());
            packageEntity.setHeight(request.getHeight());
            packageEntity.setAvailableTime(request.getAvailableTime());
            packageEntity.setDeadlineTime(request.getDeadlineTime());
            packageEntity.setProductImages(productImageUrls);
            packageEntity.setProductInvoiceImage(invoiceImageUrl);
            packageEntity.setFromAddress(request.getFromAddress());
            packageEntity.setToAddress(request.getToAddress());
            packageEntity.setAddressId(request.getAddressId());
            packageEntity.setPickUpDate(request.getPickUpDate());
            packageEntity.setDropDate(request.getDropDate());
            packageEntity.setTripCharge(request.getTripCharge());
            packageEntity.setPricePerKg(request.getPricePerKg());
            packageEntity.setPricePerTon(request.getPricePerTon());
            packageEntity.setUrl(sender.getProfileUrl());
            packageEntity.setInsurance(request.getInsurance() != null ? request.getInsurance() : false);
            packageEntity.setPickupOtp(generateOTP());
            packageEntity.setDeliveryOtp(generateOTP());
            packageEntity.setStatus(Package.PackageStatus.CREATED);
            packageEntity.setLatitude(request.getLatitude());
            packageEntity.setLongitude(request.getLongitude());
            packageEntity.setToLatitude(request.getToLatitude());   // ← ADD
            packageEntity.setToLongitude(request.getToLongitude()); // ← ADD
            log.info("📦 Package coords saved — FROM: ({}, {}) TO: ({}, {})",
                    request.getLatitude(), request.getLongitude(),
                    request.getToLatitude(), request.getToLongitude());
            Package savedPackage = packageRepository.save(packageEntity);

            if (savedPackage.getInsurance()) {
                createInsuranceForPackage(savedPackage);
            }

            // ✅ Notify nearby online carriers — runs async, never delays this response
            riderNotificationService.notifyNearbyOnlineCarriers(savedPackage);

            log.info("Package created successfully: {} with {} images",
                    savedPackage.getPackageId(), productImageUrls.size());
            return mapToPackageResponse(savedPackage);

        } catch (Exception e) {
            log.error("Error creating package: {}", e.getMessage());
            throw new RuntimeException("Failed to create package: " + e.getMessage());
        }
    }

    // ==================== UPDATE PACKAGE ====================

    @CacheEvict(value = {"senderPackages", "packageById", "packageStats", "geospatialPackages"}, allEntries = true)
    public PackageResponse updatePackage(String userId, Long packageId, PackageRequest request,
                                         List<MultipartFile> productImages,
                                         MultipartFile invoiceImage) {

        log.info("Starting package update for packageId: {} by userId: {}", packageId, userId);

        Package packageEntity = packageRepository.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));

        if (!packageEntity.getSender().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Package does not belong to sender");
        }

        if (packageEntity.getStatus() != Package.PackageStatus.CREATED) {
            throw new IllegalArgumentException("Cannot update package once requests are sent");
        }

        try {
            List<String> productImageUrls = new ArrayList<>();

            if (productImages == null || productImages.isEmpty()) {
                log.info("No new product images provided, keeping existing images");
                productImageUrls = packageEntity.getProductImages();
            } else {
                log.info("Processing {} new product images", productImages.size());

                if (packageEntity.getProductImages() != null && !packageEntity.getProductImages().isEmpty()) {
                    for (String oldImageUrl : packageEntity.getProductImages()) {
                        try {
                            callBackService.deleteFromS3(oldImageUrl);
                            log.info("Deleted old product image: {}", oldImageUrl);
                        } catch (Exception e) {
                            log.warn("Failed to delete old product image: {}", oldImageUrl);
                        }
                    }
                }

                for (MultipartFile image : productImages) {
                    if (image != null && !image.isEmpty()) {
                        String imageUrl = callBackService.uploadToS3(
                                image.getInputStream(),
                                image.getContentType(),
                                image.getSize()
                        );
                        productImageUrls.add(imageUrl);
                        log.info("Uploaded new product image: {}", imageUrl);
                    }
                }
            }

            String invoiceImageUrl = packageEntity.getProductInvoiceImage();
            if (invoiceImage != null && !invoiceImage.isEmpty()) {
                log.info("Processing new invoice image");

                if (packageEntity.getProductInvoiceImage() != null) {
                    try {
                        callBackService.deleteFromS3(packageEntity.getProductInvoiceImage());
                        log.info("Deleted old invoice image");
                    } catch (Exception e) {
                        log.warn("Failed to delete old invoice image: {}", packageEntity.getProductInvoiceImage());
                    }
                }

                invoiceImageUrl = callBackService.uploadDocumentToS3(invoiceImage, "invoice");
                log.info("Uploaded new invoice image: {}", invoiceImageUrl);
            }

            packageEntity.setProductName(request.getProductName());
            packageEntity.setProductDescription(request.getProductDescription());
            packageEntity.setProductValue(request.getProductValue());
            packageEntity.setProductType(request.getProductType());
            packageEntity.setTransportType(request.getTransportType());
            packageEntity.setWeight(request.getWeight());
            packageEntity.setLength(request.getLength());
            packageEntity.setWidth(request.getWidth());
            packageEntity.setHeight(request.getHeight());
            packageEntity.setProductImages(productImageUrls);
            packageEntity.setProductInvoiceImage(invoiceImageUrl);
            packageEntity.setFromAddress(request.getFromAddress());
            packageEntity.setToAddress(request.getToAddress());
            packageEntity.setLatitude(request.getLatitude());
            packageEntity.setLongitude(request.getLongitude());
            packageEntity.setToLatitude(request.getToLatitude());   // ← ADD
            packageEntity.setToLongitude(request.getToLongitude()); // ← ADD
            log.info("📦 Package coords updated — FROM: ({}, {}) TO: ({}, {})",
                    request.getLatitude(), request.getLongitude(),
                    request.getToLatitude(), request.getToLongitude());
            packageEntity.setPickUpDate(request.getPickUpDate());
            packageEntity.setDropDate(request.getDropDate());
            packageEntity.setTripCharge(request.getTripCharge());
            packageEntity.setPricePerKg(request.getPricePerKg());
            packageEntity.setPricePerTon(request.getPricePerTon());

            boolean wasInsured = packageEntity.getInsurance();
            boolean nowInsured = request.getInsurance() != null ? request.getInsurance() : false;
            packageEntity.setInsurance(nowInsured);

            if (!wasInsured && nowInsured) {
                log.info("Adding insurance to package");
                createInsuranceForPackage(packageEntity);
            } else if (wasInsured && !nowInsured) {
                log.info("Removing insurance from package");
                removeInsuranceForPackage(packageEntity);
            }

            Package updatedPackage = packageRepository.save(packageEntity);
            log.info("Package saved successfully: {}", updatedPackage.getPackageId());

            try {
                log.info("Mapping package to response...");
                PackageResponse response = mapToPackageResponse(updatedPackage);
                log.info("Response mapped successfully");
                return response;
            } catch (Exception mappingError) {
                log.error("RESPONSE MAPPING ERROR: {}", mappingError.getMessage(), mappingError);
                return createMinimalResponse(updatedPackage);
            }

        } catch (IOException e) {
            log.error("Error uploading images during package update: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload images: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during package update: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update package: " + e.getMessage());
        }
    }

    private PackageResponse createMinimalResponse(Package pkg) {
        log.info("Creating minimal response for package: {}", pkg.getPackageId());
        PackageResponse response = new PackageResponse();
        try {
            response.setPackageId(pkg.getPackageId());
            response.setProductName(pkg.getProductName());
            response.setProductDescription(pkg.getProductDescription());
            response.setProductValue(pkg.getProductValue());
            response.setProductType(pkg.getProductType());
            response.setTransportType(pkg.getTransportType());
            response.setWeight(pkg.getWeight());
            response.setFromAddress(pkg.getFromAddress());
            response.setToAddress(pkg.getToAddress());
            response.setStatus(pkg.getStatus());
            response.setPickUpDate(pkg.getPickUpDate());
            response.setDropDate(pkg.getDropDate());
            if (pkg.getSender() != null) response.setSenderName(pkg.getSender().getFullName());
            if (pkg.getAvailableTime() != null) response.setAvailableTime(pkg.getAvailableTime().toString());
            if (pkg.getDeadlineTime() != null) response.setDeadlineTime(pkg.getDeadlineTime().toString());
            try {
                if (pkg.getProductImages() != null) response.setProductImages(pkg.getProductImages());
            } catch (Exception e) {
                response.setProductImages(new ArrayList<>());
            }
            response.setProductInvoiceImage(pkg.getProductInvoiceImage());
            response.setTripCharge(pkg.getTripCharge());
            response.setPricePerKg(pkg.getPricePerKg());
            response.setPricePerTon(pkg.getPricePerTon());
            response.setInsurance(pkg.getInsurance());
            response.setCreatedAt(pkg.getCreatedAt());
        } catch (Exception e) {
            log.error("Error creating minimal response: {}", e.getMessage(), e);
            response.setPackageId(pkg.getPackageId());
            response.setStatus(pkg.getStatus());
        }
        return response;
    }

    public PackageResponse updatePackage(String userId, Long packageId, PackageRequest request) {
        return updatePackage(userId, packageId, request, null, null);
    }

    // ==================== GET PACKAGES ====================

    @Cacheable(value = "senderPackages", key = "#userId")
    public List<PackageResponse> getSenderPackages(String userId) {
        log.info("Cache MISS - Fetching packages for sender: {}", userId);
        User sender = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));
        List<Package> packages = packageRepository.findBySenderOrderByCreatedAtDesc(sender);
        return packages.stream().map(this::mapToPackageResponseOptimized).collect(Collectors.toList());
    }

    public List<PackageResponse> getAll() {
        log.info("Fetching all packages");
        List<Package> packages = packageRepository.findAllWithSender();
        return packages.stream().map(this::mapToPackageResponseOptimized).collect(Collectors.toList());
    }

    @Cacheable(value = "packageById", key = "#packageId")
    public PackageResponse getPackageById(String userId, Long packageId) {
        Package packageEntity = packageRepository.findByIdWithSender(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));
        if (!packageEntity.getSender().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Package does not belong to sender");
        }
        return mapToPackageResponse(packageEntity);
    }

    @Cacheable(value = "packageById", key = "#packageId")
    public PackageResponse getPackageByJustId(Long packageId) {
        Package pkg = packageRepository.findByIdWithSender(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found with id=" + packageId));

        log.info("Sender: {}", (pkg.getSender() != null ? pkg.getSender().getFullName() : "null"));
        log.info("Product: {} | Description: {} | Type: {}", pkg.getProductName(), pkg.getProductDescription(), pkg.getProductType());
        log.info("From: {} | To: {}", pkg.getFromAddress(), pkg.getToAddress());
        log.info("Weight: {}, PricePerKg: {}", pkg.getWeight(), pkg.getPricePerKg());

        PackageResponse response = new PackageResponse();
        try {
            response.setPackageId(pkg.getPackageId());
            response.setSenderName(pkg.getSender() != null ? pkg.getSender().getFullName() : null);
            response.setProductName(pkg.getProductName());
            response.setProductDescription(pkg.getProductDescription());
            response.setProductValue(pkg.getProductValue());
            response.setProductType(pkg.getProductType());
            response.setTransportType(pkg.getTransportType());
            response.setWeight(pkg.getWeight());
            response.setLength(pkg.getLength());
            response.setWidth(pkg.getWidth());
            response.setHeight(pkg.getHeight());
            response.setProductImages(pkg.getProductImages());
            response.setProductInvoiceImage(pkg.getProductInvoiceImage());
            response.setFromAddress(pkg.getFromAddress());
            response.setToAddress(pkg.getToAddress());
            response.setPickUpDate(pkg.getPickUpDate());
            response.setDropDate(pkg.getDropDate());
            response.setAvailableTime(pkg.getAvailableTime().toString());
            response.setDeadlineTime(pkg.getDeadlineTime().toString());
            response.setTripCharge(pkg.getTripCharge());
            response.setPricePerKg(pkg.getPricePerKg());
            response.setPricePerTon(pkg.getPricePerTon());
            response.setInsurance(pkg.getInsurance());
            response.setStatus(pkg.getStatus());
            response.setPickupOtp(pkg.getPickupOtp());
            response.setDeliveryOtp(pkg.getDeliveryOtp());
            response.setCreatedAt(pkg.getCreatedAt());
            response.setUrl(pkg.getUrl());
            log.info("Fetched Package: {}", response);
        } catch (Exception e) {
            log.error("Error constructing PackageResponse DTO for package {}: {}", pkg.getPackageId(), e.getMessage(), e);
            throw e;
        }
        return response;
    }

    // ==================== DELETE PACKAGE ====================

    @CacheEvict(value = {"senderPackages", "packageById", "packageStats", "geospatialPackages"}, allEntries = true)
    public void deletePackage(String userId, Long packageId) {
        Package packageEntity = packageRepository.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));

        if (!packageEntity.getSender().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Package does not belong to sender");
        }

        if (packageEntity.getStatus() != Package.PackageStatus.CREATED) {
            throw new IllegalArgumentException("Cannot delete package with active requests");
        }

        if (packageEntity.getProductImages() != null && !packageEntity.getProductImages().isEmpty()) {
            for (String imageUrl : packageEntity.getProductImages()) {
                try { callBackService.deleteFromS3(imageUrl); }
                catch (Exception e) { log.warn("Failed to delete product image: {}", imageUrl); }
            }
        }

        if (packageEntity.getProductInvoiceImage() != null) {
            try { callBackService.deleteFromS3(packageEntity.getProductInvoiceImage()); }
            catch (Exception e) { log.warn("Failed to delete invoice image: {}", packageEntity.getProductInvoiceImage()); }
        }

        packageRepository.delete(packageEntity);
        log.info("Package deleted successfully: {}", packageId);
    }

    // ==================== STATUS & OTP ====================

    public List<PackageResponse> getActivePackages(String userId) {
        User sender = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));
        List<Package> packages = packageRepository.findActiveBySender(sender);
        return packages.stream().map(this::mapToPackageResponseOptimized).collect(Collectors.toList());
    }

    @CacheEvict(value = {"senderPackages", "packageById", "packageStats"}, allEntries = true)
    public PackageResponse updatePackageStatus(Long packageId, Package.PackageStatus status) {
        Package packageEntity = packageRepository.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));
        packageEntity.setStatus(status);
        Package updatedPackage = packageRepository.save(packageEntity);
        log.info("Package status updated to {} for package: {}", status, packageId);
        return mapToPackageResponse(updatedPackage);
    }

    public boolean verifyPickupOtp(Long packageId, String otp) {
        Package packageEntity = packageRepository.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));
        boolean isValid = packageEntity.getPickupOtp().equals(otp);
        if (isValid) {
            packageEntity.setStatus(Package.PackageStatus.PICKED_UP);
            packageRepository.save(packageEntity);
            log.info("Pickup OTP verified for package: {}", packageId);
        }
        return isValid;
    }

    public boolean verifyDeliveryOtp(Long packageId, String otp) {
        Package packageEntity = packageRepository.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));
        boolean isValid = packageEntity.getDeliveryOtp().equals(otp);
        if (isValid) {
            packageEntity.setStatus(Package.PackageStatus.DELIVERED);
            packageRepository.save(packageEntity);
            log.info("Delivery OTP verified for package: {}", packageId);
        }
        return isValid;
    }

    @Cacheable(value = "packageStats", key = "#userId")
    public PackageStatsDto getPackageStats(String userId) {
        log.info("Cache MISS - Fetching package statistics for user: {}", userId);
        User sender = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));
        PackageStatsDto stats = new PackageStatsDto();
        stats.setTotalPackages(packageRepository.countBySender(sender));
        stats.setCreatedPackages(packageRepository.countBySenderAndStatus(sender, Package.PackageStatus.CREATED));
        stats.setInTransitPackages(packageRepository.countBySenderAndStatus(sender, Package.PackageStatus.IN_TRANSIT));
        stats.setDeliveredPackages(packageRepository.countBySenderAndStatus(sender, Package.PackageStatus.DELIVERED));
        stats.setCancelledPackages(packageRepository.countBySenderAndStatus(sender, Package.PackageStatus.CANCELLED));
        return stats;
    }

    // ==================== GEOSPATIAL SEARCH ====================

    @Cacheable(value = "geospatialPackages", key = "#latitude + '-' + #longitude + '-' + #radiusKm")
    public List<GeospatialPackageResponse> searchPackagesWithinRadius(
            double latitude, double longitude, double radiusKm) {
        log.info("Cache MISS - Searching packages within {}km radius", radiusKm);
        List<Long> packageIds = packageRepository.findPackageIdsWithinRadius(latitude, longitude, radiusKm);
        if (packageIds.isEmpty()) return Collections.emptyList();
        List<Package> packages = packageRepository.findByIdInWithSender(packageIds);
        return packages.stream()
                .map(pkg -> mapToGeospatialResponse(pkg, latitude, longitude))
                .collect(Collectors.toList());
    }

    public Map<String, Object> searchPackagesAlongRoute(RouteSearchRequest request) {
        Map<String, Object> result = new HashMap<>();

        List<GeospatialPackageResponse> nearStart = findPackagesNearPoint(
                request.getFromLatitude(), request.getFromLongitude(),
                request.getNearPointRadiusKm(), request, "NEAR_START");

        List<GeospatialPackageResponse> alongRoute = findPackagesInRouteCorridor(request);

        List<GeospatialPackageResponse> nearDestination = findPackagesNearPoint(
                request.getToLatitude(), request.getToLongitude(),
                request.getNearPointRadiusKm(), request, "NEAR_DESTINATION");

        Set<Long> addedPackageIds = new HashSet<>();
        List<GeospatialPackageResponse> uniqueNearStart = nearStart.stream()
                .filter(p -> addedPackageIds.add(p.getPackageId())).collect(Collectors.toList());
        List<GeospatialPackageResponse> uniqueAlongRoute = alongRoute.stream()
                .filter(p -> addedPackageIds.add(p.getPackageId())).collect(Collectors.toList());
        List<GeospatialPackageResponse> uniqueNearDestination = nearDestination.stream()
                .filter(p -> addedPackageIds.add(p.getPackageId())).collect(Collectors.toList());

        double totalRouteDistance = calculateDistance(
                request.getFromLatitude(), request.getFromLongitude(),
                request.getToLatitude(), request.getToLongitude());

        result.put("routeInfo", Map.of(
                "fromCoordinates", List.of(request.getFromLatitude(), request.getFromLongitude()),
                "toCoordinates", List.of(request.getToLatitude(), request.getToLongitude()),
                "totalDistanceKm", Math.round(totalRouteDistance * 100.0) / 100.0));
        result.put("nearStart", uniqueNearStart);
        result.put("alongRoute", uniqueAlongRoute);
        result.put("nearDestination", uniqueNearDestination);
        result.put("summary", Map.of(
                "nearStart", uniqueNearStart.size(),
                "alongRoute", uniqueAlongRoute.size(),
                "nearDestination", uniqueNearDestination.size(),
                "total", uniqueNearStart.size() + uniqueAlongRoute.size() + uniqueNearDestination.size()));
        result.put("searchTimestamp", LocalDateTime.now());

        log.info("Route search completed: {} total packages found",
                uniqueNearStart.size() + uniqueAlongRoute.size() + uniqueNearDestination.size());
        return result;
    }

    public Map<String, Object> findRouteMatchingPackages(RouteMatchingRequest request) {
        Map<String, Object> result = new HashMap<>();

        log.info("🔎 [findRouteMatchingPackages] Carrier route: ({},{}) → ({},{}), maxDeviationKm: {}",
                request.getFromLatitude(), request.getFromLongitude(),
                request.getToLatitude(), request.getToLongitude(),
                request.getMaxDeviationKm());

        double routeBearing = calculateBearing(
                request.getFromLatitude(), request.getFromLongitude(),
                request.getToLatitude(), request.getToLongitude());

        double bufferKm = request.getMaxDeviationKm();
        double minLat = Math.min(request.getFromLatitude(), request.getToLatitude()) - (bufferKm / 111.32);
        double maxLat = Math.max(request.getFromLatitude(), request.getToLatitude()) + (bufferKm / 111.32);
        double minLng = Math.min(request.getFromLongitude(), request.getToLongitude()) - (bufferKm / 111.32);
        double maxLng = Math.max(request.getFromLongitude(), request.getToLongitude()) + (bufferKm / 111.32);

        List<Long> packageIds = packageRepository.findPackageIdsInBoundingBox(
                minLat, maxLat, minLng, maxLng, Package.PackageStatus.CREATED, LocalDate.now().toString());

        log.info("📦 [findRouteMatchingPackages] Found {} candidate packages in bounding box", packageIds.size());

        if (packageIds.isEmpty()) {
            result.put("matchingPackages", Collections.emptyList());
            result.put("totalMatches", 0);
            return result;
        }

        List<Package> candidatePackages = packageRepository.findByIdInWithSender(packageIds);
        List<GeospatialPackageResponse> matchingPackages = new ArrayList<>();

        for (Package pkg : candidatePackages) {
            if (!applyRouteMatchingFilters(pkg, request)) {
                log.info("⛔ [findRouteMatchingPackages] Package {} filtered out by basic filters", pkg.getPackageId());
                continue;
            }

            // Block packages with missing/zero FROM coords
            if (pkg.getLatitude() == 0.0 && pkg.getLongitude() == 0.0) {
                log.warn("⚠️ [findRouteMatchingPackages] Package {} has 0,0 FROM coords — skipped", pkg.getPackageId());
                continue;
            }

            // Check FROM point against carrier route corridor
            double fromDistanceToRoute = calculateDistanceToRoute(
                    request.getFromLatitude(), request.getFromLongitude(),
                    request.getToLatitude(), request.getToLongitude(),
                    pkg.getLatitude(), pkg.getLongitude());

            log.info("🔍 [findRouteMatchingPackages] Package {} FROM ({},{}) → '{}' dist to carrier route: {} km (limit: {} km)",
                    pkg.getPackageId(), pkg.getLatitude(), pkg.getLongitude(),
                    pkg.getFromAddress(),
                    String.format("%.2f", fromDistanceToRoute), request.getMaxDeviationKm());

            if (fromDistanceToRoute > request.getMaxDeviationKm()) {
                log.info("❌ [findRouteMatchingPackages] Package {} FROM point too far — skipped", pkg.getPackageId());
                continue;
            }

            // Check TO point against carrier route corridor
            boolean toPointOk = true;
            if (pkg.getToLatitude() != 0.0 && pkg.getToLongitude() != 0.0) {
                double toDistanceToRoute = calculateDistanceToRoute(
                        request.getFromLatitude(), request.getFromLongitude(),
                        request.getToLatitude(), request.getToLongitude(),
                        pkg.getToLatitude(), pkg.getToLongitude());
                toPointOk = toDistanceToRoute <= request.getMaxDeviationKm();
                log.info("🔍 [findRouteMatchingPackages] Package {} TO ({},{}) → '{}' dist to carrier route: {} km — {}",
                        pkg.getPackageId(), pkg.getToLatitude(), pkg.getToLongitude(),
                        pkg.getToAddress(),
                        String.format("%.2f", toDistanceToRoute),
                        toPointOk ? "✅ pass" : "❌ fail");
            } else {
                log.warn("⚠️ [findRouteMatchingPackages] Package {} has no TO coords — skipping TO point check", pkg.getPackageId());
            }

            if (!toPointOk) {
                log.info("❌ [findRouteMatchingPackages] Package {} TO point outside corridor — skipped", pkg.getPackageId());
                continue;
            }

            GeospatialPackageResponse response = mapToGeospatialResponse(
                    pkg, request.getFromLatitude(), request.getFromLongitude());
            response.setDistanceFromRoute(Math.round(fromDistanceToRoute * 100.0) / 100.0);
            response.setDistanceCategory("ROUTE_MATCH");
            matchingPackages.add(response);
            log.info("✅ [findRouteMatchingPackages] Package {} '{}' → '{}' added as ROUTE_MATCH",
                    pkg.getPackageId(), pkg.getFromAddress(), pkg.getToAddress());
        }

        matchingPackages.sort(Comparator.comparing(GeospatialPackageResponse::getDistanceFromRoute));
        if (matchingPackages.size() > request.getMaxResults()) {
            matchingPackages = matchingPackages.subList(0, request.getMaxResults());
        }

        log.info("✅ [findRouteMatchingPackages] Final matches: {}", matchingPackages.size());

        result.put("routeInfo", Map.of(
                "fromCoordinates", List.of(request.getFromLatitude(), request.getFromLongitude()),
                "toCoordinates", List.of(request.getToLatitude(), request.getToLongitude()),
                "routeBearing", Math.round(routeBearing * 100.0) / 100.0));
        result.put("matchingPackages", matchingPackages);
        result.put("totalMatches", matchingPackages.size());
        result.put("searchTimestamp", LocalDateTime.now());
        return result;
    }
    public Map<String, List<GeospatialPackageResponse>> searchPackagesByDistanceCategory(
            double latitude, double longitude,
            double nearRadius, double mediumRadius, double maxRadius) {

        List<Long> packageIds = packageRepository.findPackageIdsWithinRadius(latitude, longitude, maxRadius);
        if (packageIds.isEmpty()) {
            return Map.of("NEAR", List.of(), "MEDIUM", List.of(), "FAR", List.of());
        }

        List<Package> packages = packageRepository.findByIdInWithSender(packageIds);
        Map<String, List<GeospatialPackageResponse>> categorizedPackages = new HashMap<>();
        categorizedPackages.put("NEAR", new ArrayList<>());
        categorizedPackages.put("MEDIUM", new ArrayList<>());
        categorizedPackages.put("FAR", new ArrayList<>());

        for (Package pkg : packages) {
            double distance = calculateDistance(latitude, longitude, pkg.getLatitude(), pkg.getLongitude());
            GeospatialPackageResponse response = mapToGeospatialResponse(pkg, latitude, longitude);
            if (distance <= nearRadius) {
                response.setDistanceCategory("NEAR");
                categorizedPackages.get("NEAR").add(response);
            } else if (distance <= mediumRadius) {
                response.setDistanceCategory("MEDIUM");
                categorizedPackages.get("MEDIUM").add(response);
            } else {
                response.setDistanceCategory("FAR");
                categorizedPackages.get("FAR").add(response);
            }
        }
        return categorizedPackages;
    }

    public List<GeospatialPackageResponse> searchPackagesInArea(
            double minLat, double maxLat, double minLng, double maxLng) {
        List<Long> packageIds = packageRepository.findPackageIdsInBoundingBox(
                minLat, maxLat, minLng, maxLng, Package.PackageStatus.CREATED, LocalDate.now().toString());
        if (packageIds.isEmpty()) return Collections.emptyList();
        List<Package> packages = packageRepository.findByIdInWithSender(packageIds);
        double centerLat = (minLat + maxLat) / 2;
        double centerLng = (minLng + maxLng) / 2;
        return packages.stream()
                .map(pkg -> mapToGeospatialResponse(pkg, centerLat, centerLng))
                .collect(Collectors.toList());
    }

    public Map<String, Object> searchPackagesUnified(GeospatialSearchRequest request) {
        Map<String, Object> result = new HashMap<>();

        log.info("🔎 [searchPackagesUnified] Carrier route: ({},{}) → ({},{}), corridorKm: {}",
                request.getFromLatitude(), request.getFromLongitude(),
                request.getToLatitude(), request.getToLongitude(),
                request.getCorridorWidthKm());

        double maxSearchRadius = Math.max(request.getNearbyRadiusKm(), request.getFarRadiusKm());
        List<Long> packageIds = packageRepository.findPackageIdsWithinRadius(
                request.getFromLatitude(), request.getFromLongitude(), maxSearchRadius);

        log.info("📦 [searchPackagesUnified] Found {} candidate packages within {}km radius",
                packageIds.size(), maxSearchRadius);

        if (packageIds.isEmpty()) return buildEmptyResult(request);

        List<Package> allPackages = packageRepository.findByIdInWithSender(packageIds);

        List<GeospatialPackageResponse> nearby = new ArrayList<>();
        List<GeospatialPackageResponse> onTheWay = new ArrayList<>();
        List<GeospatialPackageResponse> between = new ArrayList<>();
        List<GeospatialPackageResponse> far = new ArrayList<>();
        Set<Long> addedIds = new HashSet<>();

        for (Package pkg : allPackages) {
            if (!applyFilters(pkg, request)) {
                log.info("⛔ [searchPackagesUnified] Package {} filtered out by basic filters", pkg.getPackageId());
                continue;
            }

            double distance = calculateDistance(
                    request.getFromLatitude(), request.getFromLongitude(),
                    pkg.getLatitude(), pkg.getLongitude());

            GeospatialPackageResponse response = mapToGeospatialResponse(
                    pkg, request.getFromLatitude(), request.getFromLongitude());

            if (distance <= request.getNearbyRadiusKm()) {
                if (nearby.size() < request.getMaxResultsPerCategory() && addedIds.add(pkg.getPackageId())) {
                    response.setDistanceCategory("NEARBY");
                    nearby.add(response);
                    log.info("📍 [searchPackagesUnified] Package {} → NEARBY ({} km)", pkg.getPackageId(), String.format("%.2f", distance));
                }
            } else {
                // Check FROM point of package against carrier route corridor
                double fromDistanceToRoute = calculateDistanceToRoute(
                        request.getFromLatitude(), request.getFromLongitude(),
                        request.getToLatitude(), request.getToLongitude(),
                        pkg.getLatitude(), pkg.getLongitude());

                log.info("🔍 [searchPackagesUnified] Package {} FROM ({},{}) dist to carrier route: {} km",
                        pkg.getPackageId(), pkg.getLatitude(), pkg.getLongitude(),
                        String.format("%.2f", fromDistanceToRoute));

                // Check TO point of package against carrier route corridor
                boolean toPointOk = true;
                if (pkg.getToLatitude() != 0.0 && pkg.getToLongitude() != 0.0) {
                    double toDistanceToRoute = calculateDistanceToRoute(
                            request.getFromLatitude(), request.getFromLongitude(),
                            request.getToLatitude(), request.getToLongitude(),
                            pkg.getToLatitude(), pkg.getToLongitude());
                    toPointOk = toDistanceToRoute <= request.getCorridorWidthKm();
                    log.info("🔍 [searchPackagesUnified] Package {} TO ({},{}) dist to carrier route: {} km — {}",
                            pkg.getPackageId(), pkg.getToLatitude(), pkg.getToLongitude(),
                            String.format("%.2f", toDistanceToRoute),
                            toPointOk ? "✅ pass" : "❌ fail");
                } else {
                    log.warn("⚠️ [searchPackagesUnified] Package {} has no TO coords — skipping TO point check", pkg.getPackageId());
                }

                if (fromDistanceToRoute <= request.getCorridorWidthKm() && toPointOk) {
                    if (onTheWay.size() < request.getMaxResultsPerCategory() && addedIds.add(pkg.getPackageId())) {
                        response.setDistanceCategory("ON_THE_WAY");
                        response.setIsOnRoute(true);
                        onTheWay.add(response);
                        log.info("✅ [searchPackagesUnified] Package {} → ON_THE_WAY", pkg.getPackageId());
                    }
                } else if (distance <= request.getFarRadiusKm()) {
                    if (far.size() < request.getMaxResultsPerCategory() && addedIds.add(pkg.getPackageId())) {
                        response.setDistanceCategory("FAR");
                        far.add(response);
                        log.info("📍 [searchPackagesUnified] Package {} → FAR ({} km) — FROM ok: {}, TO ok: {}",
                                pkg.getPackageId(), String.format("%.2f", distance),
                                fromDistanceToRoute <= request.getCorridorWidthKm(), toPointOk);
                    }
                } else {
                    log.info("❌ [searchPackagesUnified] Package {} not added to any category — distance: {} km, FROM corridor: {} km, TO ok: {}",
                            pkg.getPackageId(), String.format("%.2f", distance),
                            String.format("%.2f", fromDistanceToRoute), toPointOk);
                }
            }
        }

        log.info("✅ [searchPackagesUnified] Result — nearby: {}, onTheWay: {}, far: {}, total: {}",
                nearby.size(), onTheWay.size(), far.size(),
                nearby.size() + onTheWay.size() + far.size());

        result.put("nearby", nearby);
        result.put("onTheWay", onTheWay);
        result.put("between", between);
        result.put("far", far);
        result.put("summary", Map.of(
                "nearby", nearby.size(), "onTheWay", onTheWay.size(),
                "between", between.size(), "far", far.size(),
                "total", nearby.size() + onTheWay.size() + between.size() + far.size()));
        result.put("searchParameters", request);
        result.put("searchTimestamp", LocalDateTime.now());
        result.put("routeDistance", Math.round(calculateDistance(
                request.getFromLatitude(), request.getFromLongitude(),
                request.getToLatitude(), request.getToLongitude()) * 100.0) / 100.0);

        return result;
    }
    private Map<String, Object> buildEmptyResult(GeospatialSearchRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("nearby", Collections.emptyList());
        result.put("onTheWay", Collections.emptyList());
        result.put("between", Collections.emptyList());
        result.put("far", Collections.emptyList());
        result.put("summary", Map.of("nearby", 0, "onTheWay", 0, "between", 0, "far", 0, "total", 0));
        result.put("searchParameters", request);
        result.put("searchTimestamp", LocalDateTime.now());
        return result;
    }

    public Map<String, Object> searchPackagesUnifiedWithFilters(GeospatialSearchRequest request) {
        Map<String, Object> allResults = searchPackagesUnified(request);

        for (String category : List.of("nearby", "onTheWay", "between", "far")) {
            if (allResults.containsKey(category) && allResults.get(category) instanceof List) {
                @SuppressWarnings("unchecked")
                List<GeospatialPackageResponse> packageResponses = (List<GeospatialPackageResponse>) allResults.get(category);

                List<GeospatialPackageResponse> filtered = packageResponses.stream()
                        .filter(pkgResponse -> {
                            boolean typeOk = request.getTransportType() == null ||
                                    pkgResponse.getTransportType().equals(request.getTransportType());
                            boolean dateOk = true;
                            if (request.getPickUpDate() != null && pkgResponse.getPickUpDate() != null)
                                dateOk = pkgResponse.getPickUpDate().equals(request.getPickUpDate());
                            if (dateOk && request.getDropDate() != null && pkgResponse.getDropDate() != null)
                                dateOk = pkgResponse.getDropDate().equals(request.getDropDate());
                            return typeOk && dateOk;
                        })
                        .collect(Collectors.toList());
                allResults.put(category, filtered);
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Integer> summary = (Map<String, Integer>) allResults.getOrDefault("summary", new HashMap<>());
        for (String category : List.of("nearby", "onTheWay", "between", "far")) {
            @SuppressWarnings("unchecked")
            List<?> filtered = (List<?>) allResults.getOrDefault(category, Collections.emptyList());
            summary.put(category, filtered.size());
        }
        int total = List.of("nearby", "onTheWay", "between", "far").stream()
                .mapToInt(cat -> { @SuppressWarnings("unchecked") List<?> l = (List<?>) allResults.getOrDefault(cat, Collections.emptyList()); return l.size(); })
                .sum();
        summary.put("total", total);
        allResults.put("summary", summary);
        return allResults;
    }

    // ==================== HELPER METHODS ====================

    private List<GeospatialPackageResponse> findNearbyPackages(GeospatialSearchRequest request) {
        List<Long> packageIds = packageRepository.findPackageIdsWithinRadius(
                request.getFromLatitude(), request.getFromLongitude(), request.getNearbyRadiusKm());
        if (packageIds.isEmpty()) return Collections.emptyList();
        List<Package> packages = packageRepository.findByIdInWithSender(packageIds);
        return packages.stream()
                .filter(pkg -> applyFilters(pkg, request))
                .limit(request.getMaxResultsPerCategory())
                .map(pkg -> { GeospatialPackageResponse r = mapToGeospatialResponse(pkg, request.getFromLatitude(), request.getFromLongitude()); r.setDistanceCategory("NEARBY"); return r; })
                .collect(Collectors.toList());
    }

    private List<GeospatialPackageResponse> findFarPackages(GeospatialSearchRequest request) {
        List<Long> packageIds = packageRepository.findPackageIdsWithinRadius(
                request.getFromLatitude(), request.getFromLongitude(), request.getFarRadiusKm());
        if (packageIds.isEmpty()) return Collections.emptyList();
        List<Package> packages = packageRepository.findByIdInWithSender(packageIds);
        return packages.stream()
                .filter(pkg -> {
                    double d = calculateDistance(request.getFromLatitude(), request.getFromLongitude(), pkg.getLatitude(), pkg.getLongitude());
                    return d > request.getNearbyRadiusKm();
                })
                .filter(pkg -> applyFilters(pkg, request))
                .limit(request.getMaxResultsPerCategory())
                .map(pkg -> { GeospatialPackageResponse r = mapToGeospatialResponse(pkg, request.getFromLatitude(), request.getFromLongitude()); r.setDistanceCategory("FAR"); return r; })
                .collect(Collectors.toList());
    }

    private List<GeospatialPackageResponse> findBetweenPackages(GeospatialSearchRequest request) {
        double minLat = Math.min(request.getFromLatitude(), request.getToLatitude()) - (request.getBetweenBufferKm() / 111.32);
        double maxLat = Math.max(request.getFromLatitude(), request.getToLatitude()) + (request.getBetweenBufferKm() / 111.32);
        double minLng = Math.min(request.getFromLongitude(), request.getToLongitude()) - (request.getBetweenBufferKm() / 111.32);
        double maxLng = Math.max(request.getFromLongitude(), request.getToLongitude()) + (request.getBetweenBufferKm() / 111.32);
        List<Long> packageIds = packageRepository.findPackageIdsInBoundingBox(minLat, maxLat, minLng, maxLng, Package.PackageStatus.CREATED, LocalDate.now().toString());
        if (packageIds.isEmpty()) return Collections.emptyList();
        List<Package> packages = packageRepository.findByIdInWithSender(packageIds);
        return packages.stream()
                .filter(pkg -> {
                    double ds = calculateDistance(request.getFromLatitude(), request.getFromLongitude(), pkg.getLatitude(), pkg.getLongitude());
                    double de = calculateDistance(request.getToLatitude(), request.getToLongitude(), pkg.getLatitude(), pkg.getLongitude());
                    return ds > request.getNearbyRadiusKm() && de > request.getNearbyRadiusKm();
                })
                .filter(pkg -> applyFilters(pkg, request))
                .limit(request.getMaxResultsPerCategory())
                .map(pkg -> { GeospatialPackageResponse r = mapToGeospatialResponse(pkg, request.getFromLatitude(), request.getFromLongitude()); r.setDistanceCategory("BETWEEN"); r.setIsOnRoute(false); return r; })
                .collect(Collectors.toList());
    }

    private List<GeospatialPackageResponse> findOnTheWayPackages(GeospatialSearchRequest request) {
        List<Long> packageIds = packageRepository.findPackageIdsAlongRouteSimplified(
                request.getFromLatitude(), request.getFromLongitude(),
                request.getToLatitude(), request.getToLongitude(), request.getCorridorWidthKm());
        if (packageIds.isEmpty()) return Collections.emptyList();
        List<Package> packages = packageRepository.findByIdInWithSender(packageIds);
        return packages.stream()
                .filter(pkg -> applyFilters(pkg, request))
                .limit(request.getMaxResultsPerCategory())
                .map(pkg -> { GeospatialPackageResponse r = mapToGeospatialResponse(pkg, request.getFromLatitude(), request.getFromLongitude()); r.setDistanceCategory("ON_THE_WAY"); r.setIsOnRoute(true); return r; })
                .collect(Collectors.toList());
    }

    private List<GeospatialPackageResponse> findPackagesNearPoint(
            double latitude, double longitude, double radiusKm,
            RouteSearchRequest request, String category) {
        List<Long> packageIds = packageRepository.findPackageIdsWithinRadius(latitude, longitude, radiusKm);
        if (packageIds.isEmpty()) return Collections.emptyList();
        List<Package> packages = packageRepository.findByIdInWithSender(packageIds);
        return packages.stream()
                .filter(pkg -> applyRouteSearchFilters(pkg, request))
                .limit(15)
                .map(pkg -> { GeospatialPackageResponse r = mapToGeospatialResponse(pkg, latitude, longitude); r.setDistanceCategory(category); return r; })
                .collect(Collectors.toList());
    }

    private List<GeospatialPackageResponse> findPackagesInRouteCorridor(RouteSearchRequest request) {
        List<Long> packageIds = packageRepository.findPackageIdsAlongRouteSimplified(
                request.getFromLatitude(), request.getFromLongitude(),
                request.getToLatitude(), request.getToLongitude(), request.getCorridorWidthKm());
        if (packageIds.isEmpty()) return Collections.emptyList();
        List<Package> packages = packageRepository.findByIdInWithSender(packageIds);
        return packages.stream()
                .filter(pkg -> applyRouteSearchFilters(pkg, request))
                .limit(20)
                .map(pkg -> { GeospatialPackageResponse r = mapToGeospatialResponse(pkg, request.getFromLatitude(), request.getFromLongitude()); r.setDistanceCategory("ALONG_ROUTE"); r.setIsOnRoute(true); return r; })
                .collect(Collectors.toList());
    }

    // ── Filters ──────────────────────────────────────────────────────────────

    private boolean applyFilters(Package pkg, GeospatialSearchRequest request) {
        try {
            if (request.getTransportType() != null && pkg.getTransportType() != null
                    && !pkg.getTransportType().equals(request.getTransportType())) return false;
            if (Boolean.TRUE.equals(request.getIncludeInsuredOnly()) && !Boolean.TRUE.equals(pkg.getInsurance())) return false;
            if (request.getMinPackageValue() != null && pkg.getProductValue() != null
                    && pkg.getProductValue() < request.getMinPackageValue()) return false;
            if (request.getMaxPackageValue() != null && pkg.getProductValue() != null
                    && pkg.getProductValue() > request.getMaxPackageValue()) return false;

            if (pkg.getPickUpDate() != null && !pkg.getPickUpDate().isEmpty()) {
                try {
                    LocalDate pickupDate = LocalDate.parse(pkg.getPickUpDate());
                    if (pickupDate.isBefore(LocalDate.now())) return false;
                } catch (Exception e) {
                    log.warn("Could not parse pickUpDate '{}' for package {}", pkg.getPickUpDate(), pkg.getPackageId());
                }
            }

            if (request.getPickUpDate() != null && pkg.getPickUpDate() != null && !pkg.getPickUpDate().isEmpty()
                    && !pkg.getPickUpDate().equals(request.getPickUpDate().toString())) return false;
            if (request.getDropDate() != null && pkg.getDropDate() != null && !pkg.getDropDate().isEmpty()
                    && !pkg.getDropDate().equals(request.getDropDate().toString())) return false;

            return true;
        } catch (Exception e) {
            log.error("Error in applyFilters for package {}: {}", pkg.getPackageId(), e.getMessage());
            return false;
        }
    }

    private boolean applyRouteSearchFilters(Package pkg, RouteSearchRequest request) {
        if (request.getTransportType() != null && !pkg.getTransportType().equals(request.getTransportType())) return false;
        if (Boolean.TRUE.equals(request.getIncludeInsuredOnly()) && !Boolean.TRUE.equals(pkg.getInsurance())) return false;
        if (request.getMinPackageValue() != null && pkg.getProductValue() < request.getMinPackageValue()) return false;
        if (request.getMaxPackageValue() != null && pkg.getProductValue() > request.getMaxPackageValue()) return false;
        if (pkg.getPickUpDate() != null && !pkg.getPickUpDate().isEmpty()) {
            try {
                LocalDate pickupDate = LocalDate.parse(pkg.getPickUpDate());
                if (pickupDate.isBefore(LocalDate.now())) return false;
            } catch (Exception e) {
                log.warn("Could not parse pickUpDate '{}' for package {}", pkg.getPickUpDate(), pkg.getPackageId());
            }
        }
        return true;
    }

    private boolean applyRouteMatchingFilters(Package pkg, RouteMatchingRequest request) {
        if (request.getTransportType() != null && !pkg.getTransportType().equals(request.getTransportType())) return false;
        if (Boolean.TRUE.equals(request.getIncludeInsuredOnly()) && !Boolean.TRUE.equals(pkg.getInsurance())) return false;
        if (pkg.getPickUpDate() != null && !pkg.getPickUpDate().isEmpty()) {
            try {
                LocalDate pickupDate = LocalDate.parse(pkg.getPickUpDate());
                if (pickupDate.isBefore(LocalDate.now())) return false;
            } catch (Exception e) {
                log.warn("Could not parse pickUpDate '{}' for package {}", pkg.getPickUpDate(), pkg.getPackageId());
            }
        }
        return true;
    }

    // ── Insurance & utility ──────────────────────────────────────────────────

    private void createInsuranceForPackage(Package packageEntity) {
        Insurance insurance = new Insurance();
        insurance.setPackageEntity(packageEntity);
        insurance.setProductValue(packageEntity.getProductValue());
        insurance.setInsuranceAmount(packageEntity.getProductValue() * 0.02);
        insurance.setCoveragePercentage(80.0);
        insurance.setStatus(Insurance.InsuranceStatus.ACTIVE);
        insurance.setPolicyNumber("INS" + System.currentTimeMillis());
        insurance.setValidUntil(LocalDateTime.now().plusDays(30));
        insuranceRepository.save(insurance);
        log.info("Insurance created for package: {}", packageEntity.getPackageId());
    }

    private void removeInsuranceForPackage(Package packageEntity) {
        insuranceRepository.findByPackageEntity(packageEntity)
                .ifPresent(insurance -> {
                    insurance.setStatus(Insurance.InsuranceStatus.CANCELLED);
                    insuranceRepository.save(insurance);
                    log.info("Insurance cancelled for package: {}", packageEntity.getPackageId());
                });
    }

    private String generateOTP() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
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
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLng);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private double calculateDistanceToRoute(double routeLat1, double routeLng1, double routeLat2, double routeLng2,
                                            double pointLat, double pointLng) {
        double d13 = calculateDistance(routeLat1, routeLng1, pointLat, pointLng) / 6371.0;
        double brng13 = Math.toRadians(calculateBearing(routeLat1, routeLng1, pointLat, pointLng));
        double brng12 = Math.toRadians(calculateBearing(routeLat1, routeLng1, routeLat2, routeLng2));
        return Math.abs(Math.asin(Math.sin(d13) * Math.sin(brng13 - brng12)) * 6371.0);
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private PackageResponse mapToPackageResponse(Package packageEntity) {
        PackageResponse response = new PackageResponse();
        response.setPackageId(packageEntity.getPackageId());
        response.setSenderName(packageEntity.getSender().getFullName());
        response.setProductName(packageEntity.getProductName());
        response.setProductDescription(packageEntity.getProductDescription());
        response.setProductValue(packageEntity.getProductValue());
        response.setProductType(packageEntity.getProductType());
        response.setTransportType(packageEntity.getTransportType());
        response.setWeight(packageEntity.getWeight());
        response.setLength(packageEntity.getLength());
        response.setWidth(packageEntity.getWidth());
        response.setHeight(packageEntity.getHeight());
        try {
            List<String> images = packageEntity.getProductImages();
            response.setProductImages(images != null ? new ArrayList<>(images) : new ArrayList<>());
        } catch (Exception e) {
            log.warn("Could not load product images for package {}: {}", packageEntity.getPackageId(), e.getMessage());
            response.setProductImages(new ArrayList<>());
        }
        response.setProductInvoiceImage(packageEntity.getProductInvoiceImage());
        response.setFromAddress(packageEntity.getFromAddress());
        response.setToAddress(packageEntity.getToAddress());
        response.setPickUpDate(packageEntity.getPickUpDate());
        response.setDropDate(packageEntity.getDropDate());
        if (packageEntity.getAvailableTime() != null) response.setAvailableTime(packageEntity.getAvailableTime().toString());
        if (packageEntity.getDeadlineTime() != null) response.setDeadlineTime(packageEntity.getDeadlineTime().toString());
        response.setTripCharge(packageEntity.getTripCharge());
        response.setPricePerKg(packageEntity.getPricePerKg());
        response.setPricePerTon(packageEntity.getPricePerTon());
        response.setInsurance(packageEntity.getInsurance());
        response.setStatus(packageEntity.getStatus());
        response.setPickupOtp(packageEntity.getPickupOtp());
        response.setDeliveryOtp(packageEntity.getDeliveryOtp());
        response.setCreatedAt(packageEntity.getCreatedAt());
        response.setUrl(packageEntity.getUrl());
        return response;
    }

    private PackageResponse mapToPackageResponseOptimized(Package packageEntity) {
        return mapToPackageResponse(packageEntity);
    }

    private GeospatialPackageResponse mapToGeospatialResponse(Package pkg, double searchLat, double searchLng) {
        GeospatialPackageResponse response = new GeospatialPackageResponse();
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
        response.setDeadlineTime(pkg.getDeadlineTime().toString());
        response.setAvailableTime(pkg.getAvailableTime().toString());
        response.setUrl("https://cdn-icons-png.flaticon.com/512/3135/3135715.png");
        double distance = calculateDistance(searchLat, searchLng, pkg.getLatitude(), pkg.getLongitude());
        response.setDistanceFromSearchPoint(Math.round(distance * 100.0) / 100.0);
        if (distance <= 2.0) response.setDistanceCategory("NEAR");
        else if (distance <= 10.0) response.setDistanceCategory("MEDIUM");
        else response.setDistanceCategory("FAR");
        return response;
    }
}