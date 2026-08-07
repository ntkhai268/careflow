package com.careflow.directory.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.directory.dto.request.CreateDoctorProfileRequest;
import com.careflow.directory.model.Department;
import com.careflow.directory.model.Room;
import com.careflow.directory.repository.DepartmentRepository;
import com.careflow.directory.repository.DoctorProfileRepository;
import com.careflow.directory.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectoryServiceAssignmentValidationTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private DoctorProfileRepository doctorProfileRepository;

    @InjectMocks
    private DirectoryService directoryService;

    @Test
    void rejectsDoctorAssignedToRoomOwnedByAnotherDepartment() {
        when(doctorProfileRepository.findByUserId(UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .thenReturn(Optional.empty());
        when(departmentRepository.findById("CARDIO"))
                .thenReturn(Optional.of(department("CARDIO", true)));
        when(roomRepository.findById("ROOM-NEURO"))
                .thenReturn(Optional.of(room("ROOM-NEURO", "NEUROLOGY", true)));

        assertThatThrownBy(() -> directoryService.createDoctorProfile(request("CARDIO", "ROOM-NEURO")))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(400);
    }

    @Test
    void rejectsDoctorAssignedToInactiveDepartment() {
        when(doctorProfileRepository.findByUserId(UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .thenReturn(Optional.empty());
        when(departmentRepository.findById("CARDIO"))
                .thenReturn(Optional.of(department("CARDIO", false)));

        assertThatThrownBy(() -> directoryService.createDoctorProfile(request("CARDIO", "ROOM-CARDIO")))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(400);
    }

    private CreateDoctorProfileRequest request(String departmentCode, String roomId) {
        return CreateDoctorProfileRequest.builder()
                .userId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .fullName("Test Doctor")
                .departmentCode(departmentCode)
                .assignedRoomId(roomId)
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
