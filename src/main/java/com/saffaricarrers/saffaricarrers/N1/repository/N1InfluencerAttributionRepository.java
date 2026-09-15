package com.saffaricarrers.saffaricarrers.N1.repository;


import com.saffaricarrers.saffaricarrers.N1.model.N1InfluencerAttribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface N1InfluencerAttributionRepository
        extends JpaRepository<N1InfluencerAttribution, Long> {

    Optional<N1InfluencerAttribution> findByUserId(String userId);

    boolean existsByUserId(String userId);
}
