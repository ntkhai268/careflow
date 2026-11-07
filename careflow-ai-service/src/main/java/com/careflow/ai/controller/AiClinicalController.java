package com.careflow.ai.controller;

import com.careflow.ai.clinical.AiAccess;
import com.careflow.ai.dto.request.ClinicalSuggestionRequest;
import com.careflow.ai.dto.response.ClinicalSuggestionResponse;
import com.careflow.ai.service.AiClinicalAssistantService;
import com.careflow.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AiClinicalController {
    private final AiClinicalAssistantService service;
    private final AiAccess access;

    public AiClinicalController(AiClinicalAssistantService service, AiAccess access) {
        this.service = service;
        this.access = access;
    }

    public record Question(
            @NotNull UUID consultationId,
            @Size(max = 2000) String question,
            @Size(max = 2000) String message,
            String pageRoute,
            String pageTitle,
            String pageData,
            UUID parentInteractionId) {
    }

    @PostMapping({"/clinical-chat", "/clinical-suggestions"})
    public ResponseEntity<?> ask(@Valid @RequestBody Question q, HttpServletRequest request) {
        access.doctor(request);

        String prompt = q.message() != null ? q.message() : (q.question() != null ? q.question() : "Gợi ý chẩn đoán phân biệt và dữ liệu còn thiếu");

        String pageData = q.pageData();
        if (pageData == null || pageData.isBlank()) {
            try {
                var actor = access.doctor(request);
                var context = access.context(q.consultationId(), actor);
                if (context != null && context.facts() != null) {
                    pageData = "Dữ liệu ca khám: " + context.facts().toString();
                }
            } catch (Exception ignored) {
            }
        }

        ClinicalSuggestionRequest req = ClinicalSuggestionRequest.builder()
                .consultationId(q.consultationId().toString())
                .question(prompt)
                .pageRoute(q.pageRoute())
                .pageTitle(q.pageTitle())
                .pageData(pageData)
                .build();

        ClinicalSuggestionResponse response = service.getClinicalSuggestions(req);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }

    @GetMapping("/interactions/{id}")
    public ResponseEntity<?> read(@PathVariable UUID id, HttpServletRequest request) {
        access.doctor(request);
        var res = service.getInteractionById(id.toString());
        if (res != null) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(ApiResponse.success(res));
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.engine().read(id, request)));
    }

    @GetMapping("/consultations/{id}/interactions")
    public ResponseEntity<?> history(@PathVariable UUID id, HttpServletRequest request) {
        access.doctor(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.engine().history(id, request)));
    }
}
