package com.careflow.consultation.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.consultation.client.AppointmentClient;
import com.careflow.consultation.client.DirectoryClient;
import com.careflow.consultation.mapper.ConsultationMapper;
import com.careflow.consultation.model.Consultation;
import com.careflow.consultation.model.ConsultationStatus;
import com.careflow.consultation.repository.ConsultationRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ConsultationReadScopeTest {
    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "ADMIN", "LAB_TECHNICIAN"})
    void operationalRolesDoNotReceiveFullConsultationsByRoleAlone(String role) {
        UUID id = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        Consultation value = Consultation.builder().patientId(UUID.randomUUID()).doctorId(doctor)
                .status(ConsultationStatus.IN_PROGRESS).build();
        value.setId(id);
        ConsultationRepository repository = mock(ConsultationRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(value));
        ConsultationService service = new ConsultationService(repository, new ConsultationMapper(), mock(RabbitTemplate.class),
                mock(AppointmentClient.class), mock(com.careflow.consultation.client.ConsultationQueueClient.class), mock(DirectoryClient.class), mock(com.careflow.consultation.handover.DoctorWorkloadLock.class), mock(com.careflow.consultation.client.ConsultationLabClient.class));

        assertThatThrownBy(() -> service.getConsultation(id, UUID.randomUUID(), role))
                .isInstanceOf(BusinessException.class).extracting("status").isEqualTo(403);
        assertThatThrownBy(() -> service.getConsultationsByDoctor(doctor, UUID.randomUUID(), role))
                .isInstanceOf(BusinessException.class).extracting("status").isEqualTo(403);
        assertThatThrownBy(() -> service.getTodayConsultationsByDoctor(doctor, UUID.randomUUID(), role))
                .isInstanceOf(BusinessException.class).extracting("status").isEqualTo(403);
        verify(repository, never()).findByDoctorIdOrderByCreatedAtDesc(doctor);
    }
    @ParameterizedTest
    @CsvSource({"403,403", "404,403", "503,503"})
    void ownershipDenialIsDistinctFromAuthorityOutage(int upstream, int expected) {
        UUID id=UUID.randomUUID(), appointment=UUID.randomUUID();
        Consultation value=Consultation.builder().appointmentId(appointment).patientId(UUID.randomUUID())
                .doctorId(UUID.randomUUID()).status(ConsultationStatus.IN_PROGRESS).build();
        value.setId(id);
        ConsultationRepository repository=mock(ConsultationRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(value));
        AppointmentClient appointments=mock(AppointmentClient.class);
        feign.Request request=feign.Request.create(feign.Request.HttpMethod.GET,"http://authority/appointment",
                java.util.Map.of(),null,java.nio.charset.StandardCharsets.UTF_8,null);
        when(appointments.getAppointmentById(org.mockito.ArgumentMatchers.eq(appointment),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("PATIENT")))
                .thenThrow(feign.FeignException.errorStatus("ownership",feign.Response.builder().status(upstream).request(request).headers(java.util.Map.of()).build()));
        ConsultationService service=new ConsultationService(repository,new ConsultationMapper(),mock(RabbitTemplate.class),
                appointments,mock(com.careflow.consultation.client.ConsultationQueueClient.class),mock(DirectoryClient.class), mock(com.careflow.consultation.handover.DoctorWorkloadLock.class), mock(com.careflow.consultation.client.ConsultationLabClient.class));
        assertThatThrownBy(()->service.getConsultation(id,UUID.randomUUID(),"PATIENT"))
                .isInstanceOf(BusinessException.class).extracting("status").isEqualTo(expected);
        verify(repository,never()).save(org.mockito.ArgumentMatchers.any());
    }
}
