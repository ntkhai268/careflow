package com.careflow.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DeepSeekClientService {

    @Value("${deepseek.api-key:}")
    private String apiKey;

    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DeepSeekClientService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl("https://api.deepseek.com").build();
        this.objectMapper = objectMapper;
    }

    public boolean isDeepSeekConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    public String generateClinicalContent(String systemInstructionText, String userPromptText) {
        if (!isDeepSeekConfigured()) {
            log.warn("DeepSeek API key is not configured.");
            return null;
        }

        try {
            Map<String, Object> requestPayload = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemInstructionText),
                            Map.of("role", "user", "content", userPromptText)
                    ),
                    "stream", false
            );

            String responseBody = webClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestPayload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody != null) {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode contentNode = root.path("choices").get(0).path("message").path("content");
                if (!contentNode.isMissingNode()) {
                    log.info("Successfully generated clinical content using DeepSeek model: {}", model);
                    return contentNode.asText();
                }
            }
        } catch (Exception e) {
            log.error("Failed to call DeepSeek API: {}", e.getMessage());
        }

        return null;
    }
}
