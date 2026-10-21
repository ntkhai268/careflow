package com.careflow.patient.client;
import com.careflow.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@FeignClient(name="identity-service",contextId="patientConfirmedIdentity")
public interface ConfirmedIdentityClient {
    record Fields(String documentNumber,String fullName,String birthDate){}
    record Match(UUID owner,Fields fields){}
    record Result(UUID checkId){}
    @PostMapping("/api/auth/identity-document/confirmed-match")
    ApiResponse<Result> match(@RequestBody Match body);
}
