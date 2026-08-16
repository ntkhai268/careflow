package com.careflow.appointment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One row per bookable room/date/time-slot.  The row is pessimistically locked
 * while a booking is checked and inserted, preventing two concurrent requests
 * from exceeding the configured slot capacity.
 */
@Entity
@Table(name = "appointment_slot_locks")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentSlotLock {

    @Id
    @Column(name = "slot_key", nullable = false, length = 200)
    private String slotKey;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
