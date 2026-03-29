package com.saffaricarrers.saffaricarrers.Repository;

import com.saffaricarrers.saffaricarrers.Entity.Insurance;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InsuranceRepository extends JpaRepository<Insurance, Long> {
    Optional<Insurance> findByPackageEntity(Package packageEntity);
    long countByStatus(Insurance.InsuranceStatus status);
}