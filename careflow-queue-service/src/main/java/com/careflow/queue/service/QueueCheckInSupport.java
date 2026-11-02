package com.careflow.queue.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.common.security.PatientOwnershipClient;
import com.careflow.queue.domain.PriorityLevel;
import com.careflow.queue.domain.QueueClass;
import com.careflow.queue.domain.QueueConfig;
import com.careflow.queue.domain.QueueEntry;
import com.careflow.queue.domain.QueueStatus;
import com.careflow.queue.dto.CheckInQrResponse;
import com.careflow.queue.dto.CheckInRequest;
import com.careflow.queue.dto.QueueEntryResponse;
import com.careflow.queue.repository.QueueEntryRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

final class QueueCheckInSupport {
    private static final String DEFAULT_SESSION_CODE = "MORNING";
    private static final double EARTH_RADIUS_METERS = 6_371_000;
    private static final Set<QueueStatus> ACTIVE_DEPARTMENT_STATUSES = Set.of(
            QueueStatus.CHECKED_IN,
            QueueStatus.CALLED,
            QueueStatus.IN_PROGRESS,
            QueueStatus.MISSED
    );

    private final QueueEntryRepository entries;
    private final QueueEventService events;
    private final QrTokenService qrTokens;
    private final HospitalCheckInConfigService hospitalCheckInConfigService;
    private final HospitalCheckInConfigService.GeofenceSettings fallbackGeofence;
    private final Supplier<LocalDate> businessDateSupplier;
    private final Supplier<PatientOwnershipClient> ownershipSupplier;
    private final Supplier<QueueRoomConfigurationService> roomConfigServiceSupplier;
    private final Function<QueueEntry, QueueConfig> configForEntry;
    private final Function<String, QueueConfig> roomConfigLookup;
    private final BiFunction<QueueEntry, QueueConfig, QueueEntryResponse> responseMapper;

    QueueCheckInSupport(QueueEntryRepository entries,
                        QueueEventService events,
                        QrTokenService qrTokens,
                        HospitalCheckInConfigService hospitalCheckInConfigService,
                        HospitalCheckInConfigService.GeofenceSettings fallbackGeofence,
                        Supplier<LocalDate> businessDateSupplier,
                        Supplier<PatientOwnershipClient> ownershipSupplier,
                        Supplier<QueueRoomConfigurationService> roomConfigServiceSupplier,
                        Function<QueueEntry, QueueConfig> configForEntry,
                        Function<String, QueueConfig> roomConfigLookup,
                        BiFunction<QueueEntry, QueueConfig, QueueEntryResponse> responseMapper) {
        this.entries = entries;
        this.events = events;
        this.qrTokens = qrTokens;
        this.hospitalCheckInConfigService = hospitalCheckInConfigService;
        this.fallbackGeofence = fallbackGeofence;
        this.businessDateSupplier = businessDateSupplier;
        this.ownershipSupplier = ownershipSupplier;
        this.roomConfigServiceSupplier = roomConfigServiceSupplier;
        this.configForEntry = configForEntry;
        this.roomConfigLookup = roomConfigLookup;
        this.responseMapper = responseMapper;
    }

    CheckInQrResponse issueHospitalCheckInQr(LocalDate date, String session) {
        LocalDate businessDate = businessDateSupplier.get();
        LocalDate sessionDate = date == null ? businessDate : date;
        if (!businessDate.equals(sessionDate)) {
            throw new BusinessException(422, "Chỉ có thể hiển thị QR check-in của ngày hiện tại");
        }
        String sessionCode = session == null || session.isBlank()
                ? DEFAULT_SESSION_CODE
                : session.trim().toUpperCase(Locale.ROOT);
        String siteId = currentGeofence().siteId();
        QrTokenService.IssuedHospitalQr issued = qrTokens.issueHospitalQr(siteId, sessionDate, sessionCode);
        return new CheckInQrResponse(
                siteId,
                sessionCode,
                sessionDate,
                issued.token(),
                issued.expiresAt());
    }

    QueueEntryResponse checkInAsPatient(CheckInRequest request, UUID patientUserId, String correlationId) {
        if (request.appointmentId() == null
                || request.checkInQrToken() == null
                || request.checkInQrToken().isBlank()) {
            throw new BusinessException(400, "Vui lòng quét QR check-in của bệnh viện");
        }
        LocalDate businessDate = businessDateSupplier.get();
        QrTokenService.HospitalQrClaims claims = qrTokens.verifyHospitalQr(request.checkInQrToken());
        if (!claims.sessionDate().equals(businessDate)) {
            throw new BusinessException(422, "QR không thuộc ngày hiện tại");
        }
        QueueEntry entry = entries.findFirstByAppointmentId(request.appointmentId())
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "appointmentId", request.appointmentId()));
        PatientOwnershipClient currentOwners = ownershipSupplier.get();
        if (currentOwners != null) {
            currentOwners.require(entry.getPatientId(), patientUserId);
        }
        if (currentOwners == null && (entry.getUserId() == null || !entry.getUserId().equals(patientUserId))) {
            throw new BusinessException(403, "Lịch hẹn không thuộc tài khoản bệnh nhân này");
        }
        if (!entry.getQueueDate().equals(claims.sessionDate())) {
            throw new BusinessException(409, "QR không khớp ngày khám của lịch hẹn");
        }
        QueueConfig config = configForEntry.apply(entry);
        HospitalCheckInConfigService.GeofenceSettings geofence = currentGeofence();
        if (!geofence.siteId().equals(claims.siteId())) {
            throw new BusinessException(409, "QR không thuộc bệnh viện này");
        }
        if (entry.getStatus() == QueueStatus.CHECKED_IN) {
            return responseMapper.apply(entry, config);
        }

        validateCoordinates(request, geofence.maxAccuracyMeters());
        double distanceMeters = distanceMeters(
                request.latitude(),
                request.longitude(),
                geofence.latitude(),
                geofence.longitude());
        if (distanceMeters > geofence.radiusMeters()) {
            throw new BusinessException(422, "Bạn đang ở ngoài khu vực bệnh viện, chưa thể check-in");
        }
        return activateCheckedInEntry(
                entry,
                config,
                request,
                patientUserId,
                correlationId,
                "PATIENT_QR_GEOFENCE",
                distanceMeters);
    }

    QueueEntryResponse checkInAsStaff(CheckInRequest request, UUID staffUserId, String correlationId) {
        if ((request.qrToken() == null || request.qrToken().isBlank())
                && (request.ticketCode() == null || request.ticketCode().isBlank())) {
            throw new BusinessException(400, "Vui lòng cung cấp mã QR hoặc mã phiếu khám");
        }
        if (request.ticketCode() != null && !request.ticketCode().isBlank()) {
            return checkInByTicketCode(request, staffUserId, correlationId);
        }
        LocalDate businessDate = businessDateSupplier.get();
        QrTokenService.QrClaims claims = qrTokens.verify(request.qrToken());
        if (!claims.queueDate().equals(businessDate)) {
            throw new BusinessException(422, "QR không thuộc ngày hiện tại");
        }
        QueueEntry entry = entries.findFirstByAppointmentId(claims.appointmentId())
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "appointmentId", claims.appointmentId()));
        if (!entry.getId().equals(claims.ticketId()) || !Objects.equals(entry.getUserId(), claims.userId())) {
            throw new BusinessException(401, "QR không khớp phiếu khám");
        }
        QueueConfig config = configForEntry.apply(entry);
        if (!config.getRoomCode().equals(request.roomId())) {
            throw new BusinessException(409, "QR không thuộc phòng tiếp nhận này");
        }
        return activateCheckedInEntry(
                entry,
                config,
                request,
                staffUserId,
                correlationId,
                "STAFF_QR",
                null);
    }

    private QueueEntryResponse checkInByTicketCode(CheckInRequest request,
                                                   UUID staffUserId,
                                                   String correlationId) {
        QueueConfig config = roomConfigLookup.apply(request.roomId());
        String ticketCode = request.ticketCode().trim();
        QueueEntry entry = entries.findFirstByQueueConfigIdAndQueueDateAndQueueNumber(
                        config.getId(),
                        businessDateSupplier.get(),
                        ticketCode)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "ticketCode", ticketCode));

        return activateCheckedInEntry(
                entry,
                config,
                request,
                staffUserId,
                correlationId,
                "STAFF_MANUAL",
                null);
    }

    private QueueEntryResponse activateCheckedInEntry(QueueEntry entry,
                                                      QueueConfig config,
                                                      CheckInRequest request,
                                                      UUID actorUserId,
                                                      String correlationId,
                                                      String checkInMethod,
                                                      Double distanceMeters) {
        LocalDate businessDate = businessDateSupplier.get();
        QueueRoomConfigurationService roomConfigurations = roomConfigServiceSupplier.get();
        if (roomConfigurations != null) {
            roomConfigurations.lockPatient(entry.getPatientId(), entry.getDepartmentId(), businessDate);
            if (entry.getStatus() == QueueStatus.WAITING
                    && entries.existsByPatientIdAndDepartmentIdAndQueueDateAndStatusIn(
                            entry.getPatientId(),
                            entry.getDepartmentId(),
                            businessDate,
                            ACTIVE_DEPARTMENT_STATUSES)) {
                throw new BusinessException(409, "Bệnh nhân đang có một lượt khám hoạt động trong khoa này.");
            }
        }
        if (entry.getStatus() == QueueStatus.CHECKED_IN) {
            return responseMapper.apply(entry, config);
        }
        if (entry.getStatus() == QueueStatus.CANCELLED) {
            throw new BusinessException(409, "Lịch hẹn đã bị hủy");
        }
        if (entry.getStatus() != QueueStatus.WAITING) {
            throw new BusinessException(409, "Trạng thái lượt khám không cho phép check-in");
        }
        if (!config.isActive()) {
            throw new BusinessException(409, "Phòng đang tạm ngừng tiếp nhận.");
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
        if (distanceMeters != null) {
            eventData.put("distanceMeters", distanceMeters);
        }
        events.append(entry, config, "PatientCheckedIn", AppConstants.RK_QUEUE_CHECKED_IN, correlationId, eventData);
        return responseMapper.apply(entry, config);
    }

    private HospitalCheckInConfigService.GeofenceSettings currentGeofence() {
        if (hospitalCheckInConfigService != null) {
            return hospitalCheckInConfigService.current();
        }
        return fallbackGeofence;
    }

    private static void validateCoordinates(CheckInRequest request, double maxAccuracyMeters) {
        if (request.latitude() == null || request.longitude() == null || request.accuracyMeters() == null) {
            throw new BusinessException(422, "Không thể xác định vị trí hiện tại của thiết bị");
        }
        if (!Double.isFinite(request.latitude())
                || !Double.isFinite(request.longitude())
                || !Double.isFinite(request.accuracyMeters())
                || request.latitude() < -90 || request.latitude() > 90
                || request.longitude() < -180 || request.longitude() > 180) {
            throw new BusinessException(422, "Tọa độ vị trí không hợp lệ");
        }
        if (request.accuracyMeters() <= 0 || request.accuracyMeters() > maxAccuracyMeters) {
            throw new BusinessException(422, "Độ chính xác vị trí chưa đạt yêu cầu");
        }
    }

    private static double distanceMeters(double latitude1,
                                         double longitude1,
                                         double latitude2,
                                         double longitude2) {
        double latDistance = Math.toRadians(latitude2 - latitude1);
        double lonDistance = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
