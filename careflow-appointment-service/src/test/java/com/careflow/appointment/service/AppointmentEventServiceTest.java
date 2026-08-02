package com.careflow.appointment.service;

import com.careflow.appointment.model.Appointment;
import com.careflow.appointment.model.AppointmentOutboxEvent;
import com.careflow.appointment.model.Department;
import com.careflow.appointment.repository.AppointmentOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppointmentEventServiceTest {
    @Mock AppointmentOutboxRepository outbox;

    @Test
    void confirmedEventCarriesTheQueueTicketContractInAnEnvelope() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        AppointmentEventService service = new AppointmentEventService(outbox, mapper);
        Department department = Department.THAN_KINH;
        Appointment appointment = Appointment.builder()
                .patientId(UUID.randomUUID())
                .ownerUserId(UUID.randomUUID())
                .department(department)
                .departmentId(department.getId())
                .roomId(department.getRoomId())
                .roomDisplayName(department.getRoomDisplayName())
                .appointmentDate(LocalDate.of(2026, 8, 18))
                .timeSlot("10:30-11:00")
                .build();
        appointment.setId(UUID.randomUUID());

        service.confirmed(appointment, "trace-1");

        ArgumentCaptor<AppointmentOutboxEvent> event =
                ArgumentCaptor.forClass(AppointmentOutboxEvent.class);
        verify(outbox).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("AppointmentConfirmed");
        assertThat(event.getValue().getRoutingKey()).isEqualTo("appointment.confirmed");
        assertThat(event.getValue().getPayload().path("eventType").asText())
                .isEqualTo("AppointmentConfirmed");
        assertThat(event.getValue().getPayload().path("correlationId").asText())
                .isEqualTo("trace-1");
        assertThat(event.getValue().getPayload().path("payload").path("roomId").asText())
                .isEqualTo("ROOM-21");
        assertThat(event.getValue().getPayload().path("payload").path("userId").asText())
                .isEqualTo(appointment.getOwnerUserId().toString());
    }
}
