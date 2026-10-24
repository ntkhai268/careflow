package com.careflow.prescription.controller;
import com.careflow.prescription.service.PrescriptionAiDraftService;import com.careflow.common.dto.ApiResponse;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.web.bind.annotation.*;import org.springframework.http.*;import java.util.*;
@RestController
public class PrescriptionAiDraftController {
    private final PrescriptionAiDraftService drafts;public PrescriptionAiDraftController(PrescriptionAiDraftService drafts){this.drafts=drafts;}
    public record Request(@NotNull UUID jobId,@Min(1) int transcriptRevision){}
    public record EditableRequest(@NotNull UUID jobId,@Min(1) int transcriptRevision,UUID prescriptionId,boolean replaceItems){}
    @PostMapping("/api/prescriptions/ai-drafts") public ResponseEntity<?> save(@Valid @RequestBody Request input,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(drafts.save(input.jobId(),input.transcriptRevision(),actor,role)));}
    @PostMapping("/api/prescriptions/ai-drafts/editable") public ResponseEntity<?> saveEditable(@Valid @RequestBody EditableRequest input,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){if(input.replaceItems()&&input.prescriptionId()==null)throw new com.careflow.common.exception.BusinessException(400,"Thiếu toa nháp cần thay thuốc");var saved=input.prescriptionId()==null?drafts.saveEditable(input.jobId(),input.transcriptRevision(),actor,role):input.replaceItems()?drafts.replaceEditable(input.jobId(),input.transcriptRevision(),actor,role,input.prescriptionId()):drafts.appendEditable(input.jobId(),input.transcriptRevision(),actor,role,input.prescriptionId());return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(saved));}
}
