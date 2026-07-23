package com.careflow.prescription.repository;

import com.careflow.prescription.model.Drug;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DrugRepository extends JpaRepository<Drug, UUID> {

    List<Drug> findByActiveTrueOrderByNameAsc();

    Optional<Drug> findByCodeAndActiveTrue(String code);

    boolean existsByCode(String code);
}
