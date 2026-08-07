package com.careflow.queue.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.queue.client.DirectoryClient;
import com.careflow.queue.client.dto.DoctorAssignmentResponse;
import com.careflow.queue.domain.*;
import com.careflow.queue.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueRoomAssignmentTest {
    @Mock QueueConfigRepository configs;
    @Mock QueueNumberSequenceRepository sequences;
    @Mock ServicePointSequenceRepository servicePointSequences;
    @Mock QueueEntryRepository entries;
    @Mock IdempotencyRecordRepository idempotencyRecords;
    @Mock QueueEventService events;
    @Mock QrTokenService qrTokens;
    @Mock DirectoryClient directory;

    private QueueManagementService service;
    private QueueConfig config;
    private QueueEntry entry;
    private UUID doctorUserId;

    @BeforeEach
    void setUp() {
        service = new QueueManagementService(configs, sequences, servicePointSequences, entries,
                idempotencyRecords, events, qrTokens, "Asia/Ho_Chi_Minh", directory);
        doctorUserId = UUID.randomUUID();

        config = new QueueConfig();
        config.setId(UUID.randomUUID());
        config.setDepartmentId(UUID.randomUUID());
        config.setDepartmentNameSnapshot("Nội tổng quát");
        config.setQueuePrefix("NOI");
        config.setRoomCode("ROOM-01");
        config.setAvgConsultationMinutes(10);
        config.setNearTurnThreshold(3);
        config.setActive(true);

        entry = new QueueEntry();
        entry.setId(UUID.randomUUID());
        entry.setQueueConfigId(config.getId());
        entry.setDepartmentId(config.getDepartmentId());
        entry.setPatientId(UUID.randomUUID());
        entry.setAppointmentId(UUID.randomUUID());
        entry.setQueueType(QueueType.CONSULTATION);
        entry.setConsultationPhase(ConsultationPhase.INITIAL);
        entry.setQueueClass(QueueClass.NORMAL);
        entry.setPriorityLevel(PriorityLevel.APPOINTMENT);
        entry.setStatus(QueueStatus.CHECKED_IN);
        entry.setQueueDate(LocalDate.now());
        entry.setQueueNumber("NOI-001");
        entry.setSequenceNumber(1);

        when(entries.findById(entry.getId())).thenReturn(Optional.of(entry));
    }

    @Test
    void otherDoctorCannotCallConsultationEntryInRoom() {
        DoctorAssignmentResponse assignment = new DoctorAssignmentResponse();
        assignment.setAssignedRoomId("ROOM-02");
        assignment.setIsActive(true);
        when(directory.getDoctorByUserId(doctorUserId)).thenReturn(ApiResponse.success(assignment));
        when(configs.findByDepartmentIdAndActiveTrue(config.getDepartmentId())).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.call(entry.getId(), doctorUserId, "DOCTOR", "idempotency-1", "trace-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getStatus()).isEqualTo(403));
    }

    @Test
    void assignedDoctorCanCallConsultationEntryInRoom() {
        DoctorAssignmentResponse assignment = new DoctorAssignmentResponse();
        assignment.setAssignedRoomId("ROOM-01");
        assignment.setIsActive(true);
        when(directory.getDoctorByUserId(doctorUserId)).thenReturn(ApiResponse.success(assignment));
        when(configs.findByDepartmentIdAndActiveTrue(config.getDepartmentId())).thenReturn(Optional.of(config));
        when(idempotencyRecords.findByCommandNameAndScopeIdAndIdempotencyKey(
                eq("CALL_ENTRY"), eq(entry.getId()), eq("idempotency-1")))
                .thenReturn(Optional.empty());
        when(entries.findFirstById(entry.getId())).thenReturn(Optional.of(entry));
        when(entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                eq(config.getId()), any(LocalDate.class), anyCollection()))
                .thenReturn(List.of());
        when(entries.existsByQueueConfigIdAndQueueDateAndStatusIn(
                eq(config.getId()), any(LocalDate.class), anyCollection()))
                .thenReturn(false);

        assertThat(service.call(entry.getId(), doctorUserId, "DOCTOR", "idempotency-1", "trace-1"))
                .extracting(response -> response.queueStatus())
                .isEqualTo(QueueStatus.CALLED);
    }

    @Test
    void staffCannotCallLabEntryWithoutServicePointAssignment() {
        entry.setQueueType(QueueType.LAB_EXECUTION);
        entry.setConsultationPhase(null);
        entry.setServicePointId("LAB-HEMATOLOGY-01");
        when(directory.hasStaffRoomAccess(doctorUserId, "LAB-HEMATOLOGY-01"))
                .thenReturn(ApiResponse.success(false));

        assertThatThrownBy(() -> service.call(entry.getId(), doctorUserId, "LAB_TECHNICIAN",
                "idempotency-lab-1", "trace-lab-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getStatus()).isEqualTo(403));
    }
}
