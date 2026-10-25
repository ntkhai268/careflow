package com.careflow.prescription.service;

import com.careflow.common.exception.*;
import com.careflow.prescription.config.*;
import com.careflow.prescription.dto.response.PrescriptionResponse;
import com.careflow.prescription.mapper.PrescriptionMapper;
import com.careflow.prescription.model.PrescriptionStatus;
import com.careflow.prescription.repository.PrescriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class PrescriptionFollowUpService {
    private final PrescriptionRepository prescriptions;private final ConsultationClient consultations;private final AppointmentClient appointments;private final PrescriptionMapper mapper;
    public PrescriptionFollowUpService(PrescriptionRepository prescriptions,ConsultationClient consultations,AppointmentClient appointments,PrescriptionMapper mapper){this.prescriptions=prescriptions;this.consultations=consultations;this.appointments=appointments;this.mapper=mapper;}
    @Transactional
    public PrescriptionResponse attempt(UUID id,UUID actor,String role){
        if(actor==null || !"DOCTOR".equals(role))throw new BusinessException(403,"Cần bác sĩ phụ trách ca khám.");
        var p=prescriptions.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Prescription","id",id));
        var c=consultations.getConsultation(p.getConsultationId());
        if(c==null || !p.getConsultationId().equals(c.id()) || !p.getPatientId().equals(c.patientId()) || !actor.equals(c.doctorId()))throw new BusinessException(403,"Quyền phụ trách ca khám đã thay đổi.");
        if(c.status()==null)throw new BusinessException(503,"Chưa xác minh được trạng thái ca khám.");
        if(!Set.of("IN_PROGRESS","AWAITING_CLS","AWAITING_REVIEW","READY_TO_COMPLETE","COMPLETED").contains(c.status()))throw new BusinessException(409,"Trạng thái ca khám không cho phép hẹn tái khám.");
        if(p.getStatus()!=PrescriptionStatus.CONFIRMED && p.getStatus()!=PrescriptionStatus.DISPENSED)throw new BusinessException(409,"Toa chưa được ký hoặc đã bị thay thế.");
        if(p.getFollowUpDate()==null || "SCHEDULED".equals(p.getFollowUpStatus()))return mapper.toResponse(p);
        if(p.getFollowUpDepartment()==null || p.getFollowUpTimeSlot()==null){p.setFollowUpStatus("NEEDS_SELECTION");p.setFollowUpErrorCode("SLOT_REQUIRED");return mapper.toResponse(prescriptions.save(p));}
        try{
            UUID booking=appointments.schedule(p.getPatientId(),p.getConsultationId(),p.getFollowUpDate(),p.getFollowUpDepartment(),p.getFollowUpTimeSlot(),p.getNotes(),actor);
            p.setFollowUpAppointmentId(booking);p.setFollowUpStatus("SCHEDULED");p.setFollowUpErrorCode(null);
        }catch(BusinessException e){
            p.setFollowUpStatus("FAILED");p.setFollowUpErrorCode("APPOINTMENT_"+e.getStatus());
        }
        return mapper.toResponse(prescriptions.save(p));
    }
}
