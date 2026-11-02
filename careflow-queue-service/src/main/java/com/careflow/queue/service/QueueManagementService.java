package com.careflow.queue.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.common.security.PatientOwnershipClient;
import com.careflow.queue.client.DirectoryClient;
import com.careflow.queue.domain.ConsultationPhase;
import com.careflow.queue.domain.IdempotencyRecord;
import com.careflow.queue.domain.MissedPolicy;
import com.careflow.queue.domain.PriorityLevel;
import com.careflow.queue.domain.QueueClass;
import com.careflow.queue.domain.QueueConfig;
import com.careflow.queue.domain.QueueEntry;
import com.careflow.queue.domain.QueueNumberSequence;
import com.careflow.queue.domain.QueueStatus;
import com.careflow.queue.domain.QueueType;
import com.careflow.queue.domain.SchedulingLane;
import com.careflow.queue.dto.CheckInQrResponse;
import com.careflow.queue.dto.CheckInRequest;
import com.careflow.queue.dto.ManualIntakeRequest;
import com.careflow.queue.dto.QueueConfigRequest;
import com.careflow.queue.dto.QueueConfigResponse;
import com.careflow.queue.dto.QueueDashboardResponse;
import com.careflow.queue.dto.QueueEntryResponse;
import com.careflow.queue.dto.RequeueRequest;
import com.careflow.queue.dto.ServicePointQueueResponse;
import com.careflow.queue.dto.VisitTicketResponse;
import com.careflow.queue.repository.IdempotencyRecordRepository;
import com.careflow.queue.repository.QueueConfigRepository;
import com.careflow.queue.repository.QueueEntryRepository;
import com.careflow.queue.repository.QueueNumberSequenceRepository;
import com.careflow.queue.repository.ServicePointSequenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class QueueManagementService {
    private static final String DEFAULT_HOSPITAL_SITE_ID = "HOSPITAL-MAIN";
    private static final double DEFAULT_GEOFENCE_LATITUDE = 10.7769;
    private static final double DEFAULT_GEOFENCE_LONGITUDE = 106.7009;
    private static final double DEFAULT_GEOFENCE_RADIUS_METERS = 150;
    private static final double DEFAULT_GEOFENCE_MAX_ACCURACY_METERS = 50;
    private static final String COMMAND_MANUAL_INTAKE = "MANUAL_INTAKE";
    private static final String COMMAND_CALL_NEXT = "CALL_NEXT";
    private static final String COMMAND_CALL_ENTRY = "CALL_ENTRY";
    private static final String COMMAND_CALL_NEXT_SERVICE_POINT = "CALL_NEXT_SERVICE_POINT";
    private static final int MAX_CALL_ATTEMPTS = 3;
    private static final Set<PriorityLevel> MANUAL_LEVELS = EnumSet.of(
            PriorityLevel.EMERGENCY, PriorityLevel.PRIORITY, PriorityLevel.WALK_IN);
    private static final Set<QueueStatus> ACTIVE_PATIENT_STATUSES = EnumSet.of(
            QueueStatus.WAITING, QueueStatus.QUEUED, QueueStatus.CHECKED_IN, QueueStatus.CALLED,
            QueueStatus.IN_PROGRESS, QueueStatus.MISSED);
    private static final Set<QueueStatus> SERVING_STATUSES = EnumSet.of(
            QueueStatus.CALLED, QueueStatus.IN_PROGRESS);

    @Autowired
    private PatientOwnershipClient currentOwners;

    @Autowired
    private QueueRoomConfigurationService roomConfigurations;

    private final QueueConfigRepository configs;
    private final QueueNumberSequenceRepository sequences;
    private final QueueEntryRepository entries;
    private final IdempotencyRecordRepository idempotencyRecords;
    private final QueueEventService events;
    private final ZoneId businessZone;
    private final QueueAccessValidator accessValidator;
    private final QueueCheckInSupport checkInSupport;
    private final ServicePointQueueSupport servicePointSupport;

    public QueueManagementService(QueueConfigRepository configs,
                                  QueueNumberSequenceRepository sequences,
                                  ServicePointSequenceRepository servicePointSequences,
                                  QueueEntryRepository entries,
                                  IdempotencyRecordRepository idempotencyRecords,
                                  QueueEventService events,
                                  QrTokenService qrTokens,
                                  @Value("${queue.business-zone:Asia/Ho_Chi_Minh}") String businessZone) {
        this(configs, sequences, servicePointSequences, entries, idempotencyRecords, events, qrTokens,
                businessZone, null, null, DEFAULT_HOSPITAL_SITE_ID, DEFAULT_GEOFENCE_LATITUDE,
                DEFAULT_GEOFENCE_LONGITUDE, DEFAULT_GEOFENCE_RADIUS_METERS,
                DEFAULT_GEOFENCE_MAX_ACCURACY_METERS);
    }

    public QueueManagementService(QueueConfigRepository configs,
                                  QueueNumberSequenceRepository sequences,
                                  ServicePointSequenceRepository servicePointSequences,
                                  QueueEntryRepository entries,
                                  IdempotencyRecordRepository idempotencyRecords,
                                  QueueEventService events,
                                  QrTokenService qrTokens,
                                  String businessZone,
                                  DirectoryClient directoryClient) {
        this(configs, sequences, servicePointSequences, entries, idempotencyRecords, events, qrTokens,
                businessZone, directoryClient, null, DEFAULT_HOSPITAL_SITE_ID, DEFAULT_GEOFENCE_LATITUDE,
                DEFAULT_GEOFENCE_LONGITUDE, DEFAULT_GEOFENCE_RADIUS_METERS,
                DEFAULT_GEOFENCE_MAX_ACCURACY_METERS);
    }

    public QueueManagementService(QueueConfigRepository configs,
                                  QueueNumberSequenceRepository sequences,
                                  ServicePointSequenceRepository servicePointSequences,
                                  QueueEntryRepository entries,
                                  IdempotencyRecordRepository idempotencyRecords,
                                  QueueEventService events,
                                  QrTokenService qrTokens,
                                  String businessZone,
                                  DirectoryClient directoryClient,
                                  HospitalCheckInConfigService hospitalCheckInConfigService) {
        this(configs, sequences, servicePointSequences, entries, idempotencyRecords, events, qrTokens,
                businessZone, directoryClient, hospitalCheckInConfigService, DEFAULT_HOSPITAL_SITE_ID,
                DEFAULT_GEOFENCE_LATITUDE, DEFAULT_GEOFENCE_LONGITUDE, DEFAULT_GEOFENCE_RADIUS_METERS,
                DEFAULT_GEOFENCE_MAX_ACCURACY_METERS);
    }

    @Autowired
    public QueueManagementService(QueueConfigRepository configs,
                                  QueueNumberSequenceRepository sequences,
                                  ServicePointSequenceRepository servicePointSequences,
                                  QueueEntryRepository entries,
                                  IdempotencyRecordRepository idempotencyRecords,
                                  QueueEventService events,
                                  QrTokenService qrTokens,
                                  @Value("${queue.business-zone:Asia/Ho_Chi_Minh}") String businessZone,
                                  DirectoryClient directoryClient,
                                  HospitalCheckInConfigService hospitalCheckInConfigService,
                                  @Value("${queue.geofence.site-id:HOSPITAL-MAIN}") String geofenceSiteId,
                                  @Value("${queue.geofence.latitude:10.7769}") double geofenceLatitude,
                                  @Value("${queue.geofence.longitude:106.7009}") double geofenceLongitude,
                                  @Value("${queue.geofence.radius-meters:150}") double geofenceRadiusMeters,
                                  @Value("${queue.geofence.max-accuracy-meters:50}") double geofenceMaxAccuracyMeters) {
        if (geofenceRadiusMeters <= 0 || geofenceMaxAccuracyMeters <= 0) {
            throw new IllegalArgumentException("Geofence radius and accuracy must be positive");
        }
        this.configs = configs;
        this.sequences = sequences;
        this.entries = entries;
        this.idempotencyRecords = idempotencyRecords;
        this.events = events;
        this.businessZone = ZoneId.of(businessZone);
        this.accessValidator = new QueueAccessValidator(entries, directoryClient);
        HospitalCheckInConfigService.GeofenceSettings fallbackGeofence =
                new HospitalCheckInConfigService.GeofenceSettings(
                        geofenceSiteId,
                        geofenceLatitude,
                        geofenceLongitude,
                        geofenceRadiusMeters,
                        geofenceMaxAccuracyMeters);
        this.checkInSupport = new QueueCheckInSupport(
                entries,
                events,
                qrTokens,
                hospitalCheckInConfigService,
                fallbackGeofence,
                this::businessDate,
                () -> this.currentOwners,
                () -> this.roomConfigurations,
                this::configFor,
                this::requireRoomConfig,
                this::response);
        this.servicePointSupport = new ServicePointQueueSupport(
                entries,
                servicePointSequences,
                events,
                this.businessZone,
                this::businessDate);
    }

    public record ClinicalContext(
            UUID entryId,
            UUID patientId,
            UUID appointmentId,
            String patientName,
            String roomId,
            String phase,
            String status,
            UUID calledByUserId
    ) {}

    @Transactional(readOnly = true)
    public ClinicalContext clinicalContext(UUID entryId, UUID actor, String role) {
        if (!"DOCTOR".equals(role)) {
            throw new BusinessException(403, "Chỉ bác sĩ được đọc ngữ cảnh bắt đầu khám.");
        }
        accessValidator.requireEntryAssignmentAccess(entryId, actor, role, this::configFor);
        QueueEntry entry = entries.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", entryId));
        if (entry.getQueueType() != QueueType.CONSULTATION
                || entry.getConsultationPhase() != ConsultationPhase.INITIAL) {
            throw new BusinessException(409, "Lượt này không phải lượt khám ban đầu.");
        }
        if (!entry.getQueueDate().equals(businessDate())) {
            throw new BusinessException(409, "Lượt khám không thuộc ngày hiện tại.");
        }
        if (!SERVING_STATUSES.contains(entry.getStatus())) {
            throw new BusinessException(409, "Cần gọi bệnh nhân trước khi bắt đầu khám.");
        }
        return new ClinicalContext(
                entry.getId(),
                entry.getPatientId(),
                entry.getAppointmentId(),
                entry.getPatientNameSnapshot(),
                configFor(entry).getRoomCode(),
                entry.getConsultationPhase().name(),
                entry.getStatus().name(),
                entry.getCalledByUserId());
    }

    public LocalDate businessDate() {
        return LocalDate.now(businessZone);
    }

    @Transactional
    public QueueConfigResponse saveConfig(UUID departmentId, QueueConfigRequest request) {
        if (roomConfigurations != null) {
            throw new BusinessException(
                    410,
                    "Cập nhật chính sách theo phòng tại /api/queues/rooms/{roomId}/config với phiên bản hiện tại.");
        }
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
        if (config.getSchedulerDate() == null || schedulingChanged) {
            config.resetScheduler(businessDate());
        }
        return QueueConfigResponse.from(configs.save(config));
    }

    @Transactional(readOnly = true)
    public QueueConfigResponse getConfig(UUID departmentId) {
        return QueueConfigResponse.from(requireConfig(departmentId));
    }

    @Transactional
    public QueueEntryResponse manualIntake(ManualIntakeRequest request,
                                           UUID actorUserId,
                                           String idempotencyKey,
                                           String correlationId) {
        return manualIntake(request, actorUserId, idempotencyKey, correlationId, null);
    }

    @Transactional
    public QueueEntryResponse manualIntake(ManualIntakeRequest request,
                                           UUID actorUserId,
                                           String idempotencyKey,
                                           String correlationId,
                                           String verifiedPatientName) {
        if (actorUserId == null) {
            throw new BusinessException(401, "Thiếu người tiếp nhận đã xác thực");
        }
        if (!MANUAL_LEVELS.contains(request.priorityLevel())) {
            throw new BusinessException(400, "Tiếp nhận thủ công chỉ cho EMERGENCY, PRIORITY hoặc WALK_IN");
        }
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        String requestFingerprint = fingerprint(
                request.patientId(), request.userId(), request.departmentId(), request.priorityLevel());
        QueueConfig config = request.roomId() == null
                ? requireLockedConfig(request.departmentId())
                : requireLockedRoomConfig(request.roomId(), request.departmentId());
        if (roomConfigurations != null) {
            roomConfigurations.lockPatient(request.patientId(), request.departmentId(), businessDate());
        }
        if (request.roomId() != null || request.serviceCode() != null) {
            requestFingerprint = fingerprint(
                    request.patientId(),
                    request.userId(),
                    request.departmentId(),
                    request.priorityLevel(),
                    config.getRoomCode(),
                    request.serviceCode(),
                    actorUserId);
        }
        Optional<QueueEntryResponse> replay = replay(
                COMMAND_MANUAL_INTAKE, config.getId(), normalizedKey, requestFingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        LocalDate date = businessDate();
        QueueEntry entry = newEntry(
                config,
                date,
                request.patientId(),
                request.userId(),
                null,
                request.priorityLevel(),
                QueueStatus.CHECKED_IN);
        entry.setPatientNameSnapshot(verifiedPatientName);
        entry.setServiceCode(request.serviceCode() == null ? "GENERAL_CONSULTATION" : request.serviceCode());
        Instant now = Instant.now();
        entry.setCheckedInAt(now);
        entry.setCheckedInByUserId(actorUserId);
        entry.setCheckInMethod("STAFF_MANUAL_INTAKE");
        entry.setEligibleSinceAt(now);
        entries.saveAndFlush(entry);
        events.append(
                entry,
                config,
                "PatientCheckedIn",
                AppConstants.RK_QUEUE_CHECKED_IN,
                correlationId,
                Map.of("priorityLevel", entry.getPriorityLevel().name()));

        recordCommand(COMMAND_MANUAL_INTAKE, config.getId(), normalizedKey, requestFingerprint, entry);
        return response(entry, config);
    }

    @Transactional(readOnly = true)
    public QueueEntryResponse myStatus(UUID userId) {
        QueueEntry entry = entries.findByUserIdAndQueueDateOrderByCreatedAtDesc(userId, businessDate()).stream()
                .filter(candidate -> currentOwners == null || userId.equals(currentOwners.owner(candidate.getPatientId())))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "userId/date", userId));
        QueueConfig config = configs.findById(entry.getQueueConfigId())
                .orElseThrow(() -> new ResourceNotFoundException("QueueConfig", "id", entry.getQueueConfigId()));
        return response(entry, config);
    }

    @Transactional(readOnly = true)
    public QueueEntryResponse patientCurrent(UUID patientId, UUID userId, boolean clinicalStaff) {
        if (!clinicalStaff && currentOwners != null) {
            currentOwners.require(patientId, userId);
        }
        Optional<QueueEntry> current = (clinicalStaff || currentOwners != null)
                ? entries.findFirstByPatientIdAndQueueDateAndStatusInOrderByCreatedAtDesc(
                        patientId, businessDate(), ACTIVE_PATIENT_STATUSES)
                : entries.findFirstByPatientIdAndUserIdAndQueueDateAndStatusInOrderByCreatedAtDesc(
                        patientId, userId, businessDate(), ACTIVE_PATIENT_STATUSES);
        QueueEntry entry = current
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "patientId/userId", patientId));
        QueueConfig config = entry.getQueueConfigId() == null
                ? null
                : configs.findById(entry.getQueueConfigId())
                        .orElseThrow(() -> new ResourceNotFoundException("QueueConfig", "id", entry.getQueueConfigId()));
        return response(entry, config);
    }

    @Transactional(readOnly = true)
    public QueueEntryResponse appointmentStatus(UUID appointmentId, UUID actor, String role) {
        ticket(appointmentId, actor, role);
        QueueEntry entry = entries.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "appointmentId", appointmentId));
        return response(entry, configFor(entry));
    }

    @Transactional(readOnly = true)
    public VisitTicketResponse ticket(UUID appointmentId, UUID requesterUserId, boolean clinicalStaff) {
        QueueEntry entry = entries.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("VisitTicket", "appointmentId", appointmentId));
        if (!clinicalStaff && currentOwners != null) {
            currentOwners.require(entry.getPatientId(), requesterUserId);
        }
        if (!clinicalStaff && currentOwners == null && !Objects.equals(entry.getUserId(), requesterUserId)) {
            throw new BusinessException(403, "Không có quyền xem phiếu khám này");
        }
        QueueConfig config = configs.findById(entry.getQueueConfigId())
                .orElseThrow(() -> new ResourceNotFoundException("QueueConfig", "id", entry.getQueueConfigId()));
        return new VisitTicketResponse(
                entry.getId(),
                entry.getQueueNumber(),
                entry.getAppointmentId(),
                entry.getPatientId(),
                entry.getQueueNumber(),
                entry.getDepartmentCode(),
                config.getDepartmentNameSnapshot(),
                config.getRoomCode(),
                entry.getRoomDisplayNameSnapshot() == null
                        ? config.getRoomCode()
                        : entry.getRoomDisplayNameSnapshot(),
                entry.getQueueDate(),
                entry.getTimeSlot(),
                null,
                entry.getStatus() == QueueStatus.WAITING
                        ? "TICKET_ISSUED"
                        : entry.getStatus().name());
    }

    @Transactional(readOnly = true)
    public CheckInQrResponse issueHospitalCheckInQr(String roomId,
                                                    LocalDate date,
                                                    String session,
                                                    UUID requesterUserId,
                                                    String role) {
        return issueHospitalCheckInQr(date, session, requesterUserId, role);
    }

    @Transactional(readOnly = true)
    public CheckInQrResponse issueHospitalCheckInQr(LocalDate date,
                                                    String session,
                                                    UUID requesterUserId,
                                                    String role) {
        return checkInSupport.issueHospitalCheckInQr(date, session);
    }

    @Transactional
    public QueueEntryResponse checkIn(CheckInRequest request, UUID actorUserId, String correlationId) {
        return checkIn(request, actorUserId, AppConstants.ROLE_STAFF, correlationId);
    }

    @Transactional
    public QueueEntryResponse checkIn(CheckInRequest request,
                                      UUID actorUserId,
                                      String role,
                                      String correlationId) {
        if (AppConstants.ROLE_PATIENT.equalsIgnoreCase(role)) {
            return checkInSupport.checkInAsPatient(request, actorUserId, correlationId);
        }
        if (request.roomId() == null || request.roomId().isBlank()) {
            throw new BusinessException(400, "Chọn phòng tiếp nhận.");
        }
        accessValidator.requireRoomAccess(request.roomId(), actorUserId, role);
        return checkInSupport.checkInAsStaff(request, actorUserId, correlationId);
    }

    @Transactional(readOnly = true)
    public QueueDashboardResponse roomDashboard(String roomId) {
        return dashboardFor(requireRoomConfig(roomId));
    }

    public QueueDashboardResponse roomDashboard(String roomId, UUID requesterUserId, String role) {
        accessValidator.requireRoomAccess(roomId, requesterUserId, role);
        return roomDashboard(roomId);
    }

    @Transactional(readOnly = true)
    public QueueDashboardResponse receptionDashboard(String roomId, UUID requesterUserId, String role) {
        accessValidator.requireRoomAccess(roomId, requesterUserId, role);
        QueueConfig config = requireRoomConfig(roomId);
        QueueDashboardResponse active = dashboardFor(config);
        List<QueueEntryResponse> receptionEntries = new ArrayList<>(active.entries());
        entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                        config.getId(), active.queueDate(), Set.of(QueueStatus.WAITING)).stream()
                .sorted(Comparator.comparing(
                                QueueEntry::getScheduledStartAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(QueueEntry::getSequenceNumber))
                .map(entry -> response(entry, config, null, null))
                .forEach(receptionEntries::add);
        return new QueueDashboardResponse(
                active.departmentId(),
                active.departmentName(),
                active.roomCode(),
                active.queueDate(),
                receptionEntries,
                active.priorityQueue(),
                active.normalQueue(),
                active.resultReviewQueue(),
                active.recommendedNext(),
                active.lastServedLane(),
                active.schedulerVersion(),
                active.missedPolicy(),
                active.maxCallAttempts(),
                active.receptionActive());
    }

    @Transactional(readOnly = true)
    public ServicePointQueueResponse servicePointDashboard(String servicePointId, LocalDate requestedDate) {
        return servicePointSupport.servicePointDashboard(servicePointId, requestedDate);
    }

    @Transactional(readOnly = true)
    public ServicePointQueueResponse servicePointDashboard(String servicePointId,
                                                           LocalDate requestedDate,
                                                           UUID requesterUserId,
                                                           String role) {
        accessValidator.requireServicePointAccess(servicePointId, requesterUserId, role);
        return servicePointDashboard(servicePointId, requestedDate);
    }

    @Transactional
    public Optional<QueueEntryResponse> callNextAtServicePoint(String servicePointId,
                                                               UUID calledByUserId,
                                                               String idempotencyKey,
                                                               String correlationId) {
        String normalized = QueueAccessValidator.normalizeServicePointId(servicePointId);
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        UUID scopeId = UUID.nameUUIDFromBytes(normalized.getBytes(StandardCharsets.UTF_8));
        String fingerprint = fingerprint(normalized, calledByUserId);
        Optional<IdempotencyRecord> existing = idempotencyRecords
                .findByCommandNameAndScopeIdAndIdempotencyKey(
                        COMMAND_CALL_NEXT_SERVICE_POINT, scopeId, normalizedKey);
        if (existing.isPresent()) {
            requireSameFingerprint(existing.get(), fingerprint);
            if (existing.get().isEmptyResult()) {
                return Optional.empty();
            }
            QueueEntry replayed = entries.findById(existing.get().getResultEntryId())
                    .orElseThrow(() -> new IllegalStateException("Idempotency result entry is missing"));
            return Optional.of(QueueEntryResponse.fromServicePoint(replayed, 0, 0));
        }
        QueueEntry candidate = entries
                .findByServicePointIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                        normalized, businessDate(), Set.of(QueueStatus.QUEUED))
                .stream()
                .findFirst()
                .orElse(null);
        if (candidate == null) {
            recordEmptyCommand(COMMAND_CALL_NEXT_SERVICE_POINT, scopeId, normalizedKey, fingerprint);
            return Optional.empty();
        }
        QueueEntry locked = requireEntryForUpdate(candidate.getId());
        if (!locked.isWaitingForCall()) {
            throw invalidTransition(locked, QueueStatus.CALLED);
        }
        locked.markCalled(calledByUserId, Instant.now());
        entries.saveAndFlush(locked);
        events.append(
                locked,
                null,
                "PatientCalled",
                AppConstants.RK_QUEUE_CALLED,
                correlationId,
                Map.of("callAttempt", 1, "calledByUserId", calledByUserId));
        recordCommand(COMMAND_CALL_NEXT_SERVICE_POINT, scopeId, normalizedKey, fingerprint, locked);
        return Optional.of(QueueEntryResponse.fromServicePoint(locked, 0, 0));
    }

    @Transactional
    public Optional<QueueEntryResponse> callNextAtServicePoint(String servicePointId,
                                                               UUID calledByUserId,
                                                               String role,
                                                               String idempotencyKey,
                                                               String correlationId) {
        accessValidator.requireServicePointAccess(servicePointId, calledByUserId, role);
        return callNextAtServicePoint(servicePointId, calledByUserId, idempotencyKey, correlationId);
    }

    @Transactional
    public Optional<QueueEntryResponse> callNextInRoom(String roomId,
                                                       UUID calledByUserId,
                                                       String idempotencyKey,
                                                       String correlationId) {
        return callNextConfigured(
                requireLockedRoomConfig(roomId, null),
                calledByUserId,
                idempotencyKey,
                correlationId);
    }

    @Transactional
    public Optional<QueueEntryResponse> callNextInRoom(String roomId,
                                                       UUID calledByUserId,
                                                       String role,
                                                       String idempotencyKey,
                                                       String correlationId) {
        accessValidator.requireRoomAccess(roomId, calledByUserId, role);
        return callNextInRoom(roomId, calledByUserId, idempotencyKey, correlationId);
    }

    @Transactional(readOnly = true)
    public QueueDashboardResponse dashboard(UUID departmentId) {
        return dashboardFor(requireConfig(departmentId));
    }

    private QueueDashboardResponse dashboardFor(QueueConfig config) {
        UUID departmentId = config.getDepartmentId();
        LocalDate date = businessDate();
        List<QueueEntry> active = entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                config.getId(),
                date,
                EnumSet.of(
                        QueueStatus.QUEUED,
                        QueueStatus.CHECKED_IN,
                        QueueStatus.CALLED,
                        QueueStatus.IN_PROGRESS,
                        QueueStatus.MISSED));
        List<QueueEntry> serving = active.stream()
                .filter(entry -> SERVING_STATUSES.contains(entry.getStatus()))
                .sorted(Comparator.comparing((QueueEntry entry) -> entry.getStatus() == QueueStatus.IN_PROGRESS ? 0 : 1)
                        .thenComparing(QueueEntry::getCalledAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<QueueEntry> waiting = active.stream()
                .filter(QueueEntry::isWaitingForCall)
                .toList();
        Map<UUID, QueueEntry> waitingById = new HashMap<>();
        waiting.forEach(entry -> waitingById.put(entry.getId(), entry));
        Instant now = Instant.now();
        int currentWorkload = serving.isEmpty() ? 0 : config.getAvgConsultationMinutes();
        List<ConsultationQueueScheduler.ScheduledEntry> schedule = ConsultationQueueScheduler.schedule(
                waiting,
                config.getLastServedLane(),
                now.plus(Duration.ofMinutes(currentWorkload)),
                config.getAvgConsultationMinutes());
        List<QueueEntryResponse> response = new ArrayList<>();
        serving.forEach(entry -> response.add(response(entry, config, 0, 0)));
        for (int index = 0; index < schedule.size(); index++) {
            ConsultationQueueScheduler.ScheduledEntry scheduled = schedule.get(index);
            QueueEntry entry = waitingById.get(scheduled.entryId());
            int position = index + 1;
            int wait = projectedWaitMinutes(now, scheduled.projectedStartAt());
            response.add(response(entry, config, position, wait));
        }
        QueueEntryResponse recommendedNext = null;
        QueueEntry recommended = ConsultationQueueScheduler.next(waiting, config.getLastServedLane());
        if (recommended != null) {
            recommendedNext = response(recommended, config, 1, 0);
        }
        List<QueueEntryResponse> priorityQueue = lane(response, SchedulingLane.PRIORITY);
        List<QueueEntryResponse> normalQueue = lane(response, SchedulingLane.NORMAL);
        List<QueueEntryResponse> resultReviewQueue = lane(response, SchedulingLane.RESULT_REVIEW);
        active.stream()
                .filter(entry -> entry.getStatus() == QueueStatus.MISSED)
                .forEach(entry -> response.add(response(entry, config, null, null)));
        return new QueueDashboardResponse(
                departmentId,
                config.getDepartmentNameSnapshot(),
                config.getRoomCode(),
                date,
                response,
                priorityQueue,
                normalQueue,
                resultReviewQueue,
                recommendedNext,
                config.getLastServedLane(),
                config.getVersion(),
                config.getMissedPolicy(),
                MAX_CALL_ATTEMPTS,
                config.isActive());
    }

    @Transactional
    public Optional<QueueEntryResponse> callNext(UUID departmentId,
                                                 UUID calledByUserId,
                                                 String idempotencyKey,
                                                 String correlationId) {
        return callNextConfigured(
                requireLockedConfig(departmentId),
                calledByUserId,
                idempotencyKey,
                correlationId);
    }

    private Optional<QueueEntryResponse> callNextConfigured(QueueConfig config,
                                                            UUID calledByUserId,
                                                            String idempotencyKey,
                                                            String correlationId) {
        UUID departmentId = config.getDepartmentId();
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        String requestFingerprint = fingerprint(departmentId, calledByUserId);
        LocalDate date = businessDate();
        if (!date.equals(config.getSchedulerDate())) {
            config.resetScheduler(date);
        }
        Optional<IdempotencyRecord> existingCommand = idempotencyRecords
                .findByCommandNameAndScopeIdAndIdempotencyKey(
                        COMMAND_CALL_NEXT, config.getId(), normalizedKey);
        if (existingCommand.isPresent()) {
            IdempotencyRecord record = existingCommand.get();
            requireSameFingerprint(record, requestFingerprint);
            if (record.isEmptyResult()) {
                return Optional.empty();
            }
            QueueEntry replayed = entries.findById(record.getResultEntryId())
                    .orElseThrow(() -> new IllegalStateException("Idempotency result entry is missing"));
            return Optional.of(response(replayed, config));
        }
        List<QueueEntry> waiting = entries
                .findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                        config.getId(), date, Set.of(QueueStatus.CHECKED_IN, QueueStatus.QUEUED));
        QueueEntry candidate = ConsultationQueueScheduler.next(waiting, config.getLastServedLane());
        if (candidate == null) {
            recordEmptyCommand(COMMAND_CALL_NEXT, config.getId(), normalizedKey, requestFingerprint);
            return Optional.empty();
        }
        config.setLastServedLane(candidate.getSchedulingLane());
        candidate.markCalled(calledByUserId, Instant.now());
        entries.saveAndFlush(candidate);
        configs.save(config);
        events.append(
                candidate,
                config,
                "PatientCalled",
                AppConstants.RK_QUEUE_CALLED,
                correlationId,
                Map.of(
                        "callAttempt", candidate.getCallAttempts(),
                        "calledByUserId", calledByUserId));
        recordCommand(COMMAND_CALL_NEXT, config.getId(), normalizedKey, requestFingerprint, candidate);
        appendNearTurnEvents(config, date, correlationId);
        return Optional.of(response(candidate, config));
    }

    @Transactional
    public QueueEntryResponse call(UUID entryId,
                                   UUID calledByUserId,
                                   String idempotencyKey,
                                   String correlationId) {
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        String requestFingerprint = fingerprint(entryId, calledByUserId);
        Optional<QueueEntryResponse> replay = replay(
                COMMAND_CALL_ENTRY, entryId, normalizedKey, requestFingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }

        QueueEntry entry = requireEntryForUpdate(entryId);
        if (!entry.isWaitingForCall()) {
            throw invalidTransition(entry, QueueStatus.CALLED);
        }
        QueueConfig config = configFor(entry);
        if (config != null && !config.isActive()) {
            throw new BusinessException(409, "Phòng đang tạm ngừng gọi lượt mới.");
        }
        entry.markCalled(calledByUserId, Instant.now());
        entries.saveAndFlush(entry);
        if (config != null && entry.getSchedulingLane() != null) {
            config.setLastServedLane(entry.getSchedulingLane());
            configs.save(config);
        }
        events.append(
                entry,
                config,
                "PatientCalled",
                AppConstants.RK_QUEUE_CALLED,
                correlationId,
                Map.of(
                        "callAttempt", entry.getCallAttempts(),
                        "calledByUserId", calledByUserId,
                        "selectedByActor", true));
        recordCommand(COMMAND_CALL_ENTRY, entryId, normalizedKey, requestFingerprint, entry);
        if (config != null) {
            appendNearTurnEvents(config, entry.getQueueDate(), correlationId);
        }
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse call(UUID entryId,
                                   UUID calledByUserId,
                                   String role,
                                   String idempotencyKey,
                                   String correlationId) {
        accessValidator.requireEntryAssignmentAccess(entryId, calledByUserId, role, this::configFor);
        return call(entryId, calledByUserId, idempotencyKey, correlationId);
    }

    @Transactional
    public QueueEntryResponse recall(UUID entryId, UUID calledByUserId, String correlationId) {
        QueueEntry entry = requireEntryForUpdate(entryId);
        if (entry.getStatus() != QueueStatus.CALLED) {
            throw invalidTransition(entry, QueueStatus.CALLED);
        }
        entry.recordRecall(calledByUserId, Instant.now(), MAX_CALL_ATTEMPTS);
        QueueConfig config = configFor(entry);
        entries.saveAndFlush(entry);
        events.append(
                entry,
                config,
                "PatientCalled",
                AppConstants.RK_QUEUE_CALLED,
                correlationId,
                Map.of(
                        "callAttempt", entry.getCallAttempts(),
                        "calledByUserId", calledByUserId,
                        "recalled", true));
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse recall(UUID entryId, UUID calledByUserId, String role, String correlationId) {
        accessValidator.requireEntryAssignmentAccess(entryId, calledByUserId, role, this::configFor);
        return recall(entryId, calledByUserId, correlationId);
    }

    @Transactional
    public QueueEntryResponse miss(UUID entryId, String correlationId) {
        QueueEntry entry = requireEntryForUpdate(entryId);
        if (entry.getStatus() != QueueStatus.CALLED) {
            throw invalidTransition(entry, QueueStatus.MISSED);
        }
        entry.markMissed(Instant.now(), MAX_CALL_ATTEMPTS);
        QueueConfig config = configFor(entry);
        entries.saveAndFlush(entry);
        events.append(
                entry,
                config,
                "QueueEntryMissed",
                AppConstants.RK_QUEUE_MISSED,
                correlationId,
                Map.of("missedCount", entry.getMissedCount()));
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse miss(UUID entryId, UUID requesterUserId, String role, String correlationId) {
        accessValidator.requireEntryAssignmentAccess(entryId, requesterUserId, role, this::configFor);
        return miss(entryId, correlationId);
    }

    @Transactional
    public QueueEntryResponse requeue(UUID entryId, RequeueRequest request, String correlationId) {
        QueueEntry snapshot = entries.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", entryId));
        QueueConfig config = snapshot.getQueueConfigId() == null ? null : requireLockedEntryConfig(snapshot);
        QueueEntry entry = requireEntryForUpdate(entryId);
        QueueStatus requeuedStatus = entry.getQueueType() == QueueType.CONSULTATION
                && entry.getConsultationPhase() == ConsultationPhase.INITIAL
                ? QueueStatus.CHECKED_IN
                : QueueStatus.QUEUED;
        if (entry.getStatus() != QueueStatus.MISSED) {
            throw invalidTransition(entry, requeuedStatus);
        }
        boolean back = resolveRequeueBackPosition(entry, config, request);
        Instant eligibleSinceAt;
        if (back) {
            eligibleSinceAt = Instant.now();
        } else {
            QueueEntry first = firstCandidate(config, entry.getQueueDate(), entry.getSchedulingLane());
            eligibleSinceAt = first == null ? Instant.now() : first.getEligibleSinceAt().minusSeconds(1);
        }
        entry.markRequeued(requeuedStatus, eligibleSinceAt);
        entries.saveAndFlush(entry);
        events.append(
                entry,
                config,
                "QueueEntryRequeued",
                "queue.requeued",
                correlationId,
                Map.of("requeued", true, "position", back ? "BACK" : "FRONT"));
        return response(entry, config);
    }

    private boolean resolveRequeueBackPosition(QueueEntry entry, QueueConfig config, RequeueRequest request) {
        if (entry.getConsultationPhase() == ConsultationPhase.RESULT_REVIEW || config == null) {
            return true;
        }
        if (config.getMissedPolicy() == MissedPolicy.REQUIRE_MANUAL) {
            if (request == null || request.position() == null) {
                throw new BusinessException(400, "Policy REQUIRE_MANUAL yêu cầu position FRONT hoặc BACK");
            }
            return request.position() == RequeueRequest.Position.BACK;
        }
        return config.getMissedPolicy() == MissedPolicy.REQUEUE_BACK;
    }

    @Transactional
    public QueueEntryResponse requeue(UUID entryId,
                                      RequeueRequest request,
                                      UUID requesterUserId,
                                      String role,
                                      String correlationId) {
        accessValidator.requireEntryAssignmentAccess(entryId, requesterUserId, role, this::configFor);
        return requeue(entryId, request, correlationId);
    }

    @Transactional
    public QueueEntryResponse start(UUID entryId, String correlationId) {
        QueueEntry entry = requireEntryForUpdate(entryId);
        if (entry.getStatus() == QueueStatus.IN_PROGRESS) {
            return response(entry, configFor(entry));
        }
        if (entry.getStatus() != QueueStatus.CALLED) {
            throw invalidTransition(entry, QueueStatus.IN_PROGRESS);
        }
        QueueConfig config = configFor(entry);
        entry.markStarted(Instant.now());
        entries.saveAndFlush(entry);
        events.append(entry, config, "QueueEntryStarted", AppConstants.RK_QUEUE_STARTED, correlationId, Map.of());
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse start(UUID entryId, UUID requesterUserId, String role, String correlationId) {
        accessValidator.requireClinicalOperator(entryId, role);
        accessValidator.requireEntryAssignmentAccess(entryId, requesterUserId, role, this::configFor);
        return start(entryId, correlationId);
    }

    @Transactional
    public QueueEntryResponse complete(UUID entryId, String correlationId) {
        QueueEntry snapshot = entries.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", entryId));
        if (snapshot.getQueueType() == QueueType.PHARMACY_DISPENSING) {
            throw new BusinessException(409, "Lượt phát thuốc chỉ hoàn tất khi nhận PrescriptionDispensed");
        }
        QueueConfig config = snapshot.getQueueConfigId() == null ? null : requireLockedEntryConfig(snapshot);
        QueueEntry entry = requireEntryForUpdate(entryId);
        if (entry.getStatus() == QueueStatus.COMPLETED) {
            return response(entry, config);
        }
        if (entry.getStatus() != QueueStatus.IN_PROGRESS) {
            throw invalidTransition(entry, QueueStatus.COMPLETED);
        }
        entry.markCompleted(Instant.now());
        entries.saveAndFlush(entry);
        if (config != null && entry.getQueueType() == QueueType.CONSULTATION) {
            updateAverage(config, entry.getQueueDate());
        }
        events.append(
                entry,
                config,
                "QueueEntryCompleted",
                AppConstants.RK_QUEUE_COMPLETED,
                correlationId,
                Map.of("serviceMinutes", consultationMinutes(entry)));
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse complete(UUID entryId, UUID requesterUserId, String role, String correlationId) {
        accessValidator.requireClinicalOperator(entryId, role);
        accessValidator.requireEntryAssignmentAccess(entryId, requesterUserId, role, this::configFor);
        return complete(entryId, correlationId);
    }

    private QueueEntry newEntry(QueueConfig config,
                                LocalDate date,
                                UUID patientId,
                                UUID userId,
                                UUID appointmentId,
                                PriorityLevel priority,
                                QueueStatus status) {
        if (appointmentId == null
                && entries.existsByPatientIdAndDepartmentIdAndQueueDateAndStatusIn(
                        patientId,
                        config.getDepartmentId(),
                        date,
                        Set.of(QueueStatus.CHECKED_IN, QueueStatus.CALLED, QueueStatus.IN_PROGRESS, QueueStatus.MISSED))) {
            throw new BusinessException(409, "Bệnh nhân đã có một lượt khám đang hoạt động tại khoa trong ngày");
        }
        QueueNumberSequence sequence = sequences.findByQueueConfigIdAndQueueDate(config.getId(), date)
                .orElseGet(() -> {
                    QueueNumberSequence created = new QueueNumberSequence();
                    created.setQueueConfigId(config.getId());
                    created.setQueueDate(date);
                    return sequences.saveAndFlush(created);
                });
        // The configuration is locked by every caller. Seed/import rows may exist
        // without a counter, or the counter may be older than persisted entries.
        sequence.setLastNumber(Math.max(sequence.getLastNumber(), entries.maximumSequence(config.getId(), date)) + 1);
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
        entry.setQueueType(QueueType.CONSULTATION);
        entry.setConsultationPhase(ConsultationPhase.INITIAL);
        entry.setQueueClass(priority == PriorityLevel.PRIORITY ? QueueClass.PRIORITY : QueueClass.NORMAL);
        entry.setStatus(status);
        return entry;
    }

    public QueueEntry createAppointmentEntry(QueueConfig config,
                                             LocalDate date,
                                             LocalTime scheduledStart,
                                             String timeSlot,
                                             String departmentCode,
                                             String roomDisplayName,
                                             UUID appointmentId,
                                             UUID patientId,
                                             UUID userId) {
        QueueEntry entry = newEntry(
                config, date, patientId, userId, appointmentId, PriorityLevel.APPOINTMENT, QueueStatus.WAITING);
        entry.setScheduledStartAt(ZonedDateTime.of(date, scheduledStart, businessZone).toInstant());
        entry.setTimeSlot(timeSlot);
        entry.setDepartmentCode(departmentCode);
        entry.setRoomDisplayNameSnapshot(roomDisplayName);
        return entry;
    }

    public QueueEntry createPharmacyEntry(UUID prescriptionId,
                                          UUID consultationId,
                                          UUID patientId,
                                          String servicePointId,
                                          Instant queuedAt) {
        return servicePointSupport.createPharmacyEntry(
                prescriptionId, consultationId, patientId, servicePointId, queuedAt);
    }

    @Transactional(readOnly = true)
    public QueueEntryResponse patientCurrent(UUID patientId, UUID userId, String role) {
        QueueEntryResponse entry = patientCurrent(patientId, userId, !AppConstants.ROLE_PATIENT.equals(role));
        if (!AppConstants.ROLE_PATIENT.equals(role)) {
            accessValidator.requireEntryAssignmentAccess(entry.entryId(), userId, role, this::configFor);
        }
        return entry;
    }

    @Transactional(readOnly = true)
    public VisitTicketResponse ticket(UUID appointmentId, UUID userId, String role) {
        VisitTicketResponse ticket = ticket(appointmentId, userId, !AppConstants.ROLE_PATIENT.equals(role));
        if (!AppConstants.ROLE_PATIENT.equals(role)) {
            QueueEntry entry = entries.findByAppointmentId(appointmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("VisitTicket", "appointmentId", appointmentId));
            accessValidator.requireEntryAssignmentAccess(entry.getId(), userId, role, this::configFor);
        }
        return ticket;
    }

    @Transactional(readOnly = true)
    public QueueDashboardResponse dashboard(UUID departmentId, UUID userId, String role) {
        QueueConfig config = requireConfig(departmentId);
        accessValidator.requireRoomAccess(config.getRoomCode(), userId, role);
        return dashboard(departmentId);
    }

    @Transactional
    public Optional<QueueEntryResponse> callNext(UUID departmentId,
                                                 UUID userId,
                                                 String role,
                                                 String idempotencyKey,
                                                 String correlationId) {
        QueueConfig config = requireConfig(departmentId);
        accessValidator.requireRoomAccess(config.getRoomCode(), userId, role);
        return callNext(departmentId, userId, idempotencyKey, correlationId);
    }

    @Transactional(readOnly = true)
    public QueueEntryResponse currentLabExecution(UUID labOrderId, String servicePointId, UUID userId, String role) {
        String point = QueueAccessValidator.normalizeServicePointId(servicePointId);
        accessValidator.requireServicePointAccess(point, userId, role);
        return servicePointSupport.currentLabExecution(labOrderId, point);
    }

    @Transactional(readOnly = true)
    public QueueEntryResponse currentLabExecution(UUID labOrderId) {
        return servicePointSupport.currentLabExecution(labOrderId);
    }

    @Transactional(readOnly = true)
    public QueueEntryResponse currentLabExecution(UUID labOrderId, UUID requesterUserId, String role) {
        QueueEntry entry = entries.findFirstByLabOrderIdOrderByCreatedAtDesc(labOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab queue entry", "labOrderId", labOrderId));
        accessValidator.requireServicePointAccess(entry.getServicePointId(), requesterUserId, role);
        return currentLabExecution(labOrderId);
    }

    @Transactional
    public QueueEntry createLabExecutionEntry(UUID labOrderId,
                                              UUID consultationId,
                                              UUID patientId,
                                              String servicePointId,
                                              Instant queuedAt) {
        return servicePointSupport.createLabExecutionEntry(
                labOrderId, consultationId, patientId, servicePointId, queuedAt);
    }

    public QueueEntry createResultReviewEntry(UUID consultationId,
                                              UUID patientId,
                                              UUID sourceQueueEntryId,
                                              Instant queuedAt) {
        Optional<QueueEntry> existing = entries.findByConsultationIdAndQueueTypeAndConsultationPhase(
                consultationId, QueueType.CONSULTATION, ConsultationPhase.RESULT_REVIEW);
        if (existing.isPresent()) {
            return existing.get();
        }

        QueueEntry source = resolveInitialConsultationEntry(consultationId, sourceQueueEntryId);
        if (!source.getPatientId().equals(patientId)) {
            throw new BusinessException(422, "patientId không khớp lượt khám ban đầu");
        }
        if (source.getConsultationId() != null && !source.getConsultationId().equals(consultationId)) {
            throw new BusinessException(422, "consultationId không khớp lượt khám ban đầu");
        }
        if (source.getConsultationId() == null) {
            source.setConsultationId(consultationId);
            entries.save(source);
        }

        QueueEntry review = new QueueEntry();
        review.setQueueConfigId(source.getQueueConfigId());
        review.setDepartmentId(source.getDepartmentId());
        review.setDepartmentCode(source.getDepartmentCode());
        review.setConsultationId(consultationId);
        review.setPatientId(patientId);
        review.setUserId(source.getUserId());
        review.setPatientNameSnapshot(source.getPatientNameSnapshot());
        review.setServiceCode(source.getServiceCode());
        review.setQueueType(QueueType.CONSULTATION);
        review.setConsultationPhase(ConsultationPhase.RESULT_REVIEW);
        review.setQueueClass(null);
        review.setQueueDate(LocalDate.ofInstant(queuedAt, businessZone));
        review.setSequenceNumber(source.getSequenceNumber());
        review.setQueueNumber(source.getQueueNumber() + "-R");
        review.setPriorityLevel(PriorityLevel.APPOINTMENT);
        review.setStatus(QueueStatus.QUEUED);
        review.setEligibleSinceAt(queuedAt);
        review.setRoomDisplayNameSnapshot(source.getRoomDisplayNameSnapshot());
        return review;
    }

    private QueueEntry resolveInitialConsultationEntry(UUID consultationId, UUID sourceQueueEntryId) {
        QueueEntry source = null;
        if (sourceQueueEntryId != null) {
            source = entries.findById(sourceQueueEntryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Initial QueueEntry", "id", sourceQueueEntryId));
        }
        if (source == null) {
            source = entries.findByConsultationIdAndQueueTypeAndConsultationPhase(
                    consultationId, QueueType.CONSULTATION, ConsultationPhase.INITIAL).orElse(null);
        }
        if (source == null) {
            throw new BusinessException(422, "Không tìm thấy lượt khám ban đầu để tạo hàng chờ đọc kết quả");
        }
        if (source.getQueueType() != QueueType.CONSULTATION
                || source.getConsultationPhase() != ConsultationPhase.INITIAL) {
            throw new BusinessException(422, "sourceQueueEntryId không phải lượt khám ban đầu");
        }
        return source;
    }

    @Transactional
    public void cancelPharmacyEntry(UUID prescriptionId, String correlationId) {
        servicePointSupport.cancelPharmacyEntry(prescriptionId, correlationId);
    }

    @Transactional
    public void completePharmacyEntry(UUID prescriptionId, Instant dispensedAt, String correlationId) {
        servicePointSupport.completePharmacyEntry(prescriptionId, dispensedAt, correlationId);
    }

    @Transactional
    public void completePharmacyEntry(UUID prescriptionId,
                                      UUID requesterUserId,
                                      String role,
                                      Instant dispensedAt,
                                      String correlationId) {
        QueueEntry entry = entries.findByPrescriptionId(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy QueueEntry", "prescriptionId", prescriptionId));
        accessValidator.requireServicePointAccess(entry.getServicePointId(), requesterUserId, role);
        completePharmacyEntry(prescriptionId, dispensedAt, correlationId);
    }

    @Transactional(readOnly = true)
    public QueueEntryResponse currentPharmacyEntry(UUID prescriptionId) {
        return servicePointSupport.currentPharmacyEntry(prescriptionId);
    }

    @Transactional(readOnly = true)
    public QueueEntryResponse currentPharmacyEntry(UUID prescriptionId, UUID requesterUserId, String role) {
        QueueEntry entry = entries.findByPrescriptionId(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy QueueEntry", "prescriptionId", prescriptionId));
        accessValidator.requireServicePointAccess(entry.getServicePointId(), requesterUserId, role);
        return currentPharmacyEntry(prescriptionId);
    }

    public QueueConfig requireLockedConfig(UUID departmentId) {
        if (roomConfigurations != null) {
            List<QueueConfig> available = configs.findAllByDepartmentIdOrderByRoomCodeAsc(departmentId).stream()
                    .filter(QueueConfig::isActive)
                    .toList();
            if (available.size() != 1) {
                throw new BusinessException(409, "Khoa có nhiều phòng hoặc chưa cấu hình; hãy chọn phòng cụ thể.");
            }
            return configs.findByIdForUpdate(available.getFirst().getId()).orElseThrow();
        }
        return configs.findFirstByDepartmentIdAndActiveTrue(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Active QueueConfig", "departmentId", departmentId));
    }

    public QueueConfig requireScheduledRoom(String roomId, UUID departmentId) {
        // A paused reception policy does not invalidate a future booked ticket.
        return roomConfigurations == null
                ? requireLockedRoomConfig(roomId, departmentId)
                : roomConfigurations.ensure(roomId, departmentId);
    }

    public QueueConfig requireLockedRoomConfig(String roomId, UUID departmentId) {
        QueueConfig result = roomConfigurations == null
                ? configs.findFirstByRoomCodeAndActiveTrue(roomId)
                        .orElseThrow(() -> new ResourceNotFoundException("QueueConfig", "roomId", roomId))
                : roomConfigurations.ensure(roomId, departmentId);
        if (!result.isActive() || (departmentId != null && !departmentId.equals(result.getDepartmentId()))) {
            throw new BusinessException(409, "Cấu hình phòng không hoạt động hoặc không thuộc khoa.");
        }
        return result;
    }

    public QueueConfig requireEntryConfigForEvent(QueueEntry entry) {
        return requireLockedEntryConfig(entry);
    }

    private QueueConfig requireLockedEntryConfig(QueueEntry entry) {
        if (roomConfigurations == null) {
            return requireLockedConfig(entry.getDepartmentId());
        }
        return configs.findByIdForUpdate(entry.getQueueConfigId())
                .orElseThrow(() -> new ResourceNotFoundException("QueueConfig", "id", entry.getQueueConfigId()));
    }

    private QueueConfig requireConfig(UUID departmentId) {
        if (roomConfigurations != null) {
            List<QueueConfig> available = configs.findAllByDepartmentIdOrderByRoomCodeAsc(departmentId).stream()
                    .filter(QueueConfig::isActive)
                    .toList();
            if (available.size() != 1) {
                throw new BusinessException(409, "Chọn phòng cụ thể để truy cập hàng đợi.");
            }
            return available.getFirst();
        }
        return configs.findByDepartmentIdAndActiveTrue(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Active QueueConfig", "departmentId", departmentId));
    }

    private QueueConfig configFor(QueueEntry entry) {
        if (entry.getQueueConfigId() == null) {
            return null;
        }
        if (roomConfigurations == null) {
            return requireConfig(entry.getDepartmentId());
        }
        return configs.findById(entry.getQueueConfigId())
                .orElseThrow(() -> new ResourceNotFoundException("QueueConfig", "id", entry.getQueueConfigId()));
    }

    private QueueConfig requireRoomConfig(String roomId) {
        Optional<QueueConfig> found = roomConfigurations == null
                ? configs.findByRoomCodeAndActiveTrue(roomId)
                : configs.findByRoomCode(roomId);
        return found.orElseThrow(() -> new ResourceNotFoundException("QueueConfig", "roomId", roomId));
    }

    private QueueEntry requireEntryForUpdate(UUID id) {
        return entries.findFirstById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", id));
    }

    private QueueEntry firstCandidate(QueueConfig config, LocalDate date, SchedulingLane lane) {
        return entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                        config.getId(), date, Set.of(QueueStatus.CHECKED_IN, QueueStatus.QUEUED))
                .stream()
                .filter(QueueEntry::isWaitingForCall)
                .filter(entry -> entry.getSchedulingLane() == lane)
                .findFirst()
                .orElse(null);
    }

    private QueueEntryResponse response(QueueEntry entry, QueueConfig config) {
        if (config == null) {
            Integer position = entry.getStatus() == QueueStatus.QUEUED
                    ? servicePointSupport.servicePointPosition(entry)
                    : 0;
            Integer wait;
            if (entry.getStatus() == QueueStatus.QUEUED) {
                wait = entry.getEstimatedWaitMinutes();
            } else {
                wait = 0;
            }
            return QueueEntryResponse.fromServicePoint(entry, position, wait);
        }
        Integer position = null;
        Integer wait = entry.getEstimatedWaitMinutes();
        if (entry.isWaitingForCall()) {
            List<QueueEntry> active = entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                    config.getId(), entry.getQueueDate(), Set.of(QueueStatus.CHECKED_IN, QueueStatus.QUEUED));
            Instant now = Instant.now();
            int currentConsultation = entries.existsByQueueConfigIdAndQueueDateAndStatusIn(
                    config.getId(), entry.getQueueDate(), SERVING_STATUSES)
                    ? config.getAvgConsultationMinutes()
                    : 0;
            List<ConsultationQueueScheduler.ScheduledEntry> schedule = ConsultationQueueScheduler.schedule(
                    active,
                    config.getLastServedLane(),
                    now.plus(Duration.ofMinutes(currentConsultation)),
                    config.getAvgConsultationMinutes());
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
        return config == null
                ? QueueEntryResponse.fromServicePoint(entry, position, wait)
                : QueueEntryResponse.from(entry, QueueConfigResponse.from(config), position, wait);
    }

    private List<QueueEntryResponse> lane(List<QueueEntryResponse> entries, SchedulingLane lane) {
        return entries.stream()
                .filter(entry -> entry.schedulingLane() == lane)
                .toList();
    }

    private void appendNearTurnEvents(QueueConfig config, LocalDate date, String correlationId) {
        List<QueueEntry> waiting = entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                config.getId(), date, Set.of(QueueStatus.CHECKED_IN, QueueStatus.QUEUED));
        Map<UUID, QueueEntry> byId = new HashMap<>();
        waiting.forEach(entry -> byId.put(entry.getId(), entry));
        Instant now = Instant.now();
        int currentWorkload = entries.existsByQueueConfigIdAndQueueDateAndStatusIn(
                config.getId(), date, SERVING_STATUSES)
                ? config.getAvgConsultationMinutes()
                : 0;
        List<ConsultationQueueScheduler.ScheduledEntry> schedule = ConsultationQueueScheduler.schedule(
                waiting,
                config.getLastServedLane(),
                now.plus(Duration.ofMinutes(currentWorkload)),
                config.getAvgConsultationMinutes());
        int limit = Math.min(config.getNearTurnThreshold(), schedule.size());
        for (int index = 0; index < limit; index++) {
            ConsultationQueueScheduler.ScheduledEntry scheduled = schedule.get(index);
            QueueEntry entry = byId.get(scheduled.entryId());
            if (entry.getNearTurnNotifiedAt() != null) {
                continue;
            }
            int position = index + 1;
            int wait = projectedWaitMinutes(now, scheduled.projectedStartAt());
            events.append(
                    entry,
                    config,
                    "QueueNearTurn",
                    AppConstants.RK_QUEUE_NEAR_TURN,
                    correlationId,
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

    private Optional<QueueEntryResponse> replay(String command,
                                                UUID scopeId,
                                                String idempotencyKey,
                                                String requestFingerprint) {
        return idempotencyRecords.findByCommandNameAndScopeIdAndIdempotencyKey(command, scopeId, idempotencyKey)
                .map(record -> {
                    requireSameFingerprint(record, requestFingerprint);
                    if (record.isEmptyResult()) {
                        throw new IllegalStateException("Command unexpectedly contains an empty result");
                    }
                    QueueEntry entry = entries.findById(record.getResultEntryId())
                            .orElseThrow(() -> new IllegalStateException("Idempotency result entry is missing"));
                    QueueConfig config = entry.getQueueConfigId() == null
                            ? null
                            : configs.findById(entry.getQueueConfigId())
                                    .orElseThrow(() -> new IllegalStateException("Idempotency result config is missing"));
                    return response(entry, config);
                });
    }

    private void recordCommand(String command,
                               UUID scopeId,
                               String idempotencyKey,
                               String requestFingerprint,
                               QueueEntry entry) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setCommandName(command);
        record.setScopeId(scopeId);
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestFingerprint(requestFingerprint);
        record.setResultEntryId(entry.getId());
        idempotencyRecords.save(record);
    }

    private void recordEmptyCommand(String command,
                                    UUID scopeId,
                                    String idempotencyKey,
                                    String requestFingerprint) {
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
            int average = (int) Math.round(completed.stream()
                    .mapToLong(this::consultationMinutes)
                    .average()
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
