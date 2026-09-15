package com.saffaricarrers.saffaricarrers.Repository;

import com.saffaricarrers.saffaricarrers.Entity.*;
import com.saffaricarrers.saffaricarrers.Entity.Package;
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
public interface DeliveryRequestRepository extends JpaRepository<DeliveryRequest, Long> {

    @EntityGraph(value = "DeliveryRequest.full", type = EntityGraph.EntityGraphType.LOAD)
    Optional<DeliveryRequest> findTopByPackageEntityAndStatusInOrderByCreatedAtDesc(
            Package packageEntity, List<DeliveryRequest.RequestStatus> statuses);

    Long countByCarrierRouteAndStatus(CarrierRoute carrierRoute, DeliveryRequest.RequestStatus status);
    Long countByPackageEntityAndStatus(Package packageEntity, DeliveryRequest.RequestStatus status);

    @EntityGraph(value = "DeliveryRequest.full", type = EntityGraph.EntityGraphType.LOAD)
    List<DeliveryRequest> findByCarrierRouteAndStatusIn(
            CarrierRoute carrierRoute, List<DeliveryRequest.RequestStatus> statuses);

    @EntityGraph(value = "DeliveryRequest.full", type = EntityGraph.EntityGraphType.LOAD)
    List<DeliveryRequest> findByCarrierRouteOrderByCreatedAtDesc(CarrierRoute carrierRoute);

    @EntityGraph(value = "DeliveryRequest.full", type = EntityGraph.EntityGraphType.LOAD)
    List<DeliveryRequest> findByPackageEntityOrderByCreatedAtDesc(Package packageEntity);

    @EntityGraph(value = "DeliveryRequest.withUsers", type = EntityGraph.EntityGraphType.LOAD)
    List<DeliveryRequest> findByCarrierAndStatusOrderByCreatedAtDesc(
            User carrier, DeliveryRequest.RequestStatus status);

    @EntityGraph(value = "DeliveryRequest.withUsers", type = EntityGraph.EntityGraphType.LOAD)
    List<DeliveryRequest> findBySenderAndStatusOrderByCreatedAtDesc(
            User sender, DeliveryRequest.RequestStatus status);

    @EntityGraph(value = "DeliveryRequest.full", type = EntityGraph.EntityGraphType.LOAD)
    Optional<DeliveryRequest> findByPackageEntityAndCarrierRouteAndStatusIn(
            Package packageEntity,
            CarrierRoute carrierRoute,
            List<DeliveryRequest.RequestStatus> statuses);

    @EntityGraph(value = "DeliveryRequest.full", type = EntityGraph.EntityGraphType.LOAD)
    List<DeliveryRequest> findByPackageEntityAndStatusIn(
            Package packageEntity,
            List<DeliveryRequest.RequestStatus> statuses);

    @EntityGraph(value = "DeliveryRequest.full", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT dr FROM DeliveryRequest dr WHERE dr.requestId = :requestId")
    Optional<DeliveryRequest> findByRequestIdWithDetails(@Param("requestId") Long requestId);

    // ✅ Admin Dashboard Methods
    long countByStatus(DeliveryRequest.RequestStatus status);

    @Query("SELECT COUNT(dr) FROM DeliveryRequest dr WHERE dr.status = :status")
    long countByStatusCustom(@Param("status") DeliveryRequest.RequestStatus status);

    @Query("SELECT COALESCE(SUM(dr.totalAmount), 0.0) FROM DeliveryRequest dr " +
            "WHERE dr.status = 'DELIVERED'")
    Double sumTotalAmountForDelivered();

    @Query("SELECT COALESCE(AVG(dr.totalAmount), 0.0) FROM DeliveryRequest dr")
    Double getAverageTotalAmount();

    // ✅ Methods with multiple statuses (for pending + active requests)
    @EntityGraph(value = "DeliveryRequest.withUsers", type = EntityGraph.EntityGraphType.LOAD)
    List<DeliveryRequest> findByCarrierAndStatusInOrderByCreatedAtDesc(
            User carrier,
            List<DeliveryRequest.RequestStatus> statuses
    );

    @EntityGraph(value = "DeliveryRequest.withUsers", type = EntityGraph.EntityGraphType.LOAD)
    List<DeliveryRequest> findBySenderAndStatusInOrderByCreatedAtDesc(
            User sender,
            List<DeliveryRequest.RequestStatus> statuses
    );

    // ✅ NEW: Get all requests for carrier (all statuses)
    @EntityGraph(value = "DeliveryRequest.withUsers", type = EntityGraph.EntityGraphType.LOAD)
    List<DeliveryRequest> findByCarrierOrderByCreatedAtDesc(User carrier);

    // ✅ NEW: Get all requests for sender (all statuses)
    @EntityGraph(value = "DeliveryRequest.withUsers", type = EntityGraph.EntityGraphType.LOAD)
    List<DeliveryRequest> findBySenderOrderByCreatedAtDesc(User sender);
    long countByRequestedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByStatusAndDeliveredAtBetween(
            DeliveryRequest.RequestStatus status,
            LocalDateTime start, LocalDateTime end);

    // ✅ For list endpoints — load sender + carrier + package in ONE query (replaces findAll() + filter)
    @EntityGraph(value = "DeliveryRequest.full", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT dr FROM DeliveryRequest dr WHERE dr.status = :status ORDER BY dr.createdAt DESC")
    List<DeliveryRequest> findAllByStatusWithDetails(
            @Param("status") DeliveryRequest.RequestStatus status);

    @Query("SELECT CAST(dr.requestedAt AS date), COUNT(dr) FROM DeliveryRequest dr " +
            "WHERE dr.requestedAt BETWEEN :from AND :to GROUP BY CAST(dr.requestedAt AS date)")
    List<Object[]> countGroupByRequestedDate(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT CAST(dr.deliveredAt AS date), COUNT(dr) FROM DeliveryRequest dr " +
            "WHERE dr.status = 'DELIVERED' AND dr.deliveredAt BETWEEN :from AND :to " +
            "GROUP BY CAST(dr.deliveredAt AS date)")
    List<Object[]> countDeliveredGroupByDate(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    @EntityGraph(value = "DeliveryRequest.withUsers", type = EntityGraph.EntityGraphType.LOAD)
    List<DeliveryRequest> findByPackageEntityAndCarrier(Package packageEntity, User carrier);
    // ─────────────────────────────────────────────────────────────────────────────
// ADD these two methods to your existing DeliveryRequestRepository interface.
// They are the only new DB queries required by getAllOrdersWithPayments().
// ─────────────────────────────────────────────────────────────────────────────

    // 1) All requests, newest first, paged — used for "all" and payment-side filters
    @Query("""
    SELECT dr FROM DeliveryRequest dr
    LEFT JOIN FETCH dr.packageEntity
    LEFT JOIN FETCH dr.sender
    LEFT JOIN FETCH dr.carrier
    LEFT JOIN FETCH dr.payment
    ORDER BY dr.requestedAt DESC
    """)
    List<DeliveryRequest> findAllWithDetailsOrderByRequestedAtDesc(Pageable pageable);

    // 2) Filtered by RequestStatus, newest first, paged
    @Query("""
    SELECT dr FROM DeliveryRequest dr
    LEFT JOIN FETCH dr.packageEntity
    LEFT JOIN FETCH dr.sender
    LEFT JOIN FETCH dr.carrier
    LEFT JOIN FETCH dr.payment
    WHERE dr.status = :status
    ORDER BY dr.requestedAt DESC
    """)
    List<DeliveryRequest> findAllByStatusWithDetailsOrderByRequestedAtDesc(
            @Param("status") DeliveryRequest.RequestStatus status,
            Pageable pageable);
//    Optional<DeliveryRequest> findTopByCarrierAndStatusInOrderByCreatedAtDesc(
//          User carrier,
//          List<DeliveryRequest.RequestStatus> statuses
//  );
//    Optional<DeliveryRequest> findTopByCarrierAndIsRiderDeliveryTrueAndStatusInOrderByCreatedAtDesc(
//            User carrier, List<DeliveryRequest.RequestStatus> statuses
//    );
    Optional<DeliveryRequest> findTopByCarrierAndIsRiderDeliveryTrueAndStatusInOrderByCreatedAtDesc(
            User carrier,
            List<DeliveryRequest.RequestStatus> statuses
    );

    // Keep old one for RiderService.getActiveDelivery fallback (not needed now but safe to keep)
    Optional<DeliveryRequest> findTopByCarrierAndStatusInOrderByCreatedAtDesc(
            User carrier,
            List<DeliveryRequest.RequestStatus> statuses
    );
    Optional<DeliveryRequest>
    findTopByCarrierAndIsRiderDeliveryTrueAndStatusOrderByCreatedAtDesc(
            User carrier,
            DeliveryRequest.RequestStatus status
    );
}
