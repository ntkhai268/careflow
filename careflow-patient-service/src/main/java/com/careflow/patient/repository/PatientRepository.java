package com.careflow.patient.repository;

import com.careflow.patient.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findFirstByUserIdOrderByCreatedAtAsc(UUID userId);

    boolean existsByUserId(UUID userId);

    long countByUserId(UUID userId);

    List<Patient> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    boolean existsByUserIdAndIdCardNumber(UUID userId, String idCardNumber);

    boolean existsByUserIdAndIdCardNumberAndIdNot(UUID userId, String idCardNumber, UUID id);

    boolean existsByIdCardNumber(String idCardNumber);
}
