package com.careflow.queue.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.queue.client.ReceptionRoomClient;
import com.careflow.queue.domain.MissedPolicy;
import com.careflow.queue.domain.QueueConfig;
import com.careflow.queue.dto.QueueConfigResponse;
import com.careflow.queue.repository.QueueConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class QueueRoomConfigurationService {
    private static final String ROOM_ID_PATTERN = "[A-Za-z0-9][A-Za-z0-9_-]{0,49}";
    private static final int DEFAULT_MAX_CALL_ATTEMPTS = 3;
    private static final String SCHEDULER_POLICY = "ROUND_ROBIN_PRIORITY_NORMAL_RESULT_REVIEW";

    private final QueueConfigRepository configs;
    private final ReceptionRoomClient directory;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public QueueRoomConfigurationService(QueueConfigRepository configs,
                                         ReceptionRoomClient directory,
                                         JdbcTemplate jdbc,
                                         ObjectMapper json) {
        this.configs = configs;
        this.directory = directory;
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Policy(
            @Min(0) long expectedVersion,
            @Min(1) @Max(240) int avgConsultationMinutes,
            @Min(1) @Max(20) int nearTurnThreshold,
            @NotNull MissedPolicy missedPolicy,
            boolean active
    ) {}

    public record View(
            QueueConfigResponse config,
            long version,
            int maxCallAttempts,
            String schedulerPolicy
    ) {}

    @Transactional
    public QueueConfig ensure(String roomId, UUID expectedDepartment) {
        if (roomId == null || !roomId.matches(ROOM_ID_PATTERN)) {
            throw new BusinessException(400, "Chọn phòng khám hợp lệ.");
        }
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "queue-room:" + roomId);

        ApiResponse<List<ReceptionRoomClient.ReceptionRoom>> roomResponse = directory.rooms();
        if (roomResponse == null || roomResponse.getData() == null) {
            throw new BusinessException(503, "Không thể xác minh danh mục phòng.");
        }
        ReceptionRoomClient.ReceptionRoom room = roomResponse.getData().stream()
                .filter(candidate -> roomId.equals(candidate.id())
                        && Boolean.TRUE.equals(candidate.isActive())
                        && "CONSULTATION".equals(candidate.roomType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(409, "Phòng khám không hoạt động."));

        ApiResponse<ReceptionRoomClient.Department> departmentResponse =
                directory.department(room.departmentCode());
        ReceptionRoomClient.Department department =
                departmentResponse == null ? null : departmentResponse.getData();
        if (department == null || !Boolean.TRUE.equals(department.isActive()) || department.id() == null) {
            throw new BusinessException(503, "Không thể xác minh khoa của phòng.");
        }
        if (expectedDepartment != null && !expectedDepartment.equals(department.id())) {
            throw new BusinessException(409, "Phòng không thuộc khoa được chọn.");
        }

        Optional<QueueConfig> existing = configs.findByRoomCode(roomId);
        if (existing.isPresent()) {
            if (!department.id().equals(existing.get().getDepartmentId())) {
                throw new BusinessException(409, "Khoa/phòng không khớp cấu hình đã lưu.");
            }
            return configs.findByIdForUpdate(existing.get().getId()).orElseThrow();
        }

        QueueConfig template = configs.findAllByDepartmentIdOrderByRoomCodeAsc(department.id()).stream()
                .findFirst()
                .orElse(null);
        QueueConfig result = new QueueConfig();
        result.setDepartmentId(department.id());
        result.setDepartmentNameSnapshot(department.name());
        result.setRoomCode(roomId);
        String defaultPrefix = room.departmentCode().substring(0, Math.min(3, room.departmentCode().length()));
        result.setQueuePrefix(template == null ? defaultPrefix : template.getQueuePrefix());
        if (template != null) {
            result.setAvgConsultationMinutes(template.getAvgConsultationMinutes());
            result.setNearTurnThreshold(template.getNearTurnThreshold());
            result.setMissedPolicy(template.getMissedPolicy());
        }
        return configs.saveAndFlush(result);
    }

    @Transactional
    public View get(String roomId) {
        return view(ensure(roomId, null));
    }

    @Transactional
    public View save(String roomId, Policy request, UUID actor) {
        QueueConfig config = ensure(roomId, null);
        if (config.getPolicyVersion() != request.expectedVersion()) {
            throw new BusinessException(409, "Cấu hình phòng đã thay đổi. Hãy tải lại.");
        }
        config.setAvgConsultationMinutes(request.avgConsultationMinutes());
        config.setNearTurnThreshold(request.nearTurnThreshold());
        config.setMissedPolicy(request.missedPolicy());
        config.setActive(request.active());
        config.setPolicyVersion(config.getPolicyVersion() + 1);
        configs.saveAndFlush(config);
        try {
            jdbc.update(
                    "INSERT INTO queue.room_policy_audit(id,queue_config_id,actor_id,policy_version,settings) VALUES (?,?,?,?,?::jsonb)",
                    UUID.randomUUID(),
                    config.getId(),
                    actor,
                    config.getPolicyVersion(),
                    json.writeValueAsString(request));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(error);
        }
        return view(config);
    }

    public void lockPatient(UUID patient, UUID department, LocalDate date) {
        String lockKey = "queue-patient:" + patient + ":" + department + ":" + date;
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", lockKey);
    }

    private View view(QueueConfig config) {
        return new View(
                QueueConfigResponse.from(config),
                config.getPolicyVersion(),
                DEFAULT_MAX_CALL_ATTEMPTS,
                SCHEDULER_POLICY);
    }
}
