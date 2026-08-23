package com.careflow.prescription.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.event.EventEnvelope;
import com.careflow.prescription.config.AppointmentClient;
import com.careflow.prescription.config.ConsultationClient;
import com.careflow.prescription.config.DirectoryClient;
import com.careflow.prescription.config.PatientIdentityClient;
import com.careflow.prescription.config.QueueExecutionClient;
import com.careflow.prescription.dto.request.AmendPrescriptionRequest;
import com.careflow.prescription.dto.response.PrescriptionResponse;
import com.careflow.prescription.mapper.PrescriptionMapper;
import com.careflow.prescription.model.Prescription;
import com.careflow.prescription.model.PrescriptionItem;
import com.careflow.prescription.model.PrescriptionStatus;
import com.careflow.prescription.repository.DrugRepository;
import com.careflow.prescription.repository.PrescriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrescriptionServiceTest {

    private static final UUID PRESCRIPTION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REPLACEMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DOCTOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PATIENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CONSULTATION_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID STAFF_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private DrugRepository drugRepository;
    @Mock private PrescriptionMapper prescriptionMapper;
    @Mock private ConsultationClient consultationClient;
    @Mock private AppointmentClient appointmentClient;
    @Mock private DirectoryClient directoryClient;
    @Mock private QueueExecutionClient queueExecutionClient;
    @Mock private PatientIdentityClient patientIdentityClient;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private PrescriptionService prescriptionService;

    @Test
    void doctorCanCancelDraftAndCancellationIsPublished() {
        Prescription draft = prescription(PrescriptionStatus.DRAFT);
        when(prescriptionRepository.findById(PRESCRIPTION_ID)).thenReturn(Optional.of(draft));
        when(prescriptionRepository.save(draft)).thenReturn(draft);
        when(prescriptionMapper.toResponse(draft)).thenReturn(new PrescriptionResponse());
        when(objectMapper.valueToTree(any())).thenReturn(JsonNodeFactory.instance.objectNode());

        prescriptionService.cancelPrescription(
                PRESCRIPTION_ID, "Sai liều thuốc", DOCTOR_ID, AppConstants.ROLE_DOCTOR);

        assertThat(draft.getStatus()).isEqualTo(PrescriptionStatus.CANCELLED);
        assertThat(draft.getCancellationReason()).isEqualTo("Sai liều thuốc");
        assertThat(draft.getCancelledByUserId()).isEqualTo(DOCTOR_ID);
        verify(rabbitTemplate).convertAndSend(eq(AppConstants.EXCHANGE_PRESCRIPTION),
                eq(AppConstants.RK_PRESCRIPTION_CANCELLED), any(EventEnvelope.class));
    }

    @Test
    void confirmedPrescriptionCanOnlyBeReplacedThroughAmendment() {
        Prescription original = prescription(PrescriptionStatus.CONFIRMED);
        when(prescriptionRepository.findById(PRESCRIPTION_ID)).thenReturn(Optional.of(original));
        when(drugRepository.existsByCode("PARA500")).thenReturn(true);
        when(prescriptionMapper.toItemEntity(any())).thenReturn(PrescriptionItem.builder()
                .medicineName("Paracetamol")
                .medicineCode("PARA500")
                .build());
        when(prescriptionMapper.toResponse(any())).thenReturn(new PrescriptionResponse());
        when(objectMapper.valueToTree(any())).thenReturn(JsonNodeFactory.instance.objectNode());
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(invocation -> {
            Prescription value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(REPLACEMENT_ID);
            return value;
        });

        AmendPrescriptionRequest request = AmendPrescriptionRequest.builder()
                .diagnosis("Updated diagnosis")
                .notes("Updated instruction")
                .items(List.of(new com.careflow.prescription.dto.request.PrescriptionItemRequest()))
                .build();
        request.getItems().get(0).setMedicineName("Paracetamol");
        request.getItems().get(0).setMedicineCode("PARA500");

        prescriptionService.amendPrescription(
                PRESCRIPTION_ID, request, DOCTOR_ID, AppConstants.ROLE_DOCTOR);

        assertThat(original.getStatus()).isEqualTo(PrescriptionStatus.CANCELLED_BY_AMENDMENT);
        assertThat(original.getCancellationReason()).contains(REPLACEMENT_ID.toString());
        verify(prescriptionRepository, times(2)).save(any(Prescription.class));
    }

    @Test
    void doctorCannotCancelAnotherDoctorsPrescription() {
        Prescription draft = prescription(PrescriptionStatus.DRAFT);
        when(prescriptionRepository.findById(PRESCRIPTION_ID)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> prescriptionService.cancelPrescription(
                PRESCRIPTION_ID,
                "Không còn cần thuốc",
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                AppConstants.ROLE_DOCTOR))
                .hasMessageContaining("assigned");
        verify(prescriptionRepository, never()).save(any());
    }

    @Test
    void pharmacyStaffCanReadPrescriptionAssignedToTheirQueue() {
        Prescription confirmed = prescription(PrescriptionStatus.CONFIRMED);
        confirmed.setDispensingServicePointId("PHARMACY-MAIN-01");
        QueueExecutionClient.QueueEntryState entry = pharmacyEntry(PRESCRIPTION_ID, PATIENT_ID);
        PrescriptionResponse response = new PrescriptionResponse();
        when(prescriptionRepository.findById(PRESCRIPTION_ID)).thenReturn(Optional.of(confirmed));
        when(queueExecutionClient.getPharmacyEntry(PRESCRIPTION_ID, STAFF_ID, AppConstants.ROLE_STAFF))
                .thenReturn(entry);
        when(prescriptionMapper.toResponse(confirmed)).thenReturn(response);

        assertThat(prescriptionService.getPrescription(
                PRESCRIPTION_ID, STAFF_ID, AppConstants.ROLE_STAFF)).isSameAs(response);
    }

    @Test
    void pharmacyStaffCannotReadPrescriptionFromMismatchedQueueEntry() {
        Prescription confirmed = prescription(PrescriptionStatus.CONFIRMED);
        confirmed.setDispensingServicePointId("PHARMACY-MAIN-01");
        QueueExecutionClient.QueueEntryState entry = pharmacyEntry(
                PRESCRIPTION_ID, UUID.fromString("77777777-7777-7777-7777-777777777777"));
        when(prescriptionRepository.findById(PRESCRIPTION_ID)).thenReturn(Optional.of(confirmed));
        when(queueExecutionClient.getPharmacyEntry(PRESCRIPTION_ID, STAFF_ID, AppConstants.ROLE_STAFF))
                .thenReturn(entry);

        assertThatThrownBy(() -> prescriptionService.getPrescription(
                PRESCRIPTION_ID, STAFF_ID, AppConstants.ROLE_STAFF))
                .hasMessageContaining("not allowed");
    }

    private QueueExecutionClient.QueueEntryState pharmacyEntry(UUID prescriptionId, UUID patientId) {
        QueueExecutionClient.QueueEntryState entry = new QueueExecutionClient.QueueEntryState();
        entry.setPrescriptionId(prescriptionId);
        entry.setPatientId(patientId);
        entry.setType("PHARMACY_DISPENSING");
        entry.setServicePointId("PHARMACY-MAIN-01");
        entry.setQueueStatus("QUEUED");
        return entry;
    }

    private Prescription prescription(PrescriptionStatus status) {
        Prescription value = Prescription.builder()
                .consultationId(CONSULTATION_ID)
                .patientId(PATIENT_ID)
                .doctorId(DOCTOR_ID)
                .status(status)
                .build();
        value.setId(PRESCRIPTION_ID);
        return value;
    }
}
