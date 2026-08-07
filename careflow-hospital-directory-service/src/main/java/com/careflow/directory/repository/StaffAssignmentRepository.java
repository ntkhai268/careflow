package com.careflow.directory.repository;

import com.careflow.directory.model.StaffAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffAssignmentRepository extends JpaRepository<StaffAssignment, UUID> {
    boolean existsByUserIdAndRoomIdAndIsActiveTrue(UUID userId, String roomId);

    Optional<StaffAssignment> findByUserIdAndRoomId(UUID userId, String roomId);

    List<StaffAssignment> findAllByOrderByRoomIdAscUserIdAsc();

    List<StaffAssignment> findByUserIdOrderByRoomIdAsc(UUID userId);

    List<StaffAssignment> findByRoomIdOrderByUserIdAsc(String roomId);
}
