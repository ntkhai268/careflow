package com.careflow.consultation.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.consultation.client.AppointmentClient;
import com.careflow.consultation.client.DirectoryClient;
import com.careflow.consultation.dto.request.UpdateConsultationStatusRequest;
import com.careflow.consultation.mapper.ConsultationMapper;
import com.careflow.consultation.model.Consultation;
import com.careflow.consultation.model.ConsultationStatus;
import com.careflow.consultation.repository.ConsultationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConsultationResumeGuardTest {
    @Test
    void neitherResumeNorLegacyStatusCanSkipPendingResults() {
        UUID id = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        Consultation consultation = Consultation.builder().patientId(UUID.randomUUID()).doctorId(doctor)
                .status(ConsultationStatus.AWAITING_CLS).build();
        consultation.setId(id);
        ConsultationRepository repository = mock(ConsultationRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(consultation));
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        var labs = mock(com.careflow.consultation.client.ConsultationLabClient.class);
        when(labs.orders(id)).thenReturn(com.careflow.common.dto.ApiResponse.success(java.util.List.of(
                new com.careflow.consultation.client.ConsultationLabClient.Order(UUID.randomUUID(), id, consultation.getPatientId(), consultation.getStatus() == ConsultationStatus.AWAITING_CLS ? "ORDERED" : "RESULT_AVAILABLE"))));
        ConsultationService service = new ConsultationService(repository, new ConsultationMapper(), rabbit,
                mock(AppointmentClient.class), mock(com.careflow.consultation.client.ConsultationQueueClient.class), mock(DirectoryClient.class), mock(com.careflow.consultation.handover.DoctorWorkloadLock.class), labs);

        assertThatThrownBy(() -> service.resume(id, doctor, "DOCTOR"))
                .isInstanceOf(BusinessException.class).extracting("status").isEqualTo(409);
        UpdateConsultationStatusRequest request = UpdateConsultationStatusRequest.builder()
                .status(ConsultationStatus.IN_PROGRESS).build();
        assertThatThrownBy(() -> service.updateStatus(id, request, doctor, "DOCTOR"))
                .isInstanceOf(BusinessException.class).extracting("status").isEqualTo(409);
        assertThat(consultation.getStatus()).isEqualTo(ConsultationStatus.AWAITING_CLS);
        verify(repository, never()).save(any());
        verifyNoInteractions(rabbit);
    }

    @Test
    void assignedDoctorCanResumeWhenResultsAreReadyForReview() {
        UUID id = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        Consultation consultation = Consultation.builder().patientId(UUID.randomUUID()).doctorId(doctor)
                .status(ConsultationStatus.AWAITING_REVIEW).build();
        consultation.setId(id);
        ConsultationRepository repository = mock(ConsultationRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(consultation));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var labs = mock(com.careflow.consultation.client.ConsultationLabClient.class);
        when(labs.orders(id)).thenReturn(com.careflow.common.dto.ApiResponse.success(java.util.List.of(
                new com.careflow.consultation.client.ConsultationLabClient.Order(UUID.randomUUID(), id, consultation.getPatientId(), consultation.getStatus() == ConsultationStatus.AWAITING_CLS ? "ORDERED" : "RESULT_AVAILABLE"))));
        ConsultationService service = new ConsultationService(repository, new ConsultationMapper(), mock(RabbitTemplate.class),
                mock(AppointmentClient.class), mock(com.careflow.consultation.client.ConsultationQueueClient.class), mock(DirectoryClient.class), mock(com.careflow.consultation.handover.DoctorWorkloadLock.class), labs);

        assertThat(service.resume(id, doctor, "DOCTOR").getStatus()).isEqualTo(ConsultationStatus.IN_PROGRESS);
        verify(repository).save(consultation);
    }
}
