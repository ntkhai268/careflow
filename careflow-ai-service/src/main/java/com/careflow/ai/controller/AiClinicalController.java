package com.careflow.ai.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.ai.dto.request.ClinicalChatRequest;
import com.careflow.ai.dto.request.ClinicalSuggestionRequest;
import com.careflow.ai.dto.response.ClinicalSuggestionResponse;
import com.careflow.ai.service.AiClinicalAssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiClinicalController {

    private final AiClinicalAssistantService aiClinicalAssistantService;

    @PostMapping("/clinical-suggestions")
    public ResponseEntity<ApiResponse<ClinicalSuggestionResponse>> getClinicalSuggestions(
            @Valid @RequestBody ClinicalSuggestionRequest request) {
        log.info("Received request for clinical suggestions for consultation: {}", request.getConsultationId());
        ClinicalSuggestionResponse response = aiClinicalAssistantService.getClinicalSuggestions(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/clinical-chat")
    public ResponseEntity<ApiResponse<ClinicalSuggestionResponse>> processClinicalChat(
            @Valid @RequestBody ClinicalChatRequest request) {
        log.info("Received clinical chat request for consultation: {}", request.getConsultationId());
        ClinicalSuggestionResponse response = aiClinicalAssistantService.processClinicalChat(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/interactions/{interactionId}")
    public ResponseEntity<ApiResponse<ClinicalSuggestionResponse>> getInteractionById(
            @PathVariable String interactionId) {
        log.info("Received request to fetch interaction audit for ID: {}", interactionId);
        ClinicalSuggestionResponse response = aiClinicalAssistantService.getInteractionById(interactionId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
