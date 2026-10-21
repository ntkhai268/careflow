package com.careflow.consultation.service;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.PatientOwnershipClient;
import com.careflow.consultation.client.*;
import com.careflow.consultation.dto.request.CreateConsultationRequest;
import com.careflow.consultation.handover.DoctorWorkloadLock;
import com.careflow.consultation.mapper.ConsultationMapper;
import com.careflow.consultation.model.*;
import com.careflow.consultation.repository.ConsultationRepository;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class WalkInConsultationTest {
    final ConsultationRepository repo=mock(ConsultationRepository.class);
    final AppointmentClient appointments=mock(AppointmentClient.class);
    final ConsultationQueueClient queue=mock(ConsultationQueueClient.class);
    final ConsultationLabClient labs=mock(ConsultationLabClient.class);
    final PatientOwnershipClient owners=mock(PatientOwnershipClient.class);
    final ConsultationService service=new ConsultationService(repo,new ConsultationMapper(),mock(RabbitTemplate.class),appointments,queue,mock(DirectoryClient.class),mock(DoctorWorkloadLock.class),labs);
    final UUID patient=UUID.randomUUID(),doctor=UUID.randomUUID(),entry=UUID.randomUUID();
    CreateConsultationRequest request(){return new CreateConsultationRequest(null,entry,patient,doctor);}
    @BeforeEach void setup(){when(repo.save(any())).thenAnswer(call->{Consultation c=call.getArgument(0);if(c.getId()==null)c.setId(UUID.randomUUID());return c;});}
    void called(){when(queue.clinicalContext(entry)).thenReturn(ApiResponse.success(new ConsultationQueueClient.ClinicalContext(entry,patient,null,"Synthetic Patient","ROOM-01","INITIAL","CALLED",doctor)));}
    @Test void walkInStartsAndCompletesWithoutInventingAppointment(){called();var started=service.createConsultation(request(),doctor,"DOCTOR");assertThat(started.getAppointmentId()).isNull();assertThat(started.getQueueEntryId()).isEqualTo(entry);assertThat(started.getPatientName()).isEqualTo("Synthetic Patient");var entity=Consultation.builder().patientId(patient).doctorId(doctor).queueEntryId(entry).status(ConsultationStatus.IN_PROGRESS).build();entity.setId(started.getId());when(repo.findById(entity.getId())).thenReturn(Optional.of(entity));when(labs.orders(entity.getId())).thenReturn(ApiResponse.success(List.of()));assertThat(service.completeConsultation(entity.getId(),doctor,"DOCTOR").getStatus()).isEqualTo(ConsultationStatus.COMPLETED);verifyNoInteractions(appointments);}
    @Test void retryReturnsSameQueueBoundVisitAndAnotherDoctorCannotTakeIt(){var entity=Consultation.builder().patientId(patient).doctorId(doctor).queueEntryId(entry).status(ConsultationStatus.IN_PROGRESS).build();entity.setId(UUID.randomUUID());when(repo.findByQueueEntryId(entry)).thenReturn(Optional.of(entity));assertThat(service.createConsultation(request(),doctor,"DOCTOR").getId()).isEqualTo(entity.getId());verifyNoInteractions(queue,appointments);UUID other=UUID.randomUUID();assertThatThrownBy(()->service.createConsultation(new CreateConsultationRequest(null,entry,patient,other),other,"DOCTOR")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));}
    @Test void noQueueContextFailsClosed(){assertThatThrownBy(()->service.createConsultation(request(),doctor,"DOCTOR")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(503));verify(repo,never()).save(any());verifyNoInteractions(appointments);}
    @Test void linkedOwnerCanReadWalkInAndRevokedOwnerCannot(){ReflectionTestUtils.setField(service,"currentOwners",owners);var entity=Consultation.builder().patientId(patient).doctorId(doctor).queueEntryId(entry).status(ConsultationStatus.COMPLETED).build();entity.setId(UUID.randomUUID());when(repo.findById(entity.getId())).thenReturn(Optional.of(entity));UUID owner=UUID.randomUUID();assertThat(service.getConsultation(entity.getId(),owner,"PATIENT").getPatientId()).isEqualTo(patient);verify(owners).require(patient,owner);doThrow(new BusinessException(403,"Revoked")).when(owners).require(patient,owner);assertThatThrownBy(()->service.getConsultation(entity.getId(),owner,"PATIENT")).isInstanceOf(BusinessException.class);verifyNoInteractions(appointments);}
}
