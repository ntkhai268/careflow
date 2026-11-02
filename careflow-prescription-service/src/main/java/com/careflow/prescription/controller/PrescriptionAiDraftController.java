package com.careflow.prescription.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.prescription.dto.response.PrescriptionResponse;
import com.careflow.prescription.service.PrescriptionAiDraftService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class PrescriptionAiDraftController {

    private final PrescriptionAiDraftService drafts;

    public PrescriptionAiDraftController(PrescriptionAiDraftService drafts) {
        this.drafts = drafts;
    }

    public record Request(@NotNull UUID jobId, @Min(1) int transcriptRevision) {
    }

    public record EditableRequest(
            @NotNull UUID jobId,
            @Min(1) int transcriptRevision,
            UUID prescriptionId,
            boolean replaceItems) {
    }

    @PostMapping("/api/prescriptions/ai-drafts")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> save(
            @Valid @RequestBody Request input,
            @RequestHeader("X-User-Id") UUID actor,
            @RequestHeader("X-User-Role") String role) {
        PrescriptionResponse saved = drafts.save(input.jobId(), input.transcriptRevision(), actor, role);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(saved));
    }

    @PostMapping("/api/prescriptions/ai-drafts/editable")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> saveEditable(
            @Valid @RequestBody EditableRequest input,
            @RequestHeader("X-User-Id") UUID actor,
            @RequestHeader("X-User-Role") String role) {
        if (input.replaceItems() && input.prescriptionId() == null) {
            throw new BusinessException(400, "Thiếu toa nháp cần thay thuốc");
        }

        PrescriptionResponse saved;
        if (input.prescriptionId() == null) {
            saved = drafts.saveEditable(input.jobId(), input.transcriptRevision(), actor, role);
        } else if (input.replaceItems()) {
            saved = drafts.replaceEditable(
                    input.jobId(), input.transcriptRevision(), actor, role, input.prescriptionId());
        } else {
            saved = drafts.appendEditable(
                    input.jobId(), input.transcriptRevision(), actor, role, input.prescriptionId());
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(saved));
    }
}
