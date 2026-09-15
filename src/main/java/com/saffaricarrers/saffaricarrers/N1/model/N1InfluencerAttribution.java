package com.saffaricarrers.saffaricarrers.N1.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "n1_influencer_attributions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_n1_attribution_user",
                        columnNames = "user_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class N1InfluencerAttribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Firebase UID.
     * One user can have only ONE influencer attribution.
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "influencer_code", nullable = false, length = 50)
    private String influencerCode;

    /**
     * Example:
     * play_install_referrer
     */
    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false)
    private LocalDateTime attributedAt;

    @PrePersist
    protected void onCreate() {
        if (attributedAt == null) {
            attributedAt = LocalDateTime.now();
        }
    }
}