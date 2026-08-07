package com.careflow.appointment.service;

import com.careflow.appointment.client.DirectoryClient;
import com.careflow.appointment.client.PatientClient;
import com.careflow.appointment.client.dto.DepartmentResponse;
import com.careflow.appointment.client.dto.DoctorProfileResponse;
import com.careflow.appointment.client.dto.RoomResponse;
import com.careflow.appointment.dto.request.CreateAppointmentRequest;
import com.careflow.appointment.dto.response.AppointmentResponse;
import com.careflow.appointment.model.AppointmentStatus;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentDirectoryResolutionTest {

    @Mock
    private AppointmentRepository appointments;

    @Mock
    private AppointmentEventService events;

    @Mock
    private DirectoryClient directory;

    @Mock
    private PatientClient patientClient;

    @Test
    void appointmentSnapshotsTheActiveDirectoryRoom() {
        UUID directoryDepartmentId = UUID.randomUUID();
        when(directory.getDepartmentByCode("NOI_TONG_QUAT"))
                .thenReturn(ApiResponse.success(DepartmentResponse.builder()
                        .code("NOI_TONG_QUAT").id(directoryDepartmentId).name("Nội tổng quát")
                        .isActive(true).build()));
        when(directory.getAllRooms()).thenReturn(ApiResponse.success(List.of(
                RoomResponse.builder().id("ROOM-DIRECTORY-01").departmentCode("NOI_TONG_QUAT")
                        .displayName("Phòng Directory 01").roomType("CONSULTATION").isActive(true).build())));
        UUID doctorUserId = UUID.randomUUID();
        when(directory.getDoctors("NOI_TONG_QUAT")).thenReturn(ApiResponse.success(List.of(
                DoctorProfileResponse.builder().userId(doctorUserId).fullName("Assigned Doctor")
                        .departmentCode("NOI_TONG_QUAT").assignedRoomId("ROOM-DIRECTORY-01")
                        .isActive(true).build())));
        when(appointments.saveAndFlush(any())).thenAnswer(invocation -> {
            var appointment = invocation.getArgument(0, com.careflow.appointment.model.Appointment.class);
            appointment.setId(UUID.randomUUID());
            return appointment;
        });

        var service = new AppointmentService(appointments, events, directory, patientClient);
        AppointmentResponse response = service.createAppointment(CreateAppointmentRequest.builder()
                .patientId(UUID.randomUUID())
                .patientName("Test Patient")
                .department("NOI_TONG_QUAT")
                .appointmentDate(LocalDate.now().plusDays(1))
                .timeSlot("10:00-10:30")
                .build(), UUID.randomUUID(), "trace-directory", null);

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED.name());
        assertThat(response.getDepartmentId()).isEqualTo(directoryDepartmentId);
        assertThat(response.getRoomId()).isEqualTo("ROOM-DIRECTORY-01");
        assertThat(response.getRoomDisplayName()).isEqualTo("Phòng Directory 01");
        assertThat(response.getDoctorId()).isEqualTo(doctorUserId);
    }

    @Test
    void appointmentFailsClosedWhenAClinicRoomHasMultipleActiveDoctors() {
        UUID directoryDepartmentId = UUID.randomUUID();
        when(directory.getDepartmentByCode("NOI_TONG_QUAT"))
                .thenReturn(ApiResponse.success(DepartmentResponse.builder()
                        .code("NOI_TONG_QUAT").id(directoryDepartmentId).isActive(true).build()));
        when(directory.getAllRooms()).thenReturn(ApiResponse.success(List.of(
                RoomResponse.builder().id("ROOM-01").departmentCode("NOI_TONG_QUAT")
                        .displayName("Phòng 01").roomType("CONSULTATION").isActive(true).build())));
        when(directory.getDoctors("NOI_TONG_QUAT")).thenReturn(ApiResponse.success(List.of(
                DoctorProfileResponse.builder().userId(UUID.randomUUID()).departmentCode("NOI_TONG_QUAT")
                        .assignedRoomId("ROOM-01").isActive(true).build(),
                DoctorProfileResponse.builder().userId(UUID.randomUUID()).departmentCode("NOI_TONG_QUAT")
                        .assignedRoomId("ROOM-01").isActive(true).build())));

        var service = new AppointmentService(appointments, events, directory, patientClient);

        assertThatThrownBy(() -> service.createAppointment(CreateAppointmentRequest.builder()
                        .patientId(UUID.randomUUID())
                        .patientName("Test Patient")
                        .department("NOI_TONG_QUAT")
                        .appointmentDate(LocalDate.now().plusDays(1))
                        .timeSlot("10:00-10:30")
                        .build(), UUID.randomUUID(), "trace-directory", null))
                .isInstanceOfSatisfying(com.careflow.common.exception.BusinessException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(409));
    }

    @Test
    void staffAccessRequiresDirectoryRoomAssignment() {
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();
        var appointment = com.careflow.appointment.model.Appointment.builder()
                .patientId(patientId)
                .ownerUserId(UUID.randomUUID())
                .department(com.careflow.appointment.model.Department.NOI_TONG_QUAT)
                .departmentId(com.careflow.appointment.model.Department.NOI_TONG_QUAT.getId())
                .roomId("ROOM-01")
                .roomDisplayName("Phòng 01")
                .appointmentDate(LocalDate.now().plusDays(1))
                .timeSlot("10:00-10:30")
                .status(AppointmentStatus.CHECKED_IN)
                .build();
        appointment.setId(appointmentId);
        when(appointments.findById(appointmentId)).thenReturn(java.util.Optional.of(appointment));
        when(directory.hasStaffRoomAccess(staffUserId, "ROOM-01"))
                .thenReturn(ApiResponse.success(true));

        var allowed = new AppointmentService(appointments, events, directory, patientClient)
                .getAssignmentAccess(patientId, appointmentId, "ROOM-01", staffUserId, "STAFF");
        assertThat(allowed.isAllowed()).isTrue();

        when(directory.hasStaffRoomAccess(staffUserId, "ROOM-01"))
                .thenReturn(ApiResponse.success(false));
        var denied = new AppointmentService(appointments, events, directory, patientClient)
                .getAssignmentAccess(patientId, appointmentId, "ROOM-01", staffUserId, "STAFF");
        assertThat(denied.isAllowed()).isFalse();
    }
}
