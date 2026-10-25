package com.careflow.consultation.client;
import com.careflow.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@FeignClient(name="prescription-service",contextId="clinicalCompletionPrescriptions")
public interface ConsultationPrescriptionClient {
    @GetMapping("/api/prescriptions/consultation/{consultationId}") ApiResponse<List<Prescription>> byConsultation(@PathVariable UUID consultationId);
    record Prescription(UUID id,UUID consultationId,UUID patientId,UUID doctorId,String status){}
}
