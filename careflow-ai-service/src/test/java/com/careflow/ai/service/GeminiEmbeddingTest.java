package com.careflow.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GeminiEmbeddingTest {

    @Autowired
    private GeminiClientService geminiClientService;

    @Test
    @DisplayName("TDD Red/Green: generateEmbedding trả về định dạng vector chuỗi '[f1,f2,...,f768]' hoặc null an toàn")
    void testGenerateEmbedding() {
        assertNotNull(geminiClientService, "GeminiClientService phải được inject");

        String query = "Bệnh nhân sốt cao, khó thở, ho có đờm";
        String embeddingVectorString = geminiClientService.generateEmbedding(query);

        if (geminiClientService.isGeminiConfigured()) {
            // Khi có API key hợp lệ
            if (embeddingVectorString != null) {
                assertTrue(embeddingVectorString.startsWith("["), "Vector phải bắt đầu bằng '['");
                assertTrue(embeddingVectorString.endsWith("]"), "Vector phải kết thúc bằng ']'");
                String[] dimensions = embeddingVectorString.substring(1, embeddingVectorString.length() - 1).split(",");
                assertEquals(768, dimensions.length, "Số chiều vector sinh ra phải đúng 768 chiều");
            }
        } else {
            // Khi không cấu hình key -> fallback an toàn trả về null (không crash)
            assertNull(embeddingVectorString, "Khi chưa có API key, hàm phải trả về null để fallback sang BM25");
        }
    }

    @Test
    @DisplayName("TDD: Khi input rỗng hoặc null, generateEmbedding phải trả về null an toàn")
    void testGenerateEmbeddingWithEmptyInput() {
        assertNull(geminiClientService.generateEmbedding(null));
        assertNull(geminiClientService.generateEmbedding("   "));
    }
}
