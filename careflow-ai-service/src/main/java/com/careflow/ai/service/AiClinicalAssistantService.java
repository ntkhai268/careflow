package com.careflow.ai.service;

import com.careflow.ai.dto.request.ClinicalChatRequest;
import com.careflow.ai.dto.request.ClinicalSuggestionRequest;
import com.careflow.ai.dto.response.AiModelInfo;
import com.careflow.ai.dto.response.ClinicalSuggestionItem;
import com.careflow.ai.dto.response.ClinicalSuggestionResponse;
import com.careflow.ai.dto.response.EvidenceRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiClinicalAssistantService {

    private static final String MEDICAL_DISCLAIMER =
            "Gợi ý hỗ trợ lâm sàng từ CareFlow AI. Bác sĩ chịu trách nhiệm hoàn toàn đối với quyết định chẩn đoán và chỉ định điều trị.";

    private static final String SYSTEM_INSTRUCTION =
            "Bạn là Bác sĩ Chồn AI 🐾 (CareFlow Clinical Assistant) - một chú Chồn bác sĩ vừa thông minh vừa HÀI HƯỚC, DUYÊN DÁNG!\n\n" +
            "QUY TẮC PHẢN HỒI (WITTY & CONCISE RULES):\n" +
            "1. TÍNH CÁCH HÀI HƯỚC DUYÊN DÁNG: Trả lời với giọng điệu vui tươi, hóm hỉnh, thả 1-2 câu đùa duyên dáng (ví von hài hước về đời sống/y tế), giúp Bác sĩ bớt căng thẳng khi trực.\n" +
            "2. SÚC TÍCH & TIẾT KIỆM TOKEN: Đùa ngắn gọn, súc tích, không viết văn xuôi dông dài. Sau câu đùa duyên dáng thì tập trung hỗ trợ chuyên môn ngay.\n" +
            "3. PHÂN TÍCH CHUYÊN MÔN: Khi phân tích ca khám, triệu chứng hay dược lý, trình bày chuẩn xác y khoa bằng các gạch đầu dòng rõ ràng và in đậm từ khóa (**in đậm**).\n" +
            "4. CHỈ HỖ TRỢ Y TẾ: Nếu câu hỏi ngoài y tế/độc hại, từ chối hài hước: \"Em là Bác sĩ Chồn AI 🐾, chỉ hỗ trợ y tế & lâm sàng CareFlow thôi ạ.\"";

    private final GeminiClientService geminiClientService;
    private final DeepSeekClientService deepSeekClientService;
    private final Map<String, ClinicalSuggestionResponse> interactionStore = new ConcurrentHashMap<>();

    public ClinicalSuggestionResponse getClinicalSuggestions(ClinicalSuggestionRequest request) {
        log.info("Processing AI clinical suggestion for consultationId: {}", request.getConsultationId());

        String interactionId = UUID.randomUUID().toString();
        String question = request.getQuestion() != null ? request.getQuestion() : "Gợi ý chẩn đoán phân biệt và dữ liệu còn thiếu";

        String prompt = String.format("Mã ca khám (ConsultationId): %s. Yêu cầu của Bác sĩ: %s", request.getConsultationId(), question);

        // 1. Try Gemini AI Provider Chain (gemini-2.5-flash, gemini-2.5-flash-lite, gemini-2.0-flash)
        if (geminiClientService.isGeminiConfigured()) {
            String aiText = geminiClientService.generateClinicalContent(SYSTEM_INSTRUCTION, prompt);
            if (aiText != null) {
                ClinicalSuggestionResponse response = ClinicalSuggestionResponse.builder()
                        .interactionId(interactionId)
                        .mode("LLM_PROVIDER")
                        .summary(aiText)
                        .suggestions(List.of(
                                ClinicalSuggestionItem.builder()
                                        .type("DIFFERENTIAL_DIAGNOSIS")
                                        .text("Gợi ý phân tích từ Gemini AI")
                                        .evidenceRefs(List.of(EvidenceRef.builder().sourceType("CONSULTATION").sourceId(request.getConsultationId()).field("symptoms").build()))
                                        .build()
                        ))
                        .missingInformation(List.of("Tiền sử gia đình", "Thời gian xuất hiện triệu chứng cụ thể"))
                        .warnings(List.of("Kiểm tra dị ứng thuốc nhóm Beta-lactam & NSAIDs"))
                        .disclaimer(MEDICAL_DISCLAIMER)
                        .model(AiModelInfo.builder()
                                .provider("Google AI Studio (Gemini)")
                                .name("gemini-2.5-flash")
                                .version("2.5")
                                .build())
                        .generatedAt(Instant.now().toString())
                        .build();

                interactionStore.put(interactionId, response);
                return response;
            }
        }

        // 2. Fallback to DeepSeek AI Provider if Gemini is rate limited or unavailable
        if (deepSeekClientService.isDeepSeekConfigured()) {
            log.info("Gemini unavailable. Falling back to DeepSeek AI for clinical suggestions...");
            String dsText = deepSeekClientService.generateClinicalContent(SYSTEM_INSTRUCTION, prompt);
            if (dsText != null) {
                ClinicalSuggestionResponse response = ClinicalSuggestionResponse.builder()
                        .interactionId(interactionId)
                        .mode("LLM_PROVIDER")
                        .summary(dsText)
                        .suggestions(List.of())
                        .missingInformation(List.of())
                        .warnings(List.of())
                        .disclaimer(MEDICAL_DISCLAIMER)
                        .model(AiModelInfo.builder()
                                .provider("DeepSeek AI")
                                .name("deepseek-chat")
                                .version("V3/R1")
                                .build())
                        .generatedAt(Instant.now().toString())
                        .build();

                interactionStore.put(interactionId, response);
                return response;
            }
        }

        // 3. Fallback to MOCK_RULE_BASED mode if all AI providers are offline/limited
        return buildRuleBasedSuggestions(interactionId, request.getConsultationId(), question);
    }

    public ClinicalSuggestionResponse processClinicalChat(ClinicalChatRequest request) {
        log.info("Processing AI clinical chat message for consultationId: {}", request.getConsultationId());

        String interactionId = UUID.randomUUID().toString();
        String message = request.getMessage();

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(String.format("Mã ca khám (ConsultationId): %s.\n", request.getConsultationId()));

        if (request.getPageTitle() != null || request.getPageRoute() != null) {
            promptBuilder.append(String.format("NGỮ CẢNH MÀN HÌNH BÁC SĨ ĐANG XEM:\n- Trang: %s (%s)\n",
                    request.getPageTitle() != null ? request.getPageTitle() : "Trang hiện tại",
                    request.getPageRoute() != null ? request.getPageRoute() : ""));
        }
        if (request.getPageData() != null && !request.getPageData().isBlank()) {
            promptBuilder.append(String.format("- DỮ LIỆU TRỰC TIẾP TRÊN MÀN HÌNH: %s\n", request.getPageData()));
        }

        promptBuilder.append(String.format("\nCÂU HỎI TƯƠNG TÁC CỦA BÁC SĨ: %s", message));

        String prompt = promptBuilder.toString();

        // 1. Try Gemini AI Provider Chain
        if (geminiClientService.isGeminiConfigured()) {
            String aiText = geminiClientService.generateClinicalContent(SYSTEM_INSTRUCTION, prompt);
            if (aiText != null) {
                ClinicalSuggestionResponse response = ClinicalSuggestionResponse.builder()
                        .interactionId(interactionId)
                        .mode("LLM_PROVIDER")
                        .summary(aiText)
                        .suggestions(List.of())
                        .missingInformation(List.of())
                        .warnings(List.of())
                        .disclaimer(MEDICAL_DISCLAIMER)
                        .model(AiModelInfo.builder()
                                .provider("Google AI Studio (Gemini)")
                                .name("gemini-2.5-flash")
                                .version("2.5")
                                .build())
                        .generatedAt(Instant.now().toString())
                        .build();

                interactionStore.put(interactionId, response);
                return response;
            }
        }

        // 2. Fallback to DeepSeek AI Provider
        if (deepSeekClientService.isDeepSeekConfigured()) {
            log.info("Gemini unavailable. Falling back to DeepSeek AI for clinical chat...");
            String dsText = deepSeekClientService.generateClinicalContent(SYSTEM_INSTRUCTION, prompt);
            if (dsText != null) {
                ClinicalSuggestionResponse response = ClinicalSuggestionResponse.builder()
                        .interactionId(interactionId)
                        .mode("LLM_PROVIDER")
                        .summary(dsText)
                        .suggestions(List.of())
                        .missingInformation(List.of())
                        .warnings(List.of())
                        .disclaimer(MEDICAL_DISCLAIMER)
                        .model(AiModelInfo.builder()
                                .provider("DeepSeek AI")
                                .name("deepseek-chat")
                                .version("V3/R1")
                                .build())
                        .generatedAt(Instant.now().toString())
                        .build();

                interactionStore.put(interactionId, response);
                return response;
            }
        }

        return buildRuleBasedChat(interactionId, request.getConsultationId(), message);
    }

    public ClinicalSuggestionResponse getInteractionById(String interactionId) {
        return interactionStore.get(interactionId);
    }

    private ClinicalSuggestionResponse buildRuleBasedSuggestions(String interactionId, String consultationId, String question) {
        List<ClinicalSuggestionItem> suggestions = List.of(
                ClinicalSuggestionItem.builder()
                        .type("DIFFERENTIAL_DIAGNOSIS")
                        .text("Cân nhắc Cơn đau thắt ngực không ổn định hoặc Viêm màng ngoài tim cấp")
                        .evidenceRefs(List.of(EvidenceRef.builder().sourceType("CONSULTATION").sourceId(consultationId).field("symptoms").build()))
                        .build()
        );

        ClinicalSuggestionResponse response = ClinicalSuggestionResponse.builder()
                .interactionId(interactionId)
                .mode("MOCK_RULE_BASED")
                .summary("Tóm tắt ca khám: Đã ghi nhận các triệu chứng lâm sàng và chỉ số sinh tồn của ca khám " + consultationId)
                .suggestions(suggestions)
                .missingInformation(List.of("Kết quả Điện tâm đồ (ECG)", "Định lượng Enzyme tim Troponin"))
                .warnings(List.of("Lưu ý tiền sử dị ứng thuốc nhóm Beta-lactam"))
                .disclaimer(MEDICAL_DISCLAIMER)
                .model(AiModelInfo.builder().provider("CareFlow Rule Engine").name("careflow-clinical-v1").version("1.0.0").build())
                .generatedAt(Instant.now().toString())
                .build();

        interactionStore.put(interactionId, response);
        return response;
    }

    private ClinicalSuggestionResponse buildRuleBasedChat(String interactionId, String consultationId, String message) {
        String replySummary = String.format("Bác sĩ Chồn AI 🐾: Đã ghi nhận câu hỏi '%s'. Em đang xử lý dữ liệu lâm sàng cho ca khám %s.",
                message != null ? message : "", consultationId);

        ClinicalSuggestionResponse response = ClinicalSuggestionResponse.builder()
                .interactionId(interactionId)
                .mode("MOCK_RULE_BASED")
                .summary(replySummary)
                .suggestions(List.of())
                .missingInformation(List.of())
                .warnings(List.of())
                .disclaimer(MEDICAL_DISCLAIMER)
                .model(AiModelInfo.builder().provider("CareFlow Rule Engine").name("careflow-clinical-v1").version("1.0.0").build())
                .generatedAt(Instant.now().toString())
                .build();

        interactionStore.put(interactionId, response);
        return response;
    }
}
