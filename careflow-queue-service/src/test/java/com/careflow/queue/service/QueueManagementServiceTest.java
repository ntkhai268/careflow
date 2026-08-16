package com.careflow.queue.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.queue.domain.*;
import com.careflow.queue.dto.QueueConfigRequest;
import com.careflow.queue.dto.QueueDashboardResponse;
import com.careflow.queue.dto.RequeueRequest;
import com.careflow.queue.dto.CheckInRequest;
import com.careflow.queue.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueManagementServiceTest {
    @Mock QueueConfigRepository configs;
    @Mock QueueNumberSequenceRepository sequences;
    @Mock ServicePointSequenceRepository servicePointSequences;
    @Mock QueueEntryRepository entries;
    @Mock IdempotencyRecordRepository idempotencyRecords;
    @Mock QueueEventService events;
    @Mock QrTokenService qrTokens;

    private QueueManagementService service;
    private QueueConfig config;
    private UUID departmentId;
    private UUID doctorUserId;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        service = new QueueManagementService(configs, sequences, servicePointSequences, entries, idempotencyRecords,
                events, qrTokens, "Asia/Ho_Chi_Minh");
        today = service.businessDate();
        departmentId = UUID.randomUUID();
        doctorUserId = UUID.randomUUID();
        config = new QueueConfig();
        config.setId(UUID.randomUUID());
        config.setDepartmentId(departmentId);
        config.setDepartmentNameSnapshot("Khoa Nội");
        config.setQueuePrefix("NOI");
        config.setRoomCode("P101");
        config.setPriorityRatioN(2);
        config.setNormalRatioM(1);
        config.setAvgConsultationMinutes(10);
        config.setNearTurnThreshold(3);
        config.setSchedulerDate(today);
        config.setCyclePhase(CyclePhase.PRIORITY);
        config.setNormalCursor(NormalCursor.APPOINTMENT);
        config.setActive(true);
    }

    @Test
    void callNextAllowsAnotherPatientWhilePatientsAreAlreadyCalledOrInProgress() {
        QueueEntry inProgress = entry(PriorityLevel.APPOINTMENT, QueueStatus.IN_PROGRESS, 1);
        QueueEntry next = entry(PriorityLevel.APPOINTMENT, QueueStatus.CHECKED_IN, 2);
        when(configs.findFirstByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));
        when(idempotencyRecords.findByCommandNameAndScopeIdAndIdempotencyKey(
                "CALL_NEXT", departmentId, "request-1")).thenReturn(Optional.empty());
        when(entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                eq(config.getId()), eq(today), anyCollection())).thenReturn(List.of(inProgress, next));

        assertThat(service.callNext(departmentId, doctorUserId, "request-1", "trace-1"))
                .get().extracting(response -> response.entryId()).isEqualTo(next.getId());
        assertThat(next.getStatus()).isEqualTo(QueueStatus.CALLED);
        assertThat(next.getCalledByUserId()).isEqualTo(doctorUserId);
    }

    @Test
    void appointmentEntryStoresSlotStartInBusinessTimezone() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(sequences.findByQueueConfigIdAndQueueDate(config.getId(), today)).thenReturn(Optional.empty());
        when(sequences.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        QueueEntry appointment = service.createAppointmentEntry(
                config, today, LocalTime.of(8, 0), "08:00-08:30", "NOI_TONG_QUAT", "Phòng 101",
                appointmentId, patientId, userId);

        assertThat(appointment.getScheduledStartAt())
                .isEqualTo(today.atTime(8, 0).atZone(serviceZone()).toInstant());
        assertThat(appointment.getPriorityLevel()).isEqualTo(PriorityLevel.APPOINTMENT);
    }

    @Test
    void staffCheckInActivatesTicketAtTheAssignedRoom() {
        UUID staffId = UUID.randomUUID();
        QueueEntry waiting = entry(PriorityLevel.APPOINTMENT, QueueStatus.WAITING, 7);
        waiting.setAppointmentId(UUID.randomUUID());
        QrTokenService.QrClaims claims = new QrTokenService.QrClaims(
                waiting.getId(), waiting.getAppointmentId(), waiting.getUserId(), today);
        when(qrTokens.verify("signed-qr")).thenReturn(claims);
        when(entries.findFirstByAppointmentId(waiting.getAppointmentId())).thenReturn(Optional.of(waiting));
        when(configs.findByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));

        service.checkIn(new CheckInRequest("signed-qr", "P101", QueueClass.NORMAL, null),
                staffId, "trace-1");

        assertThat(waiting.getStatus()).isEqualTo(QueueStatus.CHECKED_IN);
        assertThat(waiting.getCheckedInByUserId()).isEqualTo(staffId);
        assertThat(waiting.getEligibleSinceAt()).isNotNull();
        verify(events).append(eq(waiting), eq(config), eq("PatientCheckedIn"),
                eq("queue.checked-in"), eq("trace-1"), anyMap());
    }

    @Test
    void patientCheckInUsesHospitalQrAndGeofence() {
        QueueEntry waiting = entry(PriorityLevel.APPOINTMENT, QueueStatus.WAITING, 7);
        waiting.setAppointmentId(UUID.randomUUID());
        UUID patientUserId = waiting.getUserId();
        when(qrTokens.verifyHospitalQr("hospital-qr")).thenReturn(
                new QrTokenService.HospitalQrClaims("P101", "MORNING", today));
        when(entries.findFirstByAppointmentId(waiting.getAppointmentId())).thenReturn(Optional.of(waiting));
        when(configs.findByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));

        service.checkIn(new CheckInRequest(null, null, null, null, null,
                        waiting.getAppointmentId(), "hospital-qr", 10.7769, 106.7009, 8.0),
                patientUserId, "PATIENT", "trace-patient");

        assertThat(waiting.getStatus()).isEqualTo(QueueStatus.CHECKED_IN);
        assertThat(waiting.getCheckInMethod()).isEqualTo("PATIENT_QR_GEOFENCE");
        assertThat(waiting.getCheckInDistanceMeters()).isZero();
        verify(events).append(eq(waiting), eq(config), eq("PatientCheckedIn"),
                eq("queue.checked-in"), eq("trace-patient"), anyMap());
    }

    @Test
    void patientCheckInRejectsDeviceOutsideHospitalGeofence() {
        QueueEntry waiting = entry(PriorityLevel.APPOINTMENT, QueueStatus.WAITING, 7);
        waiting.setAppointmentId(UUID.randomUUID());
        when(qrTokens.verifyHospitalQr("hospital-qr")).thenReturn(
                new QrTokenService.HospitalQrClaims("P101", "MORNING", today));
        when(entries.findFirstByAppointmentId(waiting.getAppointmentId())).thenReturn(Optional.of(waiting));
        when(configs.findByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.checkIn(
                new CheckInRequest(null, null, null, null, null,
                        waiting.getAppointmentId(), "hospital-qr", 21.0278, 105.8342, 8.0),
                waiting.getUserId(), "PATIENT", "trace-outside"))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(422);
        verify(entries, never()).saveAndFlush(waiting);
    }

    @Test
    void staffCheckInRejectsQrAtAnotherRoom() {
        QueueEntry waiting = entry(PriorityLevel.APPOINTMENT, QueueStatus.WAITING, 7);
        waiting.setAppointmentId(UUID.randomUUID());
        when(qrTokens.verify("signed-qr")).thenReturn(new QrTokenService.QrClaims(
                waiting.getId(), waiting.getAppointmentId(), waiting.getUserId(), today));
        when(entries.findFirstByAppointmentId(waiting.getAppointmentId())).thenReturn(Optional.of(waiting));
        when(configs.findByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.checkIn(
                new CheckInRequest("signed-qr", "ROOM-OTHER", QueueClass.NORMAL, null),
                UUID.randomUUID(), "trace-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(409);
        verify(entries, never()).saveAndFlush(waiting);
    }

    @Test
    void staffCheckInActivatesTicketByManualCodeAtTheAssignedRoom() {
        UUID staffId = UUID.randomUUID();
        QueueEntry waiting = entry(PriorityLevel.APPOINTMENT, QueueStatus.WAITING, 7);
        when(configs.findByRoomCodeAndActiveTrue("P101")).thenReturn(Optional.of(config));
        when(entries.findFirstByQueueConfigIdAndQueueDateAndQueueNumber(
                config.getId(), today, waiting.getQueueNumber())).thenReturn(Optional.of(waiting));

        service.checkIn(new CheckInRequest(null, waiting.getQueueNumber(), "P101", QueueClass.NORMAL, null),
                staffId, "trace-manual");

        assertThat(waiting.getStatus()).isEqualTo(QueueStatus.CHECKED_IN);
        assertThat(waiting.getCheckedInByUserId()).isEqualTo(staffId);
        verify(events).append(eq(waiting), eq(config), eq("PatientCheckedIn"),
                eq("queue.checked-in"), eq("trace-manual"), anyMap());
        verifyNoInteractions(qrTokens);
    }

    @Test
    void patientCannotReadAnotherAccountsVisitTicket() {
        QueueEntry ticket = entry(PriorityLevel.APPOINTMENT, QueueStatus.WAITING, 8);
        ticket.setAppointmentId(UUID.randomUUID());
        when(entries.findByAppointmentId(ticket.getAppointmentId())).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.ticket(
                ticket.getAppointmentId(), UUID.randomUUID(), false))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(403);
        verifyNoInteractions(qrTokens);
    }

    @Test
    void callNextCallsPriorityPatientAndPersistsIdempotentOutcome() {
        QueueEntry priority = entry(PriorityLevel.PRIORITY, QueueStatus.CHECKED_IN, 1);
        when(configs.findFirstByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));
        when(idempotencyRecords.findByCommandNameAndScopeIdAndIdempotencyKey(
                "CALL_NEXT", departmentId, "request-1")).thenReturn(Optional.empty());
        when(entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                eq(config.getId()), eq(today), anyCollection())).thenReturn(List.of(priority));

        assertThat(service.callNext(departmentId, doctorUserId, "request-1", "trace-1"))
                .get().extracting(response -> response.entryId()).isEqualTo(priority.getId());
        assertThat(priority.getStatus()).isEqualTo(QueueStatus.CALLED);
        assertThat(priority.getCallAttempts()).isEqualTo(1);
        assertThat(priority.getCalledByUserId()).isEqualTo(doctorUserId);
        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRecords).save(captor.capture());
        assertThat(captor.getValue().getResultEntryId()).isEqualTo(priority.getId());
        assertThat(captor.getValue().getRequestFingerprint()).hasSize(64);
    }

    @Test
    void callNextUsesPriorityLaneBeforeNormalLaneRegardlessOfAppointmentTime() {
        QueueEntry appointment = entry(PriorityLevel.APPOINTMENT, QueueStatus.CHECKED_IN, 1);
        appointment.setScheduledStartAt(Instant.now().minusSeconds(60));
        QueueEntry priority = entry(PriorityLevel.PRIORITY, QueueStatus.CHECKED_IN, 2);
        when(configs.findFirstByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));
        when(idempotencyRecords.findByCommandNameAndScopeIdAndIdempotencyKey(
                "CALL_NEXT", departmentId, "request-1")).thenReturn(Optional.empty());
        when(entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                eq(config.getId()), eq(today), anyCollection())).thenReturn(List.of(priority, appointment));

        assertThat(service.callNext(departmentId, doctorUserId, "request-1", "trace-1"))
                .get().extracting(response -> response.entryId()).isEqualTo(priority.getId());
        assertThat(config.getLastServedLane()).isEqualTo(SchedulingLane.PRIORITY);
        assertThat(appointment.getStatus()).isEqualTo(QueueStatus.CHECKED_IN);
    }

    @Test
    void callNextReplaysPersistedIdempotentResult() {
        QueueEntry called = entry(PriorityLevel.PRIORITY, QueueStatus.CALLED, 1);
        IdempotencyRecord record = new IdempotencyRecord();
        record.setResultEntryId(called.getId());
        record.setRequestFingerprint(fingerprint(departmentId, doctorUserId));
        when(configs.findFirstByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));
        when(idempotencyRecords.findByCommandNameAndScopeIdAndIdempotencyKey(
                "CALL_NEXT", departmentId, "request-1")).thenReturn(Optional.of(record));
        when(entries.findById(called.getId())).thenReturn(Optional.of(called));

        assertThat(service.callNext(departmentId, doctorUserId, "request-1", "trace-1"))
                .get().extracting(response -> response.entryId()).isEqualTo(called.getId());
        verify(entries, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void callNextRejectsIdempotencyKeyReusedForDifferentRequestFingerprint() {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setRequestFingerprint(fingerprint(UUID.randomUUID()));
        when(configs.findFirstByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));
        when(idempotencyRecords.findByCommandNameAndScopeIdAndIdempotencyKey(
                "CALL_NEXT", departmentId, "request-1")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.callNext(
                departmentId, doctorUserId, "request-1", "trace-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(409);
    }

    @Test
    void dashboardReturnsSchedulerOrderInsteadOfRawEligibilityOrder() {
        QueueEntry appointment = entry(PriorityLevel.APPOINTMENT, QueueStatus.CHECKED_IN, 1);
        QueueEntry priority = entry(PriorityLevel.PRIORITY, QueueStatus.CHECKED_IN, 2);
        when(configs.findByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));
        when(entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                eq(config.getId()), eq(today), anyCollection())).thenReturn(List.of(appointment, priority));

        QueueDashboardResponse dashboard = service.dashboard(departmentId);

        assertThat(dashboard.entries()).extracting(response -> response.entryId())
                .containsExactly(priority.getId(), appointment.getId());
        assertThat(dashboard.entries()).extracting(response -> response.effectivePosition())
                .containsExactly(1, 2);
        assertThat(dashboard.recommendedNext().entryId()).isEqualTo(priority.getId());
    }

    @Test
    void doctorCanCallAnyCheckedInEntryInsteadOfTheRecommendation() {
        QueueEntry selected = entry(PriorityLevel.APPOINTMENT, QueueStatus.CHECKED_IN, 9);
        when(idempotencyRecords.findByCommandNameAndScopeIdAndIdempotencyKey(
                "CALL_ENTRY", selected.getId(), "call-entry-1")).thenReturn(Optional.empty());
        when(entries.findFirstById(selected.getId())).thenReturn(Optional.of(selected));
        when(configs.findByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));

        var response = service.call(
                selected.getId(), doctorUserId, "call-entry-1", "trace-1");

        assertThat(response.entryId()).isEqualTo(selected.getId());
        assertThat(selected.getStatus()).isEqualTo(QueueStatus.CALLED);
        assertThat(selected.getCalledByUserId()).isEqualTo(doctorUserId);
        assertThat(selected.getCallAttempts()).isEqualTo(1);
        verify(events).append(eq(selected), eq(config), eq("PatientCalled"),
                eq("queue.called"), eq("trace-1"),
                argThat(extra -> Boolean.TRUE.equals(extra.get("selectedByActor"))));
    }

    @Test
    void doctorCannotCallEntryThatIsNotWaitingInTheActiveQueue() {
        QueueEntry selected = entry(PriorityLevel.APPOINTMENT, QueueStatus.CALLED, 9);
        when(idempotencyRecords.findByCommandNameAndScopeIdAndIdempotencyKey(
                "CALL_ENTRY", selected.getId(), "call-entry-1")).thenReturn(Optional.empty());
        when(entries.findFirstById(selected.getId())).thenReturn(Optional.of(selected));

        assertThatThrownBy(() -> service.call(
                selected.getId(), doctorUserId, "call-entry-1", "trace-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(409);
        verify(entries, never()).saveAndFlush(any());
    }

    @Test
    void requeueFrontMovesEntryAheadOfCurrentPriorityHead() {
        config.setMissedPolicy(MissedPolicy.REQUIRE_MANUAL);
        QueueEntry missed = entry(PriorityLevel.PRIORITY, QueueStatus.MISSED, 9);
        QueueEntry first = entry(PriorityLevel.PRIORITY, QueueStatus.CHECKED_IN, 1);
        Instant firstEligibility = Instant.parse("2026-07-26T01:00:00Z");
        first.setEligibleSinceAt(firstEligibility);
        when(entries.findById(missed.getId())).thenReturn(Optional.of(missed));
        when(configs.findFirstByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));
        when(entries.findFirstById(missed.getId())).thenReturn(Optional.of(missed));
        when(entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                eq(config.getId()), eq(today), anyCollection())).thenReturn(List.of(first));

        service.requeue(missed.getId(), new RequeueRequest(RequeueRequest.Position.FRONT), "trace-1");

        assertThat(missed.getStatus()).isEqualTo(QueueStatus.CHECKED_IN);
        assertThat(missed.getEligibleSinceAt()).isBefore(firstEligibility);
        assertThat(missed.getCallAttempts()).isZero();
    }

    @Test
    void changingRatioResetsSchedulerState() {
        config.setCyclePhase(CyclePhase.NORMAL);
        config.setServedInPhase(7);
        when(configs.findByDepartmentId(departmentId)).thenReturn(Optional.of(config));
        when(configs.save(config)).thenReturn(config);
        QueueConfigRequest request = new QueueConfigRequest(
                "Khoa Nội", "NOI", "P101", 1, 1, 10, 3,
                MissedPolicy.REQUEUE_BACK, true);

        service.saveConfig(departmentId, request);

        assertThat(config.getCyclePhase()).isEqualTo(CyclePhase.PRIORITY);
        assertThat(config.getServedInPhase()).isZero();
        assertThat(config.getNormalCursor()).isEqualTo(NormalCursor.APPOINTMENT);
    }

    @Test
    void missRequiresThreeCallAttempts() {
        QueueEntry called = entry(PriorityLevel.PRIORITY, QueueStatus.CALLED, 1);
        called.setCallAttempts(2);
        when(entries.findFirstById(called.getId())).thenReturn(Optional.of(called));

        assertThatThrownBy(() -> service.miss(called.getId(), "trace-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(409);
    }

    @Test
    void recallIncrementsAttemptWithoutChangingQueueState() {
        QueueEntry called = entry(PriorityLevel.PRIORITY, QueueStatus.CALLED, 1);
        called.setCallAttempts(1);
        when(entries.findFirstById(called.getId())).thenReturn(Optional.of(called));
        when(configs.findByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));

        service.recall(called.getId(), doctorUserId, "trace-1");

        assertThat(called.getStatus()).isEqualTo(QueueStatus.CALLED);
        assertThat(called.getCallAttempts()).isEqualTo(2);
        assertThat(called.getCalledByUserId()).isEqualTo(doctorUserId);
        verify(events).append(eq(called), eq(config), eq("PatientCalled"), anyString(),
                eq("trace-1"), argThat(extra -> Integer.valueOf(2).equals(extra.get("callAttempt"))));
    }

    @Test
    void thirdCallCanTransitionToMissed() {
        QueueEntry called = entry(PriorityLevel.PRIORITY, QueueStatus.CALLED, 1);
        called.setCallAttempts(3);
        when(entries.findFirstById(called.getId())).thenReturn(Optional.of(called));
        when(configs.findByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));

        service.miss(called.getId(), "trace-1");

        assertThat(called.getStatus()).isEqualTo(QueueStatus.MISSED);
        assertThat(called.getMissedCount()).isEqualTo(1);
        assertThat(called.getMissedAt()).isNotNull();
    }

    @Test
    void prescriptionIssuedCreatesQueuedPharmacyEntryWithoutSecondCheckIn() {
        UUID prescriptionId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID patientUserId = UUID.randomUUID();
        Instant issuedAt = Instant.parse("2026-08-18T05:05:00Z");
        ServicePointSequence sequence = new ServicePointSequence();
        sequence.setLastNumber(6);
        QueueEntry previous = entry(PriorityLevel.APPOINTMENT, QueueStatus.COMPLETED, 1);
        previous.setPatientId(patientId);
        previous.setUserId(patientUserId);
        when(entries.findByPrescriptionId(prescriptionId)).thenReturn(Optional.empty());
        when(entries.findFirstByPatientIdAndUserIdIsNotNullOrderByCreatedAtDesc(patientId))
                .thenReturn(Optional.of(previous));
        when(servicePointSequences.findByServicePointIdAndQueueDate("PHARMACY-01",
                LocalDate.of(2026, 8, 18))).thenReturn(Optional.of(sequence));

        QueueEntry pharmacy = service.createPharmacyEntry(
                prescriptionId, consultationId, patientId, "pharmacy-01", issuedAt);

        assertThat(pharmacy.getQueueType()).isEqualTo(QueueType.PHARMACY_DISPENSING);
        assertThat(pharmacy.getConsultationPhase()).isNull();
        assertThat(pharmacy.getQueueClass()).isNull();
        assertThat(pharmacy.getStatus()).isEqualTo(QueueStatus.QUEUED);
        assertThat(pharmacy.getQueueNumber()).isEqualTo("RX-007");
        assertThat(pharmacy.getUserId()).isEqualTo(patientUserId);
        assertThat(pharmacy.getEligibleSinceAt()).isEqualTo(issuedAt);
    }

    @Test
    void labOrderReadyCreatesQueuedLabExecutionEntryWithoutSecondCheckIn() {
        UUID labOrderId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID patientUserId = UUID.randomUUID();
        Instant orderedAt = Instant.parse("2026-08-18T05:00:00Z");
        ServicePointSequence sequence = new ServicePointSequence();
        sequence.setLastNumber(2);
        QueueEntry previous = entry(PriorityLevel.APPOINTMENT, QueueStatus.COMPLETED, 1);
        previous.setPatientId(patientId);
        previous.setUserId(patientUserId);
        when(entries.findByLabOrderIdAndServicePointId(labOrderId, "LAB-HEMATOLOGY-01"))
                .thenReturn(Optional.empty());
        when(entries.findFirstByPatientIdAndUserIdIsNotNullOrderByCreatedAtDesc(patientId))
                .thenReturn(Optional.of(previous));
        when(servicePointSequences.findByServicePointIdAndQueueDate("LAB-HEMATOLOGY-01",
                LocalDate.of(2026, 8, 18))).thenReturn(Optional.of(sequence));

        QueueEntry lab = service.createLabExecutionEntry(
                labOrderId, consultationId, patientId, "lab-hematology-01", orderedAt);

        assertThat(lab.getQueueType()).isEqualTo(QueueType.LAB_EXECUTION);
        assertThat(lab.getConsultationPhase()).isNull();
        assertThat(lab.getQueueClass()).isNull();
        assertThat(lab.getStatus()).isEqualTo(QueueStatus.QUEUED);
        assertThat(lab.getQueueNumber()).isEqualTo("LAB-003");
        assertThat(lab.getUserId()).isEqualTo(patientUserId);
        assertThat(lab.getEligibleSinceAt()).isEqualTo(orderedAt);
    }

    @Test
    void servicePointCallNextUsesStrictFifo() {
        UUID staffId = UUID.randomUUID();
        QueueEntry first = pharmacyEntry(1, QueueStatus.QUEUED);
        QueueEntry second = pharmacyEntry(2, QueueStatus.QUEUED);
        when(idempotencyRecords.findByCommandNameAndScopeIdAndIdempotencyKey(
                eq("CALL_NEXT_SERVICE_POINT"), any(), eq("rx-call-1"))).thenReturn(Optional.empty());
        when(entries.findByServicePointIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                "PHARMACY-01", today, java.util.Set.of(QueueStatus.QUEUED)))
                .thenReturn(List.of(first, second));
        when(entries.findFirstById(first.getId())).thenReturn(Optional.of(first));

        var selected = service.callNextAtServicePoint(
                "pharmacy-01", staffId, "rx-call-1", "trace-1");

        assertThat(selected).get().extracting(response -> response.entryId()).isEqualTo(first.getId());
        assertThat(first.getStatus()).isEqualTo(QueueStatus.CALLED);
        assertThat(second.getStatus()).isEqualTo(QueueStatus.QUEUED);
        assertThat(first.getCalledByUserId()).isEqualTo(staffId);
    }

    @Test
    void patientCurrentAllowsQueuedServicePointEntryWithoutEstimatedWait() {
        QueueEntry lab = pharmacyEntry(1, QueueStatus.QUEUED);
        lab.setQueueType(QueueType.LAB_EXECUTION);
        UUID patientId = lab.getPatientId();
        UUID userId = UUID.randomUUID();
        lab.setUserId(userId);
        lab.setEstimatedWaitMinutes(null);
        when(entries.findFirstByPatientIdAndUserIdAndQueueDateAndStatusInOrderByCreatedAtDesc(
                eq(patientId), eq(userId), eq(today), anyCollection())).thenReturn(Optional.of(lab));

        var response = service.patientCurrent(patientId, userId, false);

        assertThat(response.entryId()).isEqualTo(lab.getId());
        assertThat(response.estimatedWaitMinutes()).isNull();
    }

    @Test
    void pharmacyEntryCanOnlyCompleteFromDispensedEventWhileInProgress() {
        UUID prescriptionId = UUID.randomUUID();
        QueueEntry pharmacy = pharmacyEntry(1, QueueStatus.IN_PROGRESS);
        pharmacy.setPrescriptionId(prescriptionId);
        Instant dispensedAt = Instant.parse("2026-08-18T05:15:00Z");
        when(entries.findByPrescriptionId(prescriptionId)).thenReturn(Optional.of(pharmacy));
        when(entries.findFirstById(pharmacy.getId())).thenReturn(Optional.of(pharmacy));

        service.completePharmacyEntry(prescriptionId, dispensedAt, "trace-1");

        assertThat(pharmacy.getStatus()).isEqualTo(QueueStatus.COMPLETED);
        assertThat(pharmacy.getCompletedAt()).isEqualTo(dispensedAt);
        verify(events).append(eq(pharmacy), isNull(), eq("QueueEntryCompleted"),
                eq("queue.completed"), eq("trace-1"), anyMap());
    }

    @Test
    void allRequiredResultsCreatesQueuedResultReviewFromInitialConsultation() {
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        Instant readyAt = Instant.parse("2026-08-18T05:00:00Z");
        QueueEntry initial = entry(PriorityLevel.APPOINTMENT, QueueStatus.COMPLETED, 47);
        initial.setPatientId(patientId);
        initial.setQueueNumber("NOI-047");
        initial.setRoomDisplayNameSnapshot("Phòng 101");
        when(entries.findByConsultationIdAndQueueTypeAndConsultationPhase(
                consultationId, QueueType.CONSULTATION, ConsultationPhase.RESULT_REVIEW))
                .thenReturn(Optional.empty());
        when(entries.findById(initial.getId())).thenReturn(Optional.of(initial));

        QueueEntry review = service.createResultReviewEntry(
                consultationId, patientId, initial.getId(), readyAt);

        assertThat(review.getQueueType()).isEqualTo(QueueType.CONSULTATION);
        assertThat(review.getConsultationPhase()).isEqualTo(ConsultationPhase.RESULT_REVIEW);
        assertThat(review.getQueueClass()).isNull();
        assertThat(review.getStatus()).isEqualTo(QueueStatus.QUEUED);
        assertThat(review.getEligibleSinceAt()).isEqualTo(readyAt);
        assertThat(review.getQueueNumber()).isEqualTo("NOI-047-R");
        assertThat(review.getQueueConfigId()).isEqualTo(initial.getQueueConfigId());
        assertThat(review.getUserId()).isEqualTo(initial.getUserId());
        assertThat(initial.getConsultationId()).isEqualTo(consultationId);
    }

    @Test
    void resultReviewCreationIsIdempotentByConsultation() {
        UUID consultationId = UUID.randomUUID();
        QueueEntry existing = entry(PriorityLevel.APPOINTMENT, QueueStatus.QUEUED, 47);
        existing.setConsultationPhase(ConsultationPhase.RESULT_REVIEW);
        existing.setQueueClass(null);
        when(entries.findByConsultationIdAndQueueTypeAndConsultationPhase(
                consultationId, QueueType.CONSULTATION, ConsultationPhase.RESULT_REVIEW))
                .thenReturn(Optional.of(existing));

        assertThat(service.createResultReviewEntry(
                consultationId, existing.getPatientId(), null, Instant.now())).isSameAs(existing);
        verify(entries, never()).findByPatientIdAndQueueTypeAndConsultationPhaseAndStatusInOrderByCreatedAtDesc(
                any(), any(), any(), anyCollection());
    }

    @Test
    void missedResultReviewAlwaysRequeuesAtBackOfItsLane() {
        config.setMissedPolicy(MissedPolicy.REQUIRE_MANUAL);
        QueueEntry review = entry(PriorityLevel.APPOINTMENT, QueueStatus.MISSED, 47);
        review.setConsultationPhase(ConsultationPhase.RESULT_REVIEW);
        review.setQueueClass(null);
        review.setEligibleSinceAt(Instant.EPOCH);
        when(entries.findById(review.getId())).thenReturn(Optional.of(review));
        when(entries.findFirstById(review.getId())).thenReturn(Optional.of(review));
        when(configs.findFirstByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));

        service.requeue(review.getId(), new RequeueRequest(RequeueRequest.Position.FRONT), "trace-1");

        assertThat(review.getStatus()).isEqualTo(QueueStatus.QUEUED);
        assertThat(review.getEligibleSinceAt()).isAfter(Instant.EPOCH);
    }

    private QueueEntry entry(PriorityLevel priority, QueueStatus status, int sequence) {
        QueueEntry entry = new QueueEntry();
        entry.setId(UUID.randomUUID());
        entry.setQueueConfigId(config.getId());
        entry.setDepartmentId(departmentId);
        entry.setPatientId(UUID.randomUUID());
        entry.setUserId(UUID.randomUUID());
        entry.setQueueDate(today);
        entry.setQueueNumber("NOI-" + sequence);
        entry.setPriorityLevel(priority);
        entry.setQueueType(QueueType.CONSULTATION);
        entry.setConsultationPhase(ConsultationPhase.INITIAL);
        entry.setQueueClass(priority == PriorityLevel.PRIORITY ? QueueClass.PRIORITY : QueueClass.NORMAL);
        entry.setStatus(status);
        entry.setSequenceNumber(sequence);
        entry.setEligibleSinceAt(Instant.parse("2026-07-26T00:00:00Z").plusSeconds(sequence));
        return entry;
    }

    private QueueEntry pharmacyEntry(int sequence, QueueStatus status) {
        QueueEntry entry = new QueueEntry();
        entry.setId(UUID.randomUUID());
        entry.setPatientId(UUID.randomUUID());
        entry.setQueueType(QueueType.PHARMACY_DISPENSING);
        entry.setConsultationPhase(null);
        entry.setQueueClass(null);
        entry.setServicePointId("PHARMACY-01");
        entry.setQueueDate(today);
        entry.setQueueNumber("RX-" + sequence);
        entry.setSequenceNumber(sequence);
        entry.setPriorityLevel(PriorityLevel.WALK_IN);
        entry.setStatus(status);
        entry.setEligibleSinceAt(Instant.EPOCH.plusSeconds(sequence));
        return entry;
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
            throw new IllegalStateException(exception);
        }
    }

    private java.time.ZoneId serviceZone() {
        return java.time.ZoneId.of("Asia/Ho_Chi_Minh");
    }
}
