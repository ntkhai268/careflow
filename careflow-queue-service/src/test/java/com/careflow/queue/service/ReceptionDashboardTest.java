package com.careflow.queue.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.queue.client.DirectoryClient;
import com.careflow.queue.domain.*;
import com.careflow.queue.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReceptionDashboardTest {
    private final QueueConfigRepository configs = mock(QueueConfigRepository.class);
    private final QueueEntryRepository entries = mock(QueueEntryRepository.class);
    private final DirectoryClient directory = mock(DirectoryClient.class);
    private final UUID staffId = UUID.randomUUID();
    private final QueueManagementService service = new QueueManagementService(configs,
            mock(QueueNumberSequenceRepository.class), mock(ServicePointSequenceRepository.class),
            entries, mock(IdempotencyRecordRepository.class), mock(QueueEventService.class),
            mock(QrTokenService.class), "Asia/Ho_Chi_Minh", directory);
    private QueueConfig config;

    @BeforeEach
    void setUp() {
        config = new QueueConfig();
        config.setId(UUID.randomUUID()); config.setDepartmentId(UUID.randomUUID());
        config.setRoomCode("ROOM-11"); config.setDepartmentNameSnapshot("Cơ xương khớp");
        config.setAvgConsultationMinutes(10); config.setActive(true);
        when(configs.findByRoomCodeAndActiveTrue("ROOM-11")).thenReturn(Optional.of(config));
        when(configs.findByDepartmentIdAndActiveTrue(config.getDepartmentId())).thenReturn(Optional.of(config));
        when(directory.hasStaffRoomAccess(staffId, "ROOM-11")).thenReturn(ApiResponse.success(true));
    }

    @Test
    void issuedTicketIsVisibleToReceptionButNeverRecommendedForDoctorCall() {
        QueueEntry waiting = new QueueEntry();
        waiting.setId(UUID.randomUUID()); waiting.setAppointmentId(UUID.randomUUID());
        waiting.setPatientId(UUID.randomUUID()); waiting.setUserId(UUID.randomUUID());
        waiting.setQueueConfigId(config.getId()); waiting.setDepartmentId(config.getDepartmentId());
        waiting.setQueueDate(service.businessDate()); waiting.setSequenceNumber(6);
        waiting.setQueueNumber("CXK-006"); waiting.setStatus(QueueStatus.WAITING);
        waiting.setPriorityLevel(PriorityLevel.APPOINTMENT); waiting.setScheduledStartAt(Instant.now());
        when(entries.findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                eq(config.getId()), eq(service.businessDate()), any())).thenAnswer(call -> {
            Collection<QueueStatus> statuses = call.getArgument(2);
            return statuses.contains(QueueStatus.WAITING) ? List.of(waiting) : List.of();
        });

        var reception = service.receptionDashboard("ROOM-11", staffId, "STAFF");
        assertThat(reception.entries()).extracting(e -> e.queueNumber()).containsExactly("CXK-006");
        assertThat(reception.entries().getFirst().effectivePosition()).isNull();
        assertThat(reception.recommendedNext()).isNull();
        assertThat(service.roomDashboard("ROOM-11").entries()).isEmpty();
        verify(entries, times(3)).findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                eq(config.getId()), eq(service.businessDate()), any());
    }

    @Test
    void unassignedStaffCannotReadReceptionTickets() {
        when(directory.hasStaffRoomAccess(staffId, "ROOM-11")).thenReturn(ApiResponse.success(false));
        assertThatThrownBy(() -> service.receptionDashboard("ROOM-11", staffId, "STAFF"))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus()).isEqualTo(403));
        verifyNoInteractions(entries);
    }
}
