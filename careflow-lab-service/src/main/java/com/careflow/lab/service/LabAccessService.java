package com.careflow.lab.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.lab.client.LabConsultationClient;
import com.careflow.lab.client.LabDirectoryClient;
import com.careflow.lab.client.PatientIdentityClient;
import com.careflow.lab.client.QueueExecutionClient;
import com.careflow.lab.model.LabOrder;
import com.careflow.lab.model.LabOrderStatus;
import com.careflow.lab.dto.request.LabOrderItemRequest;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LabAccessService {
    private static final Set<String> CONSULTATION_STATES = Set.of("IN_PROGRESS", "AWAITING_CLS", "AWAITING_REVIEW",
            "READY_TO_COMPLETE", "COMPLETED", "CANCELLED", "TRANSFERRED");
    private final LabConsultationClient consultations;
    private final PatientIdentityClient patients;
    private final QueueExecutionClient queues;
    private final LabDirectoryClient directory;

    public void requireServicePoints(List<LabOrderItemRequest> items) {
        if (items == null || items.isEmpty()) throw new BusinessException(400, "Lab order must contain items");
        Set<String> requested = new HashSet<>();
        for (var item : items) {
            if (item == null || item.servicePointId() == null || item.servicePointId().isBlank()) {
                throw new BusinessException(400, "Lab service point is required");
            }
            requested.add(item.servicePointId().trim().toUpperCase(Locale.ROOT));
        }
        List<LabDirectoryClient.Room> rooms;
        try {
            rooms = data(directory.getRooms());
        } catch (FeignException exception) {
            throw new BusinessException(503, "Cannot verify lab service points with Directory");
        }
        if (rooms == null) throw unavailable();
        Map<String, LabDirectoryClient.Room> catalog = new HashMap<>();
        for (var room : rooms) {
            if (room == null || room.id() == null || room.id().isBlank() || room.roomType() == null
                    || room.isActive() == null || catalog.put(room.id(), room) != null) throw unavailable();
        }
        for (String id : requested) {
            var room = catalog.get(id);
            if (room == null || !Boolean.TRUE.equals(room.isActive()) || !"LAB".equals(room.roomType())) {
                throw new BusinessException(422, "Lab service point must exist, be active and have type LAB");
            }
        }
    }

    public LabConsultationClient.Context requireConsultation(UUID id, UUID patientId, UUID actor, String role, boolean creating) {
        requireRole(actor, role, Set.of("DOCTOR"));
        ApiResponse<LabConsultationClient.Context> response;
        try {
            response = consultations.getById(id);
        } catch (FeignException exception) {
            throw providerError(exception, false);
        }
        var context = data(response);
        if (context == null || !Objects.equals(id, context.id()) || context.patientId() == null
                || context.doctorId() == null || context.status() == null || !CONSULTATION_STATES.contains(context.status())) {
            throw unavailable();
        }
        if (!actor.equals(context.doctorId()) || (patientId != null && !patientId.equals(context.patientId()))) {
            throw new BusinessException(403, "Lab request does not match the assigned consultation");
        }
        if (creating && Set.of("COMPLETED", "CANCELLED", "TRANSFERRED").contains(context.status())) {
            throw new BusinessException(409, "Cannot add lab orders to a closed consultation");
        }
        return context;
    }

    public void requireDoctorOrder(LabOrder order, UUID actor, String role) {
        requireConsultation(order.getConsultationId(), order.getPatientId(), actor, role, false);
    }

    public void requireProfile(UUID patientId, UUID actor, String role) {
        requireRole(actor, role, Set.of("PATIENT", "DOCTOR"));
        ApiResponse<Map<String, Object>> response;
        try {
            response = patients.getById(patientId, actor, role);
        } catch (FeignException exception) {
            throw providerError(exception, false);
        }
        Map<String, Object> patient = data(response);
        if (patient == null || !patientId.equals(requiredUuid(patient, "id"))) throw unavailable();
        if ("PATIENT".equals(role) && !actor.equals(requiredUuid(patient, "userId"))) {
            throw new BusinessException(403, "Cannot read another patient's lab orders");
        }
    }

    public void requireRead(LabOrder order, UUID actor, String role) {
        requireRole(actor, role, Set.of("PATIENT", "DOCTOR", "LAB_TECHNICIAN"));
        switch (role) {
            case "PATIENT" -> {
                requireProfile(order.getPatientId(), actor, role);
                if (!isPublished(order)) throw new BusinessException(403, "Lab results have not been published");
            }
            case "DOCTOR" -> requireDoctorOrder(order, actor, role);
            case "LAB_TECHNICIAN" -> requireTechnician(order, actor, role,
                    Set.of("CALLED", "IN_PROGRESS", "COMPLETED"), null);
            default -> throw new BusinessException(403, "Lab read is not allowed");
        }
    }

    public void requireTechnician(LabOrder order, UUID actor, String role, Set<String> states, String itemServicePoint) {
        requireRole(actor, role, Set.of("LAB_TECHNICIAN"));
        ApiResponse<Map<String, Object>> response;
        try {
            response = itemServicePoint == null ? queues.getCurrent(order.getId(), actor, role)
                    : queues.getAtServicePoint(order.getId(), itemServicePoint, actor, role);
        } catch (FeignException exception) {
            throw providerError(exception, true);
        }
        Map<String, Object> entry = data(response);
        if (entry == null) throw unavailable();
        requiredUuid(entry, "entryId");
        UUID orderId = requiredUuid(entry, "labOrderId");
        UUID patientId = requiredUuid(entry, "patientId");
        UUID consultationId = requiredUuid(entry, "consultationId");
        UUID claimedBy = requiredUuid(entry, "calledByUserId");
        String point = requiredText(entry, "servicePointId");
        String status = requiredText(entry, "queueStatus");
        String type = requiredText(entry, "type");
        if (!"LAB_EXECUTION".equals(type) || !order.getId().equals(orderId)
                || !order.getPatientId().equals(patientId) || !order.getConsultationId().equals(consultationId)) {
            throw new BusinessException(403, "Queue entry does not belong to this lab order");
        }
        if (!actor.equals(claimedBy) || (itemServicePoint != null && !itemServicePoint.equals(point))
                || order.getItems().stream().noneMatch(item -> point.equals(item.getServicePointId()))) {
            throw new BusinessException(403, "Lab work is not claimed for this technician and service point");
        }
        if (!states.contains(status)) throw new BusinessException(409, "Lab queue entry is not ready for this operation");
    }

    public static boolean isPublished(LabOrder order) {
        return order.getStatus() == LabOrderStatus.RESULT_AVAILABLE || order.getStatus() == LabOrderStatus.REVIEWED;
    }

    private void requireRole(UUID actor, String role, Set<String> allowed) {
        if (actor == null) throw new BusinessException(401, "Authenticated user is required");
        if (role == null || !allowed.contains(role)) throw new BusinessException(403, "Role cannot access this lab operation");
    }

    private <T> T data(ApiResponse<T> response) {
        return response == null || response.getStatus() != 200 ? null : response.getData();
    }

    private UUID requiredUuid(Map<String, Object> data, String field) {
        try {
            return UUID.fromString(requiredText(data, field));
        } catch (IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private String requiredText(Map<String, Object> data, String field) {
        Object value = data.get(field);
        if (value == null || value.toString().isBlank()) throw unavailable();
        return value.toString();
    }

    private BusinessException providerError(FeignException exception, boolean queue) {
        int status = exception.status();
        if (queue && status == 404) return new BusinessException(409, "Lab queue entry is not available");
        return new BusinessException(status == 401 || status == 403 || status == 404 ? status : 503,
                "Cannot verify lab access with the authoritative service");
    }

    private BusinessException unavailable() {
        return new BusinessException(503, "Incomplete lab authorization context");
    }
}
