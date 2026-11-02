package com.careflow.queue.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.queue.client.DirectoryClient;
import com.careflow.queue.client.PatientIntakeClient;
import com.careflow.queue.client.ReceptionRoomClient;
import com.careflow.queue.domain.PriorityLevel;
import com.careflow.queue.domain.QueueConfig;
import com.careflow.queue.dto.ManualIntakeRequest;
import com.careflow.queue.dto.QueueEntryResponse;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ReceptionIntakeService {
    private static final Set<String> INTAKE_ROLES = Set.of("STAFF", "ADMIN");

    private final QueueManagementService queues;
    private final DirectoryClient directory;
    private final PatientIntakeClient patients;
    private final ReceptionRoomClient rooms;

    public ReceptionIntakeService(QueueManagementService queues,
                                  DirectoryClient directory,
                                  PatientIntakeClient patients,
                                  ReceptionRoomClient rooms) {
        this.queues = queues;
        this.directory = directory;
        this.patients = patients;
        this.rooms = rooms;
    }

    @Transactional
    public QueueEntryResponse intake(ManualIntakeRequest request,
                                     UUID actor,
                                     String role,
                                     String key,
                                     String correlationId) {
        validateIntakeActorRole(actor, role, request.priorityLevel());
        QueueConfig config = request.roomId() == null
                ? queues.requireLockedConfig(request.departmentId())
                : queues.requireLockedRoomConfig(request.roomId(), request.departmentId());

        verifyConsultationRoomActive(config.getRoomCode());
        if ("STAFF".equals(role)) {
            verifyStaffRoomAssignment(actor, config.getRoomCode());
        }

        UUID owner = resolveVerifiedPatientOwner(request.patientId(), request.userId());
        if (request.serviceCode() != null) {
            verifyServiceActive(request.serviceCode());
        }

        String patientName = null;
        if (request.roomId() != null) {
            patientName = resolveVerifiedPatientName(request.patientId(), owner, config.getRoomCode());
        }

        ManualIntakeRequest verifiedRequest = new ManualIntakeRequest(
                request.patientId(),
                owner,
                request.departmentId(),
                request.priorityLevel(),
                request.roomId(),
                request.serviceCode());
        return queues.manualIntake(verifiedRequest, actor, key, correlationId, patientName);
    }

    private void validateIntakeActorRole(UUID actor, String role, PriorityLevel priorityLevel) {
        if (actor == null || role == null) {
            throw new BusinessException(401, "Thiếu phiên đăng nhập");
        }
        if (!INTAKE_ROLES.contains(role)) {
            throw new BusinessException(403, "Không có quyền tiếp nhận");
        }
        if ("STAFF".equals(role) && priorityLevel != PriorityLevel.WALK_IN) {
            throw new BusinessException(
                    403,
                    "Tiếp nhận STAFF chỉ tạo lượt WALK_IN; không tự cấp quyền phân loại ưu tiên hoặc cấp cứu");
        }
    }

    private void verifyConsultationRoomActive(String roomCode) {
        ApiResponse<List<ReceptionRoomClient.ReceptionRoom>> catalog;
        try {
            catalog = rooms.rooms();
        } catch (RuntimeException unavailable) {
            throw new BusinessException(503, "Không thể xác minh phòng tiếp nhận");
        }
        boolean activeConsultationRoom = catalog != null
                && catalog.getData() != null
                && catalog.getData().stream().anyMatch(room ->
                        roomCode.equals(room.id())
                                && Boolean.TRUE.equals(room.isActive())
                                && "CONSULTATION".equals(room.roomType()));
        if (!activeConsultationRoom) {
            throw new BusinessException(403, "Phòng tiếp nhận không hoạt động");
        }
    }

    private void verifyStaffRoomAssignment(UUID actor, String roomCode) {
        ApiResponse<Boolean> access;
        try {
            access = directory.hasStaffRoomAccess(actor, roomCode);
        } catch (RuntimeException unavailable) {
            throw new BusinessException(503, "Không thể xác minh phân công tiếp nhận");
        }
        if (access == null || !Boolean.TRUE.equals(access.getData())) {
            throw new BusinessException(403, "Chưa được phân công phòng tiếp nhận này");
        }
    }

    private UUID resolveVerifiedPatientOwner(UUID patientId, UUID requestedUserId) {
        ApiResponse<UUID> patient;
        try {
            patient = patients.owner(patientId);
        } catch (FeignException missingOrDenied) {
            if (missingOrDenied.status() == 404) {
                throw new BusinessException(404, "Hồ sơ bệnh nhân không tồn tại");
            }
            if (missingOrDenied.status() == 403 || missingOrDenied.status() == 401) {
                throw new BusinessException(403, "Không có quyền tra cứu hồ sơ tiếp nhận");
            }
            throw new BusinessException(503, "Không thể xác minh hồ sơ tiếp nhận");
        }
        if (patient == null || patient.getStatus() != 200) {
            throw new BusinessException(503, "Không thể xác minh hồ sơ tiếp nhận");
        }
        UUID owner = patient.getData();
        if (requestedUserId != null && !requestedUserId.equals(owner)) {
            throw new BusinessException(403, "userId không khớp chủ hồ sơ bệnh nhân");
        }
        return owner;
    }

    private void verifyServiceActive(String serviceCode) {
        try {
            ApiResponse<ReceptionRoomClient.Service> service = rooms.service(serviceCode);
            if (service == null
                    || service.getData() == null
                    || !service.getData().active()
                    || !serviceCode.equals(service.getData().code())) {
                throw new BusinessException(409, "Dịch vụ không hoạt động.");
            }
        } catch (FeignException ex) {
            throw new BusinessException(ex.status() == 404 ? 409 : 503, "Không thể xác minh dịch vụ khám.");
        }
    }

    private String resolveVerifiedPatientName(UUID patientId, UUID owner, String roomCode) {
        ApiResponse<PatientIntakeClient.ReceptionPatient> profile;
        try {
            profile = patients.minimum(patientId, roomCode);
        } catch (FeignException ex) {
            throw new BusinessException(
                    ex.status() == 403 ? 403 : 503,
                    "Không thể xác minh hồ sơ trong phòng tiếp nhận.");
        }
        if (profile == null
                || profile.getData() == null
                || !patientId.equals(profile.getData().id())
                || !Objects.equals(owner, profile.getData().userId())
                || profile.getData().fullName() == null) {
            throw new BusinessException(503, "Không thể xác minh tên hồ sơ tiếp nhận.");
        }
        return profile.getData().fullName();
    }
}
