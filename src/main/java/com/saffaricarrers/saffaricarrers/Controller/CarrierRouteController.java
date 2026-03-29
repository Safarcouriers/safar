package com.saffaricarrers.saffaricarrers.Controller;

import com.saffaricarrers.saffaricarrers.Dtos.*;
import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import com.saffaricarrers.saffaricarrers.Services.CarrierRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/carrier-routes")
@RequiredArgsConstructor
@Tag(name = "Carrier Routes", description = "Carrier route management endpoints")
public class CarrierRouteController {

    private final CarrierRouteService carrierRouteService;

    // ==================== BASIC ROUTE MANAGEMENT ====================

    @PostMapping("/{userId}")
    @Operation(summary = "Create route", description = "Create a new carrier route")
    public ResponseEntity<CarrierRouteResponse> createRoute(
            @PathVariable String userId,
             @RequestBody CarrierRouteRequest request) {
        System.out.println("route data"+request);
        CarrierRouteResponse response = carrierRouteService.createRoute(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get carrier routes", description = "Get all routes for the authenticated carrier")
    public ResponseEntity<List<CarrierRouteResponse>> getCarrierRoutes(
            @PathVariable String userId) {
        List<CarrierRouteResponse> routes = carrierRouteService.getCarrierRoutes(userId);
        return ResponseEntity.ok(routes);
    }

    @GetMapping("/{userId}/{routeId}")
    @Operation(summary = "Get route by ID", description = "Get a specific route by ID for the authenticated carrier")
    public ResponseEntity<CarrierRouteResponse> getRouteById(
            @PathVariable String userId,
            @PathVariable Long routeId) {
        CarrierRouteResponse response = carrierRouteService.getRouteById(userId, routeId);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/single/{routeId}")
    @Operation(summary = "Get route by ID", description = "Get a specific route by ID for the authenticated carrier")
    public ResponseEntity<CarrierRouteResponse> getRouteByOnlyId(
            @PathVariable Long routeId) {
        CarrierRouteResponse response = carrierRouteService.getRouteByJustId(routeId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}/{routeId}")
    public ResponseEntity<?> updateRoute(
            @PathVariable String userId,
            @PathVariable Long routeId,
            @Valid @RequestBody CarrierRouteRequest request) {

        System.err.println("🚀 UPDATE ROUTE HIT - userId=" + userId + " routeId=" + routeId); // ← BEFORE try

        try {
            System.err.println("📤 Calling service.updateRoute with request: " + request.toString());
            CarrierRouteResponse response = carrierRouteService.updateRoute(userId, routeId, request);
            System.err.println("✅ Service returned success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("💥 EXCEPTION CAUGHT in updateRoute:");
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getClass().getName() + ": " + e.getMessage()));
        }
    }


    @PutMapping("/{userId}/{routeId}/status")
    @Operation(summary = "Update route status", description = "Update the status of a carrier route")
    public ResponseEntity<CarrierRouteResponse> updateRouteStatus(
            @PathVariable String userId,
            @PathVariable Long routeId,
            @RequestParam CarrierRoute.RouteStatus status) {
        CarrierRouteResponse response = carrierRouteService.updateRouteStatus(userId, routeId, status);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{userId}/{routeId}")
    @Operation(summary = "Delete route", description = "Delete a carrier route")
    public ResponseEntity<Void> deleteRoute(
            @PathVariable String userId,
            @PathVariable Long routeId) {
        carrierRouteService.deleteRoute(userId, routeId);
        return ResponseEntity.noContent().build();
    }

    // ==================== ROUTE SEARCH ====================

//    @PostMapping("/search")
//    @Operation(summary = "Search routes", description = "Search available routes for package delivery")
//    public ResponseEntity<List<CarrierRouteResponse>> searchRoutes(
//            @Valid @RequestBody RouteSearchRequest request) {
//        List<CarrierRouteResponse> routes = carrierRouteService.searchRoutes(request);
//        return ResponseEntity.ok(routes);
//    }
//


    // ==================== GEOSPATIAL SEARCH ====================

    @GetMapping("/search/radius")
    @Operation(summary = "Search routes within radius", description = "Find routes starting within specified radius from a point")
    public ResponseEntity<List<GeospatialRouteResponse>> searchRoutesWithinRadius(
            @Parameter(description = "Latitude of search center") @RequestParam double latitude,
            @Parameter(description = "Longitude of search center") @RequestParam double longitude,
            @Parameter(description = "Search radius in kilometers") @RequestParam double radiusKm,
            @Parameter(description = "Minimum available capacity in kg") @RequestParam(defaultValue = "0.0") double minCapacityKg) {
        List<GeospatialRouteResponse> routes = carrierRouteService.searchRoutesWithinRadius(
                latitude, longitude, radiusKm, minCapacityKg);
        return ResponseEntity.ok(routes);
    }

//    @PostMapping("/search/corridor")
//    @Operation(summary = "Search routes along corridor", description = "Find routes along a specified route corridor")
//    public ResponseEntity<List<GeospatialRouteResponse>> searchRoutesAlongCorridor(
//            @Valid @RequestBody RouteSearchRequest request) {
//        List<GeospatialRouteResponse> routes = carrierRouteService.searchRoutesAlongCorridor(request);
//        return ResponseEntity.ok(routes);
//    }
//
//    @GetMapping("/search/distance-category")
//    @Operation(summary = "Search routes by distance categories", description = "Find routes categorized by distance (near, medium, far)")
//    public ResponseEntity<Map<String, List<GeospatialRouteResponse>>> searchRoutesByDistanceCategory(
//            @Parameter(description = "Latitude of search center") @RequestParam double latitude,
//            @Parameter(description = "Longitude of search center") @RequestParam double longitude,
//            @Parameter(description = "Near radius in kilometers") @RequestParam double nearRadius,
//            @Parameter(description = "Medium radius in kilometers") @RequestParam double mediumRadius,
//            @Parameter(description = "Maximum radius in kilometers") @RequestParam double maxRadius,
//            @Parameter(description = "Minimum available capacity in kg") @RequestParam(defaultValue = "0.0") double minCapacityKg) {
//
//        Map<String, List<GeospatialRouteResponse>> routes = carrierRouteService.searchRoutesByDistanceCategory(
//                latitude, longitude, nearRadius, mediumRadius, maxRadius, minCapacityKg);
//        return ResponseEntity.ok(routes);
//    }

//    @GetMapping("/search/area")
//    @Operation(summary = "Search routes in bounding box", description = "Find routes within a specified rectangular area")
//    public ResponseEntity<List<GeospatialRouteResponse>> searchRoutesInArea(
//            @Parameter(description = "Minimum latitude") @RequestParam double minLat,
//            @Parameter(description = "Maximum latitude") @RequestParam double maxLat,
//            @Parameter(description = "Minimum longitude") @RequestParam double minLng,
//            @Parameter(description = "Maximum longitude") @RequestParam double maxLng,
//            @Parameter(description = "Minimum available capacity in kg") @RequestParam(defaultValue = "0.0") double minCapacityKg) {
//
//        List<GeospatialRouteResponse> routes = carrierRouteService.searchRoutesInArea(
//                minLat, maxLat, minLng, maxLng, minCapacityKg);
//        return ResponseEntity.ok(routes);
//    }

    @PostMapping("/search/unified")
    @Operation(summary = "Unified geospatial search", description = "Comprehensive geospatial search with multiple categories")
    public ResponseEntity<Map<String, Object>> searchRoutesUnified(
            @Valid @RequestBody GeospatialRouteSearchRequest request) {
        Map<String, Object> result = carrierRouteService.searchRoutesUnified(request);
        return ResponseEntity.ok(result);
    }

    // ==================== ROUTE CAPACITY MANAGEMENT ====================

    @PutMapping("/{routeId}/capacity")
    @Operation(summary = "Update route capacity", description = "Update current weight and quantity when packages are added/removed")
    public ResponseEntity<Void> updateRouteCapacity(
            @PathVariable Long routeId,
            @Parameter(description = "Weight change (can be negative)") @RequestParam Double weightChange,
            @Parameter(description = "Quantity change (can be negative)") @RequestParam Integer quantityChange) {

        carrierRouteService.updateRouteCapacity(routeId, weightChange, quantityChange);
        return ResponseEntity.ok().build();
    }

//    @GetMapping("/{routeId}/capacity")
//    @Operation(summary = "Get route capacity", description = "Get current and available capacity information for a route")
//    public ResponseEntity<RouteCapacityDto> getRouteCapacity(
//            @PathVariable Long routeId) {
//        RouteCapacityDto capacity = carrierRouteService.getRouteCapacity(routeId);
//        return ResponseEntity.ok(capacity);
//    }
//
//
//
//    @GetMapping("/{userId}/stats")
//    @Operation(summary = "Get carrier statistics", description = "Get comprehensive statistics for a carrier")
//    public ResponseEntity<CarrierStatsDto> getCarrierStats(
//            @PathVariable String userId) {
//        CarrierStatsDto stats = carrierRouteService.getCarrierStats(userId);
//        return ResponseEntity.ok(stats);
//    }

    // ==================== UTILITY ENDPOINTS ====================

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the carrier route service is running")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "CarrierRouteService",
                "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/transport-types")
    @Operation(summary = "Get transport types", description = "Get all available transport types")
    public ResponseEntity<CarrierRoute.TransportType[]> getTransportTypes() {
        return ResponseEntity.ok(CarrierRoute.TransportType.values());
    }

    @GetMapping("/route-statuses")
    @Operation(summary = "Get route statuses", description = "Get all available route statuses")
    public ResponseEntity<CarrierRoute.RouteStatus[]> getRouteStatuses() {
        return ResponseEntity.ok(CarrierRoute.RouteStatus.values());
    }


@PostMapping("/search/route-matching")
@Operation(summary = "Find carriers along route",
        description = "Find carriers whose routes align with the specified route corridor")
public ResponseEntity<Map<String, Object>> findCarriersAlongRoute(
        @Valid @RequestBody RouteMatchingRequest request) {
        System.out.println(request.toString());
    Map<String, Object> result = carrierRouteService.findCarriersAlongRouteEnhanced(request);
    return ResponseEntity.ok(result);
}
    @PostMapping("/search/carriers/geospatial")
    public ResponseEntity<Map<String, Object>> searchCarriersGeospatial(
            @RequestBody GeospatialRouteSearchRequest request) {
        try {
            System.out.println(request);

            // ✅ CHANGE THIS LINE - Use the filtering method instead
            Map<String, Object> result = carrierRouteService.searchRoutesUnifiedWithFilters(request);

// Limit each category to 5 if you want
            for (String category : List.of("nearby", "far", "between", "onTheWay")) {
                Object value = result.get(category);
                if (value instanceof List<?> list && list.size() > 5) {
                    result.put(category, list.subList(0, 5));
                }
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Carrier search failed: " + e.getMessage()));
        }
    }


//@PostMapping("/search/carriers/geospatial")
//public ResponseEntity<Map<String, Object>> searchCarriersGeospatial(
//        @RequestBody GeospatialRouteSearchRequest request) {
//    try {
//        System.out.println(request);
//        Map<String, Object> result = carrierRouteService.searchRoutesUnified(request);
//
//        // Limit results to 5 items
//        if (result.containsKey("routes") && result.get("routes") instanceof List) {
//            List<?> routes = (List<?>) result.get("routes");
//            if (routes.size() > 5) {
//                result.put("routes", routes.subList(0, 5));
//                result.put("totalResults", routes.size()); // Optional: store original count
//                result.put("limited", true); // Optional: indicate results were limited
//            }
//        }
//
//        return ResponseEntity.ok(result);
//    } catch (Exception e) {
//        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body(Map.of("error", "Carrier search failed: " + e.getMessage()));
//    }
//}
// ==================== OPTIMIZED CONTROLLER ENDPOINTS ====================

    /**
     * ✅ OPTION 1: Simple optimized search with LIMIT (RECOMMENDED for your case)
     */


}