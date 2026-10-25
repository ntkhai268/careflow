package com.careflow.queue.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.queue.client.*;
import com.careflow.queue.domain.*;
import com.careflow.queue.dto.ManualIntakeRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReceptionIntakeServiceTest {
    final QueueManagementService queues = mock(QueueManagementService.class);
    final DirectoryClient directory = mock(DirectoryClient.class);
    final PatientIntakeClient patients = mock(PatientIntakeClient.class);
    final ReceptionRoomClient rooms = mock(ReceptionRoomClient.class);
    final ReceptionIntakeService service = new ReceptionIntakeService(queues, directory, patients, rooms);
    final UUID actor = UUID.randomUUID(), patient = UUID.randomUUID(), department = UUID.randomUUID();

    void room() {
        var config = new QueueConfig(); config.setRoomCode("ROOM-01");
        when(queues.requireLockedConfig(department)).thenReturn(config);
        when(rooms.rooms()).thenReturn(ApiResponse.success(List.of(new ReceptionRoomClient.ReceptionRoom("ROOM-01", "CONSULTATION", true))));
    }

    @Test void unassignedStaffCannotResolvePatientOrCreateEntry() {
        room(); when(directory.hasStaffRoomAccess(actor, "ROOM-01")).thenReturn(ApiResponse.success(false));
        assertThatThrownBy(() -> service.intake(new ManualIntakeRequest(patient, null, department, PriorityLevel.WALK_IN), actor, "STAFF", "key", null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(patients); verify(queues, never()).manualIntake(any(), any(), any(), any(),any());
    }

    @Test void noAccountStaysNullAndCreatorIsSeparate() {
        room(); when(directory.hasStaffRoomAccess(actor, "ROOM-01")).thenReturn(ApiResponse.success(true));
        when(patients.owner(patient)).thenReturn(ApiResponse.success((UUID) null));
        service.intake(new ManualIntakeRequest(patient, null, department, PriorityLevel.WALK_IN), actor, "STAFF", "key", null);
        var request = ArgumentCaptor.forClass(ManualIntakeRequest.class);
        verify(queues).manualIntake(request.capture(), eq(actor), eq("key"), isNull(),isNull());
        assertThat(request.getValue().userId()).isNull();
    }

    @Test void clientCannotAssignAnUnrelatedOwner() {
        room(); when(patients.owner(patient)).thenReturn(ApiResponse.success(UUID.randomUUID()));
        assertThatThrownBy(() -> service.intake(new ManualIntakeRequest(patient, actor, department, PriorityLevel.WALK_IN), actor, "ADMIN", "key", null))
                .isInstanceOf(BusinessException.class);
        verify(queues, never()).manualIntake(any(), any(), any(), any(),any());
    }

    @Test void nonReceptionRoleCannotCreateEntry() {
        assertThatThrownBy(() -> service.intake(new ManualIntakeRequest(patient, null, department, PriorityLevel.WALK_IN), actor, "PATIENT", "key", null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(queues, patients, rooms);
    }
}
