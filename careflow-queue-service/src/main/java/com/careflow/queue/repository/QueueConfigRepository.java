package com.careflow.queue.repository;

import com.careflow.queue.domain.QueueConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface QueueConfigRepository extends JpaRepository<QueueConfig, UUID> {
    java.util.List<QueueConfig> findAllByDepartmentIdOrderByRoomCodeAsc(UUID departmentId);
    Optional<QueueConfig> findByRoomCode(String roomCode);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from QueueConfig c where c.id=:id")
    Optional<QueueConfig> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
    Optional<QueueConfig> findByDepartmentId(UUID departmentId);
    Optional<QueueConfig> findByDepartmentIdAndActiveTrue(UUID departmentId);
    Optional<QueueConfig> findByRoomCodeAndActiveTrue(String roomCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QueueConfig> findFirstByDepartmentIdAndActiveTrue(UUID departmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QueueConfig> findFirstByRoomCodeAndActiveTrue(String roomCode);
}
