package com.careflow.ai.service;

import com.careflow.ai.dto.request.ClinicalChatRequest;
import com.careflow.ai.dto.request.ClinicalSuggestionRequest;
import com.careflow.ai.dto.response.AiModelInfo;
import com.careflow.ai.dto.response.ClinicalSuggestionItem;
import com.careflow.ai.dto.response.ClinicalSuggestionResponse;
import com.careflow.ai.dto.response.EvidenceRef;
import com.careflow.ai.service.ClinicalHybridSearchService.RankedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiClinicalAssistantService {

    private static final String MEDICAL_DISCLAIMER =
            "Gợi ý hỗ trợ lâm sàng từ CareFlow AI dựa trên Phác đồ điều trị Bộ Y Tế Việt Nam. Bác sĩ chịu trách nhiệm hoàn toàn đối với quyết định chẩn đoán và chỉ định điều trị.";

    private static final String SYSTEM_INSTRUCTION =
            "Bạn là Trợ lý AI Lâm Sàng CareFlow (CareFlow Clinical Assistant) - chuyên gia hỗ trợ tra cứu phác đồ điều trị và an toàn dùng thuốc tại bệnh viện theo chuẩn Bộ Y Tế Việt Nam.\n\n" +
            "QUY TẮC PHẢN HỒI LÂM SÀNG:\n" +
            "1. TÁC PHONG CHUYÊN MÔN NGHIÊM TÚC: Trả lời ngắn gọn, chuẩn xác y khoa, khách quan, trung thực. TUYỆT ĐỐI KHÔNG kể chuyện đùa, không dùng emoji cợt nhả hay nhân cách hóa con vật.\n" +
            "2. TUÂN THỦ PHÁC ĐỒ BỘ Y TẾ (CLINICAL RAG): Khi có trích dẫn Phác đồ Bộ Y Tế trong ngữ cảnh, đối chiếu trực tiếp triệu chứng, chẩn đoán phân biệt và khuyến nghị điều trị bám sát hướng dẫn Bộ Y Tế.\n" +
            "3. KIỂM TRA TƯƠNG TÁC THUỐC (DDI) & DỊ ỨNG:\n" +
            "   - NẾU CA KHÁM CHƯA CÓ ĐƠN THUỐC HOẶC THUỐC ĐANG DÙNG: Báo rõ ràng: \"Hiện tại ca khám chưa có thuốc trong đơn. Bác sĩ vui lòng thêm thuốc để hệ thống đối chiếu tương tác DDI từ Dược thư Quốc gia.\" TUYỆT ĐỐI KHÔNG tự động liệt kê các cặp tương tác thuốc không liên quan.\n" +
            "   - NẾU ĐÃ CÓ THUỐC ĐƯỢC CHỈ ĐỊNH: Chỉ phân tích và cảnh báo những tương tác thực sự có liên quan giữa các thuốc đang kê hoặc với tiền sử dị ứng của bệnh nhân.\n" +
            "4. ĐỊNH DẠNG RÕ RÀNG: Dùng các gạch đầu dòng súc tích, **in đậm** các thuật ngữ y khoa, tên thuốc, liều dùng, chỉ số sinh hiệu.\n" +
            "5. PHẠM VI HỖ TRỢ: Chỉ tập trung vào chuyên môn y tế lâm sàng và an toàn bệnh nhân.";

    private final GeminiClientService geminiClientService;
    private final DeepSeekClientService deepSeekClientService;
    private final ClinicalHybridSearchService clinicalHybridSearchService;

    private final Map<String, ClinicalSuggestionResponse> interactionStore = new ConcurrentHashMap<>();

    public ClinicalSuggestionResponse getClinicalSuggestions(ClinicalSuggestionRequest request) {
        log.info("Processing AI clinical suggestion for consultationId: {}", request.getConsultationId());

        String interactionId = UUID.randomUUID().toString();
        String question = request.getQuestion() != null ? request.getQuestion() : "Gợi ý chẩn đoán phân biệt, cận lâm sàng và phác đồ điều trị";
        String pageData = request.getPageData();

        // 1. TRUY HỒI TRI THỨC LÂM SÀNG (RAG RETRIEVAL)
        String searchQuery = buildSearchQuery(question, pageData);
        List<RankedChunk> relevantChunks = clinicalHybridSearchService.search(searchQuery, null);
        log.info("RAG Retrieval found {} relevant guideline chunks for consultation {}", relevantChunks.size(), request.getConsultationId());

        // 2. XÂY DỰNG PROMPT CÓ RAG CONTEXT
        String prompt = buildPromptWithRag(
                request.getConsultationId(),
                question,
                request.getPageTitle(),
                request.getPageRoute(),
                pageData,
                relevantChunks
        );

        // 3. XÂY DỰNG SUGGESTIONS VÀ EVIDENCE REFS TỪ CHUNKS PHÁC ĐỒ THỰC TẾ
        List<ClinicalSuggestionItem> suggestions = buildSuggestionsFromChunks(relevantChunks, request.getConsultationId());

        // 4. GỌI AI MODEL PROVIDER (Gemini -> DeepSeek -> Fallback)
        if (geminiClientService.isGeminiConfigured()) {
            String aiText = geminiClientService.generateClinicalContent(SYSTEM_INSTRUCTION, prompt);
            if (aiText != null) {
                ClinicalSuggestionResponse response = ClinicalSuggestionResponse.builder()
                        .interactionId(interactionId)
                        .mode("LLM_PROVIDER")
                        .summary(aiText)
                        .suggestions(suggestions)
                        .missingInformation(List.of("Tiền sử dị ứng chi tiết", "Thời gian khởi phát triệu chứng chính xác", "Kết quả cận lâm sàng gần nhất"))
                        .warnings(extractWarningsFromChunks(relevantChunks, pageData))
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

        // Fallback sang DeepSeek AI
        if (deepSeekClientService.isDeepSeekConfigured()) {
            log.info("Gemini unavailable. Falling back to DeepSeek AI for clinical suggestions...");
            String dsText = deepSeekClientService.generateClinicalContent(SYSTEM_INSTRUCTION, prompt);
            if (dsText != null) {
                ClinicalSuggestionResponse response = ClinicalSuggestionResponse.builder()
                        .interactionId(interactionId)
                        .mode("LLM_PROVIDER")
                        .summary(dsText)
                        .suggestions(suggestions)
                        .missingInformation(List.of("Tiền sử dị ứng chi tiết", "Thời gian khởi phát triệu chứng chính xác"))
                        .warnings(extractWarningsFromChunks(relevantChunks, pageData))
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

        // Fallback sang RULE_BASED
        return buildRuleBasedSuggestions(interactionId, request.getConsultationId(), relevantChunks);
    }

    public ClinicalSuggestionResponse processClinicalChat(ClinicalChatRequest request) {
        log.info("Processing AI clinical chat message for consultationId: {}", request.getConsultationId());

        String interactionId = UUID.randomUUID().toString();
        String message = request.getMessage();
        String pageData = request.getPageData();

        // 1. TRUY HỒI TRI THỨC LÂM SÀNG CHO CÂU HỎI CHAT
        String searchQuery = buildSearchQuery(message, pageData);
        List<RankedChunk> relevantChunks = clinicalHybridSearchService.search(searchQuery, null);
        log.info("RAG Retrieval found {} guideline chunks for chat message", relevantChunks.size());

        // 2. XÂY DỰNG PROMPT
        String prompt = buildPromptWithRag(
                request.getConsultationId(),
                message,
                request.getPageTitle(),
                request.getPageRoute(),
                pageData,
                relevantChunks
        );

        List<ClinicalSuggestionItem> suggestions = buildSuggestionsFromChunks(relevantChunks, request.getConsultationId());

        // 3. GỌI AI MODEL PROVIDER
        if (geminiClientService.isGeminiConfigured()) {
            String aiText = geminiClientService.generateClinicalContent(SYSTEM_INSTRUCTION, prompt);
            if (aiText != null) {
                ClinicalSuggestionResponse response = ClinicalSuggestionResponse.builder()
                        .interactionId(interactionId)
                        .mode("LLM_PROVIDER")
                        .summary(aiText)
                        .suggestions(suggestions)
                        .missingInformation(List.of())
                        .warnings(extractWarningsFromChunks(relevantChunks, pageData))
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

        if (deepSeekClientService.isDeepSeekConfigured()) {
            log.info("Gemini unavailable. Falling back to DeepSeek AI for clinical chat...");
            String dsText = deepSeekClientService.generateClinicalContent(SYSTEM_INSTRUCTION, prompt);
            if (dsText != null) {
                ClinicalSuggestionResponse response = ClinicalSuggestionResponse.builder()
                        .interactionId(interactionId)
                        .mode("LLM_PROVIDER")
                        .summary(dsText)
                        .suggestions(suggestions)
                        .missingInformation(List.of())
                        .warnings(extractWarningsFromChunks(relevantChunks, pageData))
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

    // --- HELPER METHODS ---

    private String buildSearchQuery(String primaryText, String pageData) {
        StringBuilder queryBuilder = new StringBuilder();
        if (primaryText != null && !primaryText.isBlank()) {
            queryBuilder.append(primaryText.trim());
        }
        if (pageData != null && !pageData.isBlank()) {
            if (queryBuilder.length() > 0) {
                queryBuilder.append(" ");
            }
            queryBuilder.append(pageData.trim());
        }
        return queryBuilder.toString();
    }

    private String buildPromptWithRag(
            String consultationId,
            String question,
            String pageTitle,
            String pageRoute,
            String pageData,
            List<RankedChunk> chunks
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Mã ca khám (ConsultationId): %s\n", consultationId));

        if (pageTitle != null || pageRoute != null) {
            sb.append(String.format("NGỮ CẢNH MÀN HÌNH BÁC SĨ:\n- Màn hình: %s (%s)\n",
                    pageTitle != null ? pageTitle : "Khám bệnh",
                    pageRoute != null ? pageRoute : ""));
        }
        if (pageData != null && !pageData.isBlank()) {
            sb.append(String.format("- DỮ LIỆU CA KHÁM HIỆN TẠI: %s\n", pageData));
        }

        if (chunks != null && !chunks.isEmpty()) {
            sb.append("\n=========================================\n");
            sb.append("TRI THỨC PHÁC ĐỒ ĐIỀU TRỊ BỘ Y TẾ (CLINICAL RAG CONTEXT):\n");
            for (int i = 0; i < chunks.size(); i++) {
                RankedChunk chunk = chunks.get(i);
                sb.append(String.format("[%d] Phác đồ: %s (%s) - Chuyên khoa: %s\n",
                        i + 1, chunk.documentTitle(), chunk.documentCode(), chunk.specialty()));
                sb.append(String.format("Mục: %s\n", chunk.sectionTitle()));
                sb.append(String.format("Nội dung: %s\n\n", chunk.content()));
            }
            sb.append("=========================================\n");
            sb.append("LƯU Ý QUAN TRỌNG: Hãy đối chiếu trực tiếp dữ liệu ca khám với các trích dẫn Phác đồ Bộ Y Tế ở trên để tư vấn phác đồ chuẩn y khoa.\n\n");
        }

        sb.append(String.format("YÊU CẦU / CÂU HỎI CỦA BÁC SĨ: %s", question));
        return sb.toString();
    }

    private List<ClinicalSuggestionItem> buildSuggestionsFromChunks(List<RankedChunk> chunks, String consultationId) {
        List<ClinicalSuggestionItem> items = new ArrayList<>();
        if (chunks == null || chunks.isEmpty()) {
            items.add(ClinicalSuggestionItem.builder()
                    .type("DIFFERENTIAL_DIAGNOSIS")
                    .text("Gợi ý phân tích lâm sàng")
                    .evidenceRefs(List.of(EvidenceRef.builder().sourceType("CONSULTATION").sourceId(consultationId).field("symptoms").build()))
                    .build());
            return items;
        }

        for (RankedChunk chunk : chunks) {
            String snippet = chunk.content();
            if (snippet.length() > 200) {
                snippet = snippet.substring(0, 200) + "...";
            }

            items.add(ClinicalSuggestionItem.builder()
                    .type("CLINICAL_GUIDELINE_RECOMMENDATION")
                    .text(String.format("[%s] %s: %s", chunk.documentCode(), chunk.sectionTitle(), snippet))
                    .evidenceRefs(List.of(EvidenceRef.builder()
                            .sourceType("CLINICAL_GUIDELINE")
                            .sourceId(chunk.documentCode())
                            .field(chunk.sectionTitle())
                            .build()))
                    .build());
        }
        return items;
    }

    private List<String> extractWarningsFromChunks(List<RankedChunk> chunks, String pageData) {
        List<String> warnings = new ArrayList<>();
        if (pageData == null || !pageData.contains("Thuốc đang chỉ định:")) {
            // Chưa có thuốc trong đơn ca khám -> Không sinh cảnh báo tương tác thuốc DDI
            return warnings;
        }

        String medsPart = "";
        int medIdx = pageData.indexOf("Thuốc đang chỉ định:");
        if (medIdx != -1) {
            medsPart = pageData.substring(medIdx).toLowerCase();
        }

        if (chunks != null) {
            for (RankedChunk chunk : chunks) {
                if ("BYT-QD-5948-DDI".equals(chunk.documentCode()) || chunk.sectionTitle().toLowerCase().contains("tương tác")) {
                    String titleLower = chunk.sectionTitle().toLowerCase();
                    String contentLower = chunk.content().toLowerCase();
                    boolean isRelevant = false;
                    for (String med : medsPart.split("[;,\\(\\)]")) {
                        String cleanMed = med.trim().replaceAll("(?i)(thuốc đang chỉ định:|\\d+mg|\\d+ml|viên|lần|ngày|sau|khi|ăn)", "").trim();
                        if (cleanMed.length() >= 3 && (titleLower.contains(cleanMed) || contentLower.contains(cleanMed))) {
                            isRelevant = true;
                            break;
                        }
                    }
                    if (isRelevant) {
                        warnings.add("Cảnh báo tương tác thuốc DDI từ Dược thư Quốc gia: " + chunk.sectionTitle());
                    }
                }
            }
        }
        return warnings.stream().distinct().collect(Collectors.toList());
    }

    private ClinicalSuggestionResponse buildRuleBasedSuggestions(String interactionId, String consultationId, List<RankedChunk> chunks) {
        List<ClinicalSuggestionItem> suggestions = buildSuggestionsFromChunks(chunks, consultationId);

        String summary = "Trợ lý AI Lâm Sàng: Hiện tại cổng LLM tạm thời chưa phản hồi, nhưng hệ thống đã tìm kiếm được các mục phác đồ Bộ Y Tế liên quan trong kho tri thức của bệnh viện để Bác sĩ tham khảo nhanh bên dưới.";

        ClinicalSuggestionResponse response = ClinicalSuggestionResponse.builder()
                .interactionId(interactionId)
                .mode("SERVICE_UNAVAILABLE")
                .summary(summary)
                .suggestions(suggestions)
                .missingInformation(List.of())
                .warnings(extractWarningsFromChunks(chunks, ""))
                .disclaimer(MEDICAL_DISCLAIMER)
                .model(AiModelInfo.builder().provider("CareFlow AI").name("careflow-clinical-assistant").version("1.0.0").build())
                .generatedAt(Instant.now().toString())
                .build();

        interactionStore.put(interactionId, response);
        return response;
    }

    private ClinicalSuggestionResponse buildRuleBasedChat(String interactionId, String consultationId, String message) {
        String replySummary = "Trợ lý AI Lâm Sàng: Hiện tại dịch vụ AI đang tạm thời chưa khả dụng. Rất mong Bác sĩ thông cảm và vui lòng sử dụng lại sau.";

        ClinicalSuggestionResponse response = ClinicalSuggestionResponse.builder()
                .interactionId(interactionId)
                .mode("SERVICE_UNAVAILABLE")
                .summary(replySummary)
                .suggestions(List.of())
                .missingInformation(List.of())
                .warnings(List.of())
                .disclaimer(MEDICAL_DISCLAIMER)
                .model(AiModelInfo.builder().provider("CareFlow AI").name("careflow-clinical-assistant").version("1.0.0").build())
                .generatedAt(Instant.now().toString())
                .build();

        interactionStore.put(interactionId, response);
        return response;
    }
}
