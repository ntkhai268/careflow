package com.careflow.consultation.service;
import com.careflow.consultation.client.ConsultationPrescriptionClient;
import com.careflow.consultation.model.Consultation;
import com.careflow.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import java.util.Set;
@Component
public class ClinicalCompletionGuard {
    private final ConsultationPrescriptionClient prescriptions;
    public ClinicalCompletionGuard(ConsultationPrescriptionClient prescriptions){this.prescriptions=prescriptions;}
    public void requireReady(Consultation consultation){
        if(consultation.getDiagnosis()==null || consultation.getDiagnosis().isBlank())throw new BusinessException(409,"Lưu chẩn đoán trước khi hoàn tất ca khám.");
        try{
            var response=prescriptions.byConsultation(consultation.getId());var rows=response==null?null:response.getData();
            if(rows==null || rows.stream().anyMatch(p->p==null || p.id()==null || p.doctorId()==null || p.status()==null || !consultation.getId().equals(p.consultationId()) || !consultation.getPatientId().equals(p.patientId()) || !Set.of("DRAFT","CONFIRMED","DISPENSED","CANCELLED","CANCELLED_BY_AMENDMENT").contains(p.status())))throw new BusinessException(503,"Chưa xác minh được trạng thái toa thuốc.");
            if(rows.stream().anyMatch(p->"DRAFT".equals(p.status()) && consultation.currentDoctorId().equals(p.doctorId())))throw new BusinessException(409,"Cần ký hoặc hủy các toa nháp trước khi hoàn tất ca khám.");
        }catch(feign.FeignException e){throw new BusinessException(503,"Chưa xác minh được trạng thái toa thuốc.");}
    }
}
