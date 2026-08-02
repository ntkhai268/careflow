package com.careflow.queue.messaging;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.event.EventEnvelope;
import com.careflow.common.exception.BusinessException;
import com.careflow.queue.config.RabbitMqConfig;
import com.careflow.queue.domain.*;
import com.careflow.queue.repository.ProcessedEventRepository;
import com.careflow.queue.repository.QueueEntryRepository;
import com.careflow.queue.service.QueueEventService;
import com.careflow.queue.service.QueueManagementService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

@Component
public class AppointmentEventConsumer {
    private static final String CONSUMER = "queue-service.appointment-events";
    private static final DateTimeFormatter APPOINTMENT_TIME = DateTimeFormatter.ofPattern("H:mm");
    private final ProcessedEventRepository processedEvents;
    private final QueueEntryRepository entries;
    private final QueueManagementService queueService;
    private final QueueEventService events;

    public AppointmentEventConsumer(ProcessedEventRepository processedEvents, QueueEntryRepository entries,
                                    QueueManagementService queueService, QueueEventService events) {
        this.processedEvents = processedEvents;
        this.entries = entries;
        this.queueService = queueService;
        this.events = events;
    }

    @RabbitListener(queues = RabbitMqConfig.APPOINTMENT_QUEUE)
    @Transactional
    public void consume(EventEnvelope envelope) {
        validateEnvelope(envelope);
        ProcessedEventId eventId = new ProcessedEventId(envelope.eventId(), CONSUMER);
        if (processedEvents.existsById(eventId)) return;
        if ("AppointmentConfirmed".equals(envelope.eventType())) create(envelope);
        else if ("AppointmentCancelled".equals(envelope.eventType())) cancel(envelope);
        else throw new BusinessException(422, "Event type không được hỗ trợ: " + envelope.eventType());
        ProcessedEvent processed = new ProcessedEvent();
        processed.setId(eventId);
        processed.setEventType(envelope.eventType());
        processed.setProcessedAt(Instant.now());
        processedEvents.save(processed);
    }

    private void create(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        UUID appointmentId = requiredUuid(payload, "appointmentId");
        UUID patientId = requiredUuid(payload, "patientId");
        UUID userId = requiredUuid(payload, "userId");
        UUID departmentId = requiredUuid(payload, "departmentId");
        String roomId = requiredText(payload, "roomId");
        String roomDisplayName = requiredText(payload, "roomDisplayName");
        LocalDate date;
        try { date = LocalDate.parse(requiredText(payload, "appointmentDate")); }
        catch (RuntimeException exception) { throw new BusinessException(422, "appointmentDate không hợp lệ"); }
        LocalTime scheduledStart = parseTimeSlotStart(requiredText(payload, "timeSlot"));
        QueueConfig config = queueService.requireLockedConfig(departmentId);
        if (!roomId.equals(config.getRoomCode())) {
            throw new BusinessException(422, "roomId không khớp cấu hình Queue của khoa");
        }
        if (entries.findByAppointmentId(appointmentId).isPresent()) return;
        QueueEntry entry = queueService.createAppointmentEntry(
                config, date, scheduledStart, requiredText(payload, "timeSlot"),
                requiredText(payload, "department"), roomDisplayName,
                appointmentId, patientId, userId);
        entries.saveAndFlush(entry);
        events.append(entry, config, "VisitTicketIssued", AppConstants.RK_QUEUE_NUMBER_ASSIGNED,
                envelope.correlationId(), Map.of(
                        "appointmentDate", date.toString(),
                        "roomId", roomId,
                        "scheduledStartAt", entry.getScheduledStartAt().toString()));
    }

    private void cancel(EventEnvelope envelope) {
        UUID appointmentId = requiredUuid(envelope.payload(), "appointmentId");
        QueueEntry snapshot = entries.findByAppointmentId(appointmentId).orElse(null);
        if (snapshot == null || snapshot.getStatus() != QueueStatus.WAITING) return;
        QueueConfig config = queueService.requireLockedConfig(snapshot.getDepartmentId());
        QueueEntry entry = entries.findFirstByAppointmentId(appointmentId).orElse(null);
        if (entry == null || entry.getStatus() != QueueStatus.WAITING) return;
        entry.setStatus(QueueStatus.CANCELLED);
        entry.setCancelledAt(Instant.now());
        entries.saveAndFlush(entry);
        events.append(entry, config, "QUEUE_CANCELLED", "queue.cancelled", envelope.correlationId(), Map.of());
    }

    private void validateEnvelope(EventEnvelope envelope) {
        if (envelope == null || envelope.eventId() == null || envelope.aggregateId() == null
                || envelope.eventVersion() != 1 || envelope.payload() == null) {
            throw new BusinessException(422, "Event envelope không hợp lệ");
        }
    }

    private UUID requiredUuid(JsonNode payload, String field) {
        try { return UUID.fromString(requiredText(payload, field)); }
        catch (IllegalArgumentException exception) { throw new BusinessException(422, field + " không phải UUID hợp lệ"); }
    }

    private String requiredText(JsonNode payload, String field) {
        if (!payload.hasNonNull(field) || payload.get(field).asText().isBlank())
            throw new BusinessException(422, "Thiếu field bắt buộc: " + field);
        return payload.get(field).asText();
    }

    private LocalTime parseTimeSlotStart(String timeSlot) {
        String[] bounds = timeSlot.trim().split("\\s*-\\s*", -1);
        if (bounds.length != 2) throw new BusinessException(422, "timeSlot phải có dạng HH:mm-HH:mm");
        try {
            LocalTime start = LocalTime.parse(bounds[0], APPOINTMENT_TIME);
            LocalTime end = LocalTime.parse(bounds[1], APPOINTMENT_TIME);
            if (!end.isAfter(start)) throw new BusinessException(422, "Giờ kết thúc timeSlot phải sau giờ bắt đầu");
            return start;
        } catch (DateTimeParseException exception) {
            throw new BusinessException(422, "timeSlot phải có dạng HH:mm-HH:mm");
        }
    }
}
