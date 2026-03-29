package com.saffaricarrers.saffaricarrers.Repository;

import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import com.saffaricarrers.saffaricarrers.Entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PackageRepository extends JpaRepository<Package, Long> {

//    List<Package> findBySenderOrderByCreatedAtDesc(User sender);
//
//    Page<Package> findBySenderAndStatus(User sender, Package.PackageStatus status, Pageable pageable);
//    List<Package> findBySenderAndStatus(User sender, Package.PackageStatus status);
//
//    @Query("SELECT p FROM Package p WHERE p.sender = :sender " +
//            "AND p.status IN ('CREATED', 'REQUEST_SENT')")
//    List<Package> findActiveBySender(User sender);
//
//    long countBySenderAndStatus(User sender, Package.PackageStatus status);
//
//    // Add this new method
//    long countBySender(User sender);
//
//    // For search functionality
//    @Query("SELECT p FROM Package p WHERE " +
//            "LOWER(p.fromAddress) LIKE LOWER(CONCAT('%', :fromLocation, '%')) " +
//            "AND LOWER(p.toAddress) LIKE LOWER(CONCAT('%', :toLocation, '%')) " +
//            "AND p.status = 'CREATED'")
//    List<Package> findPackagesForRoute(String fromLocation, String toLocation);
//
//    // Find packages by transport type
//    List<Package> findByTransportTypeAndStatus(CarrierRoute.TransportType transportType, Package.PackageStatus status);

    /**
     * Find packages within radius from given coordinates using Haversine formula
     * Distance is calculated in kilometers
     */
    @Query(value = """
    SELECT * FROM packages p 
    WHERE p.status = 'CREATED'
    AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL
    AND p.pick_up_date >= CURDATE()
    AND (
        6371 * acos(
            cos(radians(:latitude)) * cos(radians(p.latitude)) * 
            cos(radians(p.longitude) - radians(:longitude)) + 
            sin(radians(:latitude)) * sin(radians(p.latitude))
        )
    ) <= :radiusKm
    ORDER BY (
        6371 * acos(
            cos(radians(:latitude)) * cos(radians(p.latitude)) * 
            cos(radians(p.longitude) - radians(:longitude)) + 
            sin(radians(:latitude)) * sin(radians(p.latitude))
        )
    )
    """, nativeQuery = true)
    List<Package> findPackagesWithinRadius(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusKm") double radiusKm);

    /**
     * Find packages along route (from point A to point B) within specified corridor width
     */
    @Query(value = """
        SELECT p.*, (
            6371 * acos(
                cos(radians(:fromLat)) * cos(radians(p.latitude)) * 
                cos(radians(p.longitude) - radians(:fromLng)) + 
                sin(radians(:fromLat)) * sin(radians(p.latitude))
            )
        ) as distance_from_start,
        (
            6371 * acos(
                cos(radians(:toLat)) * cos(radians(p.latitude)) * 
                cos(radians(p.longitude) - radians(:toLng)) + 
                sin(radians(:toLat)) * sin(radians(p.latitude))
            )
        ) as distance_to_end
        FROM packages p 
        WHERE p.status = 'CREATED'
        AND p.transport_type = :transportType
        AND (
            -- Distance from pickup point is within corridor width
            6371 * acos(
                cos(radians(:fromLat)) * cos(radians(p.latitude)) * 
                cos(radians(p.longitude) - radians(:fromLng)) + 
                sin(radians(:fromLat)) * sin(radians(p.latitude))
            ) <= :corridorWidthKm
            OR 
            -- Package is along the route (simplified linear approximation)
            (
                ABS(
                    ((:toLng - :fromLng) * (p.latitude - :fromLat) - 
                     (:toLat - :fromLat) * (p.longitude - :fromLng)) / 
                    SQRT(
                        (:toLng - :fromLng) * (:toLng - :fromLng) + 
                        (:toLat - :fromLat) * (:toLat - :fromLat)
                    )
                ) * 111.32 <= :corridorWidthKm
                AND p.latitude BETWEEN LEAST(:fromLat, :toLat) - (:corridorWidthKm/111.32) 
                                   AND GREATEST(:fromLat, :toLat) + (:corridorWidthKm/111.32)
                AND p.longitude BETWEEN LEAST(:fromLng, :toLng) - (:corridorWidthKm/111.32) 
                                    AND GREATEST(:fromLng, :toLng) + (:corridorWidthKm/111.32)
            )
        )
        ORDER BY distance_from_start
        """, nativeQuery = true)
    List<Object[]> findPackagesAlongRoute(
            @Param("fromLat") double fromLatitude,
            @Param("fromLng") double fromLongitude,
            @Param("toLat") double toLatitude,
            @Param("toLng") double toLongitude,
            @Param("corridorWidthKm") double corridorWidthKm,
            @Param("transportType") String transportType);

    /**
     * Find packages by distance ranges (near, medium, far)
     */
    @Query(value = """
        SELECT p.*, 
        (
            6371 * acos(
                cos(radians(:latitude)) * cos(radians(p.latitude)) * 
                cos(radians(p.longitude) - radians(:longitude)) + 
                sin(radians(:latitude)) * sin(radians(p.latitude))
            )
        ) as distance_km,
        CASE 
            WHEN (6371 * acos(cos(radians(:latitude)) * cos(radians(p.latitude)) * 
                              cos(radians(p.longitude) - radians(:longitude)) + 
                              sin(radians(:latitude)) * sin(radians(p.latitude)))) <= :nearRadius 
            THEN 'NEAR'
            WHEN (6371 * acos(cos(radians(:latitude)) * cos(radians(p.latitude)) * 
                              cos(radians(p.longitude) - radians(:longitude)) + 
                              sin(radians(:latitude)) * sin(radians(p.latitude)))) <= :mediumRadius 
            THEN 'MEDIUM'
            ELSE 'FAR'
        END as distance_category
        FROM packages p 
        WHERE p.status = 'CREATED'
        AND (
            6371 * acos(
                cos(radians(:latitude)) * cos(radians(p.latitude)) * 
                cos(radians(p.longitude) - radians(:longitude)) + 
                sin(radians(:latitude)) * sin(radians(p.latitude))
            )
        ) <= :maxRadius
        ORDER BY distance_km
        """, nativeQuery = true)
    List<Object[]> findPackagesByDistanceCategory(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("nearRadius") double nearRadius,
            @Param("mediumRadius") double mediumRadius,
            @Param("maxRadius") double maxRadius);

    /**
     * Find packages between two coordinates (rectangular bounding box)
     */
    @Query("SELECT p FROM Package p WHERE " +
            "p.status = :status " +
            "AND p.latitude BETWEEN :minLat AND :maxLat " +
            "AND p.longitude BETWEEN :minLng AND :maxLng " +
            "ORDER BY p.createdAt DESC")
    List<Package> findPackagesInBoundingBox(
            @Param("minLat") double minLatitude,
            @Param("maxLat") double maxLatitude,
            @Param("minLng") double minLongitude,
            @Param("maxLng") double maxLongitude,
            @Param("status") Package.PackageStatus status);

    // Simplified route query that returns Package entities directly
    @Query(value = """
        SELECT p.*
        FROM packages p 
        WHERE p.status = 'CREATED'
        AND (
            6371 * acos(
                cos(radians(:fromLat)) * cos(radians(p.latitude)) * 
                cos(radians(p.longitude) - radians(:fromLng)) + 
                sin(radians(:fromLat)) * sin(radians(p.latitude))
            ) <= :corridorWidthKm
        )
        ORDER BY (
            6371 * acos(
                cos(radians(:fromLat)) * cos(radians(p.latitude)) * 
                cos(radians(p.longitude) - radians(:fromLng)) + 
                sin(radians(:fromLat)) * sin(radians(p.latitude))
            )
        )
        LIMIT 50
        """, nativeQuery = true)
    List<Package> findPackagesAlongRouteSimplified(
            @Param("fromLat") double fromLatitude,
            @Param("fromLng") double fromLongitude,
            @Param("toLat") double toLatitude,
            @Param("toLng") double toLongitude,
            @Param("corridorWidthKm") double corridorWidthKm);


    @EntityGraph(value = "Package.withSender", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT p FROM Package p WHERE " +
            "LOWER(p.fromAddress) LIKE LOWER(CONCAT('%', :fromLocation, '%')) " +
            "AND LOWER(p.toAddress) LIKE LOWER(CONCAT('%', :toLocation, '%')) " +
            "AND p.status = 'CREATED'")
    List<Package> findPackagesForRoute(@Param("fromLocation") String fromLocation,
                                       @Param("toLocation") String toLocation);

    @EntityGraph(value = "Package.withSender", type = EntityGraph.EntityGraphType.LOAD)
    List<Package> findByTransportTypeAndStatus(CarrierRoute.TransportType transportType,
                                               Package.PackageStatus status);

//
@EntityGraph(value = "Package.withSender", type = EntityGraph.EntityGraphType.LOAD)
List<Package> findBySenderOrderByCreatedAtDesc(User sender);

    @EntityGraph(value = "Package.withSender", type = EntityGraph.EntityGraphType.LOAD)
    Page<Package> findBySenderAndStatus(User sender, Package.PackageStatus status, Pageable pageable);

    @EntityGraph(value = "Package.withSender", type = EntityGraph.EntityGraphType.LOAD)
    List<Package> findBySenderAndStatus(User sender, Package.PackageStatus status);

    @EntityGraph(value = "Package.withSender", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT p FROM Package p WHERE p.sender = :sender " +
            "AND p.status IN ('CREATED', 'REQUEST_SENT')")
    List<Package> findActiveBySender(@Param("sender") User sender);

    // Only load the sender, and absolutely nothing else!
    @EntityGraph(attributePaths = {"sender"})
    @Query("SELECT p FROM Package p WHERE p.packageId = :id")
    Optional<Package> findByIdWithSender(@Param("id") Long id);


    // ✅ NEW: Optimized method to find all packages with sender loaded
    @EntityGraph(value = "Package.withSender", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT p FROM Package p")
    List<Package> findAllWithSender();

    // Count methods (no need for EntityGraph)
    long countBySenderAndStatus(User sender, Package.PackageStatus status);
    long countBySender(User sender);

    // ==================== GEOSPATIAL QUERIES ====================

    /**
     * ✅ OPTIMIZED: Returns Package IDs only, then bulk fetch with EntityGraph
     */
    @Query(value = """
        SELECT p.package_id FROM packages p
        WHERE p.status = 'CREATED'
        AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL
        AND (
            6371 * acos(
                cos(radians(:latitude)) * cos(radians(p.latitude)) *
                cos(radians(p.longitude) - radians(:longitude)) +
                sin(radians(:latitude)) * sin(radians(p.latitude))
            )
        ) <= :radiusKm
        ORDER BY (
            6371 * acos(
                cos(radians(:latitude)) * cos(radians(p.latitude)) *
                cos(radians(p.longitude) - radians(:longitude)) +
                sin(radians(:latitude)) * sin(radians(p.latitude))
            )
        )
        """, nativeQuery = true)
    List<Long> findPackageIdsWithinRadius(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusKm") double radiusKm);

    /**
     * ✅ OPTIMIZED: Bulk fetch packages by IDs with sender loaded
     */
    @EntityGraph(value = "Package.withSender", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT p FROM Package p WHERE p.packageId IN :ids")
    List<Package> findByIdInWithSender(@Param("ids") List<Long> ids);

    /**
     * ✅ OPTIMIZED: Get package IDs along route
     */
    @Query(value = """
        SELECT p.package_id
        FROM packages p
        WHERE p.status = 'CREATED'
        AND (
            6371 * acos(
                cos(radians(:fromLat)) * cos(radians(p.latitude)) *
                cos(radians(p.longitude) - radians(:fromLng)) +
                sin(radians(:fromLat)) * sin(radians(p.latitude))
            ) <= :corridorWidthKm
        )
        ORDER BY (
            6371 * acos(
                cos(radians(:fromLat)) * cos(radians(p.latitude)) *
                cos(radians(p.longitude) - radians(:fromLng)) +
                sin(radians(:fromLat)) * sin(radians(p.latitude))
            )
        )
        LIMIT 50
        """, nativeQuery = true)
    List<Long> findPackageIdsAlongRouteSimplified(
            @Param("fromLat") double fromLatitude,
            @Param("fromLng") double fromLongitude,
            @Param("toLat") double toLatitude,
            @Param("toLng") double toLongitude,
            @Param("corridorWidthKm") double corridorWidthKm);

    /**
     * ✅ OPTIMIZED: Get package IDs in bounding box
     */
    @Query("SELECT p.packageId FROM Package p WHERE " +
            "p.status = :status " +
            "AND p.latitude BETWEEN :minLat AND :maxLat " +
            "AND p.longitude BETWEEN :minLng AND :maxLng " +
            "AND p.pickUpDate >= :today " +          // ✅ ADD THIS
            "ORDER BY p.createdAt DESC")
    List<Long> findPackageIdsInBoundingBox(
            @Param("minLat") double minLatitude,
            @Param("maxLat") double maxLatitude,
            @Param("minLng") double minLongitude,
            @Param("maxLng") double maxLongitude,
            @Param("status") Package.PackageStatus status,
            @Param("today") String today);           // ✅ ADD THIS PARAM
    // In CarrierRouteRepository
    @Query("SELECT p FROM Package p LEFT JOIN FETCH p.sender")
    List<Package> fetchAllFull();
    List<Package> findByStatus(Package.PackageStatus status);
    @Query("SELECT p.status, COUNT(p) FROM Package p GROUP BY p.status")
    List<Object[]> countGroupByStatus();

    @Query("SELECT p.productType, COUNT(p) FROM Package p GROUP BY p.productType")
    List<Object[]> countGroupByProductType();

    @Query("SELECT p.transportType, COUNT(p) FROM Package p GROUP BY p.transportType")
    List<Object[]> countGroupByTransportType();

    long countByInsurance(boolean insurance);

    @Query("SELECT COALESCE(SUM(p.productValue), 0.0) FROM Package p")
    Double sumProductValue();

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT CAST(p.createdAt AS date), COUNT(p) FROM Package p " +
            "WHERE p.createdAt BETWEEN :from AND :to GROUP BY CAST(p.createdAt AS date)")
    List<Object[]> countGroupByCreatedDate(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}