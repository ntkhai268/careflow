package com.careflow.appointment.repository;

import com.careflow.appointment.model.AppointmentOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AppointmentOutboxRepository extends JpaRepository<AppointmentOutboxEvent, UUID> {
    List<AppointmentOutboxEvent> findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<AppointmentOutboxEvent.Status> statuses, Instant now, Pageable pageable);
}
