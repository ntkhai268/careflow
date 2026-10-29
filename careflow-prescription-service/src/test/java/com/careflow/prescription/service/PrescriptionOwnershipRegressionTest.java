package com.careflow.prescription.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.prescription.config.*;
import com.careflow.prescription.dto.request.*;
import com.careflow.prescription.mapper.PrescriptionMapper;
import com.careflow.prescription.model.*;
import com.careflow.prescription.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PrescriptionOwnershipRegressionTest {
    private final UUID doctor = UUID.randomUUID(), patient = UUID.randomUUID(), user = UUID.randomUUID();
    private final UUID consultation = UUID.randomUUID(), id = UUID.randomUUID();
    private PrescriptionRepository repository;
    private ConsultationClient consultations;
    private PatientIdentityClient patients;
    private PrescriptionEventOutbox outbox;
    private PrescriptionService service;
    private Prescription record;

    @BeforeEach
    void setUp() {
        repository = mock(PrescriptionRepository.class);
        consultations = mock(ConsultationClient.class);
        patients = mock(PatientIdentityClient.class);
        outbox = mock(PrescriptionEventOutbox.class);
        DrugRepository drugs = mock(DrugRepository.class);
        when(drugs.existsByCode("PARA500")).thenReturn(true);
        when(repository.save(any())).thenAnswer(call -> {
            Prescription value = call.getArgument(0);
            if (value.getId() == null) value.setId(UUID.randomUUID());
            return value;
        });
        service = new PrescriptionService(repository, drugs, new PrescriptionMapper(), consultations,
                mock(DirectoryClient.class), mock(QueueExecutionClient.class),
                patients, new ObjectMapper().findAndRegisterModules(), outbox, mock(PrescriptionAiReleaseGuard.class));
        record = Prescription.builder().consultationId(consultation).patientId(patient).doctorId(doctor)
                .status(PrescriptionStatus.DRAFT).build();
        record.setId(id);
        record.addItem(PrescriptionItem.builder().medicineName("Paracetamol").medicineCode("PARA500").build());
        when(repository.findById(id)).thenReturn(Optional.of(record));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(record));
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> deniedCommands() {
        return Stream.of("CREATE", "UPDATE", "CONFIRM", "AMEND").flatMap(command ->
                Stream.of("wrong-patient", "wrong-doctor", "unknown-status", "missing")
                        .map(reason -> org.junit.jupiter.params.provider.Arguments.of(command, reason)));
    }

    @ParameterizedTest
    @MethodSource("deniedCommands")
    void invalidConsultationStopsEveryWriteWithoutChangingRowsOrPublishing(String command, String reason) {
        if ("AMEND".equals(command)) record.setStatus(PrescriptionStatus.CONFIRMED);
        PrescriptionStatus before = record.getStatus();
        ConsultationClient.ConsultationContext context = new ConsultationClient.ConsultationContext(
                consultation, "wrong-patient".equals(reason) ? UUID.randomUUID() : patient,
                "wrong-doctor".equals(reason) ? UUID.randomUUID() : doctor,
                "unknown-status".equals(reason) ? "UNRECOGNIZED" : "IN_PROGRESS");
        when(consultations.getConsultation(consultation)).thenReturn("missing".equals(reason) ? null : context);
        int expected = reason.startsWith("wrong-") ? 403 : 503;
        assertDenied(expected, () -> {
            switch (command) {
                case "CREATE" -> service.createPrescription(request(), doctor, "DOCTOR");
                case "UPDATE" -> service.updatePrescription(id, request(), doctor, "DOCTOR");
                case "CONFIRM" -> service.confirmPrescription(id, doctor, "DOCTOR");
                case "AMEND" -> service.amendPrescription(id, AmendPrescriptionRequest.builder()
                        .items(request().getItems()).build(), doctor, "DOCTOR");
            }
        });
        assertThat(record.getStatus()).isEqualTo(before);
        verify(repository, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "CANCELLED", "TRANSFERRED"})
    void terminalConsultationCannotCreateDraft(String state) {
        when(consultations.getConsultation(consultation)).thenReturn(
                new ConsultationClient.ConsultationContext(consultation, patient, doctor, state));
        assertDenied(400, () -> service.createPrescription(request(), doctor, "DOCTOR"));
        verify(repository, never()).save(any());
    }

    @Test
    void createDerivesDoctorFromActorWhenBodyOmitsDoctor() {
        when(consultations.getConsultation(consultation)).thenReturn(
                new ConsultationClient.ConsultationContext(consultation, patient, doctor, "IN_PROGRESS"));
        CreatePrescriptionRequest request = request();
        request.setDoctorId(null);
        assertThat(service.createPrescription(request, doctor, "DOCTOR").getDoctorId()).isEqualTo(doctor);
        verify(repository).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"patient", "consultation", "doctor"})
    void updateCannotChangeBindingIds(String changed) {
        CreatePrescriptionRequest request = request();
        switch (changed) {
            case "patient" -> request.setPatientId(UUID.randomUUID());
            case "consultation" -> request.setConsultationId(UUID.randomUUID());
            case "doctor" -> request.setDoctorId(UUID.randomUUID());
        }
        assertDenied(403, () -> service.updatePrescription(id, request, doctor, "DOCTOR"));
        verifyNoInteractions(consultations, outbox);
        verify(repository, never()).save(any());
    }

    @Test
    void actorlessConfirmationNeverImpersonatesStoredDoctor() {
        assertDenied(403, () -> service.confirmPrescription(id, null, null));
        verify(repository, never()).findById(any());
        verifyNoInteractions(consultations, outbox);
    }

    @Test
    void patientHistoryAuthorizesRequestedProfileAndHidesDrafts() {
        record.setStatus(PrescriptionStatus.CONFIRMED);
        Prescription draft = Prescription.builder().patientId(patient).status(PrescriptionStatus.DRAFT).build();
        when(repository.findByPatientIdOrderByCreatedAtDesc(patient)).thenReturn(List.of(record, draft));
        assertThat(service.getByPatient(patient, user, "PATIENT")).extracting("id").containsExactly(id);
        verify(patients).requirePatientAccess(patient, user, "PATIENT");
    }

    @Test
    void deniedPatientScopeIsCheckedBeforeReadingHistory() {
        doThrow(new BusinessException(403, "Denied")).when(patients).requirePatientAccess(patient, user, "PATIENT");
        assertDenied(403, () -> service.getByPatient(patient, user, "PATIENT"));
        verify(repository, never()).findByPatientIdOrderByCreatedAtDesc(any());
    }

    @Test
    void doctorWithoutPatientAssignmentCannotReadHistory() {
        doThrow(new BusinessException(403, "Not assigned")).when(patients).requirePatientAccess(patient, doctor, "DOCTOR");
        assertDenied(403, () -> service.getByPatient(patient, doctor, "DOCTOR"));
        verify(repository, never()).findByPatientIdOrderByCreatedAtDesc(any());
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,PATIENT", "STAFF,PATIENT", "LAB_TECHNICIAN,PATIENT",
            "ADMIN,CONSULTATION", "STAFF,CONSULTATION", "LAB_TECHNICIAN,CONSULTATION"})
    void operationalRolesHaveNoClinicalHistoryBypass(String role, String scope) {
        assertDenied(403, () -> {
            if (scope.equals("PATIENT")) service.getByPatient(patient, user, role);
            else service.getByConsultation(consultation, user, role);
        });
        verify(repository, never()).findByPatientIdOrderByCreatedAtDesc(any());
        verify(repository, never()).findByConsultationId(any());
        verifyNoInteractions(patients);
    }

    @Test
    void providerOutageIsNotReportedAsAnEmptyConsultationPrescriptionList() {
        record.setStatus(PrescriptionStatus.CONFIRMED);
        when(repository.findByConsultationId(consultation)).thenReturn(List.of(record));
        doThrow(new BusinessException(503, "Unavailable")).when(patients).requirePatientAccess(patient, user, "PATIENT");
        assertDenied(503, () -> service.getByConsultation(consultation, user, "PATIENT"));
    }

    private CreatePrescriptionRequest request() {
        return CreatePrescriptionRequest.builder().consultationId(consultation).patientId(patient).doctorId(doctor)
                .items(List.of(PrescriptionItemRequest.builder().medicineCode("PARA500")
                        .medicineName("Paracetamol").quantity(6).build())).build();
    }

    private void assertDenied(int status, Runnable command) {
        assertThatThrownBy(command::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(status));
    }
}
