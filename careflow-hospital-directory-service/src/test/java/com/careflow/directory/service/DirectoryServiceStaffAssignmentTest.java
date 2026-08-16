package com.careflow.directory.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.directory.dto.request.StaffAssignmentRequest;
import com.careflow.directory.model.Department;
import com.careflow.directory.model.Room;
import com.careflow.directory.model.StaffAssignment;
import com.careflow.directory.repository.DepartmentRepository;
import com.careflow.directory.repository.DoctorProfileRepository;
import com.careflow.directory.repository.RoomRepository;
import com.careflow.directory.repository.StaffAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectoryServiceStaffAssignmentTest {
    @Mock DepartmentRepository departments;
    @Mock RoomRepository rooms;
    @Mock DoctorProfileRepository doctors;
    @Mock StaffAssignmentRepository assignments;

    @InjectMocks
    DirectoryService directoryService;

    @Test
    void rejectsStaffAssignmentToRoomOwnedByAnotherDepartment() {
        when(departments.findById("CARDIO")).thenReturn(Optional.of(department("CARDIO", true)));
        when(rooms.findById("ROOM-NEURO")).thenReturn(Optional.of(room("ROOM-NEURO", "NEUROLOGY", true)));

        assertThatThrownBy(() -> directoryService.createStaffAssignment(request("CARDIO", "ROOM-NEURO")))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(400);
    }

    @Test
    void createsActiveStaffAssignmentForValidRoom() {
        when(departments.findById("CARDIO")).thenReturn(Optional.of(department("CARDIO", true)));
        when(rooms.findById("ROOM-CARDIO")).thenReturn(Optional.of(room("ROOM-CARDIO", "CARDIO", true)));
        when(assignments.findByUserIdAndRoomId(any(UUID.class), any(String.class)))
                .thenReturn(Optional.empty());
        when(assignments.save(any(StaffAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = directoryService.createStaffAssignment(request("CARDIO", "ROOM-CARDIO"));

        assertThat(response.getRoomId()).isEqualTo("ROOM-CARDIO");
        assertThat(response.getDepartmentCode()).isEqualTo("CARDIO");
        assertThat(response.getIsActive()).isTrue();
    }

    private StaffAssignmentRequest request(String departmentCode, String roomId) {
        return StaffAssignmentRequest.builder()
                .userId(UUID.fromString("55555555-5555-5555-5555-555555555555"))
                .roomId(roomId)
                .departmentCode(departmentCode)
                .isActive(true)
                .build();
    }

    private Department department(String code, boolean active) {
        return Department.builder().code(code).id(UUID.randomUUID()).name(code).isActive(active).build();
    }

    private Room room(String id, String departmentCode, boolean active) {
        return Room.builder().id(id).departmentCode(departmentCode).displayName(id)
                .roomType("CONSULTATION").isActive(active).build();
    }
}
