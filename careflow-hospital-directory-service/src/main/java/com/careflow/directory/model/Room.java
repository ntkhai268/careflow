package com.careflow.directory.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "rooms", indexes = {
        @Index(name = "idx_room_department", columnList = "departmentCode")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @Column(nullable = false, length = 50)
    private String id;

    @Column(nullable = false, length = 50)
    private String departmentCode;

    @Column(nullable = false, length = 150)
    private String displayName;

    @Column(nullable = false, length = 30)
    private String roomType; // CONSULTATION, LAB, IMAGING, PHARMACY

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
