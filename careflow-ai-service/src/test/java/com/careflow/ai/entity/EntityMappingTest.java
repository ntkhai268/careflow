package com.careflow.ai.entity;

import com.careflow.ai.repository.ClinicalGuidelineChunkRepository;
import com.careflow.ai.repository.ClinicalGuidelineDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EntityMappingTest {

    @Autowired
    private ClinicalGuidelineDocumentRepository documentRepository;

    @Autowired
    private ClinicalGuidelineChunkRepository chunkRepository;

    @Test
    @DisplayName("JPA Entity mapping: Truy vấn tài liệu phác đồ gốc từ bảng clinical_guideline_documents")
    void testDocumentEntityMapping() {
        assertNotNull(documentRepository, "ClinicalGuidelineDocumentRepository phải được inject");
        long count = documentRepository.count();
        assertTrue(count >= 4, "Phải có ít nhất 4 tài liệu phác đồ cha trong DB (hiện có: " + count + ")");

        List<ClinicalGuidelineDocument> docs = documentRepository.findAll();
        assertFalse(docs.isEmpty());

        ClinicalGuidelineDocument doc = docs.getFirst();
        assertNotNull(doc.getId());
        assertNotNull(doc.getCode());
        assertNotNull(doc.getTitle());
        assertNotNull(doc.getSpecialty());
    }

    @Test
    @DisplayName("JPA Entity mapping: Truy vấn các phân đoạn tri thức từ bảng clinical_guideline_chunks")
    void testChunkEntityMapping() {
        assertNotNull(chunkRepository, "ClinicalGuidelineChunkRepository phải được inject");
        long count = chunkRepository.count();
        assertEquals(1095, count, "Bảng clinical_guideline_chunks phải có đúng 1095 chunks");

        List<ClinicalGuidelineChunk> chunks = chunkRepository.findAll();
        assertFalse(chunks.isEmpty());

        ClinicalGuidelineChunk chunk = chunks.getFirst();
        assertNotNull(chunk.getId());
        assertNotNull(chunk.getDocumentId());
        assertNotNull(chunk.getContent());
    }
}
