package com.careflow.consultation.handover;
import com.careflow.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/consultations/handovers") @RequiredArgsConstructor
public class HandoverController {
    private final HandoverService service;
    @GetMapping("/patient-access/{patientId}") public ApiResponse<HandoverService.PatientAccess> patientAccess(@PathVariable UUID patientId, @RequestHeader("X-User-Id") UUID actor, @RequestHeader("X-User-Role") String role) { return ApiResponse.success(service.patientAccess(patientId, actor, role)); }
    @GetMapping public ApiResponse<HandoverService.Workspace> workspace(@RequestHeader("X-User-Id") UUID actor, @RequestHeader("X-User-Role") String role) { return ApiResponse.success(service.workspace(actor, role)); }
    @PostMapping public ApiResponse<Handover> request(@Valid @RequestBody HandoverRequests.Create request, @RequestHeader("X-User-Id") UUID actor, @RequestHeader("X-User-Role") String role) { return ApiResponse.success(service.request(request, actor, role)); }
    @PostMapping("/{id}/{action:accept|reject|cancel}") public ApiResponse<Handover> decide(@PathVariable UUID id, @PathVariable String action, @Valid @RequestBody HandoverRequests.Decision request, @RequestHeader("X-User-Id") UUID actor, @RequestHeader("X-User-Role") String role) { return ApiResponse.success(service.decide(id, action, request.note(), actor, role)); }
    @GetMapping("/coordinators/history") public ApiResponse<List<CoordinatorAudit>> history(@RequestHeader("X-User-Id") UUID actor, @RequestHeader("X-User-Role") String role) { return ApiResponse.success(service.grantHistory(actor, role)); }
    @GetMapping("/coordinators") public ApiResponse<List<HandoverCoordinator>> grants(@RequestHeader("X-User-Id") UUID actor, @RequestHeader("X-User-Role") String role) { return ApiResponse.success(service.grants(actor, role)); }
    @PostMapping("/coordinators") public ApiResponse<HandoverCoordinator> grant(@Valid @RequestBody HandoverRequests.Grant request, @RequestHeader("X-User-Id") UUID actor, @RequestHeader("X-User-Role") String role) { return ApiResponse.success(service.grant(request, actor, role)); }
    @PostMapping("/coordinators/{id}/revoke") public ApiResponse<HandoverCoordinator> revoke(@PathVariable UUID id, @Valid @RequestBody HandoverRequests.Decision request, @RequestHeader("X-User-Id") UUID actor, @RequestHeader("X-User-Role") String role) { return ApiResponse.success(service.revoke(id, request.note(), actor, role)); }
}
