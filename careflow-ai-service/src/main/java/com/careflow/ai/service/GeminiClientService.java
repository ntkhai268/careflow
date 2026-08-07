package com.careflow.ai.service;

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
}
