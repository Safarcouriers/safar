package com.saffaricarrers.saffaricarrers.Repository;

import com.saffaricarrers.saffaricarrers.Entity.CarrierVehicleVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CarrierVehicleVerificationRepository extends JpaRepository<CarrierVehicleVerification, Long> {

    Optional<CarrierVehicleVerification> findByUid(String uid);

    boolean existsByUid(String uid);

    Optional<CarrierVehicleVerification> findByRcNumber(String rcNumber);

    Optional<CarrierVehicleVerification> findByDlNumber(String dlNumber);
}