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

    // In-memory store for audit trail of interactions
    private final Map<String, ClinicalSuggestionResponse> interactionStore = new ConcurrentHashMap<>();

    public ClinicalSuggestionResponse getClinicalSuggestions(ClinicalSuggestionRequest request) {
        log.info("Processing AI clinical suggestion for consultationId: {}", request.getConsultationId());

        String interactionId = UUID.randomUUID().toString();
        String question = request.getQuestion() != null ? request.getQuestion().toLowerCase() : "";

        List<ClinicalSuggestionItem> suggestions = new ArrayList<>();
        List<String> missingInfo = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (question.contains("tim") || question.contains("ngực") || question.contains("huyết áp")) {
            suggestions.add(ClinicalSuggestionItem.builder()
                    .type("DIFFERENTIAL_DIAGNOSIS")
                    .text("Cân nhắc Cơn đau thắt ngực không ổn định (Unstable Angina) hoặc Tăng huyết áp độ II")
                    .evidenceRefs(List.of(
                            EvidenceRef.builder().sourceType("CONSULTATION").sourceId(request.getConsultationId()).field("symptoms").build()
                    ))
                    .build());
            suggestions.add(ClinicalSuggestionItem.builder()
                    .type("DIFFERENTIAL_DIAGNOSIS")
                    .text("Theo dõi Bệnh cơ tim thiếu máu cục bộ mãn tính")
                    .evidenceRefs(List.of(
                            EvidenceRef.builder().sourceType("EMR").sourceId("EMR-HISTORY-01").field("medicalHistory").build()
                    ))
                    .build());
            missingInfo.add("Kết quả Điện tâm đồ (ECG) 12 chuyển đạo lúc nghỉ");
            missingInfo.add("Định lượng Enzyem tim (Troponin I / T)");
            warnings.add("Cảnh báo: Kiểm tra tiền sử dị ứng thuốc nhóm Beta-blocker và Thuốc giãn mạch Nitrat");
        } else if (question.contains("nhi") || question.contains("sốt") || question.contains("ho")) {
            suggestions.add(ClinicalSuggestionItem.builder()
                    .type("DIFFERENTIAL_DIAGNOSIS")
                    .text("Cân nhắc Viêm phế quản cấp tính hoặc Nhiễm trùng đường hô hấp trên do virus")
                    .evidenceRefs(List.of(
                            EvidenceRef.builder().sourceType("CONSULTATION").sourceId(request.getConsultationId()).field("symptoms").build()
                    ))
                    .build());
            missingInfo.add("Tần số thở và chỉ số SpO2 phòng học");
            missingInfo.add("Tiền sử tiêm chủng vắc-xin DPT và Phế cầu");
            warnings.add("Theo dõi dấu hiệu co kéo lồng ngực và tím quầng môi ở trẻ em");
        } else {
            suggestions.add(ClinicalSuggestionItem.builder()
                    .type("DIFFERENTIAL_DIAGNOSIS")
                    .text("Cân nhắc Hội chứng Viêm dạ dày cấp / Trào ngược dạ dày thực quản (GERD)")
                    .evidenceRefs(List.of(
                            EvidenceRef.builder().sourceType("CONSULTATION").sourceId(request.getConsultationId()).field("reasonForVisit").build()
                    ))
                    .build());
            suggestions.add(ClinicalSuggestionItem.builder()
                    .type("DIFFERENTIAL_DIAGNOSIS")
                    .text("Theo dõi Rối loạn thần kinh thực vật / Căng thẳng thể chất")
                    .evidenceRefs(List.of(
                            EvidenceRef.builder().sourceType("CONSULTATION").sourceId(request.getConsultationId()).field("vitalSigns").build()
                    ))
                    .build());
            missingInfo.add("Thời gian diễn tiến triệu chứng trong ngày (trước/sau ăn)");
            missingInfo.add("Tiền sử dùng thuốc giảm đau hạ sốt nhóm NSAIDs");
            warnings.add("Lưu ý kiểm tra dị ứng Penicillin & Aspirin được ghi nhận trên Thẻ BHYT");
        }

        ClinicalSuggestionResponse response = ClinicalSuggestionResponse.builder()
                .interactionId(interactionId)
                .mode("MOCK_RULE_BASED")
                .summary("Tóm tắt ca khám: Bệnh nhân ghi nhận triệu chứng mệt mỏi, khó chịu. Đã phân tích dấu hiệu sinh tồn và tiền sử y tế.")
                .suggestions(suggestions)
                .missingInformation(missingInfo)
                .warnings(warnings)
                .disclaimer(MEDICAL_DISCLAIMER)
                .model(AiModelInfo.builder()
                        .provider("CareFlow Clinical Rule Engine")
                        .name("careflow-clinical-v1")
                        .version("1.0.0")
                        .build())
                .generatedAt(Instant.now().toString())
                .build();

        interactionStore.put(interactionId, response);
        return response;
    }

    public ClinicalSuggestionResponse processClinicalChat(ClinicalChatRequest request) {
        log.info("Processing AI clinical chat message for consultationId: {}", request.getConsultationId());

        String interactionId = UUID.randomUUID().toString();
        String userMsg = request.getMessage().toLowerCase();

        List<ClinicalSuggestionItem> suggestions = new ArrayList<>();
        List<String> missingInfo = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (userMsg.contains("xét nghiệm") || userMsg.contains("cls") || userMsg.contains("chỉ định")) {
            suggestions.add(ClinicalSuggestionItem.builder()
                    .type("TREATMENT_REFERRAL")
                    .text("Khuyến nghị chỉ định Cận lâm sàng: Công thức máu (CBC) & Định lượng Glucose máu khẩn")
                    .evidenceRefs(List.of(
                            EvidenceRef.builder().sourceType("LAB").sourceId("LAB-HEMATOLOGY-01").field("serviceCode").build()
                    ))
                    .build());
            missingInfo.add("Chỉ số Huyết áp trung bình và Nhịp tim hiện tại");
        } else if (userMsg.contains("thuốc") || userMsg.contains("toa") || userMsg.contains("đơn")) {
            suggestions.add(ClinicalSuggestionItem.builder()
                    .type("TREATMENT_REFERRAL")
                    .text("Tham khảo phác đồ: Paracetamol 500mg (khi sốt/đau) + Phác đồ bọc niêm mạc dạ dày (Esomeprazole 20mg)")
                    .evidenceRefs(List.of(
                            EvidenceRef.builder().sourceType("CONSULTATION").sourceId(request.getConsultationId()).field("diagnosis").build()
                    ))
                    .build());
            warnings.add("Cảnh báo tương tác: Tránh phối hợp 2 thuốc cùng nhóm NSAIDs để phòng xuất huyết dạ dày");
        } else {
            suggestions.add(ClinicalSuggestionItem.builder()
                    .type("DIFFERENTIAL_DIAGNOSIS")
                    .text("Trả lời lâm sàng: Bác sĩ nên kiểm tra kỹ phản xạ đồng tử, dấu hiệu màng não và SpO2 trước khi kết luận.")
                    .evidenceRefs(List.of(
                            EvidenceRef.builder().sourceType("CONSULTATION").sourceId(request.getConsultationId()).field("physicalExam").build()
                    ))
                    .build());
        }

        ClinicalSuggestionResponse response = ClinicalSuggestionResponse.builder()
                .interactionId(interactionId)
                .mode("MOCK_RULE_BASED")
                .summary("Phản hồi tương tác câu hỏi của Bác sĩ trong ca khám ID: " + request.getConsultationId())
                .suggestions(suggestions)
                .missingInformation(missingInfo)
                .warnings(warnings)
                .disclaimer(MEDICAL_DISCLAIMER)
                .model(AiModelInfo.builder()
                        .provider("CareFlow Clinical Rule Engine")
                        .name("careflow-clinical-v1")
                        .version("1.0.0")
                        .build())
                .generatedAt(Instant.now().toString())
                .build();

        interactionStore.put(interactionId, response);
        return response;
    }

    public ClinicalSuggestionResponse getInteractionById(String interactionId) {
        return interactionStore.get(interactionId);
    }
}
