package com.careflow.appointment.service;
import com.careflow.appointment.client.*;
import com.careflow.appointment.client.dto.*;
import com.careflow.appointment.dto.request.*;
import com.careflow.appointment.model.*;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class AppointmentHandoverTest {
    final AppointmentRepository repo=mock(AppointmentRepository.class);
    final AppointmentConsultationClient clinical=mock(AppointmentConsultationClient.class);
    final DirectoryClient directory=mock(DirectoryClient.class);
    final AppointmentService service=new AppointmentService(repo,mock(AppointmentEventService.class),AppointmentPricingFixture.priced(directory),mock(PatientClient.class),null,clinical);
    final UUID patient=UUID.randomUUID(), old=UUID.randomUUID(), next=UUID.randomUUID();
    @Test void acceptedReceiverCanReadPatientWithoutRewritingBooking(){when(clinical.patientAccess(patient)).thenReturn(ApiResponse.success(new AppointmentConsultationClient.PatientResponsibility(true,List.of())));assertThat(service.getAssignmentAccess(patient,null,null,next,"DOCTOR").isAllowed()).isTrue();verifyNoInteractions(repo);}
    @Test void oldBookingCannotRestoreSupersededDoctorAccess(){var appointment=Appointment.builder().patientId(patient).doctorId(old).status(AppointmentStatus.CONFIRMED).build();appointment.setId(UUID.randomUUID());when(repo.findByPatientIdOrderByAppointmentDateDesc(patient)).thenReturn(List.of(appointment));when(clinical.patientAccess(patient)).thenReturn(ApiResponse.success(new AppointmentConsultationClient.PatientResponsibility(false,List.of(appointment.getId()))));assertThat(service.getAssignmentAccess(patient,null,null,old,"DOCTOR").isAllowed()).isFalse();}
    @Test void missingResponsibilityProviderFailsClosed(){assertThatThrownBy(()->service.getAssignmentAccess(patient,null,null,old,"DOCTOR")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(503));}
    @ParameterizedTest @ValueSource(ints={0,1,2}) void multipleDoctorsRequireExactlyOneExplicitPrimary(int primaries){
        when(directory.getDepartmentByCode("NHI")).thenReturn(ApiResponse.success(DepartmentResponse.builder().id(Department.NHI.getId()).code("NHI").name("Nhi").isActive(true).build()));
        when(directory.getAllRooms()).thenReturn(ApiResponse.success(List.of(RoomResponse.builder().id("ROOM-02").departmentCode("NHI").displayName("Nhi").roomType("CONSULTATION").isActive(true).build())));
        when(directory.getDoctors("NHI")).thenReturn(ApiResponse.success(List.of(DoctorProfileResponse.builder().userId(old).fullName("Primary").departmentCode("NHI").assignedRoomId("ROOM-02").isActive(true).primaryForBooking(primaries>0).build(),DoctorProfileResponse.builder().userId(next).fullName("Handover colleague").departmentCode("NHI").assignedRoomId("ROOM-02").isActive(true).primaryForBooking(primaries>1).build())));
        var request=CreateAppointmentRequest.builder().patientId(patient).patientName("Fixture").department("NHI").appointmentDate(LocalDate.now().plusDays(1)).timeSlot("09:00-09:30").build();
        if(primaries!=1){assertThatThrownBy(()->service.createAppointment(request,UUID.randomUUID(),"trace",null)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(409));verify(repo,never()).saveAndFlush(any());}
        else{when(repo.saveAndFlush(any())).thenAnswer(i->{Appointment a=i.getArgument(0);a.setId(UUID.randomUUID());return a;});assertThat(service.createAppointment(request,UUID.randomUUID(),"trace",null).getDoctorId()).isEqualTo(old);}
    }
    @ParameterizedTest @ValueSource(strings={"IN_PROGRESS","COMPLETED"}) void completionRequiresCommittedCompletedConsultation(String state){
        var appointment=Appointment.builder().patientId(patient).doctorId(old).department(Department.NHI).appointmentDate(LocalDate.now()).timeSlot("09:00-09:30").status(AppointmentStatus.CONFIRMED).build();appointment.setId(UUID.randomUUID());
        when(repo.findById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(clinical.getByAppointment(appointment.getId())).thenReturn(ApiResponse.success(List.of(new AppointmentConsultationClient.Context(UUID.randomUUID(),patient,next,state))));
        var request=UpdateAppointmentStatusRequest.builder().status("COMPLETED").build();
        if(!state.equals("COMPLETED")){assertThatThrownBy(()->service.updateAppointmentStatus(appointment.getId(),request,next,"DOCTOR")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(409));assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);}
        else {when(repo.save(appointment)).thenReturn(appointment);assertThat(service.updateAppointmentStatus(appointment.getId(),request,next,"DOCTOR").getStatus()).isEqualTo("COMPLETED");assertThat(appointment.getDoctorId()).isEqualTo(old);assertThat(service.updateAppointmentStatus(appointment.getId(),request,next,"DOCTOR").getStatus()).isEqualTo("COMPLETED");}
    }

}
