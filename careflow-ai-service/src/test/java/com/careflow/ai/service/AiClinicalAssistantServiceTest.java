package com.careflow.ai.service;

import com.careflow.ai.dto.request.ClinicalChatRequest;
import com.careflow.ai.dto.request.ClinicalSuggestionRequest;
import com.careflow.ai.dto.response.ClinicalSuggestionResponse;
import com.careflow.ai.service.ClinicalHybridSearchService.RankedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiClinicalAssistantServiceTest {

    @Mock
    private GeminiClientService geminiClientService;

    @Mock
    private DeepSeekClientService deepSeekClientService;

    @Mock
    private ClinicalHybridSearchService clinicalHybridSearchService;

    private AiClinicalAssistantService assistantService;

    @BeforeEach
    void setUp() {
        assistantService = new AiClinicalAssistantService(
                geminiClientService,
                deepSeekClientService,
                clinicalHybridSearchService
        );
    }

    @Test
    @DisplayName("Gợi ý lâm sàng: Bổ sung ngữ cảnh phác đồ Bộ Y Tế vào Prompt và trích xuất EvidenceRefs")
    void testGetClinicalSuggestions_WithRagContext_InjectsGuidelinesAndEvidence() {
        // Given
        UUID chunkId = UUID.randomUUID();
        RankedChunk mockChunk = new RankedChunk(
                chunkId,
                "BYT-QD-4800-HOHAP",
                "Hướng dẫn chẩn đoán và điều trị viêm phổi mắc phải tại cộng đồng",
                "HÔ HẤP",
                "4.1. Điều trị kháng sinh ban đầu",
                "Lựa chọn Amoxicillin/Clavulanic hoặc Cephalosporin thế hệ 3 kết hợp Macrolide.",
                0.032
        );

        when(clinicalHybridSearchService.search(anyString(), any()))
                .thenReturn(List.of(mockChunk));
        when(geminiClientService.isGeminiConfigured()).thenReturn(true);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(geminiClientService.generateClinicalContent(anyString(), promptCaptor.capture()))
                .thenReturn("Phân tích ca bệnh: Cần ưu tiên dùng Amoxicillin-Clavulanic theo phác đồ BYT.");

        ClinicalSuggestionRequest request = ClinicalSuggestionRequest.builder()
                .consultationId(UUID.randomUUID().toString())
                .question("Bệnh nhân ho sốt, nghi viêm phổi, xin gợi ý phác đồ kháng sinh")
                .pageData("Triệu chứng: Ho đờm xanh, sốt 38.5 độ")
                .build();

        // When
        ClinicalSuggestionResponse response = assistantService.getClinicalSuggestions(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMode()).isEqualTo("LLM_PROVIDER");
        assertThat(response.getSummary()).contains("Amoxicillin-Clavulanic");

        // Verify Prompt chứa RAG context
        String capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt).contains("BYT-QD-4800-HOHAP");
        assertThat(capturedPrompt).contains("4.1. Điều trị kháng sinh ban đầu");
        assertThat(capturedPrompt).contains("Lựa chọn Amoxicillin/Clavulanic");

        // Verify Suggestions & EvidenceRefs
        assertThat(response.getSuggestions()).isNotEmpty();
        assertThat(response.getSuggestions().stream().anyMatch(s ->
                s.getEvidenceRefs() != null && s.getEvidenceRefs().stream().anyMatch(e ->
                        "CLINICAL_GUIDELINE".equals(e.getSourceType()) &&
                        "BYT-QD-4800-HOHAP".equals(e.getSourceId())
                )
        )).isTrue();
    }

    @Test
    @DisplayName("Chat lâm sàng: Nhúng ngữ cảnh tra cứu DDI tương tác thuốc vào LLM Prompt")
    void testProcessClinicalChat_WithDdiRagContext_AugmentsPrompt() {
        // Given
        UUID chunkId = UUID.randomUUID();
        RankedChunk ddiChunk = new RankedChunk(
                chunkId,
                "BYT-QD-5948-DDI",
                "Dược thư Quốc gia & Hướng dẫn Tương tác thuốc",
                "DƯỢC LÂM SÀNG",
                "Tương tác Clopidogrel - Omeprazole",
                "Omeprazole ức chế CYP2C19 làm giảm hoạt hóa Clopidogrel, tăng nguy cơ biến cố tim mạch. Khuyến cáo thay bằng Pantoprazole.",
                0.031
        );

        when(clinicalHybridSearchService.search(anyString(), any()))
                .thenReturn(List.of(ddiChunk));
        when(geminiClientService.isGeminiConfigured()).thenReturn(true);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(geminiClientService.generateClinicalContent(anyString(), promptCaptor.capture()))
                .thenReturn("Cảnh báo DDI: Tránh kê chung Clopidogrel với Omeprazole.");

        ClinicalChatRequest chatRequest = ClinicalChatRequest.builder()
                .consultationId(UUID.randomUUID().toString())
                .message("Có thể kê Clopidogrel kèm Omeprazole cho bệnh nhân không?")
                .build();

        // When
        ClinicalSuggestionResponse response = assistantService.processClinicalChat(chatRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMode()).isEqualTo("LLM_PROVIDER");

        String capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt).contains("BYT-QD-5948-DDI");
        assertThat(capturedPrompt).contains("Tương tác Clopidogrel - Omeprazole");
        assertThat(capturedPrompt).contains("CYP2C19");
    }

    @Test
    @DisplayName("Fallback: Trả về trạng thái an toàn khi tất cả AI Providers không phản hồi")
    void testGetClinicalSuggestions_WhenAiFails_GracefulFallback() {
        // Given
        when(clinicalHybridSearchService.search(anyString(), any())).thenReturn(List.of());
        when(geminiClientService.isGeminiConfigured()).thenReturn(false);
        when(deepSeekClientService.isDeepSeekConfigured()).thenReturn(false);

        ClinicalSuggestionRequest request = ClinicalSuggestionRequest.builder()
                .consultationId(UUID.randomUUID().toString())
                .question("Tư vấn liều thuốc")
                .build();

        // When
        ClinicalSuggestionResponse response = assistantService.getClinicalSuggestions(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMode()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getSummary()).contains("Bác sĩ Chồn AI");
    }
}
