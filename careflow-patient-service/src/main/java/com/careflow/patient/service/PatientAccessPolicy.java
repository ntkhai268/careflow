package com.careflow.patient.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.patient.model.Patient;

import java.util.Locale;
import java.util.UUID;

final class PatientAccessPolicy {

    void requireCreate(UUID requesterId, String role, UUID targetUserId) {
        requireAuthenticated(requesterId, role);
        if (isAdmin(role)) {
            return;
        }
        if (!isPatient(role) || !requesterId.equals(targetUserId)) {
            throw forbidden("Only the patient owner or ADMIN may create a profile");
        }
    }

    void requireRead(UUID requesterId, String role, Patient patient) {
        requireAuthenticated(requesterId, role);
        if (isAdmin(role) || isDoctor(role) || isStaff(role) || requesterId.equals(patient.getUserId())) {
            return;
        }
        throw forbidden("You are not allowed to view this patient profile");
    }

    void requireClinicalRead(UUID requesterId, String role, Patient patient, boolean assigned) {
        requireAuthenticated(requesterId, role);
        if (isAdmin(role) || requesterId.equals(patient.getUserId())
                || (isDoctor(role) && assigned)) {
            return;
        }
        throw forbidden("You are not allowed to view this patient profile");
    }

    void requireOperationalRead(UUID requesterId, String role, boolean assigned) {
        requireAuthenticated(requesterId, role);
        if (isAdmin(role) || (isStaff(role) && assigned)) {
            return;
        }
        throw forbidden("You are not allowed to view this patient operational summary");
    }

    void requireUpdate(UUID requesterId, String role, Patient patient) {
        requireAuthenticated(requesterId, role);
        if (isAdmin(role) || isStaff(role) || requesterId.equals(patient.getUserId())) {
            return;
        }
        throw forbidden("You are not allowed to update this patient profile");
    }

    private void requireAuthenticated(UUID requesterId, String role) {
        if (requesterId == null || role == null || role.isBlank()) {
            throw new BusinessException(401, "Missing authenticated user information");
        }
    }

    private boolean isAdmin(String role) {
        return normalized(role).equals("ADMIN");
    }

    private boolean isPatient(String role) {
        return normalized(role).equals("PATIENT");
    }

    private boolean isDoctor(String role) {
        return normalized(role).equals("DOCTOR");
    }

    private boolean isStaff(String role) {
        return normalized(role).equals("STAFF");
    }

    private String normalized(String role) {
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }
}
