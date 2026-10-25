package com.careflow.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinical_guideline_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClinicalGuidelineDocument {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "code", unique = true, nullable = false, length = 100)
    private String code;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "issuer", nullable = false, length = 255)
    private String issuer;

    @Column(name = "published_year", nullable = false)
    private Integer publishedYear;

    @Column(name = "specialty", nullable = false, length = 100)
    private String specialty;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
