package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Dtos.*;
import com.saffaricarrers.saffaricarrers.Entity.*;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import com.saffaricarrers.saffaricarrers.Repository.*;
import com.saffaricarrers.saffaricarrers.Responses.RoutePricingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CarrierRouteService {

    private final CarrierRouteRepository routeRepository;
    private final CarrierProfileRepository carrierProfileRepository;
    private final RoutePricingRepository pricingRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    // ==================== CREATE ROUTE ====================

    @CacheEvict(value = {"carrierRoutes", "nearbyRoutes", "geospatialSearch"}, allEntries = true)
    public CarrierRouteResponse createRoute(String userId, CarrierRouteRequest request) {
        log.info("Creating route from ({},{}) to ({},{}), deadline: {}",
                request.getLatitude(), request.getLongitude(),
                request.getToLatitude(), request.getToLongitude(),
                request.getDeadlineTime());
        log.info(" route from ({},{}) to ({},{}), route data: {}",
               request);
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        CarrierProfile carrierProfile = carrierProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Carrier profile not found"));

        CarrierRoute route = new CarrierRoute();
        route.setCarrierProfile(carrierProfile);
        route.setFromLocation(request.getFromLocation());
        route.setToLocation(request.getToLocation());
        route.setAvailableDate(request.getAvailableDate());
        route.setAvailableTime(request.getAvailableTime());
        route.setDeadlineDate(request.getDeadlineDate());
        route.setDeadlineTime(request.getDeadlineTime());
        route.setTransportType(request.getTransportType());
        route.setMaxWeight(request.getMaxWeight());
        route.setMaxQuantity(request.getMaxQuantity());
        route.setCurrentWeight(0.0);
        route.setCurrentQuantity(0);
        route.setRouteStatus(CarrierRoute.RouteStatus.CREATED);
        route.setLongAddressId(request.getLongAddressId());
        // In createRoute() - store the ORIGIN coordinates
        route.setLatitude(request.getLatitude());       // from-lat
        route.setLongitude(request.getLongitude());     // from-lng
        route.setToLatitude(request.getToLatitude());   // to-lat
        route.setToLongitude(request.getToLongitude()); // to-lng

        CarrierRoute savedRoute = routeRepository.save(route);
        routeRepository.flush();

        log.info("✅ Route saved - ID: {}, Destination: ({},{}), Deadline: {}",
                savedRoute.getRouteId(),
                savedRoute.getLatitude(),
                savedRoute.getLongitude(),
                savedRoute.getDeadlineTime());

        if (request.getPricing() != null && !request.getPricing().isEmpty()) {
            List<RoutePricing> pricingList = request.getPricing().stream()
                    .map(pricingReq -> {
                        RoutePricing pricing = new RoutePricing();
                        pricing.setCarrierRoute(savedRoute);

                        if (pricingReq.getProductType() != null) {
                            pricing.setProductType(
                                    RoutePricing.ProductType.valueOf(pricingReq.getProductType().toString())
                            );
                        }

                        pricing.setWeightLimit(pricingReq.getWeightLimit());
                        pricing.setFixedPrice(pricingReq.getFixedPrice());
                        pricing.setPricePerTon(pricingReq.getPricePerTon());
                        return pricing;
                    })
                    .collect(Collectors.toList());

            pricingRepository.saveAll(pricingList);
            pricingRepository.flush();
        }

        log.info("Route created successfully with ID: {}", savedRoute.getRouteId());
        return mapToCarrierRouteResponse(savedRoute);
    }

    // ==================== GET ROUTES ====================

    @Cacheable(value = "carrierRoutes", key = "#userId")
    public List<CarrierRouteResponse> getCarrierRoutes(String userId) {
        log.info("Cache MISS - Fetching carrier routes for user: {}", userId);

        List<CarrierRoute> routes = routeRepository.findByCarrierProfileUserUserId(userId);

        return routes.stream()
                .map(this::mapToCarrierRouteResponseOptimized)
                .collect(Collectors.toList());
    }

    // ==================== UPDATE ROUTE ====================

    @CacheEvict(value = {"carrierRoutes", "nearbyRoutes", "geospatialSearch", "routeById"}, allEntries = true)
    public CarrierRouteResponse updateRoute(String userId, Long routeId, CarrierRouteRequest request) {
        log.info("Updating route {} for user: {}", routeId, userId);

        CarrierRoute route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));

        if (!route.getCarrierProfile().getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Unauthorized to update this route");
        }

        route.setFromLocation(request.getFromLocation());
        route.setToLocation(request.getToLocation());
        route.setAvailableDate(request.getAvailableDate());
        route.setAvailableTime(request.getAvailableTime());
        route.setDeadlineDate(request.getDeadlineDate());
        route.setDeadlineTime(request.getDeadlineTime());
        route.setTransportType(request.getTransportType());
        route.setMaxWeight(request.getMaxWeight());
        route.setMaxQuantity(request.getMaxQuantity());
        route.setLongAddressId(request.getLongAddressId());
        // ✅ Fix - store ORIGIN coordinates
        route.setLatitude(request.getLatitude());       // from-lat
        route.setLongitude(request.getLongitude());     // from-lng
        route.setToLatitude(request.getToLatitude());   // to-lat
        route.setToLongitude(request.getToLongitude()); // to-lng

        CarrierRoute updatedRoute = routeRepository.save(route);
        routeRepository.flush();

        if (request.getPricing() != null) {
            pricingRepository.deleteByCarrierRoute(route);

            List<RoutePricing> pricingList = request.getPricing().stream()
                    .map(pricingReq -> {
                        RoutePricing pricing = new RoutePricing();
                        pricing.setCarrierRoute(updatedRoute);

                        if (pricingReq.getProductType() != null) {
                            pricing.setProductType(
                                    RoutePricing.ProductType.valueOf(pricingReq.getProductType().toString())
                            );
                        }

                        pricing.setWeightLimit(pricingReq.getWeightLimit());
                        pricing.setFixedPrice(pricingReq.getFixedPrice());
                        pricing.setPricePerTon(pricingReq.getPricePerTon());
                        return pricing;
                    })
                    .collect(Collectors.toList());

            pricingRepository.saveAll(pricingList);
            pricingRepository.flush();
        }

        log.info("Route updated successfully: {}", routeId);
        return mapToCarrierRouteResponse(updatedRoute);
    }

    // ==================== DELETE ROUTE ====================

    @CacheEvict(value = {"carrierRoutes", "nearbyRoutes", "geospatialSearch", "routeById"}, allEntries = true)
    public void deleteRoute(String userId, Long routeId) {
        log.info("Deleting route {} for user: {}", routeId, userId);

        CarrierRoute route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));

        if (!route.getCarrierProfile().getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Unauthorized to delete this route");
        }

        routeRepository.delete(route);
        log.info("Route deleted successfully: {}", routeId);
    }

    // ==================== GET ROUTE BY ID ====================

    public CarrierRouteResponse getRouteById(String userId, Long routeId) {
        log.info("Fetching route {} for user: {}", routeId, userId);

        CarrierRoute route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));

        if (!route.getCarrierProfile().getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Unauthorized to access this route");
        }

        return mapToCarrierRouteResponse(route);
    }

    @Cacheable(value = "routeById", key = "#routeId")
    public CarrierRouteResponse getRouteById(Long routeId) {
        log.info("Cache MISS - Fetching route with ID: {}", routeId);

        CarrierRoute route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));

        return mapToCarrierRouteResponse(route);
    }

    public CarrierRouteResponse getRouteByJustId(Long routeId) {
        return getRouteById(routeId);
    }

    // ==================== MAPPING METHODS ====================

    private CarrierRouteResponse mapToCarrierRouteResponse(CarrierRoute route) {
        CarrierRouteResponse response = new CarrierRouteResponse();
        response.setRouteId(route.getRouteId());
        response.setCarrierName(route.getCarrierProfile().getUser().getFullName());
        response.setFromLocation(route.getFromLocation());
        response.setToLocation(route.getToLocation());
        response.setAvailableDate(route.getAvailableDate());
        response.setAvailableTime(route.getAvailableTime());

        response.setDeadlineDate(route.getDeadlineDate());
        if (route.getDeadlineTime() != null) {
            response.setDeadlineTime(route.getDeadlineTime().toString());
        }

        response.setTransportType(route.getTransportType());
        response.setMaxWeight(route.getMaxWeight());
        response.setMaxQuantity(route.getMaxQuantity());
        response.setCurrentWeight(route.getCurrentWeight());
        response.setCurrentQuantity(route.getCurrentQuantity());
        response.setRouteStatus(route.getRouteStatus());
        response.setCreatedAt(route.getCreatedAt());

        response.setToLatitude(route.getToLatitude() != null ? route.getToLatitude() : 0.0);
        response.setToLongitude(route.getToLongitude() != null ? route.getToLongitude() : 0.0);

        List<RoutePricing> pricingList = pricingRepository.findByCarrierRoute(route);
        List<RoutePricingResponse> pricingResponses = pricingList.stream()
                .map(p -> {
                    RoutePricingResponse pricingResp = new RoutePricingResponse();
                    pricingResp.setProductType(String.valueOf(p.getProductType()));
                    pricingResp.setWeightLimit(p.getWeightLimit());
                    pricingResp.setFixedPrice(p.getFixedPrice());
                    pricingResp.setPricePerTon(p.getPricePerTon());
                    return pricingResp;
                })
                .collect(Collectors.toList());

        response.setPricing(pricingResponses);
        return response;
    }

    private CarrierRouteResponse mapToCarrierRouteResponseOptimized(CarrierRoute route) {
        CarrierRouteResponse response = new CarrierRouteResponse();
        response.setRouteId(route.getRouteId());
        response.setCarrierName(route.getCarrierProfile().getUser().getFullName());
        response.setFromLocation(route.getFromLocation());
        response.setToLocation(route.getToLocation());
        response.setAvailableDate(route.getAvailableDate());
        response.setAvailableTime(route.getAvailableTime());

        response.setDeadlineDate(route.getDeadlineDate());
        if (route.getDeadlineTime() != null) {
            response.setDeadlineTime(route.getDeadlineTime().toString());
        }

        response.setTransportType(route.getTransportType());
        response.setMaxWeight(route.getMaxWeight());
        response.setMaxQuantity(route.getMaxQuantity());
        response.setCurrentWeight(route.getCurrentWeight());
        response.setCurrentQuantity(route.getCurrentQuantity());
        response.setRouteStatus(route.getRouteStatus());
        response.setCreatedAt(route.getCreatedAt());

        response.setToLatitude(route.getLatitude() != null ? route.getLatitude() : 0.0);
        response.setToLongitude(route.getLongitude() != null ? route.getLongitude() : 0.0);

        List<RoutePricingResponse> pricingResponses = route.getRoutePricing().stream()
                .map(p -> {
                    RoutePricingResponse pricingResp = new RoutePricingResponse();
                    pricingResp.setProductType(String.valueOf(p.getProductType()));
                    pricingResp.setWeightLimit(p.getWeightLimit());
                    pricingResp.setFixedPrice(p.getFixedPrice());
                    pricingResp.setPricePerTon(p.getPricePerTon());
                    return pricingResp;
                })
                .collect(Collectors.toList());

        response.setPricing(pricingResponses);
        return response;
    }

    // ==================== GEOSPATIAL SEARCH ====================

    @Cacheable(value = "nearbyRoutes", key = "#latitude + '-' + #longitude + '-' + #radiusKm")
    public List<GeospatialRouteResponse> searchRoutesWithinRadius(
            double latitude, double longitude, double radiusKm, double minCapacityKg) {

        log.info("Cache MISS - Searching routes within {}km radius", radiusKm);

        List<CarrierRoute> routes = routeRepository.findRoutesStartingWithinRadius(
                latitude, longitude, radiusKm, minCapacityKg, LocalDate.now());

        if (routes.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<RoutePricing>> pricingMap = fetchPricingForRoutes(routes);
        Map<Long, Address> addressMap = fetchAddressesForRoutes(routes);

        return routes.stream()
                .map(route -> mapToGeospatialResponseOptimized(
                        route, latitude, longitude, pricingMap, addressMap))
                .collect(Collectors.toList());
    }

    // ==================== OPTIMIZED GEOSPATIAL METHODS ====================

    private List<GeospatialRouteResponse> findNearbyRoutesOptimized(GeospatialRouteSearchRequest request) {
        List<CarrierRoute> routes = routeRepository.findRoutesStartingWithinRadius(
                request.getFromLatitude(),
                request.getFromLongitude(),
                2.0,
                request.getMinCapacityKg(),
                LocalDate.now()
        );

        if (routes.isEmpty()) return Collections.emptyList();

        Map<Long, List<RoutePricing>> pricingMap = fetchPricingForRoutes(routes);
        Map<Long, Address> addressMap = fetchAddressesForRoutes(routes);

        return routes.stream()
                .map(route -> mapToGeospatialResponseOptimized(
                        route, request.getFromLatitude(), request.getFromLongitude(),
                        pricingMap, addressMap))
                .collect(Collectors.toList());
    }

    private List<GeospatialRouteResponse> findFarRoutesOptimized(GeospatialRouteSearchRequest request) {
        List<CarrierRoute> routes = routeRepository.findRoutesStartingBetweenRadius(
                request.getFromLatitude(),
                request.getFromLongitude(),
                2.0,
                10.0,
                request.getMinCapacityKg(),
                LocalDate.now()
        );

        if (routes.isEmpty()) return Collections.emptyList();

        Map<Long, List<RoutePricing>> pricingMap = fetchPricingForRoutes(routes);
        Map<Long, Address> addressMap = fetchAddressesForRoutes(routes);

        return routes.stream()
                .map(route -> mapToGeospatialResponseOptimized(
                        route, request.getFromLatitude(), request.getFromLongitude(),
                        pricingMap, addressMap))
                .collect(Collectors.toList());
    }

    // ==================== BULK FETCH HELPERS ====================

    private Map<Long, List<RoutePricing>> fetchPricingForRoutes(List<CarrierRoute> routes) {
        if (routes.isEmpty()) {
            return Collections.emptyMap();
        }

        List<RoutePricing> allPricing = pricingRepository.findByCarrierRouteIn(routes);

        return allPricing.stream()
                .collect(Collectors.groupingBy(p -> p.getCarrierRoute().getRouteId()));
    }

    private Map<Long, Address> fetchAddressesForRoutes(List<CarrierRoute> routes) {
        if (routes.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> addressIds = routes.stream()
                .map(CarrierRoute::getLongAddressId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (addressIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Address> addresses = addressRepository.findAllById(addressIds);

        return addresses.stream()
                .collect(Collectors.toMap(Address::getAddressId, a -> a));
    }

    private GeospatialRouteResponse mapToGeospatialResponseOptimized(
            CarrierRoute route,
            double searchLat,
            double searchLng,
            Map<Long, List<RoutePricing>> pricingMap,
            Map<Long, Address> addressMap) {

        GeospatialRouteResponse response = new GeospatialRouteResponse();

        response.setRouteId(route.getRouteId());
        response.setCarrierName(route.getCarrierProfile().getUser().getFullName());
        response.setCarrierId(route.getCarrierProfile().getUser().getUserId());
        response.setFromLocation(route.getFromLocation());
        response.setToLocation(route.getToLocation());
        response.setAvailableDate(route.getAvailableDate());
        response.setDeadlineDate(route.getDeadlineDate());
        response.setTransportType(route.getTransportType());
        response.setMaxWeight(route.getMaxWeight());
        response.setMaxQuantity(route.getMaxQuantity());
        response.setCurrentWeight(route.getCurrentWeight());
        response.setCurrentQuantity(route.getCurrentQuantity());
        response.setAvailableWeight(route.getMaxWeight() - route.getCurrentWeight());
        response.setAvailableQuantity(route.getMaxQuantity() - route.getCurrentQuantity());
        response.setRouteStatus(route.getRouteStatus());
        response.setLatitude(route.getLatitude() != null ? route.getLatitude() : 0.0);
        response.setLongitude(route.getLongitude() != null ? route.getLongitude() : 0.0);

        if (route.getDeadlineTime() != null) {
            response.setDeadlineTime(route.getDeadlineTime().toString());
        }
        if (route.getAvailableTime() != null) {
            response.setAvailableTime(route.getAvailableTime().toString());
        }

        Address routeAddress = addressMap.get(route.getLongAddressId());
        if (routeAddress != null) {
            double distance = calculateDistance(searchLat, searchLng,
                    routeAddress.getLatitude(), routeAddress.getLongitude());
            response.setDistanceFromSearchPoint(distance);

            if (distance <= 2.0) {
                response.setDistanceCategory("NEAR");
            } else if (distance <= 10.0) {
                response.setDistanceCategory("MEDIUM");
            } else {
                response.setDistanceCategory("FAR");
            }
        }

        List<RoutePricing> pricingList = pricingMap.getOrDefault(route.getRouteId(), Collections.emptyList());
        List<RoutePricingResponse> pricingResponses = pricingList.stream()
                .map(p -> {
                    RoutePricingResponse pricingResp = new RoutePricingResponse();
                    pricingResp.setProductType(String.valueOf(p.getProductType()));
                    pricingResp.setWeightLimit(p.getWeightLimit());
                    pricingResp.setFixedPrice(p.getFixedPrice());
                    pricingResp.setPricePerTon(p.getPricePerTon());
                    return pricingResp;
                })
                .collect(Collectors.toList());

        response.setPricing(pricingResponses);
        return response;
    }

    // ==================== UTILITY METHODS ====================

    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    // ==================== ROUTE STATUS MANAGEMENT ====================

    @CacheEvict(value = {"carrierRoutes", "nearbyRoutes", "geospatialSearch", "routeById"}, allEntries = true)
    public CarrierRouteResponse updateRouteStatus(String userId, Long routeId, CarrierRoute.RouteStatus newStatus) {
        log.info("Updating route {} status to {} for user: {}", routeId, newStatus, userId);

        CarrierRoute route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));

        if (!route.getCarrierProfile().getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Unauthorized to update this route");
        }

        route.setRouteStatus(newStatus);
        CarrierRoute updatedRoute = routeRepository.save(route);

        log.info("Route status updated successfully: {}", routeId);
        return mapToCarrierRouteResponse(updatedRoute);
    }

    @CacheEvict(value = {"carrierRoutes", "nearbyRoutes", "geospatialSearch", "routeById"}, allEntries = true)
    public CarrierRouteResponse updateRouteCapacity(Long routeId, Double weightToAdd, Integer quantityToAdd) {
        log.info("Updating route {} capacity: weight={}, quantity={}", routeId, weightToAdd, quantityToAdd);

        CarrierRoute route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));

        route.setCurrentWeight(route.getCurrentWeight() + weightToAdd);
        route.setCurrentQuantity(route.getCurrentQuantity() + quantityToAdd);

        if (route.getCurrentWeight() >= route.getMaxWeight() ||
                route.getCurrentQuantity() >= route.getMaxQuantity()) {
            route.setRouteStatus(CarrierRoute.RouteStatus.COMPLETED);
        }

        CarrierRoute updatedRoute = routeRepository.save(route);

        log.info("Route capacity updated successfully: {}", routeId);
        return mapToCarrierRouteResponse(updatedRoute);
    }

    // ==================== ENHANCED ROUTE MATCHING ====================

    public Map<String, Object> findCarriersAlongRouteEnhanced(RouteMatchingRequest request) {
        log.info("Enhanced search for carriers along route from {},{} to {},{}",
                request.getFromLatitude(), request.getFromLongitude(),
                request.getToLatitude(), request.getToLongitude());

        LocalDate today = LocalDate.now();
        Map<String, Object> result = new HashMap<>();

        try {
            List<CarrierRoute> nearStartCarriers = routeRepository.findRoutesStartingWithinRadius(
                    request.getFromLatitude(),
                    request.getFromLongitude(),
                    10.0,
                    0.0,
                    today
            );
            log.info("Found {} carriers near start point (10km radius)", nearStartCarriers.size());

            List<CarrierRoute> nearEndCarriers = routeRepository.findRoutesStartingWithinRadius(
                    request.getToLatitude(),
                    request.getToLongitude(),
                    10.0,
                    0.0,
                    today
            );
            log.info("Found {} carriers near end point (10km radius)", nearEndCarriers.size());

            // ✅ FIXED: Added null check for coordinates
            // ✅ FIX: Use existing scoped query instead
            List<CarrierRoute> allActiveCarriers = routeRepository.findActiveRoutesWithCapacity(0.0, today);

            Map<Long, CarrierRoute> uniqueCarriersMap = new HashMap<>();
            nearStartCarriers.forEach(c -> uniqueCarriersMap.put(c.getRouteId(), c));
            nearEndCarriers.forEach(c -> uniqueCarriersMap.put(c.getRouteId(), c));
            allActiveCarriers.forEach(c -> uniqueCarriersMap.put(c.getRouteId(), c));

            List<CarrierRoute> allCarriers = new ArrayList<>(uniqueCarriersMap.values());
            log.info("Total unique carriers to process: {}", allCarriers.size());

            Map<Long, List<RoutePricing>> pricingMap = fetchPricingForRoutes(allCarriers);
            Map<Long, Address> addressMap = fetchAddressesForRoutes(allCarriers);

            List<GeospatialRouteResponse> nearStart = new ArrayList<>();
            List<GeospatialRouteResponse> nearEnd = new ArrayList<>();
            List<GeospatialRouteResponse> alongRoute = new ArrayList<>();

            double nearStartRadius = 10.0;
            double nearEndRadius = 10.0;
            double maxCorridorWidth = request.getMaxDeviationKm() > 0 ?
                    request.getMaxDeviationKm() : 50.0;

            Set<Long> categorizedIds = new HashSet<>();

            for (CarrierRoute carrier : allCarriers) {
                if (!applyRouteMatchingFilters(carrier, request)) {
                    continue;
                }

                double distanceFromStart = calculateDistance(
                        request.getFromLatitude(), request.getFromLongitude(),
                        carrier.getLatitude(), carrier.getLongitude()
                );

                double distanceFromEnd = calculateDistance(
                        request.getToLatitude(), request.getToLongitude(),
                        carrier.getLatitude(), carrier.getLongitude()
                );

                GeospatialRouteResponse response = mapToGeospatialResponseOptimized(
                        carrier, request.getFromLatitude(), request.getFromLongitude(),
                        pricingMap, addressMap
                );
                response.setDistanceFromSearchPoint(Math.round(distanceFromStart * 100.0) / 100.0);

                if (distanceFromStart <= nearStartRadius && !categorizedIds.contains(carrier.getRouteId())) {
                    response.setDistanceCategory("NEARSTART");
                    response.setIsOnRoute(false);
                    nearStart.add(response);
                    categorizedIds.add(carrier.getRouteId());
                } else if (distanceFromEnd <= nearEndRadius && !categorizedIds.contains(carrier.getRouteId())) {
                    response.setDistanceCategory("NEAREND");
                    response.setIsOnRoute(false);
                    nearEnd.add(response);
                    categorizedIds.add(carrier.getRouteId());
                } else if (!categorizedIds.contains(carrier.getRouteId())) {
                    boolean isInCorridor = isCarrierInRouteCorridorSimple(
                            carrier.getLatitude(), carrier.getLongitude(),
                            request.getFromLatitude(), request.getFromLongitude(),
                            request.getToLatitude(), request.getToLongitude(),
                            maxCorridorWidth
                    );

                    if (isInCorridor) {
                        response.setDistanceCategory("ROUTEMATCH");
                        response.setIsOnRoute(true);
                        alongRoute.add(response);
                        categorizedIds.add(carrier.getRouteId());
                    }
                }
            }

            nearStart.sort(Comparator.comparingDouble(GeospatialRouteResponse::getDistanceFromSearchPoint));
            nearEnd.sort(Comparator.comparingDouble(GeospatialRouteResponse::getDistanceFromSearchPoint));
            alongRoute.sort(Comparator.comparingDouble(GeospatialRouteResponse::getDistanceFromSearchPoint));

            List<GeospatialRouteResponse> allMatches = new ArrayList<>();
            allMatches.addAll(nearStart);
            allMatches.addAll(nearEnd);
            allMatches.addAll(alongRoute);

            result.put("nearStart", nearStart);
            result.put("nearEnd", nearEnd);
            result.put("alongRoute", alongRoute);
            result.put("matchingCarriers", allMatches);
            result.put("summary", Map.of(
                    "nearStart", nearStart.size(),
                    "nearEnd", nearEnd.size(),
                    "alongRoute", alongRoute.size(),
                    "total", allMatches.size()
            ));

            double totalDistance = calculateDistance(
                    request.getFromLatitude(), request.getFromLongitude(),
                    request.getToLatitude(), request.getToLongitude()
            );

            double routeBearing = calculateBearing(
                    request.getFromLatitude(), request.getFromLongitude(),
                    request.getToLatitude(), request.getToLongitude()
            );

            result.put("routeInfo", Map.of(
                    "fromCoordinates", List.of(request.getFromLatitude(), request.getFromLongitude()),
                    "toCoordinates", List.of(request.getToLatitude(), request.getToLongitude()),
                    "totalDistanceKm", Math.round(totalDistance * 100.0) / 100.0,
                    "routeBearing", Math.round(routeBearing * 100.0) / 100.0,
                    "searchRadiusKm", 10.0,
                    "corridorWidthKm", maxCorridorWidth
            ));

            result.put("searchTimestamp", LocalDateTime.now());
            result.put("totalMatches", allMatches.size());

            log.info("Route corridor search completed: total {} carriers", allMatches.size());

        } catch (Exception e) {
            log.error("Error in route corridor search: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("totalMatches", 0);
            result.put("matchingCarriers", new ArrayList<>());
            result.put("nearStart", new ArrayList<>());
            result.put("nearEnd", new ArrayList<>());
            result.put("alongRoute", new ArrayList<>());
        }

        return result;
    }

    private boolean isCarrierInRouteCorridorSimple(
            double carrierLat, double carrierLng,
            double startLat, double startLng,
            double endLat, double endLng,
            double corridorWidthKm) {

        double bufferDegrees = corridorWidthKm / 111.32;

        double minLat = Math.min(startLat, endLat) - bufferDegrees;
        double maxLat = Math.max(startLat, endLat) + bufferDegrees;
        double minLng = Math.min(startLng, endLng) - bufferDegrees;
        double maxLng = Math.max(startLng, endLng) + bufferDegrees;

        boolean inBoundingBox = carrierLat >= minLat && carrierLat <= maxLat &&
                carrierLng >= minLng && carrierLng <= maxLng;

        if (!inBoundingBox) {
            return false;
        }

        double distanceFromStart = calculateDistance(startLat, startLng, carrierLat, carrierLng);
        double distanceFromEnd = calculateDistance(endLat, endLng, carrierLat, carrierLng);

        return distanceFromStart > 15.0 && distanceFromEnd > 15.0;
    }

    private boolean applyRouteMatchingFilters(CarrierRoute route, RouteMatchingRequest request) {
        if (request.getTransportType() != null && !route.getTransportType().equals(request.getTransportType())) {
            return false;
        }

        double availableCapacity = route.getMaxWeight() - route.getCurrentWeight();
        if (availableCapacity <= 0) {
            return false;
        }

        return true;
    }

    private double calculateBearing(double lat1, double lng1, double lat2, double lng2) {
        double dLng = Math.toRadians(lng2 - lng1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        double y = Math.sin(dLng) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLng);

        double bearing = Math.atan2(y, x);
        return (Math.toDegrees(bearing) + 360) % 360;
    }

    // ==================== UNIFIED SEARCH (FIXED) ====================

    @Cacheable(value = "geospatialSearch", key = "#request.hashCode()")
    public Map<String, Object> searchRoutesUnified(GeospatialRouteSearchRequest request) {
        log.info("Cache MISS - Performing unified geospatial search");

        Map<String, Object> result = new HashMap<>();

        // ✅ FIX: Get ALL routes within max radius (like PackageService)
        double maxSearchRadius = Math.max(10.0, request.getFarRadiusKm() > 0 ? request.getFarRadiusKm() : 25.0);

        List<Long> routeIds = routeRepository.findRouteIdsWithinRadius(
                request.getFromLatitude(),
                request.getFromLongitude(),
                maxSearchRadius
        );

        if (routeIds.isEmpty()) {
            return buildEmptyResult();
        }

        // ✅ Bulk fetch routes with carrier profile
        List<CarrierRoute> allRoutes = routeRepository.findByIdInWithCarrierProfile(routeIds);

        // ✅ Filter in-memory
        List<CarrierRoute> activeRoutes = allRoutes.stream()
                .filter(route -> route.getRouteStatus() == CarrierRoute.RouteStatus.CREATED)
                .filter(route -> route.getLatitude() != null && route.getLongitude() != null)
                .filter(route -> route.getLatitude() != 0.0 && route.getLongitude() != 0.0)
                .filter(route -> (route.getMaxWeight() - route.getCurrentWeight()) >= request.getMinCapacityKg())
                // ✅ FIX: Skip past routes
                .filter(route -> route.getAvailableDate() != null && !route.getAvailableDate().isBefore(LocalDate.now()))
                .collect(Collectors.toList());

        if (activeRoutes.isEmpty()) {
            return buildEmptyResult();
        }

        Map<Long, List<RoutePricing>> pricingMap = fetchPricingForRoutes(activeRoutes);
        Map<Long, Address> addressMap = fetchAddressesForRoutes(activeRoutes);

        List<GeospatialRouteResponse> nearbyRoutes = new ArrayList<>();
        List<GeospatialRouteResponse> farRoutes = new ArrayList<>();
        List<GeospatialRouteResponse> betweenRoutes = new ArrayList<>();
        List<GeospatialRouteResponse> onTheWayRoutes = new ArrayList<>();

        Set<Long> processedRouteIds = new HashSet<>();

        for (CarrierRoute route : activeRoutes) {
            GeospatialRouteResponse response = mapToGeospatialResponseOptimized(
                    route, request.getFromLatitude(), request.getFromLongitude(),
                    pricingMap, addressMap
            );

            double distanceFromStart = calculateDistance(
                    request.getFromLatitude(), request.getFromLongitude(),
                    route.getLatitude(), route.getLongitude()
            );

            double distanceFromEnd = calculateDistance(
                    request.getToLatitude(), request.getToLongitude(),
                    route.getLatitude(), route.getLongitude()
            );

            response.setDistanceFromSearchPoint(Math.round(distanceFromStart * 100.0) / 100.0);

            boolean categorized = false;

            if (!categorized && isOnTheWay(route, request, distanceFromStart, distanceFromEnd)) {
                response.setDistanceCategory("ON_THE_WAY");
                response.setIsOnRoute(true);
                onTheWayRoutes.add(response);
                processedRouteIds.add(route.getRouteId());
                categorized = true;
            }

            if (!categorized && isBetweenLocations(route, request, distanceFromStart, distanceFromEnd)) {
                response.setDistanceCategory("BETWEEN");
                response.setIsOnRoute(false);
                betweenRoutes.add(response);
                processedRouteIds.add(route.getRouteId());
                categorized = true;
            }

            if (!categorized && distanceFromStart <= 2.0) {
                response.setDistanceCategory("NEARBY");
                response.setIsOnRoute(false);
                nearbyRoutes.add(response);
                processedRouteIds.add(route.getRouteId());
                categorized = true;
            }

            if (!categorized && distanceFromStart > 2.0 && distanceFromStart <= 10.0) {
                response.setDistanceCategory("FAR");
                response.setIsOnRoute(false);
                farRoutes.add(response);
                processedRouteIds.add(route.getRouteId());
            }
        }

        Comparator<GeospatialRouteResponse> distanceComparator =
                Comparator.comparingDouble(GeospatialRouteResponse::getDistanceFromSearchPoint);

        nearbyRoutes.sort(distanceComparator);
        farRoutes.sort(distanceComparator);
        betweenRoutes.sort(distanceComparator);
        onTheWayRoutes.sort(distanceComparator);

        Map<String, Object> summary = new HashMap<>();
        summary.put("nearby", nearbyRoutes.size());
        summary.put("far", farRoutes.size());
        summary.put("between", betweenRoutes.size());
        summary.put("onTheWay", onTheWayRoutes.size());
        summary.put("total", nearbyRoutes.size() + farRoutes.size() +
                betweenRoutes.size() + onTheWayRoutes.size());

        result.put("summary", summary);
        result.put("nearby", nearbyRoutes);
        result.put("far", farRoutes);
        result.put("between", betweenRoutes);
        result.put("onTheWay", onTheWayRoutes);
        result.put("searchTimestamp", LocalDateTime.now());

        return result;
    }


    private boolean isOnTheWay(CarrierRoute route, GeospatialRouteSearchRequest request,
                               double distanceFromStart, double distanceFromEnd) {
        if (distanceFromStart <= 2.0 || distanceFromEnd <= 2.0) {
            return false;
        }

        return isInRouteCorridor(
                route.getLatitude(), route.getLongitude(),
                request.getFromLatitude(), request.getFromLongitude(),
                request.getToLatitude(), request.getToLongitude(),
                2.0
        );
    }

    private boolean isBetweenLocations(CarrierRoute route, GeospatialRouteSearchRequest request,
                                       double distanceFromStart, double distanceFromEnd) {
        return distanceFromStart <= 2.0 && distanceFromEnd <= 2.0;
    }

    private boolean isInRouteCorridor(double pointLat, double pointLng,
                                      double startLat, double startLng,
                                      double endLat, double endLng,
                                      double corridorWidthKm) {
        double distance = perpendicularDistance(
                pointLat, pointLng,
                startLat, startLng,
                endLat, endLng
        );

        return distance <= corridorWidthKm;
    }

    private double perpendicularDistance(double px, double py,
                                         double x1, double y1,
                                         double x2, double y2) {
        double lat1 = Math.toRadians(x1);
        double lon1 = Math.toRadians(y1);
        double lat2 = Math.toRadians(x2);
        double lon2 = Math.toRadians(y2);
        double latP = Math.toRadians(px);
        double lonP = Math.toRadians(py);

        double dLon = lon2 - lon1;
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        double bearing1 = Math.atan2(y, x);

        double dLonP = lonP - lon1;
        double yP = Math.sin(dLonP) * Math.cos(latP);
        double xP = Math.cos(lat1) * Math.sin(latP) -
                Math.sin(lat1) * Math.cos(latP) * Math.cos(dLonP);
        double bearing2 = Math.atan2(yP, xP);

        double dXt = Math.asin(Math.sin(calculateDistance(x1, y1, px, py) / 6371.0) *
                Math.sin(bearing2 - bearing1)) * 6371.0;

        return Math.abs(dXt);
    }

    private Map<String, Object> buildEmptyResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("summary", Map.of(
                "nearby", 0, "far", 0, "between", 0, "onTheWay", 0, "total", 0
        ));
        result.put("nearby", Collections.emptyList());
        result.put("far", Collections.emptyList());
        result.put("between", Collections.emptyList());
        result.put("onTheWay", Collections.emptyList());
        result.put("searchTimestamp", LocalDateTime.now());
        return result;
    }

    public Map<String, Object> searchRoutesUnifiedWithFilters(GeospatialRouteSearchRequest request) {
        Map<String, Object> allResults = searchRoutesUnified(request);

        if (allResults == null || allResults.isEmpty()) {
            return buildEmptyResult();
        }

        for (String category : List.of("nearby", "far", "between", "onTheWay")) {
            Object data = allResults.get(category);
            if (!(data instanceof List<?> routeResponses)) {
                allResults.put(category, Collections.emptyList());
                continue;
            }

            List<GeospatialRouteResponse> filtered = routeResponses.stream()
                    .filter(GeospatialRouteResponse.class::isInstance)
                    .map(GeospatialRouteResponse.class::cast)
                    .filter(route -> {
                        boolean typeOk = request.getTransportType() == null
                                || request.getTransportType().equals(route.getTransportType());

                        boolean dateOk = true;
                        if (request.getStartDate() != null && request.getEndDate() != null) {
                            dateOk = route.getAvailableDate() != null
                                    && !route.getAvailableDate().isBefore(request.getStartDate())
                                    && !route.getAvailableDate().isAfter(request.getEndDate());
                        } else if (request.getStartDate() != null) {
                            dateOk = route.getAvailableDate() != null
                                    && !route.getAvailableDate().isBefore(request.getStartDate());
                        }
                        return typeOk && dateOk;
                    })
                    .collect(Collectors.toList());

            allResults.put(category, filtered);
        }

        Map<String, Object> summary = new HashMap<>();
        int nearbyCount = ((List<?>) allResults.getOrDefault("nearby", Collections.emptyList())).size();
        int farCount = ((List<?>) allResults.getOrDefault("far", Collections.emptyList())).size();
        int betweenCount = ((List<?>) allResults.getOrDefault("between", Collections.emptyList())).size();
        int onTheWayCount = ((List<?>) allResults.getOrDefault("onTheWay", Collections.emptyList())).size();

        summary.put("nearby", nearbyCount);
        summary.put("far", farCount);
        summary.put("between", betweenCount);
        summary.put("onTheWay", onTheWayCount);
        summary.put("total", nearbyCount + farCount + betweenCount + onTheWayCount);

        allResults.put("summary", summary);
        return allResults;
    }
}
