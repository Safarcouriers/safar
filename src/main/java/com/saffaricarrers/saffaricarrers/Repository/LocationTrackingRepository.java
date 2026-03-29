package com.saffaricarrers.saffaricarrers.Repository;

import com.saffaricarrers.saffaricarrers.Entity.LocationTracking;
import com.saffaricarrers.saffaricarrers.Entity.DeliveryRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationTrackingRepository extends JpaRepository<LocationTracking, Long> {

    // Get the latest location for a request
    Optional<LocationTracking> findTopByDeliveryRequestOrderByRecordedAtDesc(DeliveryRequest deliveryRequest);

    // Get all location history for a request (for breadcrumb trail)
    List<LocationTracking> findByDeliveryRequestOrderByRecordedAtAsc(DeliveryRequest deliveryRequest);

    // Get location history for the last N hours
    @Query("SELECT lt FROM LocationTracking lt WHERE lt.deliveryRequest = :request " +
            "AND lt.recordedAt >= (CURRENT_TIMESTAMP - :hours HOUR) ORDER BY lt.recordedAt ASC")
    List<LocationTracking> findRecentByDeliveryRequest(
            @Param("request") DeliveryRequest request,
            @Param("hours") int hours
    );
}