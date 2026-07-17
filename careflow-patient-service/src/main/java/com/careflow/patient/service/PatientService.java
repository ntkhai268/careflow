package com.careflow.patient.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.patient.dto.request.CreatePatientRequest;
import com.careflow.patient.dto.request.UpdatePatientRequest;
import com.careflow.patient.dto.response.PatientResponse;
import com.careflow.patient.mapper.PatientMapper;
import com.careflow.patient.model.Patient;
import com.careflow.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientService {

    private final PatientRepository patientRepository;

    /**
     * Tạo hồ sơ bệnh nhân mới (UC-P01)
     */
    @Transactional
    public PatientResponse createPatient(CreatePatientRequest request) {
        // Kiểm tra userId đã tồn tại chưa
        if (patientRepository.existsByUserId(request.getUserId())) {
            throw new BusinessException(409, "Bệnh nhân với userId này đã tồn tại");
        }

        // Kiểm tra CMND/CCCD trùng
        if (request.getIdCardNumber() != null &&
                patientRepository.existsByIdCardNumber(request.getIdCardNumber())) {
            throw new BusinessException(409, "CMND/CCCD đã được sử dụng bởi hồ sơ khác");
        }

        Patient patient = Patient.builder()
                .userId(request.getUserId())
                .fullName(request.getFullName())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .phone(request.getPhone())
                .idCardNumber(request.getIdCardNumber())
                .insuranceNumber(request.getInsuranceNumber())
                .occupation(request.getOccupation())
                .address(request.getAddress())
                .build();

        Patient saved = patientRepository.save(patient);
        log.info("Created patient {} for userId {}", saved.getId(), saved.getUserId());

        return PatientMapper.toResponse(saved);
    }

    /**
     * Xem hồ sơ bệnh nhân theo ID (UC-P02)
     */
    @Transactional(readOnly = true)
    public PatientResponse getPatientById(UUID id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));

        return PatientMapper.toResponse(patient);
    }

    /**
     * Tìm hồ sơ bệnh nhân theo userId
     */
    @Transactional(readOnly = true)
    public PatientResponse getPatientByUserId(UUID userId) {
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "userId", userId));

        return PatientMapper.toResponse(patient);
    }

    /**
     * Cập nhật hồ sơ bệnh nhân (UC-P03) — partial update
     */
    @Transactional
    public PatientResponse updatePatient(UUID id, UpdatePatientRequest request) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));

        // Kiểm tra CMND/CCCD trùng (nếu thay đổi)
        if (request.getIdCardNumber() != null &&
                !request.getIdCardNumber().equals(patient.getIdCardNumber()) &&
                patientRepository.existsByIdCardNumber(request.getIdCardNumber())) {
            throw new BusinessException(409, "CMND/CCCD đã được sử dụng bởi hồ sơ khác");
        }

        // Partial update — chỉ cập nhật field non-null
        if (request.getFullName() != null) patient.setFullName(request.getFullName());
        if (request.getDateOfBirth() != null) patient.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null) patient.setGender(request.getGender());
        if (request.getPhone() != null) patient.setPhone(request.getPhone());
        if (request.getIdCardNumber() != null) patient.setIdCardNumber(request.getIdCardNumber());
        if (request.getInsuranceNumber() != null) patient.setInsuranceNumber(request.getInsuranceNumber());
        if (request.getOccupation() != null) patient.setOccupation(request.getOccupation());
        if (request.getAddress() != null) patient.setAddress(request.getAddress());
        if (request.getAvatarUrl() != null) patient.setAvatarUrl(request.getAvatarUrl());

        Patient updated = patientRepository.save(patient);
        log.info("Updated patient {}", updated.getId());

        return PatientMapper.toResponse(updated);
    }
}
