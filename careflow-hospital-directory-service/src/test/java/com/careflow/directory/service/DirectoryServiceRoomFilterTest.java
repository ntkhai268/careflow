package com.careflow.directory.service;

import com.careflow.directory.model.Room;
import com.careflow.directory.repository.DepartmentRepository;
import com.careflow.directory.repository.DoctorProfileRepository;
import com.careflow.directory.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectoryServiceRoomFilterTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private DoctorProfileRepository doctorProfileRepository;

    @InjectMocks
    private DirectoryService directoryService;

    @Test
    void getAllRoomsAppliesDepartmentAndRoomTypeTogether() {
        when(roomRepository.findByDepartmentCodeAndRoomTypeAndIsActiveTrue("CARDIO", "LAB"))
                .thenReturn(List.of(
                        room("room-2", "CARDIO", "LAB")));

        var rooms = directoryService.getAllRooms("CARDIO", "LAB");

        assertThat(rooms).hasSize(1);
        assertThat(rooms.get(0).getId()).isEqualTo("room-2");
        assertThat(rooms.get(0).getRoomType()).isEqualTo("LAB");
    }

    private Room room(String id, String departmentCode, String roomType) {
        return Room.builder()
                .id(id)
                .departmentCode(departmentCode)
                .displayName(id)
                .roomType(roomType)
                .isActive(true)
                .build();
    }
}
