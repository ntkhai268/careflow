package com.careflow.appointment.service;

import com.careflow.appointment.dto.request.CreateAppointmentRequest;
import com.careflow.appointment.model.Appointment;
import com.careflow.appointment.repository.AppointmentRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppointmentIdempotencyNormalizationTest {
    @ParameterizedTest
    @ValueSource(strings = {"noi_tong_quat", " NOI_TONG_QUAT ", "NEUROLOGY"})
    void acceptedDepartmentSpellingRemainsRetryable(String department) {
        var repository = mock(AppointmentRepository.class);
        var service = new AppointmentService(repository, mock(AppointmentEventService.class), AppointmentPricingFixture.priced(null), null, null,
                mock(com.careflow.appointment.client.AppointmentConsultationClient.class));
        when(repository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        UUID patient = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        var request = CreateAppointmentRequest.builder().patientId(patient).patientName("Synthetic name")
                .department(department).appointmentDate(LocalDate.now().plusDays(5)).timeSlot("09:00-09:30").build();

        var first = service.createAppointment(request, owner, "trace", "key");
        var retry = service.createAppointment(request, owner, "trace", "key");

        assertThat(retry.getId()).isEqualTo(first.getId());
        verify(repository).saveAndFlush(any());
    }
}
