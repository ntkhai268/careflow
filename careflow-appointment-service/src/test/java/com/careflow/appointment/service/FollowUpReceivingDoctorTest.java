package com.careflow.appointment.service;

import com.careflow.appointment.client.*;
import com.careflow.appointment.client.dto.*;
import com.careflow.appointment.dto.request.CreateAppointmentRequest;
import com.careflow.appointment.model.Appointment;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class FollowUpReceivingDoctorTest {
    @Test
    void receivingDoctorCanKeepFollowUpButMustStillMatchActiveRoomAssignment() {
        var directory = mock(DirectoryClient.class);
        var appointments = mock(AppointmentRepository.class);
        var events = mock(AppointmentEventService.class);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        when(directory.getDepartmentByCode("NOI_TONG_QUAT")).thenReturn(ApiResponse.success(
                DepartmentResponse.builder().id(UUID.randomUUID()).isActive(true).build()));
        when(directory.getAllRooms()).thenReturn(ApiResponse.success(List.of(RoomResponse.builder()
                .id("ROOM-01").departmentCode("NOI_TONG_QUAT").roomType("CONSULTATION").isActive(true).build())));
        when(directory.getDoctors("NOI_TONG_QUAT")).thenReturn(ApiResponse.success(List.of(
                DoctorProfileResponse.builder().userId(a).departmentCode("NOI_TONG_QUAT")
                        .assignedRoomId("ROOM-01").isActive(true).primaryForBooking(true).build(),
                DoctorProfileResponse.builder().userId(b).departmentCode("NOI_TONG_QUAT")
                        .assignedRoomId("ROOM-01").isActive(true).primaryForBooking(false).build())));
        var receiver = DoctorProfileResponse.builder().userId(b).fullName("Receiving doctor")
                .departmentCode("NOI_TONG_QUAT").assignedRoomId("ROOM-01").isActive(true).build();
        when(directory.getDoctorByUserId(b)).thenReturn(ApiResponse.success(receiver));
        when(appointments.saveAndFlush(any())).thenAnswer(i -> {
            Appointment saved = i.getArgument(0);saved.setId(UUID.randomUUID());return saved;
        });
        var service = new AppointmentService(appointments, events, AppointmentPricingFixture.priced(directory), mock(PatientClient.class), null,
                mock(AppointmentConsultationClient.class));
        var request = CreateAppointmentRequest.builder().patientId(UUID.randomUUID()).patientName("Synthetic")
                .department("NOI_TONG_QUAT").appointmentDate(LocalDate.now().plusDays(2)).timeSlot("08:00-08:30").build();
        var result = service.createAppointment(request, UUID.randomUUID(), "follow-up-test", null, b);
        assertThat(result.getDoctorId()).isEqualTo(b);
        assertThat(result.getDoctorName()).isEqualTo("Receiving doctor");
        assertThat(result.getRoomId()).isEqualTo("ROOM-01");
        for (String invalid : List.of("inactive", "room", "department", "user")) {
            receiver.setIsActive(!invalid.equals("inactive"));
            receiver.setAssignedRoomId(invalid.equals("room") ? "ROOM-02" : "ROOM-01");
            receiver.setDepartmentCode(invalid.equals("department") ? "NHI" : "NOI_TONG_QUAT");
            receiver.setUserId(invalid.equals("user") ? a : b);
            assertThatThrownBy(() -> service.createAppointment(request, UUID.randomUUID(), "denied", null, b))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
        }
        verify(appointments, times(1)).saveAndFlush(any());
        verify(events, times(1)).confirmed(any(), any());
    }
}
