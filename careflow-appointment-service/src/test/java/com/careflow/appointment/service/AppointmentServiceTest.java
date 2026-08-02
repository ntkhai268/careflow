package com.careflow.appointment.service;

import com.careflow.appointment.dto.request.CreateAppointmentRequest;
import com.careflow.appointment.dto.response.AppointmentResponse;
import com.careflow.appointment.model.Appointment;
import com.careflow.appointment.model.AppointmentStatus;
import com.careflow.appointment.model.Department;
import com.careflow.appointment.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentEventService appointmentEvents;

    @InjectMocks
    private AppointmentService appointmentService;

    @Test
    void onlineBookingIsConfirmedImmediately() {
        UUID patientId = UUID.randomUUID();
        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .patientId(patientId)
                .patientName("Nguyen Thanh Khai")
                .department(Department.NOI_TONG_QUAT.name())
                .appointmentDate(LocalDate.now().plusDays(1))
                .timeSlot("10:00-10:30")
                .build();

        when(appointmentRepository
                .existsByPatientIdAndAppointmentDateAndTimeSlotAndStatusNot(
                        patientId,
                        request.getAppointmentDate(),
                        request.getTimeSlot(),
                        AppointmentStatus.CANCELLED))
                .thenReturn(false);
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenAnswer(invocation -> {
                    Appointment saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

        AppointmentResponse response =
                appointmentService.createAppointment(request, UUID.randomUUID(), "trace-1", null);

        ArgumentCaptor<Appointment> appointmentCaptor =
                ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).saveAndFlush(appointmentCaptor.capture());
        assertThat(appointmentCaptor.getValue().getStatus())
                .isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
        assertThat(response.getStatusDisplayName())
                .isEqualTo(AppointmentStatus.CONFIRMED.getDisplayName());
        assertThat(response.getRoomId()).isEqualTo(Department.NOI_TONG_QUAT.getRoomId());
        verify(appointmentEvents).confirmed(any(Appointment.class), org.mockito.ArgumentMatchers.eq("trace-1"));
    }

    @Test
    void createAppointmentWithIdempotencyKeyReturnsCachedResponse() {
        UUID patientId = UUID.randomUUID();
        String idempotencyKey = "key-abc-123";
        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .patientId(patientId)
                .patientName("Nguyen Thanh Khai")
                .department(Department.NOI_TONG_QUAT.name())
                .appointmentDate(LocalDate.now().plusDays(1))
                .timeSlot("10:00-10:30")
                .build();

        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenAnswer(invocation -> {
                    Appointment saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

        UUID ownerId = UUID.randomUUID();
        AppointmentResponse res1 = appointmentService.createAppointment(request, ownerId, "trace-1", idempotencyKey);
        AppointmentResponse res2 = appointmentService.createAppointment(request, ownerId, "trace-1", idempotencyKey);

        assertThat(res1.getId()).isEqualTo(res2.getId());
        verify(appointmentRepository, org.mockito.Mockito.times(1)).saveAndFlush(any(Appointment.class));
    }

    @Test
    void bookingRejectsASlotWhoseStartTimeHasPassedToday() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-31T07:56:00Z"),
                ZoneId.of("Asia/Bangkok"));
        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .patientId(UUID.randomUUID())
                .patientName("Nguyen Thanh Khai")
                .department(Department.NHI.name())
                .appointmentDate(LocalDate.of(2026, 7, 31))
                .timeSlot("07:30-08:00")
                .build();

        assertThatThrownBy(() ->
                AppointmentService.validateAppointmentTime(request, clock))
                .hasMessage("Ca khám đã qua. Vui lòng chọn ca khác");
    }

    @Test
    void patientCannotReadAnotherAccountsAppointment() {
        Appointment appointment = Appointment.builder()
                .patientId(UUID.randomUUID())
                .ownerUserId(UUID.randomUUID())
                .department(Department.NHI)
                .departmentId(Department.NHI.getId())
                .roomId(Department.NHI.getRoomId())
                .roomDisplayName(Department.NHI.getRoomDisplayName())
                .appointmentDate(LocalDate.now().plusDays(1))
                .timeSlot("10:00-10:30")
                .status(AppointmentStatus.CONFIRMED)
                .build();
        UUID appointmentId = UUID.randomUUID();
        appointment.setId(appointmentId);
        when(appointmentRepository.findById(appointmentId)).thenReturn(java.util.Optional.of(appointment));

        assertThatThrownBy(() -> appointmentService.getAppointmentById(
                appointmentId, UUID.randomUUID(), "PATIENT"))
                .isInstanceOf(com.careflow.common.exception.BusinessException.class)
                .extracting("status").isEqualTo(403);
    }
}
