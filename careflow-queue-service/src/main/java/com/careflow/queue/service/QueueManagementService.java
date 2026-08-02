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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class QueueManagementService {
    private static final String COMMAND_MANUAL_INTAKE = "MANUAL_INTAKE";
    private static final String COMMAND_CALL_NEXT = "CALL_NEXT";
    private static final String COMMAND_CALL_ENTRY = "CALL_ENTRY";
    private static final int MAX_CALL_ATTEMPTS = 3;
    private static final Set<PriorityLevel> MANUAL_LEVELS = EnumSet.of(
            PriorityLevel.EMERGENCY, PriorityLevel.PRIORITY, PriorityLevel.WALK_IN);
    private static final Set<QueueStatus> DASHBOARD_STATUSES = EnumSet.of(
            QueueStatus.CHECKED_IN, QueueStatus.CALLED, QueueStatus.IN_PROGRESS);
    private static final Set<QueueStatus> ACTIVE_PATIENT_STATUSES = EnumSet.of(
            QueueStatus.WAITING, QueueStatus.CHECKED_IN, QueueStatus.CALLED,
            QueueStatus.IN_PROGRESS, QueueStatus.MISSED);
    private static final Set<QueueStatus> SERVING_STATUSES = EnumSet.of(
            QueueStatus.CALLED, QueueStatus.IN_PROGRESS);

    private final QueueConfigRepository configs;
    private final QueueNumberSequenceRepository sequences;
    private final QueueEntryRepository entries;
    private final IdempotencyRecordRepository idempotencyRecords;
    private final QueueEventService events;
    private final QrTokenService qrTokens;
    private final ZoneId businessZone;

    public QueueManagementService(QueueConfigRepository configs, QueueNumberSequenceRepository sequences,
                                  QueueEntryRepository entries, IdempotencyRecordRepository idempotencyRecords,
                                  QueueEventService events, QrTokenService qrTokens,
                                  @Value("${queue.business-zone:Asia/Ho_Chi_Minh}") String businessZone) {
        this.configs = configs;
        this.sequences = sequences;
        this.entries = entries;
        this.idempotencyRecords = idempotencyRecords;
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
        boolean schedulingChanged = config.getId() != null
                && (config.getPriorityRatioN() != request.priorityRatioN()
                || config.getNormalRatioM() != request.normalRatioM());
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
        if (config.getSchedulerDate() == null || schedulingChanged) config.resetScheduler(businessDate());
        return QueueConfigResponse.from(configs.save(config));
    }

    @Transactional(readOnly = true)
    public QueueConfigResponse getConfig(UUID departmentId) {
        return QueueConfigResponse.from(requireConfig(departmentId));
    }

    @Transactional
    public QueueEntryResponse manualIntake(ManualIntakeRequest request, String idempotencyKey, String correlationId) {
        if (!MANUAL_LEVELS.contains(request.priorityLevel())) {
            throw new BusinessException(400, "Tiếp nhận thủ công chỉ cho EMERGENCY, PRIORITY hoặc WALK_IN");
        }
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        String requestFingerprint = fingerprint(
                request.patientId(), request.userId(), request.departmentId(), request.priorityLevel());
        QueueConfig config = requireLockedConfig(request.departmentId());
        Optional<QueueEntryResponse> replay = replay(
                COMMAND_MANUAL_INTAKE, request.departmentId(), normalizedKey, requestFingerprint);
        if (replay.isPresent()) return replay.get();
        LocalDate date = businessDate();
        QueueEntry entry = newEntry(config, date, request.patientId(), request.userId(), null,
                request.priorityLevel(), QueueStatus.CHECKED_IN);
        Instant now = Instant.now();
        entry.setCheckedInAt(now);
        entry.setEligibleSinceAt(now);
        entries.saveAndFlush(entry);
        events.append(entry, config, "PatientCheckedIn", AppConstants.RK_QUEUE_CHECKED_IN,
                correlationId, Map.of("priorityLevel", entry.getPriorityLevel().name()));
        recordCommand(COMMAND_MANUAL_INTAKE, request.departmentId(), normalizedKey, requestFingerprint, entry);
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
    public QueueEntryResponse patientCurrent(UUID patientId, UUID userId, boolean clinicalStaff) {
        Optional<QueueEntry> current = clinicalStaff
                ? entries.findFirstByPatientIdAndQueueDateAndStatusInOrderByCreatedAtDesc(
                        patientId, businessDate(), ACTIVE_PATIENT_STATUSES)
                : entries.findFirstByPatientIdAndUserIdAndQueueDateAndStatusInOrderByCreatedAtDesc(
                        patientId, userId, businessDate(), ACTIVE_PATIENT_STATUSES);
        QueueEntry entry = current
                .orElseThrow(() -> new ResourceNotFoundException(
                        "QueueEntry", "patientId/userId", patientId));
        QueueConfig config = configs.findById(entry.getQueueConfigId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "QueueConfig", "id", entry.getQueueConfigId()));
        return response(entry, config);
    }

    @Transactional(readOnly = true)
    public VisitTicketResponse ticket(UUID appointmentId, UUID requesterUserId, boolean clinicalStaff) {
        QueueEntry entry = entries.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "VisitTicket", "appointmentId", appointmentId));
        if (!clinicalStaff && !entry.getUserId().equals(requesterUserId)) {
            throw new BusinessException(403, "Không có quyền xem phiếu khám này");
        }
        QueueConfig config = configs.findById(entry.getQueueConfigId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "QueueConfig", "id", entry.getQueueConfigId()));
        return new VisitTicketResponse(
                entry.getId(), entry.getQueueNumber(), entry.getAppointmentId(),
                entry.getPatientId(), entry.getQueueNumber(),
                entry.getDepartmentCode(), config.getDepartmentNameSnapshot(), config.getRoomCode(),
                entry.getRoomDisplayNameSnapshot() == null
                        ? config.getRoomCode() : entry.getRoomDisplayNameSnapshot(),
                entry.getQueueDate(), entry.getTimeSlot(), qrTokens.issue(entry),
                entry.getStatus() == QueueStatus.WAITING
                        ? "TICKET_ISSUED" : entry.getStatus().name());
    }

    @Transactional(readOnly = true)
    public String issueQr(UUID appointmentId, UUID userId) {
        QueueEntry entry = entries.findByAppointmentIdAndUserId(appointmentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "appointmentId", appointmentId));
        if (entry.getStatus() != QueueStatus.WAITING && entry.getStatus() != QueueStatus.CHECKED_IN) {
            throw new BusinessException(409, "Trạng thái lượt khám không cho phép sinh QR");
        }
        return qrTokens.issue(entry);
    }

    @Transactional
    public QueueEntryResponse checkIn(CheckInRequest request, UUID staffUserId, String correlationId) {
        QrTokenService.QrClaims claims = qrTokens.verify(request.qrToken());
        if (!claims.queueDate().equals(businessDate())) throw new BusinessException(422, "QR không thuộc ngày hiện tại");
        QueueEntry entry = entries.findFirstByAppointmentId(claims.appointmentId())
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "appointmentId", claims.appointmentId()));
        if (!entry.getId().equals(claims.ticketId()) || !entry.getUserId().equals(claims.userId())) {
            throw new BusinessException(401, "QR không khớp phiếu khám");
        }
        QueueConfig config = requireConfig(entry.getDepartmentId());
        if (!config.getRoomCode().equals(request.roomId())) {
            throw new BusinessException(409, "QR không thuộc phòng tiếp nhận này");
        }
        if (entry.getStatus() == QueueStatus.CHECKED_IN) return response(entry, config);
        if (entry.getStatus() == QueueStatus.CANCELLED) throw new BusinessException(409, "Lịch hẹn đã bị hủy");
        if (entry.getStatus() != QueueStatus.WAITING) throw new BusinessException(409, "Trạng thái lượt khám không cho phép check-in");
        Instant now = Instant.now();
        entry.setStatus(QueueStatus.CHECKED_IN);
        entry.setCheckedInAt(now);
        entry.setCheckedInByUserId(staffUserId);
        QueueClass queueClass = request.queueClass() == null ? QueueClass.NORMAL : request.queueClass();
        if (queueClass == QueueClass.PRIORITY) {
            if (request.priorityReasonCode() == null || request.priorityReasonCode().isBlank()) {
                throw new BusinessException(400, "Lượt ưu tiên phải có lý do đã được xác minh");
            }
            entry.setPriorityLevel(PriorityLevel.PRIORITY);
            entry.setPriorityReasonCode(request.priorityReasonCode().trim());
        } else {
            entry.setPriorityLevel(PriorityLevel.APPOINTMENT);
            entry.setPriorityReasonCode(null);
        }
        entry.setEligibleSinceAt(now);
        entries.saveAndFlush(entry);
        events.append(entry, config, "PatientCheckedIn", AppConstants.RK_QUEUE_CHECKED_IN,
                correlationId, Map.of(
                        "queueClass", queueClass.name(),
                        "checkedInByUserId", staffUserId));
        return response(entry, config);
    }

    @Transactional(readOnly = true)
    public QueueDashboardResponse roomDashboard(String roomId) {
        return dashboard(requireRoomConfig(roomId).getDepartmentId());
    }

    @Transactional
    public Optional<QueueEntryResponse> callNextInRoom(
            String roomId, UUID calledByUserId, String idempotencyKey, String correlationId) {
        return callNext(requireRoomConfig(roomId).getDepartmentId(), calledByUserId,
                idempotencyKey, correlationId);
    }

    @Transactional(readOnly = true)
    public QueueDashboardResponse dashboard(UUID departmentId) {
        QueueConfig config = requireConfig(departmentId);
        LocalDate date = businessDate();
        List<QueueEntry> active = entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                config.getId(), date, DASHBOARD_STATUSES);
        List<QueueEntry> serving = active.stream()
                .filter(entry -> SERVING_STATUSES.contains(entry.getStatus()))
                .sorted(Comparator.comparing((QueueEntry entry) -> entry.getStatus() == QueueStatus.IN_PROGRESS ? 0 : 1)
                        .thenComparing(QueueEntry::getCalledAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<QueueEntry> waiting = active.stream()
                .filter(entry -> entry.getStatus() == QueueStatus.CHECKED_IN)
                .toList();
        Map<UUID, QueueEntry> waitingById = new HashMap<>();
        waiting.forEach(entry -> waitingById.put(entry.getId(), entry));
        Instant now = Instant.now();
        int currentWorkload = serving.isEmpty() ? 0 : config.getAvgConsultationMinutes();
        List<QueueScheduleSimulator.ScheduledEntry> schedule = QueueScheduleSimulator.schedule(
                config, date, waiting, now.plus(Duration.ofMinutes(currentWorkload)));
        List<QueueEntryResponse> response = new ArrayList<>();
        serving.forEach(entry -> response.add(response(entry, config, 0, 0)));
        for (int index = 0; index < schedule.size(); index++) {
            QueueScheduleSimulator.ScheduledEntry scheduled = schedule.get(index);
            QueueEntry entry = waitingById.get(scheduled.entryId());
            int position = index + 1;
            int wait = projectedWaitMinutes(now, scheduled.projectedStartAt());
            response.add(response(entry, config, position, wait));
        }
        QueueEntryResponse recommendedNext = null;
        List<QueueScheduleSimulator.ScheduledEntry> recommendationSchedule =
                QueueScheduleSimulator.schedule(config, date, waiting, now);
        if (!recommendationSchedule.isEmpty()) {
            QueueScheduleSimulator.ScheduledEntry recommended = recommendationSchedule.getFirst();
            if (!recommended.projectedStartAt().isAfter(now)) {
                QueueEntry entry = waitingById.get(recommended.entryId());
                recommendedNext = response(entry, config, 1,
                        projectedWaitMinutes(now, recommended.projectedStartAt()));
            }
        }
        return new QueueDashboardResponse(departmentId, config.getDepartmentNameSnapshot(),
                config.getRoomCode(), date, response, recommendedNext);
    }

    @Transactional
    public Optional<QueueEntryResponse> callNext(UUID departmentId, UUID calledByUserId,
                                                  String idempotencyKey, String correlationId) {
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        String requestFingerprint = fingerprint(departmentId, calledByUserId);
        QueueConfig config = requireLockedConfig(departmentId);
        LocalDate date = businessDate();
        if (!date.equals(config.getSchedulerDate())) config.resetScheduler(date);
        Optional<IdempotencyRecord> existingCommand = idempotencyRecords
                .findByCommandNameAndScopeIdAndIdempotencyKey(
                        COMMAND_CALL_NEXT, departmentId, normalizedKey);
        if (existingCommand.isPresent()) {
            IdempotencyRecord record = existingCommand.get();
            requireSameFingerprint(record, requestFingerprint);
            if (record.isEmptyResult()) return Optional.empty();
            QueueEntry replayed = entries.findById(record.getResultEntryId())
                    .orElseThrow(() -> new IllegalStateException("Idempotency result entry is missing"));
            return Optional.of(response(replayed, config));
        }
        List<QueueEntry> waiting = entries
                .findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                        config.getId(), date, Set.of(QueueStatus.CHECKED_IN));
        Map<UUID, QueueEntry> waitingById = new HashMap<>();
        waiting.forEach(entry -> waitingById.put(entry.getId(), entry));
        Instant now = Instant.now();
        QueueScheduleSimulator.ScheduledEntry next = QueueScheduleSimulator
                .schedule(config, date, waiting, now)
                .stream().findFirst().orElse(null);
        if (next == null || next.projectedStartAt().isAfter(now)) {
            recordEmptyCommand(COMMAND_CALL_NEXT, departmentId, normalizedKey, requestFingerprint);
            return Optional.empty();
        }
        QueueEntry candidate = waitingById.get(next.entryId());
        if (candidate == null) throw new IllegalStateException("Scheduled queue entry is missing");
        if (next.mode() == QueueScheduleSimulator.SelectionMode.PRIORITY_CYCLE) {
            advancePriority(config);
        } else if (next.mode() == QueueScheduleSimulator.SelectionMode.NORMAL_CYCLE) {
            advanceNormal(config, candidate.getPriorityLevel());
        }

        candidate.setStatus(QueueStatus.CALLED);
        candidate.setCalledAt(now);
        candidate.setCalledByUserId(calledByUserId);
        candidate.setCallAttempts(1);
        entries.saveAndFlush(candidate);
        configs.save(config);
        events.append(candidate, config, "PatientCalled", AppConstants.RK_QUEUE_CALLED,
                correlationId, Map.of(
                        "callAttempt", candidate.getCallAttempts(),
                        "calledByUserId", calledByUserId));
        recordCommand(COMMAND_CALL_NEXT, departmentId, normalizedKey, requestFingerprint, candidate);
        appendNearTurnEvents(config, date, correlationId);
        return Optional.of(response(candidate, config));
    }

    @Transactional
    public QueueEntryResponse call(UUID entryId, UUID calledByUserId,
                                   String idempotencyKey, String correlationId) {
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        String requestFingerprint = fingerprint(entryId, calledByUserId);
        Optional<QueueEntryResponse> replay = replay(
                COMMAND_CALL_ENTRY, entryId, normalizedKey, requestFingerprint);
        if (replay.isPresent()) return replay.get();

        QueueEntry entry = requireEntryForUpdate(entryId);
        if (entry.getStatus() != QueueStatus.CHECKED_IN) {
            throw invalidTransition(entry, QueueStatus.CALLED);
        }
        QueueConfig config = requireConfig(entry.getDepartmentId());
        Instant now = Instant.now();
        entry.setStatus(QueueStatus.CALLED);
        entry.setCalledAt(now);
        entry.setCalledByUserId(calledByUserId);
        entry.setCallAttempts(1);
        entries.saveAndFlush(entry);
        events.append(entry, config, "PatientCalled", AppConstants.RK_QUEUE_CALLED,
                correlationId, Map.of(
                        "callAttempt", entry.getCallAttempts(),
                        "calledByUserId", calledByUserId,
                        "selectedByDoctor", true));
        recordCommand(COMMAND_CALL_ENTRY, entryId, normalizedKey, requestFingerprint, entry);
        appendNearTurnEvents(config, entry.getQueueDate(), correlationId);
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse recall(UUID entryId, String correlationId) {
        QueueEntry entry = requireEntryForUpdate(entryId);
        if (entry.getStatus() != QueueStatus.CALLED) throw invalidTransition(entry, QueueStatus.CALLED);
        if (entry.getCallAttempts() >= MAX_CALL_ATTEMPTS) {
            throw new BusinessException(409, "Đã gọi đủ " + MAX_CALL_ATTEMPTS + " lần; hãy đánh dấu lỡ lượt");
        }
        QueueConfig config = requireConfig(entry.getDepartmentId());
        entry.setCallAttempts(entry.getCallAttempts() + 1);
        entry.setCalledAt(Instant.now());
        entries.saveAndFlush(entry);
        events.append(entry, config, "PatientCalled", AppConstants.RK_QUEUE_CALLED,
                correlationId, Map.of("callAttempt", entry.getCallAttempts(), "recalled", true));
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse miss(UUID entryId, String correlationId) {
        QueueEntry entry = requireEntryForUpdate(entryId);
        if (entry.getStatus() != QueueStatus.CALLED) throw invalidTransition(entry, QueueStatus.MISSED);
        if (entry.getCallAttempts() < MAX_CALL_ATTEMPTS) {
            throw new BusinessException(409,
                    "Phải gọi đủ " + MAX_CALL_ATTEMPTS + " lần trước khi đánh dấu lỡ lượt");
        }
        QueueConfig config = requireConfig(entry.getDepartmentId());
        entry.setStatus(QueueStatus.MISSED);
        entry.setMissedAt(Instant.now());
        entry.setMissedCount(entry.getMissedCount() + 1);
        entries.saveAndFlush(entry);
        events.append(entry, config, "QueueEntryMissed", AppConstants.RK_QUEUE_MISSED, correlationId,
                Map.of("missedCount", entry.getMissedCount()));
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse requeue(UUID entryId, RequeueRequest request, String correlationId) {
        QueueEntry snapshot = entries.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", entryId));
        QueueConfig config = requireLockedConfig(snapshot.getDepartmentId());
        QueueEntry entry = requireEntryForUpdate(entryId);
        if (entry.getStatus() != QueueStatus.MISSED) throw invalidTransition(entry, QueueStatus.CHECKED_IN);
        boolean back;
        if (config.getMissedPolicy() == MissedPolicy.REQUIRE_MANUAL) {
            if (request == null || request.position() == null) {
                throw new BusinessException(400, "Policy REQUIRE_MANUAL yêu cầu position FRONT hoặc BACK");
            }
            back = request.position() == RequeueRequest.Position.BACK;
        } else {
            back = config.getMissedPolicy() == MissedPolicy.REQUEUE_BACK;
        }
        if (back) {
            entry.setEligibleSinceAt(Instant.now());
        } else {
            QueueEntry first = firstCandidate(config, entry.getQueueDate(), entry.getPriorityLevel());
            entry.setEligibleSinceAt(first == null
                    ? Instant.now()
                    : first.getEligibleSinceAt().minusSeconds(1));
        }
        entry.setStatus(QueueStatus.CHECKED_IN);
        entry.setCalledAt(null);
        entry.setCalledByUserId(null);
        entry.setCallAttempts(0);
        entry.setNearTurnNotifiedAt(null);
        entries.saveAndFlush(entry);
        events.append(entry, config, "PatientCheckedIn", AppConstants.RK_QUEUE_CHECKED_IN, correlationId,
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
        events.append(entry, config, "QueueEntryStarted", AppConstants.RK_QUEUE_STARTED, correlationId, Map.of());
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
        events.append(entry, config, "QueueEntryCompleted", AppConstants.RK_QUEUE_COMPLETED, correlationId,
                Map.of("consultationMinutes", consultationMinutes(entry)));
        return response(entry, config);
    }

    private QueueEntry newEntry(QueueConfig config, LocalDate date, UUID patientId, UUID userId,
                                UUID appointmentId, PriorityLevel priority, QueueStatus status) {
        if (entries.existsByPatientIdAndDepartmentIdAndQueueDateAndStatusIn(
                patientId, config.getDepartmentId(), date, ACTIVE_PATIENT_STATUSES)) {
            throw new BusinessException(409, "Bệnh nhân đã có một lượt khám đang hoạt động tại khoa trong ngày");
        }
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

    public QueueEntry createAppointmentEntry(QueueConfig config, LocalDate date, LocalTime scheduledStart,
                                             String timeSlot, String departmentCode, String roomDisplayName,
                                             UUID appointmentId, UUID patientId, UUID userId) {
        QueueEntry entry = newEntry(
                config, date, patientId, userId, appointmentId, PriorityLevel.APPOINTMENT, QueueStatus.WAITING);
        entry.setScheduledStartAt(ZonedDateTime.of(date, scheduledStart, businessZone).toInstant());
        entry.setTimeSlot(timeSlot);
        entry.setDepartmentCode(departmentCode);
        entry.setRoomDisplayNameSnapshot(roomDisplayName);
        return entry;
    }

    public QueueConfig requireLockedConfig(UUID departmentId) {
        return configs.findFirstByDepartmentIdAndActiveTrue(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Active QueueConfig", "departmentId", departmentId));
    }

    private QueueConfig requireConfig(UUID departmentId) {
        return configs.findByDepartmentIdAndActiveTrue(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Active QueueConfig", "departmentId", departmentId));
    }

    private QueueConfig requireRoomConfig(String roomId) {
        return configs.findByRoomCodeAndActiveTrue(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active QueueConfig", "roomId", roomId));
    }

    private QueueEntry requireEntryForUpdate(UUID id) {
        return entries.findFirstById(id).orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", id));
    }

    private QueueEntry firstCandidate(QueueConfig config, LocalDate date, PriorityLevel level) {
        return entries.findByQueueConfigIdAndQueueDateAndStatusAndPriorityLevelOrderByEligibleSinceAtAscSequenceNumberAsc(
                config.getId(), date, QueueStatus.CHECKED_IN, level, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
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
            Instant now = Instant.now();
            int currentConsultation = entries.existsByQueueConfigIdAndQueueDateAndStatusIn(
                    config.getId(), entry.getQueueDate(), SERVING_STATUSES)
                    ? config.getAvgConsultationMinutes() : 0;
            List<QueueScheduleSimulator.ScheduledEntry> schedule = QueueScheduleSimulator.schedule(
                    config, entry.getQueueDate(), active,
                    now.plus(Duration.ofMinutes(currentConsultation)));
            int index = -1;
            for (int candidateIndex = 0; candidateIndex < schedule.size(); candidateIndex++) {
                if (schedule.get(candidateIndex).entryId().equals(entry.getId())) {
                    index = candidateIndex;
                    break;
                }
            }
            if (index >= 0) {
                position = index + 1;
                wait = projectedWaitMinutes(now, schedule.get(index).projectedStartAt());
            }
        } else if (entry.getStatus() == QueueStatus.CALLED || entry.getStatus() == QueueStatus.IN_PROGRESS) {
            position = 0;
            wait = 0;
        }
        return QueueEntryResponse.from(entry, QueueConfigResponse.from(config), position, wait);
    }

    private QueueEntryResponse response(QueueEntry entry, QueueConfig config, Integer position, Integer wait) {
        return QueueEntryResponse.from(entry, QueueConfigResponse.from(config), position, wait);
    }

    private void appendNearTurnEvents(QueueConfig config, LocalDate date, String correlationId) {
        List<QueueEntry> waiting = entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                config.getId(), date, Set.of(QueueStatus.CHECKED_IN));
        Map<UUID, QueueEntry> byId = new HashMap<>();
        waiting.forEach(entry -> byId.put(entry.getId(), entry));
        Instant now = Instant.now();
        int currentWorkload = entries.existsByQueueConfigIdAndQueueDateAndStatusIn(
                config.getId(), date, SERVING_STATUSES)
                ? config.getAvgConsultationMinutes() : 0;
        List<QueueScheduleSimulator.ScheduledEntry> schedule = QueueScheduleSimulator.schedule(
                config, date, waiting, now.plus(Duration.ofMinutes(currentWorkload)));
        int limit = Math.min(config.getNearTurnThreshold(), schedule.size());
        for (int index = 0; index < limit; index++) {
            QueueScheduleSimulator.ScheduledEntry scheduled = schedule.get(index);
            QueueEntry entry = byId.get(scheduled.entryId());
            if (entry.getNearTurnNotifiedAt() != null) continue;
            int position = index + 1;
            int wait = projectedWaitMinutes(now, scheduled.projectedStartAt());
            events.append(entry, config, "QueueNearTurn", AppConstants.RK_QUEUE_NEAR_TURN, correlationId,
                    Map.of("effectivePosition", position, "estimatedWaitMinutes", wait));
            entry.setNearTurnNotifiedAt(Instant.now());
        }
    }

    private int projectedWaitMinutes(Instant now, Instant projectedStartAt) {
        long seconds = Math.max(0, Duration.between(now, projectedStartAt).getSeconds());
        return Math.toIntExact((seconds + 59) / 60);
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(400, "Thiếu header " + AppConstants.HEADER_IDEMPOTENCY_KEY);
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 100) {
            throw new BusinessException(400, AppConstants.HEADER_IDEMPOTENCY_KEY + " tối đa 100 ký tự");
        }
        return normalized;
    }

    private Optional<QueueEntryResponse> replay(
            String command, UUID scopeId, String idempotencyKey, String requestFingerprint) {
        return idempotencyRecords.findByCommandNameAndScopeIdAndIdempotencyKey(command, scopeId, idempotencyKey)
                .map(record -> {
                    requireSameFingerprint(record, requestFingerprint);
                    if (record.isEmptyResult()) {
                        throw new IllegalStateException("Command unexpectedly contains an empty result");
                    }
                    QueueEntry entry = entries.findById(record.getResultEntryId())
                            .orElseThrow(() -> new IllegalStateException("Idempotency result entry is missing"));
                    QueueConfig config = configs.findById(entry.getQueueConfigId())
                            .orElseThrow(() -> new IllegalStateException("Idempotency result config is missing"));
                    return response(entry, config);
                });
    }

    private void recordCommand(String command, UUID scopeId, String idempotencyKey,
                               String requestFingerprint, QueueEntry entry) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setCommandName(command);
        record.setScopeId(scopeId);
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestFingerprint(requestFingerprint);
        record.setResultEntryId(entry.getId());
        idempotencyRecords.save(record);
    }

    private void recordEmptyCommand(
            String command, UUID scopeId, String idempotencyKey, String requestFingerprint) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setCommandName(command);
        record.setScopeId(scopeId);
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestFingerprint(requestFingerprint);
        record.setEmptyResult(true);
        idempotencyRecords.save(record);
    }

    private void requireSameFingerprint(IdempotencyRecord record, String requestFingerprint) {
        if (!MessageDigest.isEqual(
                record.getRequestFingerprint().getBytes(StandardCharsets.UTF_8),
                requestFingerprint.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(409, "Idempotency-Key đã được dùng cho request có nội dung khác");
        }
    }

    private String fingerprint(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void updateAverage(QueueConfig config, LocalDate date) {
        List<QueueEntry> completed = entries
                .findByQueueConfigIdAndQueueDateAndStatusAndStartedAtIsNotNullAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        config.getId(), date, QueueStatus.COMPLETED, PageRequest.of(0, 20));
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
