package com.careflow.appointment.listener;

import com.careflow.appointment.config.RabbitMQConfig;
import com.careflow.appointment.dto.request.UpdateAppointmentStatusRequest;
import com.careflow.appointment.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConsultationCompletedConsumer {

    private final AppointmentService appointmentService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_CONSULTATION_COMPLETED)
    public void handleConsultationCompleted(Map<String, Object> event) {
        log.info("Received consultation.completed event: {}", event);
        try {
            Object appointmentIdObj = event.get("appointmentId");
            if (appointmentIdObj == null) {
                log.warn("Event consultation.completed missing appointmentId, skipping");
                return;
            }

            UUID appointmentId = UUID.fromString(appointmentIdObj.toString());
            log.info("Updating appointment {} status to COMPLETED after consultation finished", appointmentId);

            UpdateAppointmentStatusRequest updateRequest = new UpdateAppointmentStatusRequest();
            updateRequest.setStatus("COMPLETED");
            updateRequest.setNotes("Hoàn thành khám bệnh theo phiên khám " + event.get("id"));

            appointmentService.updateAppointmentStatus(appointmentId, updateRequest);
            log.info("Successfully updated appointment {} to COMPLETED", appointmentId);
        } catch (Exception e) {
            log.error("Failed to process consultation.completed event: {}", e.getMessage(), e);
        }
    }
}
