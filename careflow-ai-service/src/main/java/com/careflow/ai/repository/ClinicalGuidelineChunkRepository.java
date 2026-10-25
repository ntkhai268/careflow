package com.careflow.ai.repository;

import com.careflow.ai.entity.ClinicalGuidelineChunk;
import com.careflow.ai.repository.projection.ChunkSearchResultProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClinicalGuidelineChunkRepository extends JpaRepository<ClinicalGuidelineChunk, UUID> {

    List<ClinicalGuidelineChunk> findByDocumentId(UUID documentId);

    long countByDocumentId(UUID documentId);

    /**
     * DENSE VECTOR COSINE SEARCH:
     * Sử dụng toán tử Cosine Distance (<=>) trên chỉ mục HNSW (idx_clinical_chunks_hnsw)
     * Điểm tương đồng: similarityScore = 1.0 - (embedding <=> cast(:embedding as vector))
     */
    @Query(value = """
        SELECT
            c.id                                                        AS id,
            c.document_id                                               AS documentId,
            d.code                                                      AS documentCode,
            d.title                                                     AS documentTitle,
            d.specialty                                                 AS specialty,
            c.section_title                                             AS sectionTitle,
            c.content                                                   AS content,
            cast(c.metadata as text)                                    AS metadata,
            (1.0 - (c.embedding <=> cast(:embedding as vector)))        AS similarityScore
        FROM clinical_guideline_chunks c
        JOIN clinical_guideline_documents d ON c.document_id = d.id
        WHERE d.is_active = true
          AND (:specialty IS NULL OR d.specialty = :specialty)
        ORDER BY c.embedding <=> cast(:embedding as vector) ASC
        LIMIT :limitVal
        """, nativeQuery = true)
    List<ChunkSearchResultProjection> searchByVector(
            @Param("embedding") String embeddingString,
            @Param("specialty") String specialty,
            @Param("limitVal") int limit);

    /**
     * SPARSE BM25 FULLTEXT SEARCH:
     * Sử dụng toán tử @@ với dictionary 'simple' trên chỉ mục GIN (idx_clinical_chunks_tsv)
     * Điểm tương đồng: similarityScore = ts_rank_cd(tsv_content, plainto_tsquery('simple', :query))
     */
    @Query(value = """
        SELECT
            c.id                                                                        AS id,
            c.document_id                                                               AS documentId,
            d.code                                                                      AS documentCode,
            d.title                                                                     AS documentTitle,
            d.specialty                                                                 AS specialty,
            c.section_title                                                             AS sectionTitle,
            c.content                                                                   AS content,
            cast(c.metadata as text)                                                    AS metadata,
            ts_rank_cd(c.tsv_content, plainto_tsquery('simple', :query))               AS similarityScore
        FROM clinical_guideline_chunks c
        JOIN clinical_guideline_documents d ON c.document_id = d.id
        WHERE d.is_active = true
          AND c.tsv_content @@ plainto_tsquery('simple', :query)
          AND (:specialty IS NULL OR d.specialty = :specialty)
        ORDER BY similarityScore DESC
        LIMIT :limitVal
        """, nativeQuery = true)
    List<ChunkSearchResultProjection> searchByBm25(
            @Param("query") String query,
            @Param("specialty") String specialty,
            @Param("limitVal") int limit);

    /**
     * JSONB METADATA FILTER:
     * Lọc cứng các đoạn phác đồ gắn mã ICD-10 trong mảng JSONB metadata.icd10_codes
     */
    @Query(value = """
        SELECT
            c.id                                                        AS id,
            c.document_id                                               AS documentId,
            d.code                                                      AS documentCode,
            d.title                                                     AS documentTitle,
            d.specialty                                                 AS specialty,
            c.section_title                                             AS sectionTitle,
            c.content                                                   AS content,
            cast(c.metadata as text)                                    AS metadata,
            1.0                                                         AS similarityScore
        FROM clinical_guideline_chunks c
        JOIN clinical_guideline_documents d ON c.document_id = d.id
        WHERE d.is_active = true
          AND c.metadata->'icd10_codes' @> cast(concat('["', :icd10Code, '"]') as jsonb)
        LIMIT :limitVal
        """, nativeQuery = true)
    List<ChunkSearchResultProjection> findByIcd10Code(
            @Param("icd10Code") String icd10Code,
            @Param("limitVal") int limit);
}
