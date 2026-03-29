package com.saffaricarrers.saffaricarrers.Repository;


import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import com.saffaricarrers.saffaricarrers.Entity.RoutePricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoutePricingRepository extends JpaRepository<RoutePricing, Long> {

    List<RoutePricing> findByCarrierRoute(CarrierRoute carrierRoute);

    Optional<RoutePricing> findByCarrierRouteAndProductType(CarrierRoute carrierRoute, RoutePricing.ProductType productType);

    void deleteByCarrierRoute(CarrierRoute carrierRoute);


    // ✅ NEW: Bulk fetch pricing for multiple routes
    @Query("SELECT rp FROM RoutePricing rp WHERE rp.carrierRoute IN :routes")
    List<RoutePricing> findByCarrierRouteIn(@Param("routes") List<CarrierRoute> routes);
}