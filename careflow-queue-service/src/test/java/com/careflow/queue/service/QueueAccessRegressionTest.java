package com.careflow.queue.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.queue.client.DirectoryClient;
import com.careflow.queue.client.dto.DoctorAssignmentResponse;
import com.careflow.queue.domain.*;
import com.careflow.queue.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueueAccessRegressionTest {
    private final QueueConfigRepository configs = mock(QueueConfigRepository.class);
    private final QueueEntryRepository entries = mock(QueueEntryRepository.class);
    private final DirectoryClient directory = mock(DirectoryClient.class);
    private final QueueEventService events = mock(QueueEventService.class);
    private final UUID actor = UUID.randomUUID(), order = UUID.randomUUID(), department = UUID.randomUUID();
    private final QueueManagementService service = new QueueManagementService(configs, mock(QueueNumberSequenceRepository.class),
            mock(ServicePointSequenceRepository.class), entries, mock(IdempotencyRecordRepository.class), events,
            mock(QrTokenService.class), "Asia/Ho_Chi_Minh", directory);
    private QueueConfig config;

    @BeforeEach
    void setUp() {
        config = new QueueConfig(); config.setId(UUID.randomUUID()); config.setDepartmentId(department);
        config.setRoomCode("ROOM-01"); config.setActive(true);
        when(configs.findByDepartmentIdAndActiveTrue(department)).thenReturn(Optional.of(config));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dashboard", "call-next"})
    void departmentAliasesCannotBypassRoomAssignment(String command) {
        var assignment = new DoctorAssignmentResponse(); assignment.setIsActive(true); assignment.setAssignedRoomId("ROOM-02");
        when(directory.getDoctorByUserId(actor)).thenReturn(ApiResponse.success(assignment));
        denied(403, () -> {
            if (command.equals("dashboard")) service.dashboard(department, actor, "DOCTOR");
            else service.callNext(department, actor, "DOCTOR", "key", "trace");
        });
        verifyNoInteractions(entries, events);
    }

    @Test
    void pointSelectorDoesNotUseAnotherPointsLatestEntry() {
        when(directory.hasStaffRoomAccess(actor, "LAB-A")).thenReturn(ApiResponse.success(true));
        var entry = new QueueEntry(); entry.setId(UUID.randomUUID()); entry.setLabOrderId(order);
        entry.setQueueType(QueueType.LAB_EXECUTION); entry.setServicePointId("LAB-A");
        when(entries.findByLabOrderIdAndServicePointId(order, "LAB-A")).thenReturn(Optional.of(entry));
        assertThat(service.currentLabExecution(order, " lab-a ", actor, "LAB_TECHNICIAN").entryId()).isEqualTo(entry.getId());
        verify(entries, never()).findFirstByLabOrderIdOrderByCreatedAtDesc(any());
    }

    @Test
    void pointSelectorChecksAssignmentBeforeReadingOrder() {
        when(directory.hasStaffRoomAccess(actor, "LAB-A")).thenReturn(ApiResponse.success(false));
        denied(403, () -> service.currentLabExecution(order, "LAB-A", actor, "LAB_TECHNICIAN"));
        verifyNoInteractions(entries);
    }

    @Test
    void unavailableDirectoryDoesNotReturnAQueueEntry() {
        when(directory.hasStaffRoomAccess(actor, "LAB-A")).thenThrow(new IllegalStateException("Authority unavailable"));
        denied(403, () -> service.currentLabExecution(order, "LAB-A", actor, "LAB_TECHNICIAN"));
        verifyNoInteractions(entries);
    }

    @Test
    void missingDirectoryCannotEnableRoomOrPointAccess() {
        var missing = new QueueManagementService(configs, null, null, entries, null, events, null, "Asia/Ho_Chi_Minh");
        denied(503, () -> missing.dashboard(department, actor, "DOCTOR"));
        denied(503, () -> missing.currentLabExecution(order, "LAB-A", actor, "LAB_TECHNICIAN"));
        verifyNoInteractions(entries, events);
    }

    @Test
    void patientCurrentDoesNotGiveUnassignedDoctorAnotherRoomsEntry() {
        var entry = new QueueEntry(); entry.setId(UUID.randomUUID()); entry.setQueueType(QueueType.LAB_EXECUTION);
        entry.setServicePointId("LAB-A");
        when(entries.findFirstByPatientIdAndQueueDateAndStatusInOrderByCreatedAtDesc(any(), any(), any())).thenReturn(Optional.of(entry));
        when(entries.findById(entry.getId())).thenReturn(Optional.of(entry));
        denied(403, () -> service.patientCurrent(UUID.randomUUID(), actor, "DOCTOR"));
    }

    private void denied(int status, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus()).isEqualTo(status));
    }
}
