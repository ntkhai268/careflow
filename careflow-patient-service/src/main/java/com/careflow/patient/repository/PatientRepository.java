package com.careflow.patient.repository;

import com.careflow.patient.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByIdCardNumber(String idCardNumber);
}
