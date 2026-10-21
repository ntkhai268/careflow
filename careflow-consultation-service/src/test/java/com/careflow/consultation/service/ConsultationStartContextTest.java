package com.careflow.consultation.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.consultation.client.*;
import com.careflow.consultation.client.dto.AppointmentResponse;
import com.careflow.consultation.dto.request.CreateConsultationRequest;
import com.careflow.consultation.mapper.ConsultationMapper;
import com.careflow.consultation.repository.ConsultationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConsultationStartContextTest {
    final ConsultationRepository repository=mock(ConsultationRepository.class);
    final AppointmentClient appointments=mock(AppointmentClient.class);
    final ConsultationQueueClient queue=mock(ConsultationQueueClient.class);
    final DirectoryClient directory=mock(DirectoryClient.class);
    final ConsultationService service=new ConsultationService(repository,new ConsultationMapper(),mock(RabbitTemplate.class),appointments,queue,directory,mock(com.careflow.consultation.handover.DoctorWorkloadLock.class), mock(com.careflow.consultation.client.ConsultationLabClient.class));
    final UUID appointment=UUID.randomUUID(),patient=UUID.randomUUID(),doctor=UUID.randomUUID();
    CreateConsultationRequest request(){return new CreateConsultationRequest(appointment,null,patient,doctor);}
    void ticket(String status,UUID owner){when(queue.ticket(appointment)).thenReturn(ApiResponse.success(new ConsultationQueueClient.TicketContext(UUID.randomUUID(),appointment,owner,status,"ROOM-01")));}
    void denied(int status){assertThatThrownBy(()->service.createConsultation(request(),doctor,"DOCTOR")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(status));verify(repository,never()).save(any());}
    @ParameterizedTest @ValueSource(strings={"TICKET_ISSUED","CHECKED_IN","CANCELLED","NO_SHOW"})
    void notCalledNeverCreatesConsultation(String status){ticket(status,patient);denied(409);verifyNoInteractions(appointments);}
    @Test void wrongPatientNeverCreatesConsultation(){ticket("CALLED",UUID.randomUUID());denied(403);verifyNoInteractions(appointments);}
    @Test void missingQueueAuthorityFailsClosed(){denied(503);verifyNoInteractions(appointments);}
    @Test void missingAppointmentAuthorityFailsClosed(){ticket("CALLED",patient);denied(503);}
    @Test void wrongDoctorNeverCreatesConsultation(){ticket("CALLED",patient);when(appointments.getAppointmentById(appointment,doctor,"DOCTOR")).thenReturn(ApiResponse.success(AppointmentResponse.builder().id(appointment).patientId(patient).doctorId(UUID.randomUUID()).status("CONFIRMED").build()));denied(403);}
    @Test void assignedDoctorStartsCalledPatientWithoutChangingAppointmentToInvalidStatus(){
        ticket("CALLED",patient);when(appointments.getAppointmentById(appointment,doctor,"DOCTOR")).thenReturn(ApiResponse.success(AppointmentResponse.builder().id(appointment).patientId(patient).doctorId(doctor).status("CONFIRMED").build()));
        when(repository.save(any())).thenAnswer(call->{com.careflow.consultation.model.Consultation value=call.getArgument(0);value.setId(UUID.randomUUID());return value;});
        assertThat(service.createConsultation(request(),doctor,"DOCTOR").getId()).isNotNull();
        verify(appointments,never()).updateAppointmentStatus(any(),any(),any(),any());
    }
}
