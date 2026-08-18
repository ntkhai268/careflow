package com.careflow.queue.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.queue.domain.*;
import com.careflow.queue.dto.*;
import com.careflow.queue.client.DirectoryClient;
import com.careflow.queue.client.dto.DoctorAssignmentResponse;
import com.careflow.queue.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final String DEFAULT_HOSPITAL_SITE_ID = "HOSPITAL-MAIN";
    private static final String DEFAULT_SESSION_CODE = "MORNING";
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

    private final QueueConfigRepository configs;
    private final QueueNumberSequenceRepository sequences;
    private final ServicePointSequenceRepository servicePointSequences;
    private final QueueEntryRepository entries;
    private final IdempotencyRecordRepository idempotencyRecords;
    private final QueueEventService events;
    private final QrTokenService qrTokens;
    private final HospitalCheckInConfigService hospitalCheckInConfigService;
    private final ZoneId businessZone;
    private final DirectoryClient directoryClient;
    private final String geofenceSiteId;
    private final double geofenceLatitude;
    private final double geofenceLongitude;
    private final double geofenceRadiusMeters;
    private final double geofenceMaxAccuracyMeters;

    public QueueManagementService(QueueConfigRepository configs, QueueNumberSequenceRepository sequences,
                                  ServicePointSequenceRepository servicePointSequences,
                                  QueueEntryRepository entries, IdempotencyRecordRepository idempotencyRecords,
                                  QueueEventService events, QrTokenService qrTokens,
                                  @Value("${queue.business-zone:Asia/Ho_Chi_Minh}") String businessZone) {
        this(configs, sequences, servicePointSequences, entries, idempotencyRecords, events, qrTokens,
                businessZone, null, null, DEFAULT_HOSPITAL_SITE_ID, DEFAULT_GEOFENCE_LATITUDE,
                DEFAULT_GEOFENCE_LONGITUDE, DEFAULT_GEOFENCE_RADIUS_METERS,
                DEFAULT_GEOFENCE_MAX_ACCURACY_METERS);
    }

    public QueueManagementService(QueueConfigRepository configs, QueueNumberSequenceRepository sequences,
                                  ServicePointSequenceRepository servicePointSequences,
                                  QueueEntryRepository entries, IdempotencyRecordRepository idempotencyRecords,
                                  QueueEventService events, QrTokenService qrTokens,
                                  String businessZone, DirectoryClient directoryClient) {
        this(configs, sequences, servicePointSequences, entries, idempotencyRecords, events, qrTokens,
                businessZone, directoryClient, null, DEFAULT_HOSPITAL_SITE_ID, DEFAULT_GEOFENCE_LATITUDE,
                DEFAULT_GEOFENCE_LONGITUDE, DEFAULT_GEOFENCE_RADIUS_METERS,
                DEFAULT_GEOFENCE_MAX_ACCURACY_METERS);
    }

    public QueueManagementService(QueueConfigRepository configs, QueueNumberSequenceRepository sequences,
                                  ServicePointSequenceRepository servicePointSequences,
                                  QueueEntryRepository entries, IdempotencyRecordRepository idempotencyRecords,
                                  QueueEventService events, QrTokenService qrTokens,
                                  String businessZone, DirectoryClient directoryClient,
                                  HospitalCheckInConfigService hospitalCheckInConfigService) {
        this(configs, sequences, servicePointSequences, entries, idempotencyRecords, events, qrTokens,
                businessZone, directoryClient, hospitalCheckInConfigService, DEFAULT_HOSPITAL_SITE_ID,
                DEFAULT_GEOFENCE_LATITUDE, DEFAULT_GEOFENCE_LONGITUDE, DEFAULT_GEOFENCE_RADIUS_METERS,
                DEFAULT_GEOFENCE_MAX_ACCURACY_METERS);
    }

    @Autowired
    public QueueManagementService(QueueConfigRepository configs, QueueNumberSequenceRepository sequences,
                                  ServicePointSequenceRepository servicePointSequences,
                                  QueueEntryRepository entries, IdempotencyRecordRepository idempotencyRecords,
                                  QueueEventService events, QrTokenService qrTokens,
                                  @Value("${queue.business-zone:Asia/Ho_Chi_Minh}") String businessZone,
                                  DirectoryClient directoryClient,
                                  HospitalCheckInConfigService hospitalCheckInConfigService,
                                  @Value("${queue.geofence.site-id:HOSPITAL-MAIN}") String geofenceSiteId,
                                  @Value("${queue.geofence.latitude:10.7769}") double geofenceLatitude,
                                  @Value("${queue.geofence.longitude:106.7009}") double geofenceLongitude,
                                  @Value("${queue.geofence.radius-meters:150}") double geofenceRadiusMeters,
                                  @Value("${queue.geofence.max-accuracy-meters:50}") double geofenceMaxAccuracyMeters) {
        this.configs = configs;
        this.sequences = sequences;
        this.servicePointSequences = servicePointSequences;
        this.entries = entries;
        this.idempotencyRecords = idempotencyRecords;
        this.events = events;
        this.qrTokens = qrTokens;
        this.hospitalCheckInConfigService = hospitalCheckInConfigService;
        this.businessZone = ZoneId.of(businessZone);
        this.directoryClient = directoryClient;
        this.geofenceSiteId = geofenceSiteId;
        this.geofenceLatitude = geofenceLatitude;
        this.geofenceLongitude = geofenceLongitude;
        if (geofenceRadiusMeters <= 0 || geofenceMaxAccuracyMeters <= 0) {
            throw new IllegalArgumentException("Geofence radius and accuracy must be positive");
        }
        this.geofenceRadiusMeters = geofenceRadiusMeters;
        this.geofenceMaxAccuracyMeters = geofenceMaxAccuracyMeters;
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
        QueueConfig config = entry.getQueueConfigId() == null ? null : configs.findById(entry.getQueueConfigId())
                .orElseThrow(() -> new ResourceNotFoundException("QueueConfig", "id", entry.getQueueConfigId()));
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
                entry.getQueueDate(), entry.getTimeSlot(), null,
                entry.getStatus() == QueueStatus.WAITING
                        ? "TICKET_ISSUED" : entry.getStatus().name());
    }

    @Transactional(readOnly = true)
    public CheckInQrResponse issueHospitalCheckInQr(String roomId, LocalDate date, String session,
                                                    UUID requesterUserId, String role) {
        requireRoomAccess(roomId, requesterUserId, role);
        requireRoomConfig(roomId);
        LocalDate sessionDate = date == null ? businessDate() : date;
        if (!businessDate().equals(sessionDate)) {
            throw new BusinessException(422, "Chỉ có thể hiển thị QR check-in của ngày hiện tại");
        }
        String sessionCode = session == null || session.isBlank()
                ? DEFAULT_SESSION_CODE : session.trim().toUpperCase(Locale.ROOT);
        QrTokenService.IssuedHospitalQr issued = qrTokens.issueHospitalQr(roomId, sessionDate, sessionCode);
        return new CheckInQrResponse(currentGeofence().siteId(), roomId, sessionCode, sessionDate,
                issued.token(), issued.expiresAt());
    }

    @Transactional
    public QueueEntryResponse checkIn(CheckInRequest request, UUID actorUserId, String correlationId) {
        return checkIn(request, actorUserId, AppConstants.ROLE_STAFF, correlationId);
    }

    @Transactional
    public QueueEntryResponse checkIn(CheckInRequest request, UUID actorUserId, String role,
                                      String correlationId) {
        if (AppConstants.ROLE_PATIENT.equalsIgnoreCase(role)) {
            return checkInAsPatient(request, actorUserId, correlationId);
        }
        return checkInAsStaff(request, actorUserId, correlationId);
    }

    private QueueEntryResponse checkInAsPatient(CheckInRequest request, UUID patientUserId,
                                                String correlationId) {
        if (request.appointmentId() == null || request.checkInQrToken() == null
                || request.checkInQrToken().isBlank()) {
            throw new BusinessException(400, "Vui lòng quét QR check-in của bệnh viện");
        }
        QrTokenService.HospitalQrClaims claims = qrTokens.verifyHospitalQr(request.checkInQrToken());
        if (!claims.sessionDate().equals(businessDate())) {
            throw new BusinessException(422, "QR không thuộc ngày hiện tại");
        }
        QueueEntry entry = entries.findFirstByAppointmentId(request.appointmentId())
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "appointmentId", request.appointmentId()));
        if (entry.getUserId() == null || !entry.getUserId().equals(patientUserId)) {
            throw new BusinessException(403, "Lịch hẹn không thuộc tài khoản bệnh nhân này");
        }
        if (!entry.getQueueDate().equals(claims.sessionDate())) {
            throw new BusinessException(409, "QR không khớp ngày khám của lịch hẹn");
        }
        QueueConfig config = requireConfig(entry.getDepartmentId());
        if (!config.getRoomCode().equals(claims.roomId())) {
            throw new BusinessException(409, "QR không thuộc phòng khám của lịch hẹn");
        }
        if (entry.getStatus() == QueueStatus.CHECKED_IN) return response(entry, config);

        HospitalCheckInConfigService.GeofenceSettings geofence = currentGeofence();
        validateCoordinates(request, geofence.maxAccuracyMeters());
        double distanceMeters = distanceMeters(request.latitude(), request.longitude(),
                geofence.latitude(), geofence.longitude());
        if (distanceMeters > geofence.radiusMeters()) {
            throw new BusinessException(422, "Bạn đang ở ngoài khu vực bệnh viện, chưa thể check-in");
        }
        return activateCheckedInEntry(entry, config, request, patientUserId, correlationId,
                "PATIENT_QR_GEOFENCE", distanceMeters);
    }

    private QueueEntryResponse checkInAsStaff(CheckInRequest request, UUID staffUserId,
                                              String correlationId) {
        if ((request.qrToken() == null || request.qrToken().isBlank())
                && (request.ticketCode() == null || request.ticketCode().isBlank())) {
            throw new BusinessException(400, "Vui l\u00f2ng cung c\u1ea5p m\u00e3 QR ho\u1eb7c m\u00e3 phi\u1ebfu kh\u00e1m");
        }
        if (request.ticketCode() != null && !request.ticketCode().isBlank()) {
            return checkInByTicketCode(request, staffUserId, correlationId);
        }
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
        return activateCheckedInEntry(entry, config, request, staffUserId, correlationId,
                "STAFF_QR", null);
    }

    private QueueEntryResponse activateCheckedInEntry(QueueEntry entry, QueueConfig config,
                                                       CheckInRequest request, UUID staffUserId,
                                                       String correlationId) {
        return activateCheckedInEntry(entry, config, request, staffUserId, correlationId,
                "STAFF_MANUAL", null);
    }

    private QueueEntryResponse activateCheckedInEntry(QueueEntry entry, QueueConfig config,
                                                       CheckInRequest request, UUID actorUserId,
                                                       String correlationId, String checkInMethod,
                                                       Double distanceMeters) {
        if (entry.getStatus() == QueueStatus.CHECKED_IN) return response(entry, config);
        if (entry.getStatus() == QueueStatus.CANCELLED) {
            throw new BusinessException(409, "Lịch hẹn đã bị hủy");
        }
        if (entry.getStatus() != QueueStatus.WAITING) {
            throw new BusinessException(409, "Trạng thái lượt khám không cho phép check-in");
        }

        Instant now = Instant.now();
        entry.setStatus(QueueStatus.CHECKED_IN);
        entry.setCheckedInAt(now);
        entry.setCheckedInByUserId(actorUserId);
        entry.setCheckInMethod(checkInMethod);
        entry.setCheckInDistanceMeters(distanceMeters);
        entry.setCheckInAccuracyMeters(request.accuracyMeters());
        entry.setCheckInLatitude(request.latitude());
        entry.setCheckInLongitude(request.longitude());
        QueueClass queueClass = request.queueClass() == null ? QueueClass.NORMAL : request.queueClass();
        if (queueClass == QueueClass.PRIORITY) {
            if (request.priorityReasonCode() == null || request.priorityReasonCode().isBlank()) {
                throw new BusinessException(400, "Lượt ưu tiên phải có lý do đã được xác minh");
            }
            entry.setPriorityLevel(PriorityLevel.PRIORITY);
            entry.setQueueClass(QueueClass.PRIORITY);
            entry.setPriorityReasonCode(request.priorityReasonCode().trim());
        } else {
            entry.setPriorityLevel(PriorityLevel.APPOINTMENT);
            entry.setQueueClass(QueueClass.NORMAL);
            entry.setPriorityReasonCode(null);
        }
        entry.setEligibleSinceAt(now);
        entries.saveAndFlush(entry);
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("queueClass", queueClass.name());
        eventData.put("checkedInByUserId", actorUserId);
        eventData.put("checkInMethod", checkInMethod);
        if (distanceMeters != null) eventData.put("distanceMeters", distanceMeters);
        events.append(entry, config, "PatientCheckedIn", AppConstants.RK_QUEUE_CHECKED_IN,
                correlationId, eventData);
        return response(entry, config);
    }

    private QueueEntryResponse checkInByTicketCode(CheckInRequest request, UUID staffUserId,
                                                    String correlationId) {
        QueueConfig config = requireRoomConfig(request.roomId());
        String ticketCode = request.ticketCode().trim();
        QueueEntry entry = entries.findFirstByQueueConfigIdAndQueueDateAndQueueNumber(
                        config.getId(), businessDate(), ticketCode)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "ticketCode", ticketCode));

        return activateCheckedInEntry(entry, config, request, staffUserId, correlationId,
                "STAFF_MANUAL", null);
    }

    private void validateCoordinates(CheckInRequest request, double maxAccuracyMeters) {
        if (request.latitude() == null || request.longitude() == null || request.accuracyMeters() == null) {
            throw new BusinessException(422, "Không thể xác định vị trí hiện tại của thiết bị");
        }
        if (request.latitude() < -90 || request.latitude() > 90
                || request.longitude() < -180 || request.longitude() > 180) {
            throw new BusinessException(422, "Tọa độ vị trí không hợp lệ");
        }
        if (request.accuracyMeters() <= 0 || request.accuracyMeters() > maxAccuracyMeters) {
            throw new BusinessException(422, "Độ chính xác vị trí chưa đạt yêu cầu");
        }
    }

    private HospitalCheckInConfigService.GeofenceSettings currentGeofence() {
        if (hospitalCheckInConfigService != null) return hospitalCheckInConfigService.current();
        return new HospitalCheckInConfigService.GeofenceSettings(
                geofenceSiteId, geofenceLatitude, geofenceLongitude,
                geofenceRadiusMeters, geofenceMaxAccuracyMeters);
    }

    private double distanceMeters(double latitude1, double longitude1,
                                  double latitude2, double longitude2) {
        double earthRadiusMeters = 6_371_000;
        double latDistance = Math.toRadians(latitude2 - latitude1);
        double lonDistance = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @Transactional(readOnly = true)
    public QueueDashboardResponse roomDashboard(String roomId) {
        return dashboard(requireRoomConfig(roomId).getDepartmentId());
    }

    public QueueDashboardResponse roomDashboard(String roomId, UUID requesterUserId, String role) {
        requireRoomAccess(roomId, requesterUserId, role);
        return roomDashboard(roomId);
    }

    @Transactional(readOnly = true)
    public ServicePointQueueResponse servicePointDashboard(String servicePointId, LocalDate requestedDate) {
        String normalized = normalizeServicePointId(servicePointId);
        LocalDate date = requestedDate == null ? businessDate() : requestedDate;
        List<QueueEntry> active = entries
                .findByServicePointIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                        normalized, date, EnumSet.of(QueueStatus.QUEUED, QueueStatus.CALLED, QueueStatus.IN_PROGRESS));
        List<QueueEntryResponse> response = new ArrayList<>();
        active.stream().filter(entry -> SERVING_STATUSES.contains(entry.getStatus()))
                .sorted(Comparator.comparing(QueueEntry::getCalledAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(entry -> response.add(QueueEntryResponse.fromServicePoint(entry, 0, 0)));
        int position = 1;
        QueueEntryResponse recommended = null;
        for (QueueEntry entry : active) {
            if (entry.getStatus() != QueueStatus.QUEUED) continue;
            QueueEntryResponse item = QueueEntryResponse.fromServicePoint(entry, position++, null);
            if (recommended == null) recommended = item;
            response.add(item);
        }
        return new ServicePointQueueResponse(normalized, date, response, recommended);
    }

    @Transactional(readOnly = true)
    public ServicePointQueueResponse servicePointDashboard(String servicePointId, LocalDate requestedDate,
                                                           UUID requesterUserId, String role) {
        requireServicePointAccess(servicePointId, requesterUserId, role);
        return servicePointDashboard(servicePointId, requestedDate);
    }

    @Transactional
    public Optional<QueueEntryResponse> callNextAtServicePoint(
            String servicePointId, UUID calledByUserId, String idempotencyKey, String correlationId) {
        String normalized = normalizeServicePointId(servicePointId);
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        UUID scopeId = UUID.nameUUIDFromBytes(normalized.getBytes(StandardCharsets.UTF_8));
        String fingerprint = fingerprint(normalized, calledByUserId);
        Optional<IdempotencyRecord> existing = idempotencyRecords
                .findByCommandNameAndScopeIdAndIdempotencyKey(
                        COMMAND_CALL_NEXT_SERVICE_POINT, scopeId, normalizedKey);
        if (existing.isPresent()) {
            requireSameFingerprint(existing.get(), fingerprint);
            if (existing.get().isEmptyResult()) return Optional.empty();
            QueueEntry replayed = entries.findById(existing.get().getResultEntryId())
                    .orElseThrow(() -> new IllegalStateException("Idempotency result entry is missing"));
            return Optional.of(QueueEntryResponse.fromServicePoint(replayed, 0, 0));
        }
        QueueEntry candidate = entries
                .findByServicePointIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                        normalized, businessDate(), Set.of(QueueStatus.QUEUED))
                .stream().findFirst().orElse(null);
        if (candidate == null) {
            recordEmptyCommand(COMMAND_CALL_NEXT_SERVICE_POINT, scopeId, normalizedKey, fingerprint);
            return Optional.empty();
        }
        QueueEntry locked = requireEntryForUpdate(candidate.getId());
        if (!locked.isWaitingForCall()) throw invalidTransition(locked, QueueStatus.CALLED);
        Instant now = Instant.now();
        locked.setStatus(QueueStatus.CALLED);
        locked.setCalledAt(now);
        locked.setCalledByUserId(calledByUserId);
        locked.setCallAttempts(1);
        entries.saveAndFlush(locked);
        events.append(locked, null, "PatientCalled", AppConstants.RK_QUEUE_CALLED,
                correlationId, Map.of("callAttempt", 1, "calledByUserId", calledByUserId));
        recordCommand(COMMAND_CALL_NEXT_SERVICE_POINT, scopeId, normalizedKey, fingerprint, locked);
        return Optional.of(QueueEntryResponse.fromServicePoint(locked, 0, 0));
    }

    @Transactional
    public Optional<QueueEntryResponse> callNextAtServicePoint(
            String servicePointId, UUID calledByUserId, String role,
            String idempotencyKey, String correlationId) {
        requireServicePointAccess(servicePointId, calledByUserId, role);
        return callNextAtServicePoint(servicePointId, calledByUserId, idempotencyKey, correlationId);
    }

    @Transactional
    public Optional<QueueEntryResponse> callNextInRoom(
            String roomId, UUID calledByUserId, String idempotencyKey, String correlationId) {
        return callNext(requireRoomConfig(roomId).getDepartmentId(), calledByUserId,
                idempotencyKey, correlationId);
    }

    public Optional<QueueEntryResponse> callNextInRoom(
            String roomId, UUID calledByUserId, String role, String idempotencyKey, String correlationId) {
        requireRoomAccess(roomId, calledByUserId, role);
        return callNextInRoom(roomId, calledByUserId, idempotencyKey, correlationId);
    }

    private void requireRoomAccess(String roomId, UUID userId, String role) {
        if (AppConstants.ROLE_ADMIN.equalsIgnoreCase(role)) {
            return;
        }
        if (directoryClient == null) {
            return;
        }
        boolean allowed = false;
        try {
            if (AppConstants.ROLE_DOCTOR.equalsIgnoreCase(role)) {
                ApiResponse<DoctorAssignmentResponse> response = directoryClient.getDoctorByUserId(userId);
                DoctorAssignmentResponse doctor = response == null ? null : response.getData();
                allowed = doctor != null && Boolean.TRUE.equals(doctor.getIsActive())
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

    private void requireServicePointAccess(String servicePointId, UUID userId, String role) {
        String normalizedServicePointId = normalizeServicePointId(servicePointId);
        if (AppConstants.ROLE_ADMIN.equalsIgnoreCase(role)) {
            return;
        }
        if (directoryClient == null) {
            return;
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

    @Transactional(readOnly = true)
    public QueueDashboardResponse dashboard(UUID departmentId) {
        QueueConfig config = requireConfig(departmentId);
        LocalDate date = businessDate();
        List<QueueEntry> active = entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                config.getId(), date, EnumSet.of(QueueStatus.QUEUED, QueueStatus.CHECKED_IN,
                        QueueStatus.CALLED, QueueStatus.IN_PROGRESS));
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
                waiting, config.getLastServedLane(), now.plus(Duration.ofMinutes(currentWorkload)),
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
        return new QueueDashboardResponse(departmentId, config.getDepartmentNameSnapshot(),
                config.getRoomCode(), date, response, priorityQueue, normalQueue, resultReviewQueue,
                recommendedNext, config.getLastServedLane(), config.getVersion());
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
                        config.getId(), date, Set.of(QueueStatus.CHECKED_IN, QueueStatus.QUEUED));
        Instant now = Instant.now();
        QueueEntry candidate = ConsultationQueueScheduler.next(waiting, config.getLastServedLane());
        if (candidate == null) {
            recordEmptyCommand(COMMAND_CALL_NEXT, departmentId, normalizedKey, requestFingerprint);
            return Optional.empty();
        }
        config.setLastServedLane(candidate.getSchedulingLane());
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
        if (!entry.isWaitingForCall()) {
            throw invalidTransition(entry, QueueStatus.CALLED);
        }
        QueueConfig config = configFor(entry);
        Instant now = Instant.now();
        entry.setStatus(QueueStatus.CALLED);
        entry.setCalledAt(now);
        entry.setCalledByUserId(calledByUserId);
        entry.setCallAttempts(1);
        entries.saveAndFlush(entry);
        if (config != null && entry.getSchedulingLane() != null) {
            config.setLastServedLane(entry.getSchedulingLane());
            configs.save(config);
        }
        events.append(entry, config, "PatientCalled", AppConstants.RK_QUEUE_CALLED,
                correlationId, Map.of(
                        "callAttempt", entry.getCallAttempts(),
                        "calledByUserId", calledByUserId,
                        "selectedByActor", true));
        recordCommand(COMMAND_CALL_ENTRY, entryId, normalizedKey, requestFingerprint, entry);
        if (config != null) appendNearTurnEvents(config, entry.getQueueDate(), correlationId);
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse call(UUID entryId, UUID calledByUserId, String role,
                                   String idempotencyKey, String correlationId) {
        requireEntryAssignmentAccess(entryId, calledByUserId, role);
        return call(entryId, calledByUserId, idempotencyKey, correlationId);
    }

    @Transactional
    public QueueEntryResponse recall(UUID entryId, UUID calledByUserId, String correlationId) {
        QueueEntry entry = requireEntryForUpdate(entryId);
        if (entry.getStatus() != QueueStatus.CALLED) throw invalidTransition(entry, QueueStatus.CALLED);
        if (entry.getCallAttempts() >= MAX_CALL_ATTEMPTS) {
            throw new BusinessException(409, "Đã gọi đủ " + MAX_CALL_ATTEMPTS + " lần; hãy đánh dấu lỡ lượt");
        }
        QueueConfig config = configFor(entry);
        entry.setCallAttempts(entry.getCallAttempts() + 1);
        entry.setCalledAt(Instant.now());
        entry.setCalledByUserId(calledByUserId);
        entries.saveAndFlush(entry);
        events.append(entry, config, "PatientCalled", AppConstants.RK_QUEUE_CALLED,
                correlationId, Map.of(
                        "callAttempt", entry.getCallAttempts(),
                        "calledByUserId", calledByUserId,
                        "recalled", true));
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse recall(UUID entryId, UUID calledByUserId, String role, String correlationId) {
        requireEntryAssignmentAccess(entryId, calledByUserId, role);
        return recall(entryId, calledByUserId, correlationId);
    }

    @Transactional
    public QueueEntryResponse miss(UUID entryId, String correlationId) {
        QueueEntry entry = requireEntryForUpdate(entryId);
        if (entry.getStatus() != QueueStatus.CALLED) throw invalidTransition(entry, QueueStatus.MISSED);
        if (entry.getCallAttempts() < MAX_CALL_ATTEMPTS) {
            throw new BusinessException(409,
                    "Phải gọi đủ " + MAX_CALL_ATTEMPTS + " lần trước khi đánh dấu lỡ lượt");
        }
        QueueConfig config = configFor(entry);
        entry.setStatus(QueueStatus.MISSED);
        entry.setMissedAt(Instant.now());
        entry.setMissedCount(entry.getMissedCount() + 1);
        entries.saveAndFlush(entry);
        events.append(entry, config, "QueueEntryMissed", AppConstants.RK_QUEUE_MISSED, correlationId,
                Map.of("missedCount", entry.getMissedCount()));
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse miss(UUID entryId, UUID requesterUserId, String role, String correlationId) {
        requireEntryAssignmentAccess(entryId, requesterUserId, role);
        return miss(entryId, correlationId);
    }

    @Transactional
    public QueueEntryResponse requeue(UUID entryId, RequeueRequest request, String correlationId) {
        QueueEntry snapshot = entries.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", entryId));
        QueueConfig config = snapshot.getDepartmentId() == null ? null : requireLockedConfig(snapshot.getDepartmentId());
        QueueEntry entry = requireEntryForUpdate(entryId);
        QueueStatus requeuedStatus = entry.getQueueType() == QueueType.CONSULTATION
                && entry.getConsultationPhase() == ConsultationPhase.INITIAL
                ? QueueStatus.CHECKED_IN : QueueStatus.QUEUED;
        if (entry.getStatus() != QueueStatus.MISSED) throw invalidTransition(entry, requeuedStatus);
        boolean back;
        if (entry.getConsultationPhase() == ConsultationPhase.RESULT_REVIEW) {
            back = true;
        } else if (config == null) {
            back = true;
        } else if (config.getMissedPolicy() == MissedPolicy.REQUIRE_MANUAL) {
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
            QueueEntry first = firstCandidate(config, entry.getQueueDate(), entry.getSchedulingLane());
            entry.setEligibleSinceAt(first == null
                    ? Instant.now()
                    : first.getEligibleSinceAt().minusSeconds(1));
        }
        entry.setStatus(requeuedStatus);
        entry.setCalledAt(null);
        entry.setCalledByUserId(null);
        entry.setCallAttempts(0);
        entry.setNearTurnNotifiedAt(null);
        entries.saveAndFlush(entry);
        events.append(entry, config, "QueueEntryRequeued", "queue.requeued", correlationId,
                Map.of("requeued", true, "position", back ? "BACK" : "FRONT"));
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse requeue(UUID entryId, RequeueRequest request, UUID requesterUserId,
                                      String role, String correlationId) {
        requireEntryAssignmentAccess(entryId, requesterUserId, role);
        return requeue(entryId, request, correlationId);
    }

    @Transactional
    public QueueEntryResponse start(UUID entryId, String correlationId) {
        QueueEntry entry = requireEntryForUpdate(entryId);
        if (entry.getStatus() != QueueStatus.CALLED) throw invalidTransition(entry, QueueStatus.IN_PROGRESS);
        QueueConfig config = configFor(entry);
        entry.setStatus(QueueStatus.IN_PROGRESS);
        entry.setStartedAt(Instant.now());
        entries.saveAndFlush(entry);
        events.append(entry, config, "QueueEntryStarted", AppConstants.RK_QUEUE_STARTED, correlationId, Map.of());
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse start(UUID entryId, UUID requesterUserId, String role, String correlationId) {
        requireEntryAssignmentAccess(entryId, requesterUserId, role);
        return start(entryId, correlationId);
    }

    @Transactional
    public QueueEntryResponse complete(UUID entryId, String correlationId) {
        QueueEntry snapshot = entries.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", entryId));
        if (snapshot.getQueueType() == QueueType.PHARMACY_DISPENSING) {
            throw new BusinessException(409, "Lượt phát thuốc chỉ hoàn tất khi nhận PrescriptionDispensed");
        }
        QueueConfig config = snapshot.getDepartmentId() == null ? null : requireLockedConfig(snapshot.getDepartmentId());
        QueueEntry entry = requireEntryForUpdate(entryId);
        if (entry.getStatus() != QueueStatus.IN_PROGRESS) throw invalidTransition(entry, QueueStatus.COMPLETED);
        entry.setStatus(QueueStatus.COMPLETED);
        entry.setCompletedAt(Instant.now());
        entries.saveAndFlush(entry);
        if (config != null && entry.getQueueType() == QueueType.CONSULTATION) updateAverage(config, entry.getQueueDate());
        events.append(entry, config, "QueueEntryCompleted", AppConstants.RK_QUEUE_COMPLETED, correlationId,
                Map.of("serviceMinutes", consultationMinutes(entry)));
        return response(entry, config);
    }

    @Transactional
    public QueueEntryResponse complete(UUID entryId, UUID requesterUserId, String role, String correlationId) {
        requireEntryAssignmentAccess(entryId, requesterUserId, role);
        return complete(entryId, correlationId);
    }

    private void requireEntryAssignmentAccess(UUID entryId, UUID requesterUserId, String role) {
        QueueEntry entry = entries.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", entryId));
        if (entry.getQueueType() != QueueType.CONSULTATION) {
            if ((entry.getQueueType() == QueueType.LAB_EXECUTION
                    || entry.getQueueType() == QueueType.PHARMACY_DISPENSING)
                    && entry.getServicePointId() != null) {
                requireServicePointAccess(entry.getServicePointId(), requesterUserId, role);
            }
            return;
        }
        QueueConfig config = configFor(entry);
        if (config == null) {
            throw new BusinessException(403, "Lượt khám không có phòng assignment hợp lệ");
        }
        requireRoomAccess(config.getRoomCode(), requesterUserId, role);
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
        entry.setQueueType(QueueType.CONSULTATION);
        entry.setConsultationPhase(ConsultationPhase.INITIAL);
        entry.setQueueClass(priority == PriorityLevel.PRIORITY ? QueueClass.PRIORITY : QueueClass.NORMAL);
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

    public QueueEntry createPharmacyEntry(UUID prescriptionId, UUID consultationId, UUID patientId,
                                          String servicePointId, Instant queuedAt) {
        Optional<QueueEntry> existing = entries.findByPrescriptionId(prescriptionId);
        if (existing.isPresent()) return existing.get();
        String normalizedServicePoint = normalizeServicePointId(servicePointId);
        LocalDate date = LocalDate.ofInstant(queuedAt, businessZone);
        ServicePointSequence sequence = servicePointSequences
                .findByServicePointIdAndQueueDate(normalizedServicePoint, date)
                .orElseGet(() -> {
                    ServicePointSequence created = new ServicePointSequence();
                    created.setServicePointId(normalizedServicePoint);
                    created.setQueueDate(date);
                    return servicePointSequences.saveAndFlush(created);
                });
        sequence.setLastNumber(sequence.getLastNumber() + 1);
        servicePointSequences.save(sequence);

        QueueEntry entry = new QueueEntry();
        entry.setQueueConfigId(null);
        entry.setDepartmentId(null);
        entry.setDepartmentCode(null);
        entry.setPrescriptionId(prescriptionId);
        entry.setConsultationId(consultationId);
        entry.setPatientId(patientId);
        entry.setUserId(entries.findFirstByPatientIdAndUserIdIsNotNullOrderByCreatedAtDesc(patientId)
                .map(QueueEntry::getUserId).orElse(null));
        entry.setQueueType(QueueType.PHARMACY_DISPENSING);
        entry.setConsultationPhase(null);
        entry.setQueueClass(null);
        entry.setServicePointId(normalizedServicePoint);
        entry.setQueueDate(date);
        entry.setSequenceNumber(sequence.getLastNumber());
        entry.setQueueNumber("RX-" + String.format("%03d", sequence.getLastNumber()));
        entry.setPriorityLevel(PriorityLevel.WALK_IN);
        entry.setStatus(QueueStatus.QUEUED);
        entry.setEligibleSinceAt(queuedAt);
        return entry;
    }

    @Transactional(readOnly = true)
    public QueueEntryResponse currentLabExecution(UUID labOrderId) {
        QueueEntry entry = entries.findFirstByLabOrderIdOrderByCreatedAtDesc(labOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab queue entry", "labOrderId", labOrderId));
        return QueueEntryResponse.fromServicePoint(entry, null, null);
    }

    @Transactional(readOnly = true)
    public QueueEntryResponse currentLabExecution(UUID labOrderId, UUID requesterUserId, String role) {
        QueueEntry entry = entries.findFirstByLabOrderIdOrderByCreatedAtDesc(labOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab queue entry", "labOrderId", labOrderId));
        requireServicePointAccess(entry.getServicePointId(), requesterUserId, role);
        return currentLabExecution(labOrderId);
    }

    @Transactional
    public QueueEntry createLabExecutionEntry(UUID labOrderId, UUID consultationId, UUID patientId,
                                               String servicePointId, Instant queuedAt) {
        Optional<QueueEntry> existing = entries.findByLabOrderIdAndServicePointId(
                labOrderId, normalizeServicePointId(servicePointId));
        if (existing.isPresent()) return existing.get();

        String normalizedServicePoint = normalizeServicePointId(servicePointId);
        LocalDate date = LocalDate.ofInstant(queuedAt, businessZone);
        ServicePointSequence sequence = servicePointSequences
                .findByServicePointIdAndQueueDate(normalizedServicePoint, date)
                .orElseGet(() -> {
                    ServicePointSequence created = new ServicePointSequence();
                    created.setServicePointId(normalizedServicePoint);
                    created.setQueueDate(date);
                    return servicePointSequences.saveAndFlush(created);
                });
        sequence.setLastNumber(sequence.getLastNumber() + 1);
        servicePointSequences.save(sequence);

        QueueEntry entry = new QueueEntry();
        entry.setQueueConfigId(null);
        entry.setDepartmentId(null);
        entry.setDepartmentCode(null);
        entry.setLabOrderId(labOrderId);
        entry.setConsultationId(consultationId);
        entry.setPatientId(patientId);
        entry.setUserId(entries.findFirstByPatientIdAndUserIdIsNotNullOrderByCreatedAtDesc(patientId)
                .map(QueueEntry::getUserId).orElse(null));
        entry.setQueueType(QueueType.LAB_EXECUTION);
        entry.setConsultationPhase(null);
        entry.setQueueClass(null);
        entry.setServicePointId(normalizedServicePoint);
        entry.setQueueDate(date);
        entry.setSequenceNumber(sequence.getLastNumber());
        entry.setQueueNumber("LAB-" + String.format("%03d", sequence.getLastNumber()));
        entry.setPriorityLevel(PriorityLevel.WALK_IN);
        entry.setStatus(QueueStatus.QUEUED);
        entry.setEligibleSinceAt(queuedAt);
        return entry;
    }

    public QueueEntry createResultReviewEntry(UUID consultationId, UUID patientId,
                                              UUID sourceQueueEntryId, Instant queuedAt) {
        Optional<QueueEntry> existing = entries.findByConsultationIdAndQueueTypeAndConsultationPhase(
                consultationId, QueueType.CONSULTATION, ConsultationPhase.RESULT_REVIEW);
        if (existing.isPresent()) return existing.get();

        QueueEntry source = resolveInitialConsultationEntry(consultationId, patientId, sourceQueueEntryId);
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

    private QueueEntry resolveInitialConsultationEntry(UUID consultationId, UUID patientId,
                                                       UUID sourceQueueEntryId) {
        QueueEntry source = null;
        if (sourceQueueEntryId != null) {
            source = entries.findById(sourceQueueEntryId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Initial QueueEntry", "id", sourceQueueEntryId));
        }
        if (source == null) {
            source = entries.findByConsultationIdAndQueueTypeAndConsultationPhase(
                    consultationId, QueueType.CONSULTATION, ConsultationPhase.INITIAL).orElse(null);
        }
        if (source == null) {
            source = entries.findByPatientIdAndQueueTypeAndConsultationPhaseAndStatusInOrderByCreatedAtDesc(
                            patientId, QueueType.CONSULTATION, ConsultationPhase.INITIAL,
                            EnumSet.of(QueueStatus.IN_PROGRESS, QueueStatus.COMPLETED))
                    .stream().findFirst().orElse(null);
        }
        if (source == null) {
            throw new BusinessException(422,
                    "Không tìm thấy lượt khám ban đầu để tạo hàng chờ đọc kết quả");
        }
        if (source.getQueueType() != QueueType.CONSULTATION
                || source.getConsultationPhase() != ConsultationPhase.INITIAL) {
            throw new BusinessException(422, "sourceQueueEntryId không phải lượt khám ban đầu");
        }
        return source;
    }

    @Transactional
    public void cancelPharmacyEntry(UUID prescriptionId, String correlationId) {
        QueueEntry snapshot = entries.findByPrescriptionId(prescriptionId).orElse(null);
        if (snapshot == null || snapshot.getStatus() == QueueStatus.COMPLETED
                || snapshot.getStatus() == QueueStatus.CANCELLED) return;
        QueueEntry entry = requireEntryForUpdate(snapshot.getId());
        if (entry.getStatus() == QueueStatus.COMPLETED || entry.getStatus() == QueueStatus.CANCELLED) return;
        entry.setStatus(QueueStatus.CANCELLED);
        entry.setCancelledAt(Instant.now());
        entries.saveAndFlush(entry);
        events.append(entry, null, "QueueEntryCancelled", "queue.cancelled", correlationId,
                Map.of("prescriptionId", prescriptionId));
    }

    @Transactional
    public void completePharmacyEntry(UUID prescriptionId, Instant dispensedAt, String correlationId) {
        QueueEntry snapshot = entries.findByPrescriptionId(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy QueueEntry", "prescriptionId", prescriptionId));
        if (snapshot.getStatus() == QueueStatus.COMPLETED) return;
        QueueEntry entry = requireEntryForUpdate(snapshot.getId());
        if (entry.getStatus() == QueueStatus.COMPLETED) return;
        if (entry.getStatus() != QueueStatus.IN_PROGRESS) {
            throw new BusinessException(409, "Lượt phát thuốc phải ở IN_PROGRESS trước khi xác nhận đã phát");
        }
        entry.setStatus(QueueStatus.COMPLETED);
        entry.setCompletedAt(dispensedAt);
        entries.saveAndFlush(entry);
        events.append(entry, null, "QueueEntryCompleted", AppConstants.RK_QUEUE_COMPLETED,
                correlationId, Map.of("prescriptionId", prescriptionId, "dispensed", true));
    }

    @Transactional
    public void completePharmacyEntry(UUID prescriptionId, UUID requesterUserId, String role,
                                      Instant dispensedAt, String correlationId) {
        QueueEntry entry = entries.findByPrescriptionId(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy QueueEntry", "prescriptionId", prescriptionId));
        requireServicePointAccess(entry.getServicePointId(), requesterUserId, role);
        completePharmacyEntry(prescriptionId, dispensedAt, correlationId);
    }

    @Transactional(readOnly = true)
    public QueueEntryResponse currentPharmacyEntry(UUID prescriptionId) {
        QueueEntry entry = entries.findByPrescriptionId(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy QueueEntry", "prescriptionId", prescriptionId));
        if (entry.getQueueType() != QueueType.PHARMACY_DISPENSING) {
            throw new BusinessException(422, "Queue entry is not a pharmacy dispensing entry");
        }
        Integer position = entry.getStatus() == QueueStatus.QUEUED ? 1 : 0;
        return QueueEntryResponse.fromServicePoint(entry, position, position == 0 ? 0 : null);
    }

    @Transactional(readOnly = true)
    public QueueEntryResponse currentPharmacyEntry(UUID prescriptionId, UUID requesterUserId, String role) {
        QueueEntry entry = entries.findByPrescriptionId(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy QueueEntry", "prescriptionId", prescriptionId));
        requireServicePointAccess(entry.getServicePointId(), requesterUserId, role);
        return currentPharmacyEntry(prescriptionId);
    }

    public QueueConfig requireLockedConfig(UUID departmentId) {
        return configs.findFirstByDepartmentIdAndActiveTrue(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Active QueueConfig", "departmentId", departmentId));
    }

    private QueueConfig requireConfig(UUID departmentId) {
        return configs.findByDepartmentIdAndActiveTrue(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Active QueueConfig", "departmentId", departmentId));
    }

    private QueueConfig configFor(QueueEntry entry) {
        return entry.getDepartmentId() == null ? null : requireConfig(entry.getDepartmentId());
    }

    private QueueConfig requireRoomConfig(String roomId) {
        return configs.findByRoomCodeAndActiveTrue(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active QueueConfig", "roomId", roomId));
    }

    private QueueEntry requireEntryForUpdate(UUID id) {
        return entries.findFirstById(id).orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", id));
    }

    private QueueEntry firstCandidate(QueueConfig config, LocalDate date, SchedulingLane lane) {
        return entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                        config.getId(), date, Set.of(QueueStatus.CHECKED_IN, QueueStatus.QUEUED))
                .stream().filter(QueueEntry::isWaitingForCall)
                .filter(entry -> entry.getSchedulingLane() == lane)
                .findFirst().orElse(null);
    }

    private QueueEntryResponse response(QueueEntry entry, QueueConfig config) {
        if (config == null) {
            Integer position = entry.getStatus() == QueueStatus.QUEUED ? servicePointPosition(entry) : 0;
            Integer wait;
            if (entry.getStatus() == QueueStatus.QUEUED) {
                wait = entry.getEstimatedWaitMinutes();
            } else {
                wait = 0;
            }
            return QueueEntryResponse.fromServicePoint(entry, position,
                    wait);
        }
        Integer position = null;
        Integer wait = entry.getEstimatedWaitMinutes();
        if (entry.isWaitingForCall()) {
            List<QueueEntry> active = entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                    config.getId(), entry.getQueueDate(), Set.of(QueueStatus.CHECKED_IN, QueueStatus.QUEUED));
            Instant now = Instant.now();
            int currentConsultation = entries.existsByQueueConfigIdAndQueueDateAndStatusIn(
                    config.getId(), entry.getQueueDate(), SERVING_STATUSES)
                    ? config.getAvgConsultationMinutes() : 0;
            List<ConsultationQueueScheduler.ScheduledEntry> schedule = ConsultationQueueScheduler.schedule(
                    active, config.getLastServedLane(), now.plus(Duration.ofMinutes(currentConsultation)),
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

    private int servicePointPosition(QueueEntry entry) {
        List<QueueEntry> waiting = entries
                .findByServicePointIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                        entry.getServicePointId(), entry.getQueueDate(), Set.of(QueueStatus.QUEUED));
        for (int index = 0; index < waiting.size(); index++) {
            if (waiting.get(index).getId().equals(entry.getId())) return index + 1;
        }
        return 0;
    }

    private List<QueueEntryResponse> lane(List<QueueEntryResponse> entries, SchedulingLane lane) {
        return entries.stream().filter(entry -> entry.schedulingLane() == lane).toList();
    }

    private String normalizeServicePointId(String servicePointId) {
        if (servicePointId == null || servicePointId.isBlank()) {
            throw new BusinessException(400, "servicePointId không được để trống");
        }
        return servicePointId.trim().toUpperCase(Locale.ROOT);
    }

    private void appendNearTurnEvents(QueueConfig config, LocalDate date, String correlationId) {
        List<QueueEntry> waiting = entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                config.getId(), date, Set.of(QueueStatus.CHECKED_IN, QueueStatus.QUEUED));
        Map<UUID, QueueEntry> byId = new HashMap<>();
        waiting.forEach(entry -> byId.put(entry.getId(), entry));
        Instant now = Instant.now();
        int currentWorkload = entries.existsByQueueConfigIdAndQueueDateAndStatusIn(
                config.getId(), date, SERVING_STATUSES)
                ? config.getAvgConsultationMinutes() : 0;
        List<ConsultationQueueScheduler.ScheduledEntry> schedule = ConsultationQueueScheduler.schedule(
                waiting, config.getLastServedLane(), now.plus(Duration.ofMinutes(currentWorkload)),
                config.getAvgConsultationMinutes());
        int limit = Math.min(config.getNearTurnThreshold(), schedule.size());
        for (int index = 0; index < limit; index++) {
            ConsultationQueueScheduler.ScheduledEntry scheduled = schedule.get(index);
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
                    QueueConfig config = entry.getQueueConfigId() == null ? null : configs.findById(entry.getQueueConfigId())
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
