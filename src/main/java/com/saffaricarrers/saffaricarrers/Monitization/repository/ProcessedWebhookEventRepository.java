package com.saffaricarrers.saffaricarrers.Monitization.repository;


import com.saffaricarrers.saffaricarrers.Monitization.webhook.ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedWebhookEventRepository
        extends JpaRepository<ProcessedWebhookEvent, Long> {

    boolean existsByEventId(String eventId);
}