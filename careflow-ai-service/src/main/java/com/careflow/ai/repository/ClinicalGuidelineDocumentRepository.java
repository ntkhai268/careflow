package com.careflow.ai.repository;

import com.careflow.ai.entity.ClinicalGuidelineDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClinicalGuidelineDocumentRepository extends JpaRepository<ClinicalGuidelineDocument, UUID> {

    Optional<ClinicalGuidelineDocument> findByCode(String code);

    List<ClinicalGuidelineDocument> findBySpecialtyAndIsActiveTrue(String specialty);

    List<ClinicalGuidelineDocument> findByIsActiveTrue();
}
