package com.careflow.patient.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.patient.dto.request.CreatePatientRequest;
import com.careflow.patient.dto.request.UpdatePatientRequest;
import com.careflow.patient.dto.response.PatientResponse;
import com.careflow.patient.dto.response.PatientOperationalResponse;
import com.careflow.patient.client.AssignmentClient;
import com.careflow.patient.client.dto.AssignmentAccessResponse;
import com.careflow.patient.mapper.PatientMapper;
import com.careflow.patient.model.Patient;
import com.careflow.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientService {

    private static final int MAX_PROFILES_PER_USER = 10;
    private final PatientRepository patientRepository;
    private final AssignmentClient assignmentClient;
    private static final PatientAccessPolicy ACCESS_POLICY = new PatientAccessPolicy();

    /**
     * Tạo hồ sơ bệnh nhân mới (UC-P01)
     */
    @Transactional
    public PatientResponse createPatient(CreatePatientRequest request, UUID requesterId, String role) {
        ACCESS_POLICY.requireCreate(requesterId, role, request.getUserId());

        if (!"ADMIN".equalsIgnoreCase(role)
                && patientRepository.countByUserId(request.getUserId()) >= MAX_PROFILES_PER_USER) {
            throw new BusinessException(409, "Tài khoản đã đạt tối đa 10 hồ sơ bệnh nhân");
        }

        // Một CCCD có thể được khai báo lại trên tài khoản khác khi chủ tài khoản cũ
        // mất quyền truy cập. Chỉ chặn bản ghi trùng trong cùng một tài khoản.
        if (request.getIdCardNumber() != null
                && patientRepository.existsByUserIdAndIdCardNumber(
                        request.getUserId(), request.getIdCardNumber())) {
            throw new BusinessException(409, "Tài khoản này đã có hồ sơ dùng số CCCD đã nhập");
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
    public PatientResponse getPatientById(UUID id, UUID requesterId, String role) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));

        boolean assigned = hasAssignment(id, null, null, requesterId, role);
        ACCESS_POLICY.requireClinicalRead(requesterId, role, patient, assigned);

        return PatientMapper.toResponse(patient);
    }

    @Transactional(readOnly = true)
    public PatientOperationalResponse getOperationalSummary(UUID id, UUID appointmentId, String roomId,
                                                             UUID requesterId, String role) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));
        boolean assigned = hasAssignment(id, appointmentId, roomId, requesterId, role);
        ACCESS_POLICY.requireOperationalRead(requesterId, role, assigned);
        return PatientOperationalResponse.builder()
                .id(patient.getId())
                .fullName(patient.getFullName())
                .dateOfBirth(patient.getDateOfBirth())
                .gender(patient.getGender())
                .build();
    }

    @Transactional(readOnly = true)
    public UUID getOwnerUserId(UUID id, UUID requesterId, String role) {
        if (requesterId == null || role == null
                || !("DOCTOR".equalsIgnoreCase(role) || "STAFF".equalsIgnoreCase(role)
                || "ADMIN".equalsIgnoreCase(role))) {
            throw new BusinessException(403, "Clinical access is required");
        }
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id))
                .getUserId();
    }

    /**
     * Tìm hồ sơ bệnh nhân theo userId
     */
    @Transactional(readOnly = true)
    public PatientResponse getPatientByUserId(UUID userId, UUID requesterId, String role) {
        Patient patient = patientRepository.findFirstByUserIdOrderByCreatedAtAsc(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "userId", userId));

        ACCESS_POLICY.requireRead(requesterId, role, patient);

        return PatientMapper.toResponse(patient);
    }

    /**
     * Lấy tất cả hồ sơ thuộc cùng tài khoản, theo thứ tự tạo.
     */
    @Transactional(readOnly = true)
    public List<PatientResponse> getPatientsByUserId(UUID userId, UUID requesterId, String role) {
        List<Patient> patients = patientRepository.findAllByUserIdOrderByCreatedAtAsc(userId);
        patients.forEach(patient -> ACCESS_POLICY.requireRead(requesterId, role, patient));
        return patients.stream().map(PatientMapper::toResponse).toList();
    }

    /**
     * Cập nhật hồ sơ bệnh nhân (UC-P03) — partial update
     */
    @Transactional
    public PatientResponse updatePatient(UUID id, UpdatePatientRequest request, UUID requesterId, String role) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));

        ACCESS_POLICY.requireUpdate(requesterId, role, patient);

        // Cho phép CCCD tồn tại trên tài khoản khác; chỉ chặn trùng trong cùng tài khoản.
        if (request.getIdCardNumber() != null
                && !request.getIdCardNumber().equals(patient.getIdCardNumber())
                && patientRepository.existsByUserIdAndIdCardNumberAndIdNot(
                        patient.getUserId(), request.getIdCardNumber(), patient.getId())) {
            throw new BusinessException(409, "Tài khoản này đã có hồ sơ dùng số CCCD đã nhập");
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
        if (request.getAllergyNotes() != null) patient.setAllergyNotes(request.getAllergyNotes());
        if (request.getMedicalHistory() != null) patient.setMedicalHistory(request.getMedicalHistory());

        Patient updated = patientRepository.save(patient);
        log.info("Updated patient {}", updated.getId());

        return PatientMapper.toResponse(updated);
    }

    private boolean hasAssignment(UUID patientId, UUID appointmentId, String roomId,
                                  UUID requesterId, String role) {
        if (requesterId == null || role == null || role.isBlank()
                || "PATIENT".equalsIgnoreCase(role)) {
            return false;
        }
        try {
            var response = assignmentClient.getPatientAccess(
                    patientId, appointmentId, roomId, requesterId, role);
            AssignmentAccessResponse access = response == null ? null : response.getData();
            return access != null && access.isAllowed();
        } catch (RuntimeException exception) {
            log.warn("Assignment authorization unavailable for patient {} and actor {}", patientId, requesterId);
            return false;
        }
    }
}
