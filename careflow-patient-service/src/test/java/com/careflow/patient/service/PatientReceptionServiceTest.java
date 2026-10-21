package com.careflow.patient.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.patient.client.ReceptionDirectoryClient;
import com.careflow.patient.dto.request.ReceptionPatientRequest;
import com.careflow.patient.model.Patient;
import com.careflow.patient.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PatientReceptionServiceTest {
    @Test
    void assignedStaffCreatesARealPatientWithoutBecomingOwner() {
        var patients = mock(PatientRepository.class);
        var directory = mock(ReceptionDirectoryClient.class);
        UUID actor = UUID.randomUUID(), id = UUID.randomUUID();
        when(directory.rooms()).thenReturn(ApiResponse.success(List.of(new ReceptionDirectoryClient.ReceptionRoom("ROOM-01", "CONSULTATION", true))));
        when(directory.roomAccess(actor, "ROOM-01")).thenReturn(ApiResponse.success(true));
        when(patients.saveAndFlush(any())).thenAnswer(call -> { Patient patient = call.getArgument(0); patient.setId(id); return patient; });
        var service = new PatientReceptionService(patients, directory);
        var result = service.create(new ReceptionPatientRequest("ROOM-01", "Sample", null, null, null, null), actor, "STAFF");
        assertThat(result.id()).isEqualTo(id); assertThat(result.userId()).isNull();
        var saved = ArgumentCaptor.forClass(Patient.class); verify(patients).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUserId()).isNull();
        assertThat(saved.getValue().getReceptionCreatedByUserId()).isEqualTo(actor);
    }

    @Test
    void missingAssignmentCannotWritePatient() {
        var patients = mock(PatientRepository.class);
        var directory = mock(ReceptionDirectoryClient.class);
        UUID actor = UUID.randomUUID();
        when(directory.rooms()).thenReturn(ApiResponse.success(List.of(new ReceptionDirectoryClient.ReceptionRoom("ROOM-01", "CONSULTATION", true))));
        when(directory.roomAccess(actor, "ROOM-01")).thenReturn(ApiResponse.success(false));
        assertThatThrownBy(() -> new PatientReceptionService(patients, directory).create(
                new ReceptionPatientRequest("ROOM-01", "Sample", null, null, null, null), actor, "STAFF"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(patients);
    }

    @Test
    void patientRoleCannotCreateUnownedProfiles() {
        var patients = mock(PatientRepository.class);
        var directory = mock(ReceptionDirectoryClient.class);
        assertThatThrownBy(() -> new PatientReceptionService(patients, directory).create(
                new ReceptionPatientRequest("ROOM-01", "Sample", null, null, null, null), UUID.randomUUID(), "PATIENT"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(patients, directory);
    }
}
