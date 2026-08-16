package com.careflow.directory.repository;

import com.careflow.directory.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, String> {
    List<Department> findByIsActiveTrue();
    Optional<Department> findById(UUID id);
}
