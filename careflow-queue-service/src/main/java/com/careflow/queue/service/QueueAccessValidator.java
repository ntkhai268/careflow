package com.careflow.queue.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.queue.client.DirectoryClient;
import com.careflow.queue.client.dto.DoctorAssignmentResponse;
import com.careflow.queue.domain.QueueConfig;
import com.careflow.queue.domain.QueueEntry;
import com.careflow.queue.domain.QueueType;
import com.careflow.queue.repository.QueueEntryRepository;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

final class QueueAccessValidator {
    private final QueueEntryRepository entries;
    private final DirectoryClient directoryClient;

    QueueAccessValidator(QueueEntryRepository entries, DirectoryClient directoryClient) {
        this.entries = entries;
        this.directoryClient = directoryClient;
    }

    void requireRoomAccess(String roomId, UUID userId, String role) {
        if (AppConstants.ROLE_ADMIN.equalsIgnoreCase(role)) {
            return;
        }
        if (directoryClient == null) {
            throw new BusinessException(503, "Không thể xác minh assignment của actor với phòng");
        }
        boolean allowed = false;
        try {
            if (AppConstants.ROLE_DOCTOR.equalsIgnoreCase(role)) {
                ApiResponse<DoctorAssignmentResponse> response = directoryClient.getDoctorByUserId(userId);
                DoctorAssignmentResponse doctor = response == null ? null : response.getData();
                allowed = doctor != null
                        && Boolean.TRUE.equals(doctor.getIsActive())
                        && roomId.equals(doctor.getAssignedRoomId());
            } else if (AppConstants.ROLE_STAFF.equalsIgnoreCase(role)) {
                ApiResponse<Boolean> response = directoryClient.hasStaffRoomAccess(userId, roomId);
                allowed = response != null && Boolean.TRUE.equals(response.getData());
            }
        } catch (RuntimeException ex) {
            throw new BusinessException(403, "Không thể xác minh assignment của actor với phòng");
        }
        if (!allowed) {
            throw new BusinessException(403, "Actor chưa được phân công vào phòng này");
        }
    }

    void requireServicePointAccess(String servicePointId, UUID userId, String role) {
        String normalizedServicePointId = normalizeServicePointId(servicePointId);
        if (AppConstants.ROLE_ADMIN.equalsIgnoreCase(role)) {
            return;
        }
        if (directoryClient == null) {
            throw new BusinessException(503, "Không thể xác minh assignment của actor với service point");
        }
        boolean allowed = false;
        try {
            if (AppConstants.ROLE_STAFF.equalsIgnoreCase(role)
                    || AppConstants.ROLE_LAB_TECHNICIAN.equalsIgnoreCase(role)) {
                ApiResponse<Boolean> response = directoryClient.hasStaffRoomAccess(
                        userId, normalizedServicePointId);
                allowed = response != null && Boolean.TRUE.equals(response.getData());
            }
        } catch (RuntimeException ex) {
            throw new BusinessException(403, "Không thể xác minh assignment của actor với service point");
        }
        if (!allowed) {
            throw new BusinessException(403, "Actor chưa được phân công vào service point này");
        }
    }

    void requireClinicalOperator(UUID entryId, String role) {
        QueueEntry entry = entries.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", entryId));
        if (entry.getQueueType() == QueueType.CONSULTATION && !"DOCTOR".equals(role)) {
            throw new BusinessException(403, "Chỉ bác sĩ phụ trách được bắt đầu/hoàn tất lượt khám.");
        }
    }

    void requireEntryAssignmentAccess(UUID entryId,
                                      UUID requesterUserId,
                                      String role,
                                      Function<QueueEntry, QueueConfig> configResolver) {
        QueueEntry entry = entries.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", entryId));
        if (entry.getQueueType() != QueueType.CONSULTATION) {
            if ((entry.getQueueType() == QueueType.LAB_EXECUTION
                    || entry.getQueueType() == QueueType.PHARMACY_DISPENSING)
                    && entry.getServicePointId() != null) {
                requireServicePointAccess(entry.getServicePointId(), requesterUserId, role);
                return;
            }
            throw new BusinessException(403, "Lượt phục vụ không có service point assignment hợp lệ");
        }
        QueueConfig config = configResolver.apply(entry);
        if (config == null) {
            throw new BusinessException(403, "Lượt khám không có phòng assignment hợp lệ");
        }
        requireRoomAccess(config.getRoomCode(), requesterUserId, role);
    }

    static String normalizeServicePointId(String servicePointId) {
        if (servicePointId == null || servicePointId.isBlank()) {
            throw new BusinessException(400, "servicePointId không được để trống");
        }
        return servicePointId.trim().toUpperCase(Locale.ROOT);
    }
}
