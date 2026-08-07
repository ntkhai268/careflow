package com.careflow.patient.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.patient.dto.request.CreatePatientRequest;
import com.careflow.patient.dto.request.UpdatePatientRequest;
import com.careflow.patient.dto.response.PatientResponse;
import com.careflow.patient.dto.response.PatientOperationalResponse;
import com.careflow.patient.client.AssignmentClient;
import com.careflow.patient.client.dto.AssignmentAccessResponse;
import com.careflow.common.dto.ApiResponse;
import com.careflow.patient.model.Patient;
import com.careflow.patient.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceSecurityTest {

    private static final UUID OWNER_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PATIENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private AssignmentClient assignmentClient;

    @InjectMocks
    private PatientService patientService;

    @Test
    void createRejectsPatientCreatingProfileForAnotherUser() {
        CreatePatientRequest request = CreatePatientRequest.builder()
                .userId(OTHER_USER_ID)
                .fullName("Nguyen Van A")
                .build();

        assertThatThrownBy(() -> patientService.createPatient(request, OWNER_USER_ID, "PATIENT"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(403));
        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    void createRejectsMissingIdentity() {
        CreatePatientRequest request = CreatePatientRequest.builder()
                .userId(OWNER_USER_ID)
                .fullName("Nguyen Van A")
                .build();

        assertThatThrownBy(() -> patientService.createPatient(request, null, "PATIENT"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(401));
    }

    @Test
    void createAllowsAdminForAnotherUser() {
        CreatePatientRequest request = CreatePatientRequest.builder()
                .userId(OTHER_USER_ID)
                .fullName("Nguyen Van A")
                .build();
        Patient saved = Patient.builder().userId(OTHER_USER_ID).fullName("Nguyen Van A").build();
        saved.setId(PATIENT_ID);

        when(patientRepository.save(any(Patient.class))).thenReturn(saved);

        PatientResponse response = patientService.createPatient(request, OWNER_USER_ID, "ADMIN");

        assertThat(response.getId()).isEqualTo(PATIENT_ID);
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    void createAllowsMultipleProfilesForTheSamePatientAccount() {
        CreatePatientRequest request = CreatePatientRequest.builder()
                .userId(OWNER_USER_ID)
                .fullName("Nguyen Thi B")
                .build();
        Patient saved = Patient.builder().userId(OWNER_USER_ID).fullName("Nguyen Thi B").build();
        saved.setId(PATIENT_ID);

        when(patientRepository.countByUserId(OWNER_USER_ID)).thenReturn(1L);
        when(patientRepository.save(any(Patient.class))).thenReturn(saved);

        PatientResponse response = patientService.createPatient(request, OWNER_USER_ID, "PATIENT");

        assertThat(response.getId()).isEqualTo(PATIENT_ID);
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    void listProfilesAllowsTheOwningPatientAccountToReadEveryProfile() {
        Patient first = Patient.builder().userId(OWNER_USER_ID).fullName("Nguyen Van A").build();
        Patient second = Patient.builder().userId(OWNER_USER_ID).fullName("Nguyen Thi B").build();
        first.setId(PATIENT_ID);
        second.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        when(patientRepository.findAllByUserIdOrderByCreatedAtAsc(OWNER_USER_ID))
                .thenReturn(List.of(first, second));

        List<PatientResponse> response = patientService.getPatientsByUserId(
                OWNER_USER_ID, OWNER_USER_ID, "PATIENT");

        assertThat(response).extracting(PatientResponse::getFullName)
                .containsExactly("Nguyen Van A", "Nguyen Thi B");
    }

    @Test
    void readRejectsAnotherPatientsProfile() {
        Patient patient = Patient.builder().userId(OWNER_USER_ID).fullName("Nguyen Van A").build();
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> patientService.getPatientById(PATIENT_ID, OTHER_USER_ID, "PATIENT"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(403));
    }

    @Test
    void readAllowsAssignedDoctorToLoadAClinicalQueuePatient() {
        Patient patient = Patient.builder().userId(OWNER_USER_ID).fullName("Nguyen Van A").build();
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(assignmentClient.getPatientAccess(PATIENT_ID, null, null, OTHER_USER_ID, "DOCTOR"))
                .thenReturn(ApiResponse.success(AssignmentAccessResponse.allowed()));

        PatientResponse response = patientService.getPatientById(PATIENT_ID, OTHER_USER_ID, "DOCTOR");

        assertThat(response.getFullName()).isEqualTo("Nguyen Van A");
    }

    @Test
    void readRejectsUnassignedDoctor() {
        Patient patient = Patient.builder().userId(OWNER_USER_ID).fullName("Nguyen Van A").build();
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));

        when(assignmentClient.getPatientAccess(PATIENT_ID, null, null, OTHER_USER_ID, "DOCTOR"))
                .thenReturn(ApiResponse.success(AssignmentAccessResponse.denied()));

        assertThatThrownBy(() -> patientService.getPatientById(PATIENT_ID, OTHER_USER_ID, "DOCTOR"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(403));
    }

    @Test
    void readRejectsStaffFromClinicalProfile() {
        Patient patient = Patient.builder().userId(OWNER_USER_ID).fullName("Nguyen Van A").build();
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> patientService.getPatientById(PATIENT_ID, OTHER_USER_ID, "STAFF"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(403));
    }

    @Test
    void operationalSummaryAllowsStaffForAssignedAppointment() {
        Patient patient = Patient.builder().userId(OWNER_USER_ID).fullName("Nguyen Van A").build();
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(assignmentClient.getPatientAccess(PATIENT_ID, PATIENT_ID, "ROOM-01", OTHER_USER_ID, "STAFF"))
                .thenReturn(ApiResponse.success(AssignmentAccessResponse.allowed()));

        PatientOperationalResponse response = patientService.getOperationalSummary(
                PATIENT_ID, PATIENT_ID, "ROOM-01", OTHER_USER_ID, "STAFF");

        assertThat(response.getFullName()).isEqualTo("Nguyen Van A");
    }

    @Test
    void operationalSummaryRejectsStaffForWrongAssignment() {
        Patient patient = Patient.builder().userId(OWNER_USER_ID).fullName("Nguyen Van A").build();
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(assignmentClient.getPatientAccess(PATIENT_ID, PATIENT_ID, "ROOM-02", OTHER_USER_ID, "STAFF"))
                .thenReturn(ApiResponse.success(AssignmentAccessResponse.denied()));

        assertThatThrownBy(() -> patientService.getOperationalSummary(
                PATIENT_ID, PATIENT_ID, "ROOM-02", OTHER_USER_ID, "STAFF"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(403));
    }

    @Test
    void updateRejectsAnotherPatientsProfile() {
        Patient patient = Patient.builder().userId(OWNER_USER_ID).fullName("Nguyen Van A").build();
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> patientService.updatePatient(
                        PATIENT_ID, new UpdatePatientRequest(), OTHER_USER_ID, "PATIENT"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(403));
        verify(patientRepository, never()).save(any(Patient.class));
    }
}
