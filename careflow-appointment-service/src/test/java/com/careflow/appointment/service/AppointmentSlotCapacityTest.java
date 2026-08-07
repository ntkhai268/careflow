package com.careflow.appointment.service;

import com.careflow.appointment.client.DirectoryClient;
import com.careflow.appointment.client.PatientClient;
import com.careflow.appointment.client.dto.DepartmentResponse;
import com.careflow.appointment.client.dto.DoctorProfileResponse;
import com.careflow.appointment.client.dto.RoomResponse;
import com.careflow.appointment.dto.request.CreateAppointmentRequest;
import com.careflow.appointment.dto.response.TimeSlotAvailabilityResponse;
import com.careflow.appointment.model.Appointment;
import com.careflow.appointment.model.AppointmentSlotLock;
import com.careflow.appointment.model.AppointmentStatus;
import com.careflow.appointment.model.Department;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.appointment.repository.AppointmentSlotLockRepository;
import com.careflow.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentSlotCapacityTest {

    private static final String ROOM_ID = "ROOM-01";
    private static final UUID DOCTOR_ID = UUID.randomUUID();

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentEventService appointmentEvents;

    @Mock
    private DirectoryClient directoryClient;

    @Mock
    private PatientClient patientClient;

    @Mock
    private AppointmentSlotLockRepository slotLockRepository;

    @InjectMocks
    private AppointmentService appointmentService;

    @BeforeEach
    void setUpDirectory() {
        when(directoryClient.getDepartmentByCode(Department.NOI_TONG_QUAT.name()))
                .thenReturn(ApiResponse.success(DepartmentResponse.builder()
                        .code(Department.NOI_TONG_QUAT.name())
                        .id(Department.NOI_TONG_QUAT.getId())
                        .name("Nội tổng quát")
                        .isActive(true)
                        .build()));
        when(directoryClient.getAllRooms()).thenReturn(ApiResponse.success(List.of(
                RoomResponse.builder()
                        .id(ROOM_ID)
                        .departmentCode(Department.NOI_TONG_QUAT.name())
                        .displayName("Phòng 01")
                        .roomType("CONSULTATION")
                        .isActive(true)
                        .build())));
        when(directoryClient.getDoctors(Department.NOI_TONG_QUAT.name()))
                .thenReturn(ApiResponse.success(List.of(
                        DoctorProfileResponse.builder()
                                .userId(DOCTOR_ID)
                                .fullName("Bác sĩ Nội")
                                .departmentCode(Department.NOI_TONG_QUAT.name())
                                .assignedRoomId(ROOM_ID)
                                .isActive(true)
                                .build())));
    }

    @Test
    void rejectsBookingWhenRoomDoctorSlotIsFull() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(slotLockRepository.findBySlotKeyForUpdate(anyString()))
                .thenReturn(Optional.of(AppointmentSlotLock.builder().slotKey("lock").build()));
        when(appointmentRepository.existsByPatientIdAndAppointmentDateAndTimeSlotAndStatusNot(
                any(), any(), anyString(), any())).thenReturn(false);
        when(appointmentRepository.countByRoomIdAndAppointmentDateAndTimeSlotAndStatusNot(
                ROOM_ID, date, "07:30-08:30", AppointmentStatus.CANCELLED)).thenReturn(5L);

        CreateAppointmentRequest request = request(date, "7:30 - 8:30");

        assertThatThrownBy(() -> appointmentService.createAppointment(
                request, UUID.randomUUID(), "trace-capacity", "capacity-key"))
                .hasMessageContaining("đã đủ 5 người đặt khám");

        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void reportsFullAndRemainingSlotsForMobile() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(appointmentRepository.findByRoomIdAndAppointmentDateAndStatusNot(
                ROOM_ID, date, AppointmentStatus.CANCELLED))
                .thenReturn(List.of(
                        booked("07:30-08:00"), booked("07:30-08:00"),
                        booked("07:30-08:00"), booked("07:30-08:00"),
                        booked("07:30-08:00"), booked("08:00-08:30")));

        List<TimeSlotAvailabilityResponse> slots = appointmentService
                .getTimeSlotAvailability(Department.NOI_TONG_QUAT.name(), date);

        TimeSlotAvailabilityResponse full = slots.get(0);
        TimeSlotAvailabilityResponse remaining = slots.get(1);
        assertThat(full.isAvailable()).isFalse();
        assertThat(full.getBookedCount()).isEqualTo(5);
        assertThat(full.getRemaining()).isZero();
        assertThat(full.getUnavailableReason()).isEqualTo("FULL");
        assertThat(remaining.isAvailable()).isTrue();
        assertThat(remaining.getBookedCount()).isEqualTo(1);
        assertThat(remaining.getRemaining()).isEqualTo(4);
    }

    private CreateAppointmentRequest request(LocalDate date, String timeSlot) {
        return CreateAppointmentRequest.builder()
                .patientId(UUID.randomUUID())
                .patientName("Nguyễn An")
                .department(Department.NOI_TONG_QUAT.name())
                .appointmentDate(date)
                .timeSlot(timeSlot)
                .build();
    }

    private Appointment booked(String timeSlot) {
        return Appointment.builder()
                .patientId(UUID.randomUUID())
                .ownerUserId(UUID.randomUUID())
                .department(Department.NOI_TONG_QUAT)
                .departmentId(Department.NOI_TONG_QUAT.getId())
                .roomId(ROOM_ID)
                .roomDisplayName("Phòng 01")
                .appointmentDate(LocalDate.now().plusDays(1))
                .timeSlot(timeSlot)
                .status(AppointmentStatus.CONFIRMED)
                .build();
    }
}
