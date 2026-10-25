package com.careflow.patient.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.patient.dto.request.ReceptionPatientRequest;
import com.careflow.patient.service.PatientReceptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients/reception")
public class PatientReceptionController {
    private final PatientReceptionService reception;
    public PatientReceptionController(PatientReceptionService reception) { this.reception = reception; }

    public record Search(@jakarta.validation.constraints.NotBlank String roomId,@jakarta.validation.constraints.Size(min=3,max=80) @jakarta.validation.constraints.NotBlank String query){}
    @PostMapping("/search")
    public ApiResponse<java.util.List<PatientReceptionService.SearchPatient>> search(@Valid @RequestBody Search input,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){return ApiResponse.success(reception.search(input.roomId(),input.query(),actor,role));}
    @GetMapping("/{id}")
    public ApiResponse<PatientReceptionService.ReceptionPatient> minimum(@PathVariable UUID id,@RequestParam String roomId,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){return ApiResponse.success(reception.minimum(id,roomId,actor,role));}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PatientReceptionService.ReceptionPatient> create(
            @Valid @RequestBody ReceptionPatientRequest request,
            @RequestHeader("X-User-Id") UUID actor, @RequestHeader("X-User-Role") String role,
            @RequestHeader("Idempotency-Key") UUID key) {
        return ApiResponse.success("Đã tạo hồ sơ tiếp nhận không tài khoản", reception.create(request, actor, role,key));
    }
}
