package com.careflow.patient.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.patient.model.Patient;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.UUID;

@Service
public class UploadQuotaService {
    @PersistenceContext private EntityManager entityManager;
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireCapacity(UUID patientId,long incomingBytes,long incomingFiles) {
        // Serialize upload reservations across service instances through the same patient row.
        if(entityManager.find(Patient.class,patientId,LockModeType.PESSIMISTIC_WRITE)==null)
            throw new BusinessException(404,"Không tìm thấy hồ sơ bệnh nhân.");
        Object[] totals=entityManager.createQuery("select count(f),coalesce(sum(f.fileSize),0) from HealthRecordFile f where f.healthRecord.patientId=:patient",Object[].class)
                .setParameter("patient",patientId).getSingleResult();
        if(((Number)totals[0]).longValue()+incomingFiles>100 || ((Number)totals[1]).longValue()+incomingBytes>200L*1024*1024)
            throw new BusinessException(413,"Hồ sơ đã đạt giới hạn 100 tệp hoặc 200MB. Vui lòng quản lý các tệp hiện có.");
    }
}
