package com.careflow.queue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hospital_check_in_configs", schema = "queue")
@Getter
@Setter
@NoArgsConstructor
public class HospitalCheckInConfig {
    @Id
    private UUID id;

    @Column(name = "facility_code", nullable = false, unique = true, length = 50)
    private String siteId;

    @Column(name = "facility_name", nullable = false, length = 160)
    private String facilityName;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "allowed_radius_meters", nullable = false)
    private double radiusMeters;

    @Column(name = "max_accuracy_meters", nullable = false)
    private double maxAccuracyMeters;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }
}
