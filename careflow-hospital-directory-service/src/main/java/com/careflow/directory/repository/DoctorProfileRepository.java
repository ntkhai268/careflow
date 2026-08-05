package com.careflow.directory.repository;

import com.careflow.directory.model.DoctorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, UUID> {
    Optional<DoctorProfile> findByUserId(UUID userId);
    List<DoctorProfile> findByDepartmentCodeAndIsActiveTrue(String departmentCode);
    List<DoctorProfile> findByIsActiveTrue();
}
