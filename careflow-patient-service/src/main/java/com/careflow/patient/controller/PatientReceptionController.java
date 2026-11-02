package com.careflow.patient.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.patient.dto.request.ReceptionPatientRequest;
import com.careflow.patient.service.PatientReceptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients/reception")
public class PatientReceptionController {
    private final PatientReceptionService reception;

    public PatientReceptionController(PatientReceptionService reception) {
        this.reception = reception;
    }

    public record Search(
            @NotBlank String roomId,
            @Size(min = 3, max = 80) @NotBlank String query
    ) {}

    @PostMapping("/search")
    public ApiResponse<List<PatientReceptionService.SearchPatient>> search(
            @Valid @RequestBody Search input,
            @RequestHeader("X-User-Id") UUID actor,
            @RequestHeader("X-User-Role") String role) {
        return ApiResponse.success(reception.search(input.roomId(), input.query(), actor, role));
    }

    @GetMapping("/{id}")
    public ApiResponse<PatientReceptionService.ReceptionPatient> minimum(
            @PathVariable UUID id,
            @RequestParam String roomId,
            @RequestHeader("X-User-Id") UUID actor,
            @RequestHeader("X-User-Role") String role) {
        return ApiResponse.success(reception.minimum(id, roomId, actor, role));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PatientReceptionService.ReceptionPatient> create(
            @Valid @RequestBody ReceptionPatientRequest request,
            @RequestHeader("X-User-Id") UUID actor,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader("Idempotency-Key") UUID key) {
        return ApiResponse.success(
                "Đã tạo hồ sơ tiếp nhận không tài khoản",
                reception.create(request, actor, role, key));
    }
}
