package com.careflow.ai.service;

import com.careflow.ai.repository.ClinicalGuidelineChunkRepository;
import com.careflow.ai.repository.projection.ChunkSearchResultProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalHybridSearchServiceTest {

    @Mock
    private ClinicalGuidelineChunkRepository chunkRepository;

    @Mock
    private GeminiClientService geminiClientService;

    @InjectMocks
    private ClinicalHybridSearchService hybridSearchService;

    private UUID chunkIdA;
    private UUID chunkIdB;
    private UUID chunkIdC;

    @BeforeEach
    void setUp() {
        chunkIdA = UUID.randomUUID();
        chunkIdB = UUID.randomUUID();
        chunkIdC = UUID.randomUUID();
    }

    private ChunkSearchResultProjection createMockProjection(UUID id, String docCode, String title, String content) {
        return new ChunkSearchResultProjection() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public UUID getDocumentId() {
                return UUID.randomUUID();
            }

            @Override
            public String getDocumentCode() {
                return docCode;
            }

            @Override
            public String getDocumentTitle() {
                return title;
            }

            @Override
            public String getSpecialty() {
                return "HO_HAP";
            }

            @Override
            public String getSectionTitle() {
                return "Phác đồ điều trị";
            }

            @Override
            public String getContent() {
                return content;
            }

            @Override
            public String getMetadata() {
                return "{}";
            }

            @Override
            public Double getSimilarityScore() {
                return 0.95;
            }
        };
    }

    @Test
    @DisplayName("TDD Red/Green: RRF (k=60) cộng dồn điểm cho tài liệu xuất hiện ở CẢ HAI bên và đưa lên Hạng 1")
    void testRrfScoringAndRanking() {
        // GIVEN:
        // Vector search: Chunk A (rank 1), Chunk B (rank 2)
        // BM25 search: Chunk C (rank 1), Chunk A (rank 2)
        ChunkSearchResultProjection projA = createMockProjection(chunkIdA, "BYT-QD-4800", "Phác đồ viêm phổi", "Liều Amoxicillin...");
        ChunkSearchResultProjection projB = createMockProjection(chunkIdB, "BYT-QD-4800", "Phác đồ viêm phổi", "Chẩn đoán hình ảnh...");
        ChunkSearchResultProjection projC = createMockProjection(chunkIdC, "BYT-QD-5948", "Dược thư DDI", "Tương tác thuốc...");

        when(geminiClientService.generateEmbedding(anyString())).thenReturn("[0.01,0.02]");
        when(chunkRepository.searchByVector(anyString(), isNull(), anyInt())).thenReturn(List.of(projA, projB));
        when(chunkRepository.searchByBm25(anyString(), isNull(), anyInt())).thenReturn(List.of(projC, projA));

        // WHEN:
        List<ClinicalHybridSearchService.RankedChunk> results = hybridSearchService.search("viêm phổi", null);

        // THEN:
        assertNotNull(results);
        assertEquals(3, results.size(), "Phải hợp nhất thành 3 chunks phân biệt (A, C, B)");

        // Chunk A xuất hiện ở rank 1 Vector và rank 2 BM25 -> Điểm: 1/61 + 1/62 ≈ 0.03252
        ClinicalHybridSearchService.RankedChunk topChunk = results.getFirst();
        assertEquals(chunkIdA, topChunk.chunkId(), "Chunk A phải xếp Hạng 1 vì được cả 2 thuật toán đồng thuận");
        assertTrue(topChunk.rrfScore() > 0.03, "Điểm RRF của Chunk A phải xấp xỉ 0.0325");

        // Chunk C (rank 1 BM25) có điểm 1/61 ≈ 0.01639 -> Hạng 2
        assertEquals(chunkIdC, results.get(1).chunkId(), "Chunk C phải đứng Hạng 2 (1/61)");

        // Chunk B (rank 2 Vector) có điểm 1/62 ≈ 0.01613 -> Hạng 3
        assertEquals(chunkIdB, results.get(2).chunkId(), "Chunk B phải đứng Hạng 3 (1/62)");
    }

    @Test
    @DisplayName("TDD Red/Green: Khi Gemini Embedding trả về null (mất mạng/hết quota), hệ thống tự động Fallback sang BM25-only")
    void testFallbackToBm25WhenEmbeddingNull() {
        ChunkSearchResultProjection projC = createMockProjection(chunkIdC, "BYT-QD-5948", "Dược thư DDI", "Tương tác thuốc...");

        when(geminiClientService.generateEmbedding(anyString())).thenReturn(null);
        when(chunkRepository.searchByBm25(anyString(), isNull(), anyInt())).thenReturn(List.of(projC));

        List<ClinicalHybridSearchService.RankedChunk> results = hybridSearchService.search("kháng sinh", null);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(chunkIdC, results.getFirst().chunkId());
        assertEquals(1.0 / 61.0, results.getFirst().rrfScore(), 0.0001, "Điểm RRF của rank 1 duy nhất là 1/61");
    }
}
