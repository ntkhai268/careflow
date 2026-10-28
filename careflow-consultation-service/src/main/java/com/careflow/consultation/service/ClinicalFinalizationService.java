package com.careflow.consultation.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.consultation.client.*;
import com.careflow.consultation.dto.response.ConsultationResponse;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * No spanning transaction: committed clinical state survives a downstream
 * timeout.
 */
@Service
public class ClinicalFinalizationService {
    private final ConsultationService consultations;
    private final ConsultationQueueClient queues;
    private final AppointmentClient appointments;

    public ClinicalFinalizationService(ConsultationService consultations, ConsultationQueueClient queues,
            AppointmentClient appointments) {
        this.consultations = consultations;
        this.queues = queues;
        this.appointments = appointments;
    }

    public record Result(ConsultationResponse consultation, boolean queueCompleted, boolean appointmentCompleted,
            boolean complete, List<String> pendingSteps) {
    }

    public Result finalizeVisit(UUID id, UUID actor, String role) {
        var consultation = consultations.completeConsultation(id, actor, role);
        var pending = new ArrayList<String>();
        boolean queueDone = false, appointmentDone = consultation.getAppointmentId() == null;
        try {
            UUID initial = consultation.getQueueEntryId();
            if (initial == null && consultation.getAppointmentId() != null) {
                var response = queues.ticket(consultation.getAppointmentId());
                var ticket = response == null ? null : response.getData();
                if (ticket == null || !consultation.getPatientId().equals(ticket.patientId())
                        || !consultation.getAppointmentId().equals(ticket.appointmentId()))
                    throw new BusinessException(503, "Phiếu không khớp ca khám.");
                initial = ticket.ticketId();
            }
            if (initial == null)
                throw new BusinessException(503, "Chưa xác định được phiếu của ca khám.");
            var response = queues.completionEntries(id, consultation.getPatientId(), initial);
            var rows = response == null ? null : response.getData();
            if (rows == null || rows.isEmpty())
                throw new BusinessException(503, "Chưa đọc được trạng thái hàng đợi.");
            for (var row : rows) {
                if (row.entryId() == null || !consultation.getPatientId().equals(row.patientId()))
                    throw new BusinessException(503, "Phiếu không khớp bệnh nhân.");
                if (!"COMPLETED".equals(row.status())) {
                    var completed = queues.complete(row.entryId());
                    var state = completed == null ? null : completed.getData();
                    if (state == null || !row.entryId().equals(state.entryId())
                            || !consultation.getPatientId().equals(state.patientId())
                            || !"COMPLETED".equals(state.queueStatus()))
                        throw new BusinessException(503, "Chưa xác nhận hoàn tất phiếu.");
                }
            }
            queueDone = true;
        } catch (RuntimeException e) {
            pending.add("QUEUE_COMPLETION");
        }
        if (consultation.getAppointmentId() != null) {
            try {
                var response = appointments.completeClinical(
                        new AppointmentClient.ClinicalCompletion(consultation.getAppointmentId(), id));
                var state = response == null ? null : response.getData();
                if (state == null || !consultation.getAppointmentId().equals(state.getId())
                        || !consultation.getPatientId().equals(state.getPatientId())
                        || !"COMPLETED".equals(state.getStatus()))
                    throw new BusinessException(503, "Chưa xác nhận hoàn tất lịch.");
                appointmentDone = true;
            } catch (RuntimeException e) {
                pending.add("APPOINTMENT_COMPLETION");
            }
        }
        return new Result(consultation, queueDone, appointmentDone, queueDone && appointmentDone, List.copyOf(pending));
    }
}
