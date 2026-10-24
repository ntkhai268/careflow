package com.careflow.ai.service;

import com.careflow.ai.repository.ClinicalGuidelineChunkRepository;
import com.careflow.ai.repository.projection.ChunkSearchResultProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Thuật toán Hybrid Search kết hợp Reciprocal Rank Fusion (RRF k=60)
 * Chuẩn học thuật: Cormack et al. (SIGIR 2009)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClinicalHybridSearchService {

    public static final int RRF_K = 60;
    public static final int CANDIDATES_PER_SOURCE = 20;
    public static final int FINAL_TOP_K = 5;

    private final ClinicalGuidelineChunkRepository chunkRepository;
    private final GeminiClientService geminiClientService;

    public record RankedChunk(
            UUID chunkId,
            String documentCode,
            String documentTitle,
            String specialty,
            String sectionTitle,
            String content,
            double rrfScore
    ) {}

    public List<RankedChunk> search(String queryText, String specialty) {
        if (queryText == null || queryText.trim().isEmpty()) {
            return List.of();
        }

        Map<UUID, Double> rrfScores = new HashMap<>();
        Map<UUID, ChunkSearchResultProjection> chunkCache = new HashMap<>();

        // 1. DENSE VECTOR SEARCH (HNSW Cosine Distance)
        try {
            String embeddingString = geminiClientService.generateEmbedding(queryText);
            if (embeddingString != null) {
                List<ChunkSearchResultProjection> vectorResults =
                        chunkRepository.searchByVector(embeddingString, specialty, CANDIDATES_PER_SOURCE);

                if (vectorResults != null) {
                    for (int rank = 1; rank <= vectorResults.size(); rank++) {
                        ChunkSearchResultProjection chunk = vectorResults.get(rank - 1);
                        UUID id = chunk.getId();
                        double score = 1.0 / (RRF_K + rank);
                        rrfScores.merge(id, score, Double::sum);
                        chunkCache.putIfAbsent(id, chunk);
                    }
                }
            } else {
                log.info("Gemini Embedding unavailable. Falling back to BM25-only search.");
            }
        } catch (Exception e) {
            log.warn("Dense Vector search encountered error: {}. Continuing with BM25.", e.getMessage());
        }

        // 2. SPARSE BM25 SEARCH (PostgreSQL GIN tsvector)
        try {
            List<ChunkSearchResultProjection> bm25Results =
                    chunkRepository.searchByBm25(queryText.trim(), specialty, CANDIDATES_PER_SOURCE);

            if (bm25Results != null) {
                for (int rank = 1; rank <= bm25Results.size(); rank++) {
                    ChunkSearchResultProjection chunk = bm25Results.get(rank - 1);
                    UUID id = chunk.getId();
                    double score = 1.0 / (RRF_K + rank);
                    rrfScores.merge(id, score, Double::sum);
                    chunkCache.putIfAbsent(id, chunk);
                }
            }
        } catch (Exception e) {
            log.warn("Sparse BM25 search encountered error: {}.", e.getMessage());
        }

        // 3. RE-RANKING & SELECT TOP K
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(FINAL_TOP_K)
                .map(entry -> {
                    ChunkSearchResultProjection c = chunkCache.get(entry.getKey());
                    return new RankedChunk(
                            c.getId(),
                            c.getDocumentCode(),
                            c.getDocumentTitle(),
                            c.getSpecialty(),
                            c.getSectionTitle(),
                            c.getContent(),
                            entry.getValue()
                    );
                })
                .collect(Collectors.toList());
    }
}
