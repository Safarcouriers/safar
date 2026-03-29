package com.saffaricarrers.saffaricarrers.Repository;

import com.saffaricarrers.saffaricarrers.Entity.CarrierProfile;
import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CarrierRouteRepository extends JpaRepository<CarrierRoute, Long> {

    // ==================== BASIC CRUD QUERIES ====================

    @EntityGraph(value = "CarrierRoute.withCarrierProfile", type = EntityGraph.EntityGraphType.LOAD)
    List<CarrierRoute> findByCarrierProfileOrderByCreatedAtDesc(CarrierProfile carrierProfile);

    @EntityGraph(value = "CarrierRoute.withCarrierProfile", type = EntityGraph.EntityGraphType.LOAD)
    List<CarrierRoute> findByCarrierProfileAndRouteStatus(
            CarrierProfile carrierProfile, CarrierRoute.RouteStatus status);

    @EntityGraph(value = "CarrierRoute.withCarrierProfile", type = EntityGraph.EntityGraphType.LOAD)
    Optional<CarrierRoute> findByRouteIdAndCarrierProfileUserUserId(Long routeId, String userId);

    @Query("SELECT DISTINCT cr FROM CarrierRoute cr " +
            "LEFT JOIN FETCH cr.carrierProfile cp " +
            "LEFT JOIN FETCH cp.user " +
            "LEFT JOIN FETCH cr.routePricing " +
            "LEFT JOIN FETCH cp.bankDetails " +
            "WHERE cp.user.userId = :userId")
    List<CarrierRoute> findByCarrierProfileUserUserId(@Param("userId") String userId);

    // ==================== SEARCH QUERIES ====================

    @EntityGraph(value = "CarrierRoute.withCarrierProfile", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT cr FROM CarrierRoute cr WHERE cr.fromLocation LIKE %:from% " +
            "AND cr.toLocation LIKE %:to% AND cr.availableDate = :date " +
            "AND cr.routeStatus = 'CREATED'")
    Page<CarrierRoute> searchRoutes(@Param("from") String fromLocation,
                                    @Param("to") String toLocation,
                                    @Param("date") LocalDate date,
                                    Pageable pageable);

    @EntityGraph(value = "CarrierRoute.withCarrierProfile", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT cr FROM CarrierRoute cr WHERE cr.routeStatus = 'CREATED' " +
            "AND LOWER(cr.fromLocation) LIKE LOWER(CONCAT('%', :from, '%')) AND " +
            "LOWER(cr.toLocation) LIKE LOWER(CONCAT('%', :to, '%')) AND " +
            "cr.availableDate >= :date AND " +
            "(cr.maxWeight - cr.currentWeight) >= :weight " +
            "ORDER BY cr.availableDate ASC")
    List<CarrierRoute> findAvailableRoutes(@Param("from") String fromLocation,
                                           @Param("to") String toLocation,
                                           @Param("date") LocalDate date,
                                           @Param("weight") Double weight);

    // ==================== ACTIVE ROUTES ====================

    @EntityGraph(value = "CarrierRoute.withCarrierProfile", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT cr FROM CarrierRoute cr WHERE cr.routeStatus = 'CREATED' " +
            "AND cr.availableDate >= :currentDate")
    List<CarrierRoute> findActiveRoutes(@Param("currentDate") LocalDate currentDate);

    @Query("""
    SELECT DISTINCT cr FROM CarrierRoute cr
    LEFT JOIN FETCH cr.carrierProfile cp
    LEFT JOIN FETCH cp.user u
    WHERE cr.routeStatus = 'CREATED'
    AND cr.availableDate >= :currentDate
    AND (cr.maxWeight - cr.currentWeight) >= :minCapacity
    AND cr.latitude IS NOT NULL 
    AND cr.longitude IS NOT NULL
    AND cr.latitude != 0.0 
    AND cr.longitude != 0.0
    """)
    List<CarrierRoute> findActiveRoutesWithCapacity(
            @Param("minCapacity") Double minCapacity,
            @Param("currentDate") LocalDate currentDate);

    // ==================== GEOSPATIAL QUERIES (PRIMARY) ====================

    /**
     * ✅ PRIMARY: Find routes within radius from a point
     * Used by both /search and /search/route-matching endpoints
     */
    @Query(value = """
        SELECT * FROM carrier_routes cr
        WHERE cr.route_status = 'CREATED'
        AND cr.available_date >= :currentDate
        AND cr.latitude IS NOT NULL AND cr.longitude IS NOT NULL
        AND (
            6371 * acos(
                cos(radians(:latitude)) * cos(radians(cr.latitude)) *
                cos(radians(cr.longitude) - radians(:longitude)) +
                sin(radians(:latitude)) * sin(radians(cr.latitude))
            )
        ) <= :radiusKm
        AND (cr.max_weight - cr.current_weight) >= :minCapacityKg
        ORDER BY (
            6371 * acos(
                cos(radians(:latitude)) * cos(radians(cr.latitude)) *
                cos(radians(cr.longitude) - radians(:longitude)) +
                sin(radians(:latitude)) * sin(radians(cr.latitude))
            )
        )
        """, nativeQuery = true)
    List<CarrierRoute> findRoutesStartingWithinRadius(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusKm") double radiusKm,
            @Param("minCapacityKg") double minCapacityKg,
            @Param("currentDate") LocalDate currentDate);

    /**
     * ✅ Find routes between two radius distances (donut shape)
     */
    @Query(value = """
        SELECT * FROM carrier_routes cr
        WHERE cr.route_status = 'CREATED'
        AND cr.available_date >= :currentDate
        AND cr.latitude IS NOT NULL AND cr.longitude IS NOT NULL
        AND (
            6371 * acos(
                cos(radians(:latitude)) * cos(radians(cr.latitude)) *
                cos(radians(cr.longitude) - radians(:longitude)) +
                sin(radians(:latitude)) * sin(radians(cr.latitude))
            )
        ) > :minRadiusKm
        AND (
            6371 * acos(
                cos(radians(:latitude)) * cos(radians(cr.latitude)) *
                cos(radians(cr.longitude) - radians(:longitude)) +
                sin(radians(:latitude)) * sin(radians(cr.latitude))
            )
        ) <= :maxRadiusKm
        AND (cr.max_weight - cr.current_weight) >= :minCapacityKg
        ORDER BY (
            6371 * acos(
                cos(radians(:latitude)) * cos(radians(cr.latitude)) *
                cos(radians(cr.longitude) - radians(:longitude)) +
                sin(radians(:latitude)) * sin(radians(cr.latitude))
            )
        )
        """, nativeQuery = true)
    List<CarrierRoute> findRoutesStartingBetweenRadius(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("minRadiusKm") double minRadiusKm,
            @Param("maxRadiusKm") double maxRadiusKm,
            @Param("minCapacityKg") double minCapacityKg,
            @Param("currentDate") LocalDate currentDate);

    // ==================== ✅ NEW OPTIMIZED METHODS (LIKE PACKAGESERVICE) ====================

    /**
     * ✅ NEW: Get route IDs within radius WITHOUT date filter (like PackageService)
     * This is the key fix - no date filtering at database level
     */
    @Query(value = """
    SELECT cr.route_id FROM carrier_routes cr
    WHERE cr.route_status = 'CREATED'
    AND cr.available_date >= CURDATE()
    AND cr.latitude IS NOT NULL 
    AND cr.longitude IS NOT NULL
    AND cr.latitude <> 0.0 
    AND cr.longitude <> 0.0
    AND (
        6371 * acos(
            cos(radians(:latitude)) * cos(radians(cr.latitude)) *
            cos(radians(cr.longitude) - radians(:longitude)) +
            sin(radians(:latitude)) * sin(radians(cr.latitude))
        )
    ) <= :radiusKm
    """, nativeQuery = true)
    List<Long> findRouteIdsWithinRadius(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusKm") double radiusKm
    );

    /**
     * ✅ NEW: Bulk fetch routes with carrier profile by IDs (like PackageService)
     */
    @Query("SELECT DISTINCT cr FROM CarrierRoute cr " +
            "LEFT JOIN FETCH cr.carrierProfile cp " +
            "LEFT JOIN FETCH cp.user " +
            "WHERE cr.routeId IN :routeIds")
    List<CarrierRoute> findByIdInWithCarrierProfile(@Param("routeIds") List<Long> routeIds);

    // ==================== EXISTING BOUNDING BOX QUERIES ====================

    /**
     * ✅ Find routes in bounding box (for corridor search)
     */
    @Query("SELECT cr FROM CarrierRoute cr " +
            "WHERE cr.routeStatus = 'CREATED' " +
            "AND cr.availableDate >= :currentDate " +
            "AND cr.latitude IS NOT NULL AND cr.longitude IS NOT NULL " +
            "AND (cr.maxWeight - cr.currentWeight) >= :minCapacityKg " +
            "AND cr.latitude BETWEEN :minLat AND :maxLat " +
            "AND cr.longitude BETWEEN :minLng AND :maxLng " +
            "ORDER BY cr.availableDate ASC")
    List<CarrierRoute> findRoutesInBoundingBox(
            @Param("minLat") double minLatitude,
            @Param("maxLat") double maxLatitude,
            @Param("minLng") double minLongitude,
            @Param("maxLng") double maxLongitude,
            @Param("minCapacityKg") double minCapacityKg,
            @Param("currentDate") LocalDate currentDate);

    /**
     * ✅ Find routes with transport type filter in bounding box
     */
    @Query("SELECT cr FROM CarrierRoute cr " +
            "WHERE cr.routeStatus = 'CREATED' " +
            "AND cr.availableDate >= :currentDate " +
            "AND cr.transportType = :transportType " +
            "AND cr.latitude IS NOT NULL AND cr.longitude IS NOT NULL " +
            "AND (cr.maxWeight - cr.currentWeight) >= :minCapacityKg " +
            "AND cr.latitude BETWEEN :minLat AND :maxLat " +
            "AND cr.longitude BETWEEN :minLng AND :maxLng " +
            "ORDER BY cr.availableDate ASC")
    List<CarrierRoute> findRoutesInCorridorBoundingBox(
            @Param("minLat") double minLatitude,
            @Param("maxLat") double maxLatitude,
            @Param("minLng") double minLongitude,
            @Param("maxLng") double maxLongitude,
            @Param("transportType") CarrierRoute.TransportType transportType,
            @Param("minCapacityKg") double minCapacityKg,
            @Param("currentDate") LocalDate currentDate);

    // ==================== UTILITY QUERIES ====================

    @EntityGraph(value = "CarrierRoute.withCarrierProfile", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT cr FROM CarrierRoute cr " +
            "WHERE cr.routeStatus = 'CREATED' " +
            "AND cr.latitude IS NOT NULL AND cr.longitude IS NOT NULL " +
            "AND cr.latitude != 0.0 AND cr.longitude != 0.0 " +
            "ORDER BY cr.availableDate ASC")
    List<CarrierRoute> findRoutesWithValidCoordinates();

    @Query("SELECT COUNT(cr) FROM CarrierRoute cr " +
            "WHERE cr.routeStatus = 'CREATED' " +
            "AND (cr.latitude IS NULL OR cr.longitude IS NULL " +
            "OR cr.latitude = 0.0 OR cr.longitude = 0.0)")
    long countRoutesWithInvalidCoordinates();

    @Query("SELECT COUNT(cr) FROM CarrierRoute cr WHERE cr.carrierProfile = :carrier AND " +
            "cr.fromLocation = :from AND cr.toLocation = :to AND cr.availableDate = :date")
    long countSimilarRoutes(@Param("carrier") CarrierProfile carrier,
                            @Param("from") String fromLocation,
                            @Param("to") String toLocation,
                            @Param("date") LocalDate availableDate);

    /**
     * Find routes by transport type and date range
     */
    @Query("SELECT cr FROM CarrierRoute cr " +
            "WHERE cr.routeStatus = 'CREATED' " +
            "AND cr.availableDate BETWEEN :startDate AND :endDate " +
            "AND cr.transportType = :transportType " +
            "AND (cr.maxWeight - cr.currentWeight) >= :minCapacityKg " +
            "ORDER BY cr.availableDate ASC")
    List<CarrierRoute> findRoutesByTransportTypeAndDateRange(
            @Param("transportType") CarrierRoute.TransportType transportType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("minCapacityKg") double minCapacityKg);

    /**
     * ✅ Paginated query with all relationships
     */
    @Query(value = "SELECT DISTINCT cr FROM CarrierRoute cr " +
            "LEFT JOIN FETCH cr.carrierProfile cp " +
            "LEFT JOIN FETCH cp.user " +
            "LEFT JOIN FETCH cr.routePricing",
            countQuery = "SELECT COUNT(DISTINCT cr) FROM CarrierRoute cr")
    Page<CarrierRoute> findAllWithDetails(Pageable pageable);

    // ==================== DEBUG/TEST QUERIES ====================

    /**
     * Simple distance test query for debugging
     */
    @Query(value = """
            SELECT cr.route_id, cr.from_location, cr.to_location, 
                   cr.latitude, cr.longitude,
                   (
                       6371 * acos(
                           cos(radians(:searchLat)) * cos(radians(cr.latitude)) * 
                           cos(radians(cr.longitude) - radians(:searchLng)) + 
                           sin(radians(:searchLat)) * sin(radians(cr.latitude))
                       )
                   ) as distance_km
            FROM carrier_routes cr 
            WHERE cr.route_status = 'CREATED'
            AND cr.latitude IS NOT NULL AND cr.longitude IS NOT NULL
            ORDER BY distance_km
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> testDistanceCalculation(
            @Param("searchLat") double searchLatitude,
            @Param("searchLng") double searchLongitude);
    long countByRouteStatusAndAvailableDate(CarrierRoute.RouteStatus status, LocalDate date);

}
