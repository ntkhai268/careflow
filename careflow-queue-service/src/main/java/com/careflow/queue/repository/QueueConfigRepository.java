package com.careflow.queue.repository;

import com.careflow.queue.domain.QueueConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface QueueConfigRepository extends JpaRepository<QueueConfig, UUID> {
    Optional<QueueConfig> findByDepartmentId(UUID departmentId);
    Optional<QueueConfig> findByDepartmentIdAndActiveTrue(UUID departmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QueueConfig> findFirstByDepartmentIdAndActiveTrue(UUID departmentId);
}
