package com.careflow.patient.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.patient.security.HealthRecordActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HealthRecordAccessService {
    private final PatientService patientService;

    public void requireRead(UUID patientId, HealthRecordActor actor) {
        requireAuthenticated(actor);
        if (!"PATIENT".equals(actor.role()) && !"DOCTOR".equals(actor.role())) {
            throw new BusinessException(403, "Only the patient owner or assigned doctor may read uploaded records");
        }
        patientService.getPatientById(patientId, actor.userId(), actor.role());
    }

    public void requireWrite(UUID patientId, HealthRecordActor actor) {
        requireAuthenticated(actor);
        if (!"PATIENT".equals(actor.role())) {
            throw new BusinessException(403, "Only the patient owner may change uploaded records");
        }
        patientService.getPatientById(patientId, actor.userId(), actor.role());
    }

    private void requireAuthenticated(HealthRecordActor actor) {
        if (actor == null || actor.userId() == null || actor.role() == null) {
            throw new BusinessException(401, "Authenticated user is required");
        }
    }
}
