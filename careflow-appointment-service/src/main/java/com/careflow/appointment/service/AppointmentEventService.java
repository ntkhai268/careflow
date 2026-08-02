package com.careflow.appointment.service;

import com.careflow.appointment.model.Appointment;
import com.careflow.appointment.model.AppointmentOutboxEvent;
import com.careflow.appointment.repository.AppointmentOutboxRepository;
import com.careflow.common.constants.AppConstants;
import com.careflow.common.event.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AppointmentEventService {
    private final AppointmentOutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public AppointmentEventService(AppointmentOutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public void confirmed(Appointment appointment, String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appointmentId", appointment.getId());
        payload.put("patientId", appointment.getPatientId());
        payload.put("userId", appointment.getOwnerUserId());
        payload.put("departmentId", appointment.getDepartmentId());
        payload.put("department", appointment.getDepartment().name());
        payload.put("departmentDisplayName", appointment.getDepartment().getDisplayName());
        payload.put("roomId", appointment.getRoomId());
        payload.put("roomDisplayName", appointment.getRoomDisplayName());
        payload.put("appointmentDate", appointment.getAppointmentDate());
        payload.put("timeSlot", appointment.getTimeSlot());
        append(appointment, "AppointmentConfirmed", AppConstants.RK_APPOINTMENT_CONFIRMED,
                correlationId, payload);
    }

    public void cancelled(Appointment appointment, String correlationId) {
        append(appointment, "AppointmentCancelled", AppConstants.RK_APPOINTMENT_CANCELLED,
                correlationId, Map.of(
                        "appointmentId", appointment.getId(),
                        "patientId", appointment.getPatientId(),
                        "userId", appointment.getOwnerUserId(),
                        "roomId", appointment.getRoomId()));
    }

    private void append(Appointment appointment, String eventType, String routingKey,
                        String correlationId, Map<String, Object> payload) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        EventEnvelope envelope = new EventEnvelope(
                eventId, eventType, 1, appointment.getId(), 1, now,
                "appointment-service",
                correlationId == null || correlationId.isBlank() ? eventId.toString() : correlationId,
                objectMapper.valueToTree(payload));
        AppointmentOutboxEvent event = new AppointmentOutboxEvent();
        event.setEventId(eventId);
        event.setAggregateId(appointment.getId());
        event.setEventType(eventType);
        event.setRoutingKey(routingKey);
        event.setPayload(objectMapper.valueToTree(envelope));
        event.setStatus(AppointmentOutboxEvent.Status.PENDING);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        outbox.save(event);
    }
}
