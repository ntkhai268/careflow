package com.careflow.emr.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.emr.client.ConsultationClient;
import com.careflow.emr.client.LabClient;
import com.careflow.emr.client.PatientClient;
import com.careflow.emr.client.PrescriptionClient;
import com.careflow.emr.mapper.MedicalRecordMapper;
import com.careflow.emr.repository.MedicalRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmrMissingProviderDataTest {
    @Test
    void missingProviderEnvelopeCannotBecomeEmptyHistoryOrCreateRecord() {
        UUID patientId = UUID.randomUUID();
        PatientClient patients = mock(PatientClient.class);
        ConsultationClient consultations = mock(ConsultationClient.class);
        PrescriptionClient prescriptions = mock(PrescriptionClient.class);
        LabClient labs = mock(LabClient.class);
        MedicalRecordRepository repository = mock(MedicalRecordRepository.class);
        when(repository.findByPatientId(patientId)).thenReturn(Optional.empty());
        when(patients.getPatientById(patientId)).thenReturn(ApiResponse.success(Map.of("id", patientId.toString())));
        when(consultations.getConsultationsByPatient(patientId)).thenReturn(ApiResponse.success(null));
        MedicalRecordService service = new MedicalRecordService(repository, new MedicalRecordMapper(), patients,
                consultations, prescriptions, labs);

        assertThatThrownBy(() -> service.getPatientSummary(patientId))
                .isInstanceOf(BusinessException.class).extracting("status").isEqualTo(503);
        verify(repository, never()).save(any());
        verifyNoInteractions(prescriptions, labs);
    }
}
