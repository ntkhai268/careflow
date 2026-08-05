package com.careflow.directory.service;

import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.directory.dto.DepartmentResponse;
import com.careflow.directory.dto.DoctorProfileResponse;
import com.careflow.directory.dto.RoomResponse;
import com.careflow.directory.model.Department;
import com.careflow.directory.model.DoctorProfile;
import com.careflow.directory.model.Room;
import com.careflow.directory.repository.DepartmentRepository;
import com.careflow.directory.repository.DoctorProfileRepository;
import com.careflow.directory.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DirectoryService {

    private final DepartmentRepository departmentRepository;
    private final RoomRepository roomRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    public List<DepartmentResponse> getAllDepartments() {
        return departmentRepository.findByIsActiveTrue().stream()
                .map(this::toDepartmentResponse)
                .toList();
    }

    public DepartmentResponse getDepartmentByCode(String code) {
        Department dept = departmentRepository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "code", code));
        return toDepartmentResponse(dept);
    }

    public List<RoomResponse> getAllRooms(String departmentCode, String roomType) {
        List<Room> rooms;
        if (departmentCode != null && !departmentCode.isBlank()) {
            rooms = roomRepository.findByDepartmentCodeAndIsActiveTrue(departmentCode);
        } else if (roomType != null && !roomType.isBlank()) {
            rooms = roomRepository.findByRoomTypeAndIsActiveTrue(roomType);
        } else {
            rooms = roomRepository.findByIsActiveTrue();
        }
        return rooms.stream().map(this::toRoomResponse).toList();
    }

    public List<DoctorProfileResponse> getDoctors(String departmentCode) {
        List<DoctorProfile> doctors;
        if (departmentCode != null && !departmentCode.isBlank()) {
            doctors = doctorProfileRepository.findByDepartmentCodeAndIsActiveTrue(departmentCode);
        } else {
            doctors = doctorProfileRepository.findByIsActiveTrue();
        }
        return doctors.stream().map(this::toDoctorProfileResponse).toList();
    }

    public DoctorProfileResponse getDoctorByUserId(UUID userId) {
        DoctorProfile doctor = doctorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("DoctorProfile", "userId", userId));
        return toDoctorProfileResponse(doctor);
    }

    private DepartmentResponse toDepartmentResponse(Department dept) {
        return DepartmentResponse.builder()
                .code(dept.getCode())
                .id(dept.getId())
                .name(dept.getName())
                .description(dept.getDescription())
                .isActive(dept.getIsActive())
                .build();
    }

    private RoomResponse toRoomResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .departmentCode(room.getDepartmentCode())
                .displayName(room.getDisplayName())
                .roomType(room.getRoomType())
                .isActive(room.getIsActive())
                .build();
    }

    private DoctorProfileResponse toDoctorProfileResponse(DoctorProfile doc) {
        String deptName = null;
        if (doc.getDepartmentCode() != null) {
            deptName = departmentRepository.findById(doc.getDepartmentCode())
                    .map(Department::getName)
                    .orElse(null);
        }
        return DoctorProfileResponse.builder()
                .id(doc.getId())
                .userId(doc.getUserId())
                .fullName(doc.getFullName())
                .title(doc.getTitle())
                .departmentCode(doc.getDepartmentCode())
                .departmentName(deptName)
                .assignedRoomId(doc.getAssignedRoomId())
                .specialization(doc.getSpecialization())
                .licenseNumber(doc.getLicenseNumber())
                .isActive(doc.getIsActive())
                .build();
    }
}
