package com.careflow.prescription.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.prescription.config.AppointmentClient;
import com.careflow.prescription.config.ConsultationClient;
import com.careflow.prescription.dto.response.PrescriptionResponse;
import com.careflow.prescription.mapper.PrescriptionMapper;
import com.careflow.prescription.model.Prescription;
import com.careflow.prescription.model.PrescriptionStatus;
import com.careflow.prescription.repository.PrescriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class PrescriptionFollowUpService {

    private static final Set<String> ALLOWED_CONSULTATION_STATUSES = Set.of(
            "IN_PROGRESS", "AWAITING_CLS", "AWAITING_REVIEW", "READY_TO_COMPLETE", "COMPLETED");

    private final PrescriptionRepository prescriptions;
    private final ConsultationClient consultations;
    private final AppointmentClient appointments;
    private final PrescriptionMapper mapper;

    public PrescriptionFollowUpService(PrescriptionRepository prescriptions,
                                       ConsultationClient consultations,
                                       AppointmentClient appointments,
                                       PrescriptionMapper mapper) {
        this.prescriptions = prescriptions;
        this.consultations = consultations;
        this.appointments = appointments;
        this.mapper = mapper;
    }

    @Transactional
    public PrescriptionResponse attempt(UUID id, UUID actor, String role) {
        if (actor == null || !"DOCTOR".equals(role)) {
            throw new BusinessException(403, "Cần bác sĩ phụ trách ca khám.");
        }

        Prescription prescription = prescriptions.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", "id", id));

        verifyResponsibleDoctorAndStatus(prescription, actor);
        requireSignedPrescription(prescription);

        if (prescription.getFollowUpDate() == null || "SCHEDULED".equals(prescription.getFollowUpStatus())) {
            return mapper.toResponse(prescription);
        }

        if (prescription.getFollowUpDepartment() == null || prescription.getFollowUpTimeSlot() == null) {
            prescription.setFollowUpStatus("NEEDS_SELECTION");
            prescription.setFollowUpErrorCode("SLOT_REQUIRED");
            return mapper.toResponse(prescriptions.save(prescription));
        }

        try {
            UUID booking = appointments.schedule(
                    prescription.getPatientId(),
                    prescription.getConsultationId(),
                    prescription.getFollowUpDate(),
                    prescription.getFollowUpDepartment(),
                    prescription.getFollowUpTimeSlot(),
                    prescription.getNotes(),
                    actor);
            prescription.setFollowUpAppointmentId(booking);
            prescription.setFollowUpStatus("SCHEDULED");
            prescription.setFollowUpErrorCode(null);
        } catch (BusinessException e) {
            prescription.setFollowUpStatus("FAILED");
            prescription.setFollowUpErrorCode("APPOINTMENT_" + e.getStatus());
        }

        return mapper.toResponse(prescriptions.save(prescription));
    }

    private void verifyResponsibleDoctorAndStatus(Prescription prescription, UUID actor) {
        var consultation = consultations.getConsultation(prescription.getConsultationId());
        if (consultation == null
                || !prescription.getConsultationId().equals(consultation.id())
                || !prescription.getPatientId().equals(consultation.patientId())
                || !actor.equals(consultation.doctorId())) {
            throw new BusinessException(403, "Quyền phụ trách ca khám đã thay đổi.");
        }
        if (consultation.status() == null) {
            throw new BusinessException(503, "Chưa xác minh được trạng thái ca khám.");
        }
        if (!ALLOWED_CONSULTATION_STATUSES.contains(consultation.status())) {
            throw new BusinessException(409, "Trạng thái ca khám không cho phép hẹn tái khám.");
        }
    }

    private void requireSignedPrescription(Prescription prescription) {
        if (prescription.getStatus() != PrescriptionStatus.CONFIRMED
                && prescription.getStatus() != PrescriptionStatus.DISPENSED) {
            throw new BusinessException(409, "Toa chưa được ký hoặc đã bị thay thế.");
        }
    }
}
