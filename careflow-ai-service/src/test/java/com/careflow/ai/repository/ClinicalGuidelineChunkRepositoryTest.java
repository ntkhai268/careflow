package com.careflow.ai.repository;

import com.careflow.ai.repository.projection.ChunkSearchResultProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ClinicalGuidelineChunkRepositoryTest {

    @Autowired
    private ClinicalGuidelineChunkRepository chunkRepository;

    private String createDummy768dVectorString() {
        // Tạo chuỗi vector 768 chiều [0.01, 0.01, ..., 0.01]
        return "[" + String.join(",", Collections.nCopies(768, "0.01")) + "]";
    }

    @Test
    @DisplayName("TDD Red/Green: Vector Cosine Search (<=>) trên HNSW index trả về Top K chunks kèm similarityScore")
    void testSearchByVector() {
        String dummyVector = createDummy768dVectorString();
        List<ChunkSearchResultProjection> results = chunkRepository.searchByVector(dummyVector, null, 5);

        assertNotNull(results, "Kết quả tìm kiếm vector không được null");
        assertFalse(results.isEmpty(), "Phải tìm thấy ít nhất 1 chunk từ 1.095 chunks trong DB");
        assertTrue(results.size() <= 5, "Số lượng trả về không được vượt quá limit 5");

        ChunkSearchResultProjection topChunk = results.getFirst();
        assertNotNull(topChunk.getId());
        assertNotNull(topChunk.getDocumentCode());
        assertNotNull(topChunk.getContent());
        assertNotNull(topChunk.getSimilarityScore(), "similarityScore (1 - distance) phải có giá trị");
    }

    @Test
    @DisplayName("TDD Red/Green: Sparse BM25 Search (tsv_content @@ to_tsquery) bắt chính xác từ khóa 'viêm phổi'")
    void testSearchByBm25() {
        List<ChunkSearchResultProjection> results = chunkRepository.searchByBm25("viêm phổi", null, 5);

        assertNotNull(results, "Kết quả BM25 không được null");
        assertFalse(results.isEmpty(), "Kho tri thức có phác đồ viêm phổi (QĐ 4800) nên BM25 phải tìm thấy kết quả");

        ChunkSearchResultProjection topChunk = results.getFirst();
        assertNotNull(topChunk.getId());
        assertNotNull(topChunk.getDocumentCode());
        assertNotNull(topChunk.getContent());
        assertNotNull(topChunk.getSimilarityScore());
        assertTrue(topChunk.getSimilarityScore() > 0, "BM25 rank score phải lớn hơn 0");
    }

    @Test
    @DisplayName("TDD Red/Green: JSONB Metadata Filter tìm kiếm theo mã ICD-10 'J18'")
    void testFindByIcd10Code() {
        List<ChunkSearchResultProjection> results = chunkRepository.findByIcd10Code("J18", 5);

        assertNotNull(results, "Kết quả tìm theo ICD-10 không được null");
        assertFalse(results.isEmpty(), "Kho tri thức phải có các chunks gắn mã ICD-10 J18");

        ChunkSearchResultProjection chunk = results.getFirst();
        assertNotNull(chunk.getId());
        assertNotNull(chunk.getMetadata());
        assertTrue(chunk.getMetadata().contains("J18"), "Metadata phải chứa mã ICD-10 J18");
    }
}
