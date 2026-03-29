package com.saffaricarrers.saffaricarrers.Entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "insurance")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Insurance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long insuranceId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id", nullable = false)
    private Package packageEntity;

    @Column(nullable = false)
    private Double productValue;

    @Column(nullable = false)
    private Double insuranceAmount;

    @Column(nullable = false)
    private Double coveragePercentage; // e.g., 80% of product value

    @Enumerated(EnumType.STRING)
    private InsuranceStatus status = InsuranceStatus.ACTIVE;

    private String policyNumber;
    private LocalDateTime validUntil;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum InsuranceStatus {ACTIVE, CLAIMED, EXPIRED, CANCELLED}
}