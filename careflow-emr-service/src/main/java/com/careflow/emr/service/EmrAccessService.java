package com.careflow.emr.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.emr.client.PatientClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmrAccessService {
    private final PatientClient patientClient;

    public void requireRead(UUID patientId, UUID actorUserId, String role) {
        if (actorUserId == null || role == null) {
            throw new BusinessException(401, "Authenticated user is required");
        }
        if (!AppConstants.ROLE_PATIENT.equals(role) && !AppConstants.ROLE_DOCTOR.equals(role)) {
            throw new BusinessException(403, "Patient owner or assigned clinician access is required");
        }
        ApiResponse<Map<String, Object>> response = fetchPatientForAccessCheck(patientId);
        Map<String, Object> patient = response == null ? null : response.getData();
        if (patient == null || !patientId.toString().equals(String.valueOf(patient.get("id")))) {
            throw new BusinessException(403, "Patient access is not allowed");
        }
        if (AppConstants.ROLE_PATIENT.equals(role)
                && !actorUserId.toString().equals(String.valueOf(patient.get("userId")))) {
            throw new BusinessException(403, "Patient access is not allowed");
        }
    }

    public void requireWrite(UUID patientId, UUID actorUserId, String role) {
        if (actorUserId == null || role == null) {
            throw new BusinessException(401, "Authenticated user is required");
        }
        if (!AppConstants.ROLE_DOCTOR.equals(role)) {
            throw new BusinessException(403, "Assigned clinician access is required");
        }
        requireRead(patientId, actorUserId, role);
    }

    private ApiResponse<Map<String, Object>> fetchPatientForAccessCheck(UUID patientId) {
        try {
            return patientClient.getPatientById(patientId);
        } catch (FeignException exception) {
            if (exception.status() == 403 || exception.status() == 404) {
                throw new BusinessException(403, "Patient access is not allowed");
            }
            throw new BusinessException(503, "Unable to verify patient access");
        } catch (RuntimeException exception) {
            throw new BusinessException(503, "Unable to verify patient access");
        }
    }
}
