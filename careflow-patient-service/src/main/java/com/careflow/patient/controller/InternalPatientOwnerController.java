package com.careflow.patient.controller;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.ServiceTransportIdentity;
import com.careflow.patient.repository.PatientRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
@RestController
public class InternalPatientOwnerController {
    private final PatientRepository patients;
    private final JsonNode readers;
    public InternalPatientOwnerController(PatientRepository patients,ObjectMapper json,@Value("${patient.internal-readers-json:{}}") String config)throws Exception{
        this.patients=patients;this.readers=json.readTree(config);
    }
    public record Owner(UUID patientId,UUID userId){}
    @GetMapping("/internal/patients/{id}/owner")
    public ResponseEntity<?> owner(@PathVariable UUID id,HttpServletRequest request){
        String caller=request.getHeader("X-Careflow-Caller"),key=request.getHeader("X-Careflow-Caller-Key");
        if(!Set.of("appointment-service","queue-service","notification-service","consultation-service").contains(caller==null?"":caller))throw new BusinessException(401,"Caller không hợp lệ.");
        ServiceTransportIdentity.require(request,caller,false);
        String expected=readers.path(caller).asText();
        if(expected.length()<32 || key==null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8)))throw new BusinessException(401,"Caller không hợp lệ.");
        var patient=patients.findById(id).orElseThrow(()->new BusinessException(404,"Không tìm thấy hồ sơ."));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(new Owner(id,patient.getUserId())));
    }
}
