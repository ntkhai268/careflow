package com.careflow.prescription.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.prescription.dto.response.PrescriptionResponse;
import com.careflow.prescription.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/prescriptions")
public class PrescriptionReleaseController {
    private final PrescriptionService prescriptions;private final PrescriptionFollowUpService followUps;
    public PrescriptionReleaseController(PrescriptionService prescriptions,PrescriptionFollowUpService followUps){this.prescriptions=prescriptions;this.followUps=followUps;}
    @PostMapping("/{id}/release")
    public ApiResponse<PrescriptionResponse> release(@PathVariable UUID id,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){
        // This controller has no spanning transaction: signature commits before appointment delivery.
        var signed=prescriptions.confirmPrescription(id,actor,role);
        if(signed.getFollowUpDate()==null)return ApiResponse.success(signed);
        try{return ApiResponse.success(followUps.attempt(id,actor,role));}
        catch(com.careflow.common.exception.BusinessException e){return ApiResponse.success(signed);}
    }
    @PostMapping("/{id}/follow-up/retry")
    public ApiResponse<PrescriptionResponse> retry(@PathVariable UUID id,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){return ApiResponse.success(followUps.attempt(id,actor,role));}
}
