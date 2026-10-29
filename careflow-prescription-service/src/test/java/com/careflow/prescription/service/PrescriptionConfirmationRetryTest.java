package com.careflow.prescription.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.prescription.config.*;
import com.careflow.prescription.mapper.PrescriptionMapper;
import com.careflow.prescription.model.*;
import com.careflow.prescription.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrescriptionConfirmationRetryTest {
    private final UUID id = UUID.randomUUID(), doctor = UUID.randomUUID(), patient = UUID.randomUUID(), consultation = UUID.randomUUID();
    private final PrescriptionRepository repository = mock(PrescriptionRepository.class);
    private final ConsultationClient consultations = mock(ConsultationClient.class);
    private final AppointmentClient appointments = mock(AppointmentClient.class);
    private final DirectoryClient directory = mock(DirectoryClient.class);
    private final QueueExecutionClient queue = mock(QueueExecutionClient.class);
    private final PrescriptionEventOutbox outbox = mock(PrescriptionEventOutbox.class);
    private final PrescriptionService service = new PrescriptionService(repository, mock(DrugRepository.class),
            new PrescriptionMapper(), consultations, directory, queue, mock(PatientIdentityClient.class),
            new ObjectMapper().findAndRegisterModules(), outbox, mock(PrescriptionAiReleaseGuard.class));
    private Prescription prescription;

    @BeforeEach
    void setUp() {
        prescription = Prescription.builder().consultationId(consultation).patientId(patient).doctorId(doctor)
                .status(PrescriptionStatus.CONFIRMED).build();
        prescription.setId(id);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(prescription));
    }

    @ParameterizedTest
    @EnumSource(value = PrescriptionStatus.class, names = {"CONFIRMED", "DISPENSED"})
    void retryReturnsCurrentStateWithoutAnotherEventOrFollowUp(PrescriptionStatus status) {
        prescription.setStatus(status);
        prescription.setFollowUpDate(java.time.LocalDate.now().plusDays(14));
        when(consultations.getConsultation(consultation)).thenReturn(new ConsultationClient.ConsultationContext(
                consultation, patient, doctor, "COMPLETED"));

        assertThat(service.confirmPrescription(id, doctor, "DOCTOR").getStatus()).isEqualTo(status);
        verify(repository, never()).save(any());
        verifyNoInteractions(appointments, directory, queue, outbox);
    }

    @Test
    void retryCannotBypassChangedConsultationAssignment() {
        when(consultations.getConsultation(consultation)).thenReturn(new ConsultationClient.ConsultationContext(
                consultation, patient, UUID.randomUUID(), "COMPLETED"));
        denied(403);
    }

    @Test
    void retryCannotBypassChangedPatientBinding() {
        when(consultations.getConsultation(consultation)).thenReturn(new ConsultationClient.ConsultationContext(
                consultation, UUID.randomUUID(), doctor, "COMPLETED"));
        denied(403);
    }

    @Test
    void unavailableAuthorityStillDeniesRetry() {
        denied(503);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "TRANSFERRED"})
    void cancelledAuthorityDeniesRetry(String state) {
        when(consultations.getConsultation(consultation)).thenReturn(new ConsultationClient.ConsultationContext(
                consultation, patient, doctor, state));
        denied(400);
    }

    private void denied(int status) {
        assertThatThrownBy(() -> service.confirmPrescription(id, doctor, "DOCTOR"))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus()).isEqualTo(status));
        verify(repository, never()).save(any());
        verifyNoInteractions(appointments, directory, queue, outbox);
    }
}
