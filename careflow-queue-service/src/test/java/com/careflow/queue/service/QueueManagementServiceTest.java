package com.careflow.queue.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.queue.domain.*;
import com.careflow.queue.dto.QueueConfigRequest;
import com.careflow.queue.dto.QueueDashboardResponse;
import com.careflow.queue.dto.RequeueRequest;
import com.careflow.queue.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
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
    @Mock QueueEntryRepository entries;
    @Mock IdempotencyRecordRepository idempotencyRecords;
    @Mock QueueEventService events;
    @Mock QrTokenService qrTokens;

    private QueueManagementService service;
    private QueueConfig config;
    private UUID departmentId;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        service = new QueueManagementService(configs, sequences, entries, idempotencyRecords,
                events, qrTokens, "Asia/Ho_Chi_Minh");
        today = service.businessDate();
        departmentId = UUID.randomUUID();
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
    void callNextRejectsNewCommandWhileAnotherPatientIsBeingServed() {
        when(configs.findFirstByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));
        when(idempotencyRecords.findByCommandNameAndScopeIdAndIdempotencyKey(
                "CALL_NEXT", departmentId, "request-1")).thenReturn(Optional.empty());
        when(entries.findFirstByQueueConfigIdAndQueueDateAndStatusInOrderByCalledAtAsc(
                eq(config.getId()), eq(today), anyCollection())).thenReturn(Optional.of(entry(
                        PriorityLevel.APPOINTMENT, QueueStatus.IN_PROGRESS, 1)));

        assertThatThrownBy(() -> service.callNext(departmentId, "request-1", "trace-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(409);
        verify(entries, never()).saveAndFlush(any());
    }

    @Test
    void callNextCallsPriorityPatientAndPersistsIdempotentOutcome() {
        QueueEntry priority = entry(PriorityLevel.PRIORITY, QueueStatus.CHECKED_IN, 1);
        when(configs.findFirstByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));
        when(idempotencyRecords.findByCommandNameAndScopeIdAndIdempotencyKey(
                "CALL_NEXT", departmentId, "request-1")).thenReturn(Optional.empty());
        when(entries.findFirstByQueueConfigIdAndQueueDateAndStatusInOrderByCalledAtAsc(
                eq(config.getId()), eq(today), anyCollection())).thenReturn(Optional.empty());
        when(entries.findByQueueConfigIdAndQueueDateAndStatusAndPriorityLevelOrderByEligibleSinceAtAscSequenceNumberAsc(
                eq(config.getId()), eq(today), eq(QueueStatus.CHECKED_IN),
                eq(PriorityLevel.EMERGENCY), any())).thenReturn(List.of());
        when(entries.findByQueueConfigIdAndQueueDateAndStatusAndPriorityLevelOrderByEligibleSinceAtAscSequenceNumberAsc(
                eq(config.getId()), eq(today), eq(QueueStatus.CHECKED_IN),
                eq(PriorityLevel.PRIORITY), any())).thenReturn(List.of(priority));

        assertThat(service.callNext(departmentId, "request-1", "trace-1"))
                .get().extracting(response -> response.entryId()).isEqualTo(priority.getId());
        assertThat(priority.getStatus()).isEqualTo(QueueStatus.CALLED);
        assertThat(priority.getCallAttempts()).isEqualTo(1);
        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRecords).save(captor.capture());
        assertThat(captor.getValue().getResultEntryId()).isEqualTo(priority.getId());
        assertThat(captor.getValue().getRequestFingerprint()).hasSize(64);
    }

    @Test
    void callNextReplaysPersistedIdempotentResult() {
        QueueEntry called = entry(PriorityLevel.PRIORITY, QueueStatus.CALLED, 1);
        IdempotencyRecord record = new IdempotencyRecord();
        record.setResultEntryId(called.getId());
        record.setRequestFingerprint(fingerprint(departmentId));
        when(configs.findFirstByDepartmentIdAndActiveTrue(departmentId)).thenReturn(Optional.of(config));
        when(idempotencyRecords.findByCommandNameAndScopeIdAndIdempotencyKey(
                "CALL_NEXT", departmentId, "request-1")).thenReturn(Optional.of(record));
        when(entries.findById(called.getId())).thenReturn(Optional.of(called));

        assertThat(service.callNext(departmentId, "request-1", "trace-1"))
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

        assertThatThrownBy(() -> service.callNext(departmentId, "request-1", "trace-1"))
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
        when(entries.findByQueueConfigIdAndQueueDateAndStatusAndPriorityLevelOrderByEligibleSinceAtAscSequenceNumberAsc(
                eq(config.getId()), eq(today), eq(QueueStatus.CHECKED_IN),
                eq(PriorityLevel.PRIORITY), any())).thenReturn(List.of(first));

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

        service.recall(called.getId(), "trace-1");

        assertThat(called.getStatus()).isEqualTo(QueueStatus.CALLED);
        assertThat(called.getCallAttempts()).isEqualTo(2);
        verify(events).append(eq(called), eq(config), eq("QUEUE_CALLED"), anyString(),
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
        entry.setStatus(status);
        entry.setSequenceNumber(sequence);
        entry.setEligibleSinceAt(Instant.parse("2026-07-26T00:00:00Z").plusSeconds(sequence));
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
}
