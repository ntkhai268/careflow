package com.careflow.consultation.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.consultation.client.ConsultationQueueClient;
import com.careflow.consultation.model.ConsultationStatus;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalkInHandoverTest {
    ClinicalHandoverTest f;
    UUID entry;
    @BeforeEach void setup(){
        f=new ClinicalHandoverTest();f.setup();entry=UUID.randomUUID();
        f.ca.setAppointmentId(null);f.ca.setQueueEntryId(entry);f.ca.setDepartmentCode("NOI");f.ca.setStatus(ConsultationStatus.AWAITING_CLS);
        source(f.ca.getPatientId(),f.ca.getId(),"ROOM-01","NOI");
    }
    void source(UUID patient,UUID consultation,String room,String department){when(f.queue.assignmentContext(entry)).thenReturn(ApiResponse.success(new ConsultationQueueClient.AssignmentContext(entry,patient,null,consultation,room,department)));}
    @Test void waitingWalkInCanBeHandedOverWithoutInventingAppointment(){
        var h=f.create();f.service.decide(h.getId(),"accept","Read results",f.b,"DOCTOR");
        assertThat(f.ca.currentDoctorId()).isEqualTo(f.b);assertThat(f.ca.getDoctorId()).isEqualTo(f.a);
        assertThat(f.ca.getAppointmentId()).isNull();assertThat(f.ca.getQueueEntryId()).isEqualTo(entry);
        verify(f.queue,never()).ticket(null);
    }
    @Test void wrongPatientOrVisitCannotAuthorizeHandover(){source(UUID.randomUUID(),f.ca.getId(),"ROOM-01","NOI");f.denied(503,f::create);source(f.ca.getPatientId(),UUID.randomUUID(),"ROOM-01","NOI");f.denied(503,f::create);assertThat(f.stored).isEmpty();}
    @Test void departmentAndRoomAreVerifiedAgainstOriginalTicket(){source(f.ca.getPatientId(),f.ca.getId(),"ROOM-01","OTHER");f.denied(503,f::create);source(f.ca.getPatientId(),f.ca.getId(),"ROOM-02","NOI");f.denied(422,f::create);}
    @Test void acceptanceRechecksSourceInsteadOfTrustingPendingRequest(){var h=f.create();source(f.ca.getPatientId(),f.ca.getId(),"ROOM-02","NOI");f.denied(409,()->f.service.decide(h.getId(),"accept","Read",f.b,"DOCTOR"));assertThat(f.ca.currentDoctorId()).isEqualTo(f.a);}
    @Test void sourceOutageAndMissingOriginalTicketFailClosed(){when(f.queue.assignmentContext(entry)).thenReturn(null);f.denied(503,f::create);f.ca.setQueueEntryId(null);f.denied(422,f::create);}
    @Test void legacyTicketWithoutDepartmentCodeStillRequiresVerifiedRoom(){source(f.ca.getPatientId(),f.ca.getId(),"ROOM-01",null);assertThat(f.create().getRoomId()).isEqualTo("ROOM-01");}
}
