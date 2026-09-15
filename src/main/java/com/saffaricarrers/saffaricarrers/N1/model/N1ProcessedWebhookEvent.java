package com.saffaricarrers.saffaricarrers.N1.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name="n1_processed_webhook_events", indexes=@Index(name="idx_n1_event_id", columnList="eventId", unique=true))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class N1ProcessedWebhookEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, unique=true, length=64) private String eventId;
    @Column(nullable=false, length=64) private String eventType;
    @Column(length=80) private String orderId;
    @Column(length=80) private String paymentId;
    @Column(nullable=false) private LocalDateTime processedAt;
    @PrePersist protected void onCreate(){ if(processedAt==null) processedAt=LocalDateTime.now(); }
}
