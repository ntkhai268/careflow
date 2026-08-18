package com.careflow.queue.repository;

import com.careflow.queue.domain.HospitalCheckInConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HospitalCheckInConfigRepository extends JpaRepository<HospitalCheckInConfig, UUID> {
    Optional<HospitalCheckInConfig> findFirstByOrderByUpdatedAtDesc();
}
