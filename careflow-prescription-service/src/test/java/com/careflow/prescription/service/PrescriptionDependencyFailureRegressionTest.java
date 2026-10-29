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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PrescriptionDependencyFailureRegressionTest {
    private final UUID doctor = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();
    private final UUID consultation = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();
    private PrescriptionRepository repository;
    private PrescriptionEventOutbox outbox;
    private PrescriptionService service;
    private Prescription prescription;

    @BeforeEach
    void setUp() {
        repository = mock(PrescriptionRepository.class);
        outbox = mock(PrescriptionEventOutbox.class);
        DrugRepository drugs = mock(DrugRepository.class);
        when(drugs.existsByCode("PARA500")).thenReturn(true);
        when(repository.save(any())).thenAnswer(call -> {
            Prescription value = call.getArgument(0);
            if (value.getId() == null) value.setId(UUID.randomUUID());
            return value;
        });
        DirectoryClient directory = mock(DirectoryClient.class);
        when(directory.getActivePharmacyServicePointId()).thenReturn("PHARMACY-MAIN-01");
        service = new PrescriptionService(repository, drugs, new PrescriptionMapper(),
                mock(ConsultationClient.class), directory,
                mock(QueueExecutionClient.class), mock(PatientIdentityClient.class),
                new ObjectMapper().findAndRegisterModules(), outbox, mock(PrescriptionAiReleaseGuard.class));
        prescription = Prescription.builder().consultationId(consultation).patientId(patient)
                .doctorId(doctor).status(PrescriptionStatus.DRAFT).build();
        prescription.setId(id);
        prescription.addItem(PrescriptionItem.builder().medicineCode("PARA500")
                .medicineName("Paracetamol").quantity(6).build());
        when(repository.findById(id)).thenReturn(Optional.of(prescription));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(prescription));
    }

    @Test
    void unavailableConsultationCannotCreateDraft() {
        CreatePrescriptionRequest request = CreatePrescriptionRequest.builder()
                .consultationId(consultation).patientId(patient).doctorId(doctor)
                .items(List.of(item())).build();
        assertUnavailable(() -> service.createPrescription(request, doctor, "DOCTOR"));
    }

    @Test
    void unavailableConsultationCannotConfirmOrPublish() {
        assertUnavailable(() -> service.confirmPrescription(id, doctor, "DOCTOR"));
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.DRAFT);
    }

    @Test
    void unavailableConsultationCannotCancelOriginalThroughAmendment() {
        prescription.setStatus(PrescriptionStatus.CONFIRMED);
        AmendPrescriptionRequest request = AmendPrescriptionRequest.builder().items(List.of(item())).build();
        assertUnavailable(() -> service.amendPrescription(id, request, doctor, "DOCTOR"));
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.CONFIRMED);
    }

    private void assertUnavailable(Runnable command) {
        assertThatThrownBy(command::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(503));
        verify(repository, never()).save(any());
        verifyNoInteractions(outbox);
    }

    private PrescriptionItemRequest item() {
        return PrescriptionItemRequest.builder().medicineCode("PARA500")
                .medicineName("Paracetamol").quantity(6).build();
    }
}
