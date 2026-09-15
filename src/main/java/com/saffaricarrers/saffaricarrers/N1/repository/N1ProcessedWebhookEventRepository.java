package com.saffaricarrers.saffaricarrers.N1.repository;

import com.saffaricarrers.saffaricarrers.N1.model.N1ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface N1ProcessedWebhookEventRepository extends JpaRepository<N1ProcessedWebhookEvent, Long> {
    boolean existsByEventId(String eventId);
}
