package com.saffaricarrers.saffaricarrers.Repository;

import com.saffaricarrers.saffaricarrers.Entity.CarrierProfile;
import com.saffaricarrers.saffaricarrers.Entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CarrierProfileRepository extends JpaRepository<CarrierProfile, Long> {

    List<CarrierProfile> findByIsOnlineTrue();
    @Query("""
        SELECT cp FROM CarrierProfile cp
        JOIN FETCH cp.user u
        WHERE cp.isOnline = true
          AND cp.lastLat IS NOT NULL
          AND cp.lastLng IS NOT NULL
          AND cp.lastLat  BETWEEN :minLat AND :maxLat
          AND cp.lastLng  BETWEEN :minLng AND :maxLng
    """)
    List<CarrierProfile> findOnlineCarriersInBoundingBox(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng
    );
    @EntityGraph(value = "CarrierProfile.withBankDetails", type = EntityGraph.EntityGraphType.LOAD)
    Optional<CarrierProfile> findByUserUid(String userId);

    // ✅ ADD THIS - for AdminDashboard to use
    @EntityGraph(value = "CarrierProfile.withBankDetails", type = EntityGraph.EntityGraphType.LOAD)
    @Override
    List<CarrierProfile> findAll();
    Optional<CarrierProfile> findByUser(User user);

    boolean existsByUser(User user);

    @EntityGraph(value = "CarrierProfile.withBankDetails", type = EntityGraph.EntityGraphType.LOAD)
    Optional<CarrierProfile> findByUserUserId(String userId);
    @Query("SELECT COALESCE(SUM(c.pendingCommission), 0.0) FROM CarrierProfile c")
    Double sumPendingCommission();

    @Query("SELECT COALESCE(SUM(c.totalEarnings), 0.0) FROM CarrierProfile c")
    Double sumTotalEarnings();

    @Query("SELECT COUNT(c) FROM CarrierProfile c WHERE c.pendingCommission > 0")
    long countWithPendingCommission();

    long countByStatus(CarrierProfile.CarrierStatus status);

}