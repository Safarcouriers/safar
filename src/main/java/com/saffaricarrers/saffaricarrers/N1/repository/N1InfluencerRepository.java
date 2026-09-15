package com.saffaricarrers.saffaricarrers.N1.repository;

import com.saffaricarrers.saffaricarrers.N1.model.N1Influencer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface N1InfluencerRepository
        extends JpaRepository<N1Influencer, Long> {

    Optional<N1Influencer> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<N1Influencer> findByActiveTrueOrderByCreatedAtDesc();
}