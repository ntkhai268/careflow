package com.careflow.queue.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.queue.domain.*;
import com.careflow.queue.dto.*;
import com.careflow.queue.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class QueueManagementService {
    private static final Set<PriorityLevel> MANUAL_LEVELS = EnumSet.of(
            PriorityLevel.EMERGENCY, PriorityLevel.PRIORITY, PriorityLevel.WALK_IN);
    private static final Set<QueueStatus> DASHBOARD_STATUSES = EnumSet.of(
            QueueStatus.CHECKED_IN, QueueStatus.CALLED, QueueStatus.IN_PROGRESS);

    private final QueueConfigRepository configs;
    private final QueueNumberSequenceRepository sequences;
    private final QueueEntryRepository entries;
    private final QueueEventService events;
    private final QrTokenService qrTokens;
    private final ZoneId businessZone;

    public QueueManagementService(QueueConfigRepository configs, QueueNumberSequenceRepository sequences,
                                  QueueEntryRepository entries, QueueEventService events, QrTokenService qrTokens,
                                  @Value("${queue.business-zone:Asia/Ho_Chi_Minh}") String businessZone) {
        this.configs = configs;
        this.sequences = sequences;
        this.entries = entries;
        this.events = events;
        this.qrTokens = qrTokens;
        this.businessZone = ZoneId.of(businessZone);
    }

    public LocalDate businessDate() {
        return LocalDate.now(businessZone);
    }

    @Transactional
    public QueueConfigResponse saveConfig(UUID departmentId, QueueConfigRequest request) {
        QueueConfig config = configs.findByDepartmentId(departmentId).orElseGet(QueueConfig::new);
        config.setDepartmentId(departmentId);
        config.setDepartmentNameSnapshot(request.departmentName().trim());
        config.setQueuePrefix(request.queuePrefix().trim().toUpperCase(Locale.ROOT));
        config.setRoomCode(request.roomCode());
        config.setPriorityRatioN(request.priorityRatioN());
        config.setNormalRatioM(request.normalRatioM());
        config.setAvgConsultationMinutes(request.avgConsultationMinutes());
        config.setNearTurnThreshold(request.nearTurnThreshold());
        config.setMissedPolicy(request.missedPolicy());
        config.setActive(request.active());
        if (config.getSchedulerDate() == null) config.resetScheduler(businessDate());
        return QueueConfigResponse.from(configs.save(config));
    }

    @Transactional(readOnly = true)
    public QueueConfigResponse getConfig(UUID departmentId) {
        return QueueConfigResponse.from(requireConfig(departmentId));
    }

    @Transactional
    public QueueEntryResponse manualIntake(ManualIntakeRequest request, String correlationId) {
        if (!MANUAL_LEVELS.contains(request.priorityLevel())) {
            throw new BusinessException(400, "Tiếp nhận thủ công chỉ cho EMERGENCY, PRIORITY hoặc WALK_IN");
        }
        QueueConfig config = requireLockedConfig(request.departmentId());
        LocalDate date = businessDate();
        QueueEntry entry = newEntry(config, date, request.patientId(), request.userId(), null,
                request.priorityLevel(), QueueStatus.CHECKED_IN);
        Instant now = Instant.now();
        entry.setCheckedInAt(now);
        entry.setEligibleSinceAt(now);
        entries.saveAndFlush(entry);
        events.append(entry, config, "QUEUE_CHECKED_IN", AppConstants.RK_QUEUE_CHECKED_IN,
                correlationId, Map.of("priorityLevel", entry.getPriorityLevel().name()));
        return response(entry, config);
    }

    @Transactional(readOnly = true)
    public QueueEntryResponse myStatus(UUID userId) {
        QueueEntry entry = entries.findByUserIdAndQueueDateOrderByCreatedAtDesc(userId, businessDate()).stream()
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "userId/date", userId));
        QueueConfig config = configs.findById(entry.getQueueConfigId())
                .orElseThrow(() -> new ResourceNotFoundException("QueueConfig", "id", entry.getQueueConfigId()));
        return response(entry, config);
    }

    @Transactional(readOnly = true)
    public String issueQr(UUID appointmentId, UUID userId) {
        QueueEntry entry = entries.findByAppointmentIdAndUserId(appointmentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "appointmentId", appointmentId));
        if (!entry.getQueueDate().equals(businessDate())) throw new BusinessException(422, "Lịch hẹn không thuộc ngày hiện tại");
        if (entry.getStatus() != QueueStatus.WAITING && entry.getStatus() != QueueStatus.CHECKED_IN) {
            throw new BusinessException(409, "Trạng thái lượt khám không cho phép sinh QR");
        }
        return qrTokens.issue(entry);
    }

    @Transactional
    public QueueEntryResponse checkIn(String token, UUID authenticatedUserId, String correlationId) {
        QrTokenService.QrClaims claims = qrTokens.verify(token);
        if (!claims.userId().equals(authenticatedUserId)) throw new BusinessException(403, "QR không thuộc tài khoản hiện tại");
        if (!claims.queueDate().equals(businessDate())) throw new BusinessException(422, "QR không thuộc ngày hiện tại");
        QueueEntry entry = entries.findByAppointmentIdForUpdate(claims.appointmentId())
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "appointmentId", claims.appointmentId()));
        if (!entry.getUserId().equals(authenticatedUserId)) throw new BusinessException(403, "Không có quyền check-in lượt này");
        QueueConfig config = requireConfig(entry.getDepartmentId());
        if (entry.getStatus() == QueueStatus.CHECKED_IN) return response(entry, config);
        if (entry.getStatus() == QueueStatus.CANCELLED) throw new BusinessException(409, "Lịch hẹn đã bị hủy");
        if (entry.getStatus() != QueueStatus.WAITING) throw new BusinessException(409, "Trạng thái lượt khám không cho phép check-in");
        Instant now = Instant.now();
        entry.setStatus(QueueStatus.CHECKED_IN);
        entry.setCheckedInAt(now);
        entry.setEligibleSinceAt(now);
        entries.saveAndFlush(entry);
        events.append(entry, config, "QUEUE_CHECKED_IN", AppConstants.RK_QUEUE_CHECKED_IN,
                correlationId, Map.of("priorityLevel", entry.getPriorityLevel().name()));
        return response(entry, config);
    }

    @Transactional(readOnly = true)
    public QueueDashboardResponse dashboard(UUID departmentId) {
        QueueConfig config = requireConfig(departmentId);
        LocalDate date = businessDate();
        List<QueueEntry> queue = entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                config.getId(), date, DASHBOARD_STATUSES);
        List<QueueEntryResponse> response = queue.stream().map(e -> response(e, config)).toList();
        return new QueueDashboardResponse(departmentId, config.getDepartmentNameSnapshot(), config.getRoomCode(), date, response);
    }

    @Transactional
    public Optional<QueueEntryResponse> callNext(UUID departmentId, String correlationId) {
        QueueConfig config = requireLockedConfig(departmentId);
        LocalDate date = businessDate();
        if (!date.equals(config.getSchedulerDate())) config.resetScheduler(date);

        QueueEntry candidate = firstCandidate(config, date, PriorityLevel.EMERGENCY);
        if (candidate == null && config.getCyclePhase() == CyclePhase.PRIORITY) {
            candidate = firstCandidate(config, date, PriorityLevel.PRIORITY);
            if (candidate != null) advancePriority(config);
            else { config.setCyclePhase(CyclePhase.NORMAL); config.setServedInPhase(0); }
        }
        if (candidate == null && config.getCyclePhase() == CyclePhase.NORMAL) {
            PriorityLevel preferred = config.getNormalCursor() == NormalCursor.APPOINTMENT
                    ? PriorityLevel.APPOINTMENT : PriorityLevel.WALK_IN;
            candidate = firstCandidate(config, date, preferred);
            if (candidate == null) candidate = firstCandidate(config, date,
                    preferred == PriorityLevel.APPOINTMENT ? PriorityLevel.WALK_IN : PriorityLevel.APPOINTMENT);
            if (candidate != null) advanceNormal(config, candidate.getPriorityLevel());
        }
        if (candidate == null) {
            candidate = firstCandidate(config, date, PriorityLevel.PRIORITY);
            if (candidate != null) {
                config.setCyclePhase(CyclePhase.PRIORITY);
                config.setServedInPhase(0);
                advancePriority(config);
            }
        }
        if (candidate == null) return Optional.empty();

        candidate.setStatus(QueueStatus.CALLED);
        candidate.setCalledAt(Instant.now());
        entries.saveAndFlush(candidate);
        configs.save(config);
        events.append(candidate, config, "QUEUE_CALLED", AppConstants.RK_QUEUE_CALLED, correlationId, Map.of());
        appendNearTurnEvents(config, date, correlationId);
        return Optional.of(response(candidate, config));
    }

    @Transactional
    public QueueEntryResponse miss(UUID entryId, String correlationId) {
        QueueEntry entry = requireEntryForUpdate(entryId);
        if (entry.getStatus() != QueueStatus.CALLED) throw invalidTransition(entry, QueueStatus.MISSED);
        QueueConfig config = requireConfig(entry.getDepartmentId());
        entry.setStatus(QueueStatus.MISSED);
        entry.setMissedAt(Instant.now());
        entry.setMissedCount(entry.getMissedCount() + 1);
        entries.saveAndFlush(entry);
        events.append(entry, config, "QUEUE_MISSED", AppConstants.RK_QUEUE_MISSED, correlationId,
                Map.of("missedCount", entry.getMissedCount()));
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse requeue(UUID entryId, RequeueRequest request, String correlationId) {
        QueueEntry entry = requireEntryForUpdate(entryId);
        if (entry.getStatus() != QueueStatus.MISSED) throw invalidTransition(entry, QueueStatus.CHECKED_IN);
        QueueConfig config = requireConfig(entry.getDepartmentId());
        boolean back;
        if (config.getMissedPolicy() == MissedPolicy.REQUIRE_MANUAL) {
            if (request == null || request.position() == null) {
                throw new BusinessException(400, "Policy REQUIRE_MANUAL yêu cầu position FRONT hoặc BACK");
            }
            back = request.position() == RequeueRequest.Position.BACK;
        } else {
            back = config.getMissedPolicy() == MissedPolicy.REQUEUE_BACK;
        }
        if (back) entry.setEligibleSinceAt(Instant.now());
        entry.setStatus(QueueStatus.CHECKED_IN);
        entries.saveAndFlush(entry);
        events.append(entry, config, "QUEUE_CHECKED_IN", AppConstants.RK_QUEUE_CHECKED_IN, correlationId,
                Map.of("requeued", true, "position", back ? "BACK" : "FRONT"));
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse start(UUID entryId, String correlationId) {
        QueueEntry entry = requireEntryForUpdate(entryId);
        if (entry.getStatus() != QueueStatus.CALLED) throw invalidTransition(entry, QueueStatus.IN_PROGRESS);
        QueueConfig config = requireConfig(entry.getDepartmentId());
        entry.setStatus(QueueStatus.IN_PROGRESS);
        entry.setStartedAt(Instant.now());
        entries.saveAndFlush(entry);
        events.append(entry, config, "QUEUE_STARTED", "queue.started", correlationId, Map.of());
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse complete(UUID entryId, String correlationId) {
        QueueEntry snapshot = entries.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", entryId));
        QueueConfig config = requireLockedConfig(snapshot.getDepartmentId());
        QueueEntry entry = requireEntryForUpdate(entryId);
        if (entry.getStatus() != QueueStatus.IN_PROGRESS) throw invalidTransition(entry, QueueStatus.COMPLETED);
        entry.setStatus(QueueStatus.COMPLETED);
        entry.setCompletedAt(Instant.now());
        entries.saveAndFlush(entry);
        updateAverage(config, entry.getQueueDate());
        events.append(entry, config, "QUEUE_COMPLETED", AppConstants.RK_QUEUE_COMPLETED, correlationId,
                Map.of("consultationMinutes", consultationMinutes(entry)));
        return response(entry, config);
    }

    private QueueEntry newEntry(QueueConfig config, LocalDate date, UUID patientId, UUID userId,
                                UUID appointmentId, PriorityLevel priority, QueueStatus status) {
        QueueNumberSequence sequence = sequences.findByQueueConfigIdAndQueueDate(config.getId(), date).orElseGet(() -> {
            QueueNumberSequence created = new QueueNumberSequence();
            created.setQueueConfigId(config.getId());
            created.setQueueDate(date);
            return sequences.saveAndFlush(created);
        });
        sequence.setLastNumber(sequence.getLastNumber() + 1);
        sequences.save(sequence);
        QueueEntry entry = new QueueEntry();
        entry.setQueueConfigId(config.getId());
        entry.setDepartmentId(config.getDepartmentId());
        entry.setAppointmentId(appointmentId);
        entry.setPatientId(patientId);
        entry.setUserId(userId);
        entry.setQueueDate(date);
        entry.setSequenceNumber(sequence.getLastNumber());
        entry.setQueueNumber(config.getQueuePrefix() + "-" + String.format("%03d", sequence.getLastNumber()));
        entry.setPriorityLevel(priority);
        entry.setStatus(status);
        return entry;
    }

    public QueueEntry createAppointmentEntry(QueueConfig config, LocalDate date, UUID appointmentId,
                                             UUID patientId, UUID userId) {
        return newEntry(config, date, patientId, userId, appointmentId, PriorityLevel.APPOINTMENT, QueueStatus.WAITING);
    }

    public QueueConfig requireLockedConfig(UUID departmentId) {
        return configs.findActiveForUpdate(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Active QueueConfig", "departmentId", departmentId));
    }

    private QueueConfig requireConfig(UUID departmentId) {
        return configs.findByDepartmentIdAndActiveTrue(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Active QueueConfig", "departmentId", departmentId));
    }

    private QueueEntry requireEntryForUpdate(UUID id) {
        return entries.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", id));
    }

    private QueueEntry firstCandidate(QueueConfig config, LocalDate date, PriorityLevel level) {
        return entries.findCandidatesForUpdate(config.getId(), date, level, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
    }

    private void advancePriority(QueueConfig config) {
        config.setServedInPhase(config.getServedInPhase() + 1);
        if (config.getServedInPhase() >= config.getPriorityRatioN()) {
            config.setCyclePhase(CyclePhase.NORMAL);
            config.setServedInPhase(0);
        }
    }

    private void advanceNormal(QueueConfig config, PriorityLevel selected) {
        config.setNormalCursor(selected == PriorityLevel.APPOINTMENT ? NormalCursor.WALK_IN : NormalCursor.APPOINTMENT);
        config.setServedInPhase(config.getServedInPhase() + 1);
        if (config.getServedInPhase() >= config.getNormalRatioM()) {
            config.setCyclePhase(CyclePhase.PRIORITY);
            config.setServedInPhase(0);
        }
    }

    private QueueEntryResponse response(QueueEntry entry, QueueConfig config) {
        Integer position = null;
        Integer wait = entry.getEstimatedWaitMinutes();
        if (entry.getStatus() == QueueStatus.CHECKED_IN) {
            List<QueueEntry> active = entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                    config.getId(), entry.getQueueDate(), Set.of(QueueStatus.CHECKED_IN));
            List<UUID> order = QueueScheduleSimulator.order(config, entry.getQueueDate(), active);
            int index = order.indexOf(entry.getId());
            if (index >= 0) {
                position = index + 1;
                int currentConsultation = entries.existsByQueueConfigIdAndQueueDateAndStatus(
                        config.getId(), entry.getQueueDate(), QueueStatus.IN_PROGRESS)
                        ? config.getAvgConsultationMinutes() : 0;
                wait = currentConsultation + Math.max(position - 1, 0) * config.getAvgConsultationMinutes();
                entry.setEstimatedWaitMinutes(wait);
            }
        } else if (entry.getStatus() == QueueStatus.CALLED || entry.getStatus() == QueueStatus.IN_PROGRESS) {
            position = 0;
            wait = 0;
        }
        return QueueEntryResponse.from(entry, QueueConfigResponse.from(config), position, wait);
    }

    private void appendNearTurnEvents(QueueConfig config, LocalDate date, String correlationId) {
        List<QueueEntry> waiting = entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                config.getId(), date, Set.of(QueueStatus.CHECKED_IN));
        List<UUID> order = QueueScheduleSimulator.order(config, date, waiting);
        Map<UUID, QueueEntry> byId = new HashMap<>();
        waiting.forEach(entry -> byId.put(entry.getId(), entry));
        int limit = Math.min(config.getNearTurnThreshold(), order.size());
        for (int index = 0; index < limit; index++) {
            QueueEntry entry = byId.get(order.get(index));
            int position = index + 1;
            int wait = Math.max(position - 1, 0) * config.getAvgConsultationMinutes();
            events.append(entry, config, "QUEUE_NEAR_TURN", AppConstants.RK_QUEUE_NEAR_TURN, correlationId,
                    Map.of("effectivePosition", position, "estimatedWaitMinutes", wait));
        }
    }

    private void updateAverage(QueueConfig config, LocalDate date) {
        List<QueueEntry> completed = entries.findRecentCompleted(config.getId(), date, PageRequest.of(0, 20));
        if (!completed.isEmpty()) {
            int average = (int) Math.round(completed.stream().mapToLong(this::consultationMinutes).average()
                    .orElse(config.getAvgConsultationMinutes()));
            config.setAvgConsultationMinutes(Math.max(1, average));
            configs.save(config);
        }
    }

    private long consultationMinutes(QueueEntry entry) {
        return Math.max(1, Duration.between(entry.getStartedAt(), entry.getCompletedAt()).toMinutes());
    }

    private BusinessException invalidTransition(QueueEntry entry, QueueStatus target) {
        return new BusinessException(409, "Không thể chuyển QueueEntry từ " + entry.getStatus() + " sang " + target);
    }
}
