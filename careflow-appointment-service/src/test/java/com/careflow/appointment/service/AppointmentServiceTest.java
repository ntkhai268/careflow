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
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

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
        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(invocation -> {
                    Appointment saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

        AppointmentResponse response =
                appointmentService.createAppointment(request);

        ArgumentCaptor<Appointment> appointmentCaptor =
                ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(appointmentCaptor.capture());
        assertThat(appointmentCaptor.getValue().getStatus())
                .isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
        assertThat(response.getStatusDisplayName())
                .isEqualTo(AppointmentStatus.CONFIRMED.getDisplayName());
    }
}
