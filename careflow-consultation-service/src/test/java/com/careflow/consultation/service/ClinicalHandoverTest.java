package com.careflow.consultation.service;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.consultation.client.*;
import com.careflow.consultation.client.dto.DoctorProfileResponse;
import com.careflow.consultation.handover.*;
import com.careflow.consultation.mapper.ConsultationMapper;
import com.careflow.consultation.model.*;
import com.careflow.consultation.repository.ConsultationRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ClinicalHandoverTest {
    final UUID a=UUID.randomUUID(), b=UUID.randomUUID(), coordinator=UUID.randomUUID(), outsider=UUID.randomUUID();
    final ConsultationRepository consultations=mock(ConsultationRepository.class);
    final HandoverRepository requests=mock(HandoverRepository.class);
    final CoordinatorRepository grants=mock(CoordinatorRepository.class);
    final CoordinatorAuditRepository audit=mock(CoordinatorAuditRepository.class);
    final DirectoryClient directory=mock(DirectoryClient.class);
    final ConsultationQueueClient queue=mock(ConsultationQueueClient.class);
    final DoctorWorkloadLock workload=mock(DoctorWorkloadLock.class);
    final HandoverService service=new HandoverService(consultations,requests,grants,audit,directory,workload,queue,new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
    final Consultation ca=Consultation.builder().patientId(UUID.randomUUID()).doctorId(a).doctorName("Author A").status(ConsultationStatus.IN_PROGRESS).build();
    final Map<UUID,Handover> stored=new HashMap<>();
    @BeforeEach void setup(){
        ca.setId(UUID.randomUUID());ca.setAppointmentId(UUID.randomUUID());
        when(queue.ticket(ca.getAppointmentId())).thenReturn(ApiResponse.success(new ConsultationQueueClient.TicketContext(UUID.randomUUID(),ca.getAppointmentId(),ca.getPatientId(),"IN_PROGRESS","ROOM-01")));
        when(consultations.findByIdForUpdate(ca.getId())).thenReturn(Optional.of(ca));
        when(consultations.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        for(UUID id:List.of(a,b,coordinator)) when(directory.getDoctorByUserId(id)).thenReturn(ApiResponse.success(profile(id,"ROOM-01")));
        when(directory.getDoctorByUserId(outsider)).thenReturn(ApiResponse.success(profile(outsider,"ROOM-02")));
        when(requests.findByConsultationIdOrderByCreatedAtDesc(ca.getId())).thenAnswer(i->new ArrayList<>(stored.values()));
        when(requests.saveAndFlush(any())).thenAnswer(i->{Handover h=i.getArgument(0);if(h.getId()==null)h.setId(UUID.randomUUID());stored.put(h.getId(),h);return h;});
        when(requests.findConsultationId(any())).thenAnswer(i->stored.containsKey(i.getArgument(0))?Optional.of(ca.getId()):Optional.empty());
        when(requests.findById(any())).thenAnswer(i->Optional.ofNullable(stored.get(i.getArgument(0))));
    }
    DoctorProfileResponse profile(UUID id,String room){return DoctorProfileResponse.builder().userId(id).fullName("Doctor "+id).departmentCode("NOI").assignedRoomId(room).isActive(true).build();}
    HandoverRequests.Create payload(UUID receiver){return new HandoverRequests.Create(ca.getId(),receiver,"End shift","Stable","No known allergy","Await assessment","Continue clinical examination");}
    Handover create(){return service.request(payload(b),a,"DOCTOR");}
    void denied(int expected,Runnable run){assertThatThrownBy(run::run).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(expected));}
    @ParameterizedTest @ValueSource(strings={"ADMIN","PATIENT","STAFF","LAB_TECHNICIAN"}) void rolesCannotSendOrReceive(String role){denied(403,()->service.request(payload(b),a,role));var h=create();denied(403,()->service.decide(h.getId(),"accept","Read",b,role));assertThat(ca.getResponsibleDoctorId()).isNull();}
    @Test void requestDoesNotTransferAndReplayDoesNotDuplicate(){var h=create();assertThat(ca.currentDoctorId()).isEqualTo(a);assertThat(service.request(payload(b),a,"DOCTOR").getId()).isEqualTo(h.getId());assertThat(stored).hasSize(1);}
    @Test void conflictingPendingRequestIsRejected(){create();denied(409,()->service.request(payload(coordinator),a,"DOCTOR"));}
    @Test void cannotSendToSelf(){denied(422,()->service.request(payload(a),a,"DOCTOR"));}
    @Test void differentRoomIsRejected(){denied(422,()->service.request(payload(outsider),a,"DOCTOR"));}
    @Test void unassignedDoctorCannotRequest(){denied(403,()->service.request(payload(b),coordinator,"DOCTOR"));}
    @Test void inactiveReceiverIsRejected(){var p=profile(b,"ROOM-01");p.setIsActive(false);when(directory.getDoctorByUserId(b)).thenReturn(ApiResponse.success(p));denied(422,this::create);}
    @Test void missingDirectoryFailsClosed(){when(directory.getDoctorByUserId(b)).thenReturn(null);denied(503,this::create);assertThat(stored).isEmpty();}
    @ParameterizedTest @ValueSource(strings={"COMPLETED","CANCELLED","TRANSFERRED"}) void closedCaseCannotBeHandedOver(String status){ca.setStatus(ConsultationStatus.valueOf(status));denied(409,this::create);}
    @Test void onlyReceiverCanAccept(){var h=create();denied(403,()->service.decide(h.getId(),"accept","Read",a,"DOCTOR"));assertThat(ca.currentDoctorId()).isEqualTo(a);}
    @Test void acceptMovesResponsibilityAndKeepsOriginalAuthor(){var h=create();service.decide(h.getId(),"accept","Read and accepted",b,"DOCTOR");assertThat(ca.currentDoctorId()).isEqualTo(b);assertThat(ca.getDoctorId()).isEqualTo(a);assertThat(ca.getDoctorName()).isEqualTo("Author A");var response=new ConsultationMapper().toResponse(ca);assertThat(response.getDoctorId()).isEqualTo(b);assertThat(response.getAuthoredByDoctorId()).isEqualTo(a);assertThat(h.getDecidedBy()).isEqualTo(b);assertThat(h.getDecidedAt()).isNotNull();assertThat(h.getClinicalSnapshot()).contains("Author A");verify(workload).acquire(b);}
    @Test void acceptanceReplayIsIdempotent(){var h=create();service.decide(h.getId(),"accept","Read",b,"DOCTOR");service.decide(h.getId(),"accept","Read",b,"DOCTOR");verify(consultations,times(1)).saveAndFlush(ca);}
    @Test void busyReceiverCannotAccept(){var h=create();when(consultations.findCurrentByDoctorAndStatus(b,ConsultationStatus.IN_PROGRESS)).thenReturn(List.of(new Consultation()));denied(409,()->service.decide(h.getId(),"accept","Read",b,"DOCTOR"));assertThat(ca.currentDoctorId()).isEqualTo(a);assertThat(h.getStatus()).isEqualTo(Handover.Status.PENDING);}
    @Test void rejectAndCancelDoNotChangeResponsibility(){var h=create();service.decide(h.getId(),"reject","Cannot cover",b,"DOCTOR");assertThat(ca.currentDoctorId()).isEqualTo(a);denied(409,()->service.decide(h.getId(),"accept","Read",b,"DOCTOR"));}
    @Test void cancelPreventsAcceptance(){var h=create();service.decide(h.getId(),"cancel","Continue own care",a,"DOCTOR");denied(409,()->service.decide(h.getId(),"accept","Read",b,"DOCTOR"));assertThat(ca.currentDoctorId()).isEqualTo(a);}
    @Test void receiverCannotCancelInsteadOfReject(){var h=create();denied(403,()->service.decide(h.getId(),"cancel","Cancel",b,"DOCTOR"));}
    @Test void caseClosedWhilePendingCannotBeAccepted(){var h=create();ca.setStatus(ConsultationStatus.COMPLETED);denied(409,()->service.decide(h.getId(),"accept","Read",b,"DOCTOR"));}
    @Test void changedAssignmentCannotBeAccepted(){var h=create();when(directory.getDoctorByUserId(b)).thenReturn(ApiResponse.success(profile(b,"ROOM-02")));denied(409,()->service.decide(h.getId(),"accept","Read",b,"DOCTOR"));}
    @Test void changedOwnerCannotBeAccepted(){var h=create();ca.setResponsibleDoctorId(coordinator);denied(409,()->service.decide(h.getId(),"accept","Read",b,"DOCTOR"));}
    @Test void coordinatorCanInitiateButCannotSignForReceiver(){when(grants.existsByDoctorIdAndActiveTrue(coordinator)).thenReturn(true);var h=service.request(payload(b),coordinator,"DOCTOR");assertThat(h.isExceptional()).isTrue();assertThat(h.getFromDoctorId()).isEqualTo(a);assertThat(h.getInitiatedBy()).isEqualTo(coordinator);denied(403,()->service.decide(h.getId(),"accept","Read",coordinator,"DOCTOR"));service.decide(h.getId(),"accept","Accepted",b,"DOCTOR");assertThat(ca.currentDoctorId()).isEqualTo(b);}
    @Test void revokedCoordinatorCannotCompleteExceptionalTakeover(){when(grants.existsByDoctorIdAndActiveTrue(coordinator)).thenReturn(true);var h=service.request(payload(b),coordinator,"DOCTOR");when(grants.existsByDoctorIdAndActiveTrue(coordinator)).thenReturn(false);denied(409,()->service.decide(h.getId(),"accept","Read",b,"DOCTOR"));}
    @Test void oldDoctorLosesClinicalReadAndWriteAfterAcceptance(){var h=create();service.decide(h.getId(),"accept","Accepted",b,"DOCTOR");when(consultations.findById(ca.getId())).thenReturn(Optional.of(ca));var clinical=new ConsultationService(consultations,new ConsultationMapper(),mock(RabbitTemplate.class),mock(AppointmentClient.class),mock(ConsultationQueueClient.class),directory,workload, mock(com.careflow.consultation.client.ConsultationLabClient.class));denied(403,()->clinical.getConsultation(ca.getId(),a,"DOCTOR"));denied(403,()->clinical.waitForResults(ca.getId(),a,"DOCTOR"));assertThat(clinical.getConsultation(ca.getId(),b,"DOCTOR").getId()).isEqualTo(ca.getId());}
    @Test void doctorCannotGrantOwnCoordinatorPrivilege(){denied(403,()->service.grant(new HandoverRequests.Grant(a,"Self grant"),a,"DOCTOR"));verifyNoInteractions(audit);}
    @Test void administrativeGrantHasAuditAndDoesNotTransferCare(){when(grants.save(any())).thenAnswer(i->i.getArgument(0));var result=service.grant(new HandoverRequests.Grant(coordinator,"Hospital authorization"),UUID.randomUUID(),"ADMIN");assertThat(result.isActive()).isTrue();verify(audit).save(argThat(entry->entry.isGranted()&&entry.getDoctorId().equals(coordinator)));assertThat(ca.currentDoctorId()).isEqualTo(a);}
    @Test void scopedCoordinatorCanCancelAnUnreachablePendingRequest(){var h=create();when(grants.existsByDoctorIdAndActiveTrue(coordinator)).thenReturn(true);service.decide(h.getId(),"cancel","Receiver unavailable; new assignment required",coordinator,"DOCTOR");assertThat(h.getDecidedBy()).isEqualTo(coordinator);assertThat(h.getStatus()).isEqualTo(Handover.Status.CANCELLED);assertThat(ca.currentDoctorId()).isEqualTo(a);}
    @Test void coordinatorCannotCancelAnotherRoomsRequest(){var h=create();when(grants.existsByDoctorIdAndActiveTrue(outsider)).thenReturn(true);denied(403,()->service.decide(h.getId(),"cancel","No access",outsider,"DOCTOR"));assertThat(h.getStatus()).isEqualTo(Handover.Status.PENDING);}

    @Test void profileChangeCannotMoveAVisitToAnotherQueueRoom(){when(directory.getDoctorByUserId(a)).thenReturn(ApiResponse.success(profile(a,"ROOM-02")));when(directory.getDoctorByUserId(b)).thenReturn(ApiResponse.success(profile(b,"ROOM-02")));denied(422,this::create);assertThat(stored).isEmpty();}
    @Test void queueOutageDoesNotPermitHandover(){when(queue.ticket(ca.getAppointmentId())).thenReturn(null);denied(503,this::create);assertThat(ca.currentDoctorId()).isEqualTo(a);}
    @Test void changedQueueRoomCannotBeAccepted(){var h=create();when(queue.ticket(ca.getAppointmentId())).thenReturn(ApiResponse.success(new ConsultationQueueClient.TicketContext(UUID.randomUUID(),ca.getAppointmentId(),ca.getPatientId(),"IN_PROGRESS","ROOM-02")));denied(409,()->service.decide(h.getId(),"accept","Read",b,"DOCTOR"));assertThat(h.getStatus()).isEqualTo(Handover.Status.PENDING);}

}
