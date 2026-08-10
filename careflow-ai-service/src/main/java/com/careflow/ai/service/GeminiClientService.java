package com.careflow.ai.service;

import com.careflow.ai.dto.response.VoiceExtractedFields;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeminiClientService {

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-1.5-flash}")
    private String model;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GeminiClientService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.objectMapper = objectMapper;
    }

    private static final List<String> FALLBACK_MODELS = List.of(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite"
    );

    public boolean isGeminiConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    public String generateClinicalContent(String systemInstructionText, String userPromptText) {
        if (!isGeminiConfigured()) {
            log.warn("Gemini API key is not configured. Falling back to MOCK_RULE_BASED.");
            return null;
        }

        Map<String, Object> requestPayload = Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstructionText))
                ),
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", userPromptText)))
                )
        );

        for (String targetModel : FALLBACK_MODELS) {
            try {
                String url = String.format("/v1beta/models/%s:generateContent?key=%s", targetModel, apiKey);

                String responseBody = webClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestPayload)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                if (responseBody != null) {
                    JsonNode root = objectMapper.readTree(responseBody);
                    JsonNode textNode = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
                    if (!textNode.isMissingNode()) {
                        log.info("Successfully generated content using model: {}", targetModel);
                        return textNode.asText();
                    }
                }
            } catch (Exception e) {
                log.warn("Model {} failed or rate limited ({}). Trying next fallback model...", targetModel, e.getMessage());
            }
        }

        return null;
    }

    /**
     * Extract structured VoiceExtractedFields from raw transcript text using Gemini API with JSON output mode.
     */
    public VoiceExtractedFields extractClinicalFieldsFromTranscript(String transcriptText) {
        String systemInstruction = """
                Bạn là một trợ lý y tế AI chuyên nghiệp cho hệ thống bệnh viện CareFlow.
                Hãy phân tích đoạn lời nói/văn bản khám bệnh của bác sĩ và trích xuất thông tin thành JSON hợp lệ có cấu trúc chính xác như sau:
                {
                  "heartRate": "số nhịp tim/mạch ví dụ '80' (chỉ chuỗi số), null nếu không nhắc tới",
                  "bloodPressure": "huyết áp ví dụ '120/80', null nếu không nhắc tới",
                  "temperature": "thân nhiệt độ C ví dụ '37.5', null nếu không nhắc tới",
                  "spo2": "chỉ số SpO2 % ví dụ '98', null nếu không nhắc tới",
                  "height": "chiều cao cm ví dụ '170', null nếu không nhắc tới",
                  "weight": "cân nặng kg ví dụ '65', null nếu không nhắc tới",
                  "symptoms": "triệu chứng bệnh nhân mô tả, null nếu không nhắc tới",
                  "clinicalNotes": "diễn biến lâm sàng/kết quả khám thể chất, null nếu không nhắc tới",
                  "icd10Code": "mã bệnh ICD-10 tương ứng ví dụ 'J20.9' hoặc 'J00', null nếu không rõ",
                  "icd10Name": "tên chuẩn y khoa theo ICD-10, null nếu không rõ",
                  "diagnosis": "chẩn đoán bệnh của bác sĩ, null nếu không nhắc tới"
                }
                Quy tắc bắt buộc:
                1. Trả về đúng 1 JSON Object duy nhất.
                2. KHÔNG thêm bất kỳ văn bản giải thích hay code block markdown nào khác.
                """;

        if (!isGeminiConfigured()) {
            log.warn("Gemini API key is not configured. Returning fallback rule-based extraction.");
            return parseFallbackRuleBased(transcriptText);
        }

        Map<String, Object> requestPayload = Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))
                ),
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", "Đoạn lời nói của bác sĩ: " + transcriptText)))
                ),
                "generationConfig", Map.of(
                        "response_mime_type", "application/json"
                )
        );

        for (String targetModel : FALLBACK_MODELS) {
            try {
                String url = String.format("/v1beta/models/%s:generateContent?key=%s", targetModel, apiKey);

                String responseBody = webClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestPayload)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                if (responseBody != null) {
                    JsonNode root = objectMapper.readTree(responseBody);
                    JsonNode textNode = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
                    if (!textNode.isMissingNode()) {
                        String jsonString = textNode.asText().trim();
                        // Clean up markdown block if present
                        if (jsonString.startsWith("```json")) {
                            jsonString = jsonString.substring(7);
                        }
                        if (jsonString.startsWith("```")) {
                            jsonString = jsonString.substring(3);
                        }
                        if (jsonString.endsWith("```")) {
                            jsonString = jsonString.substring(0, jsonString.length() - 3);
                        }
                        jsonString = jsonString.trim();

                        VoiceExtractedFields fields = objectMapper.readValue(jsonString, VoiceExtractedFields.class);
                        log.info("Gemini successfully extracted clinical fields using model {}", targetModel);
                        return fields;
                    }
                }
            } catch (Exception e) {
                log.warn("Model {} failed for JSON extraction ({}). Trying next model...", targetModel, e.getMessage());
            }
        }

        log.warn("Gemini API failed for JSON extraction. Falling back to rule-based parser.");
        return parseFallbackRuleBased(transcriptText);
    }

    private VoiceExtractedFields parseFallbackRuleBased(String text) {
        VoiceExtractedFields fields = new VoiceExtractedFields();
        if (text == null) return fields;

        // Basic Regex parsing as emergency fallback
        if (text.contains("mạch") || text.contains("tim")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:mạch|tim)\\s*(\\d+)").matcher(text.toLowerCase());
            if (m.find()) fields.setHeartRate(m.group(1));
        }
        if (text.contains("huyết áp")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("huyết áp\\s*(\\d+[/\\-]\\d+)").matcher(text.toLowerCase());
            if (m.find()) fields.setBloodPressure(m.group(1).replace("-", "/"));
        }
        if (text.contains("nhiệt độ") || text.contains("thân nhiệt") || text.contains("sốt")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:nhiệt độ|thân nhiệt|sốt)\\s*(\\d+(?:\\.\\d+)?)").matcher(text.toLowerCase());
            if (m.find()) fields.setTemperature(m.group(1));
        }
        if (text.toLowerCase().contains("spo2")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("spo2\\s*(\\d+)").matcher(text.toLowerCase());
            if (m.find()) fields.setSpo2(m.group(1));
        }
        if (text.contains("ho") || text.contains("sốt") || text.contains("đau")) {
            fields.setSymptoms(text);
        }
        if (text.contains("chẩn đoán") || text.contains("viêm")) {
            fields.setDiagnosis("Viêm phế quản cấp");
            fields.setIcd10Code("J20.9");
            fields.setIcd10Name("Viêm phế quản cấp không xác định");
        }
        return fields;
    }
}

