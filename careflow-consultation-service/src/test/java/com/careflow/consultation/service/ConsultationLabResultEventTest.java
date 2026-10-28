package com.careflow.consultation.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.event.EventEnvelope;
import com.careflow.common.exception.BusinessException;
import com.careflow.consultation.client.AppointmentClient;
import com.careflow.consultation.client.ConsultationLabClient;
import com.careflow.consultation.client.ConsultationQueueClient;
import com.careflow.consultation.client.DirectoryClient;
import com.careflow.consultation.handover.DoctorWorkloadLock;
import com.careflow.consultation.mapper.ConsultationMapper;
import com.careflow.consultation.model.Consultation;
import com.careflow.consultation.model.ConsultationStatus;
import com.careflow.consultation.repository.ConsultationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConsultationLabResultEventTest {

    private final ConsultationRepository repository = mock(ConsultationRepository.class);
    private final ConsultationLabClient labs = mock(ConsultationLabClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ConsultationService consultationService;
    private ConsultationLabEventConsumer consumer;

    private final UUID consultationId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consultationService = new ConsultationService(
                repository,
                new ConsultationMapper(),
                mock(RabbitTemplate.class),
                mock(AppointmentClient.class),
                mock(ConsultationQueueClient.class),
                mock(DirectoryClient.class),
                mock(DoctorWorkloadLock.class),
                labs
        );
        consumer = new ConsultationLabEventConsumer(consultationService);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private EventEnvelope validEnvelope(UUID targetConsultationId, UUID targetPatientId) {
        return new EventEnvelope(
                UUID.randomUUID(),
                "AllRequiredResultsAvailable",
                1,
                orderId,
                1,
                Instant.now(),
                "lab-service",
                "corr-1",
                objectMapper.valueToTree(Map.of(
                        "orderId", orderId.toString(),
                        "consultationId", targetConsultationId.toString(),
                        "patientId", targetPatientId.toString()
                ))
        );
    }

    @Test
    void awaitingClsTransitionsToAwaitingReviewWhenAllLabResultsAvailable() {
        Consultation consultation = Consultation.builder()
                .patientId(patientId)
                .doctorId(doctorId)
                .status(ConsultationStatus.AWAITING_CLS)
                .build();
        consultation.setId(consultationId);
        when(repository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(labs.orders(consultationId)).thenReturn(ApiResponse.success(List.of(
                new ConsultationLabClient.Order(orderId, consultationId, patientId, "RESULT_AVAILABLE")
        )));

        consumer.consume(validEnvelope(consultationId, patientId));

        assertThat(consultation.getStatus()).isEqualTo(ConsultationStatus.AWAITING_REVIEW);
        verify(repository).save(consultation);
    }

    @Test
    void duplicateOrLateEventDoesNotOverwriteCompletedOrInProgressConsultation() {
        Consultation consultation = Consultation.builder()
                .patientId(patientId)
                .doctorId(doctorId)
                .status(ConsultationStatus.COMPLETED)
                .build();
        consultation.setId(consultationId);
        when(repository.findById(consultationId)).thenReturn(Optional.of(consultation));

        consumer.consume(validEnvelope(consultationId, patientId));

        assertThat(consultation.getStatus()).isEqualTo(ConsultationStatus.COMPLETED);
        verify(repository, never()).save(any());
        verifyNoInteractions(labs);
    }

    @Test
    void mismatchedPatientIdInEventRejectsTransition() {
        Consultation consultation = Consultation.builder()
                .patientId(patientId)
                .doctorId(doctorId)
                .status(ConsultationStatus.AWAITING_CLS)
                .build();
        consultation.setId(consultationId);
        when(repository.findById(consultationId)).thenReturn(Optional.of(consultation));

        assertThatThrownBy(() -> consumer.consume(validEnvelope(consultationId, UUID.randomUUID())))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(403);

        assertThat(consultation.getStatus()).isEqualTo(ConsultationStatus.AWAITING_CLS);
        verify(repository, never()).save(any());
    }
}
