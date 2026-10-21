package com.careflow.consultation.controller;
import com.careflow.common.dto.ApiResponse;
import com.careflow.consultation.service.ClinicalFinalizationService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/consultations")
public class ClinicalFinalizationController {
    private final ClinicalFinalizationService finalization;
    public ClinicalFinalizationController(ClinicalFinalizationService finalization){this.finalization=finalization;}
    @PostMapping("/{id}/finalize")
    public ApiResponse<ClinicalFinalizationService.Result> finalizeVisit(@PathVariable UUID id,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){return ApiResponse.success(finalization.finalizeVisit(id,actor,role));}
}
