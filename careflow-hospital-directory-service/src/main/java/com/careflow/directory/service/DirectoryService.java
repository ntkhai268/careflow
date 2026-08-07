package com.careflow.directory.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.directory.dto.DepartmentResponse;
import com.careflow.directory.dto.DoctorProfileResponse;
import com.careflow.directory.dto.RoomResponse;
import com.careflow.directory.dto.StaffAssignmentResponse;
import com.careflow.directory.dto.request.CreateDepartmentRequest;
import com.careflow.directory.dto.request.CreateDoctorProfileRequest;
import com.careflow.directory.dto.request.CreateRoomRequest;
import com.careflow.directory.dto.request.UpdateDepartmentRequest;
import com.careflow.directory.dto.request.UpdateDoctorProfileRequest;
import com.careflow.directory.dto.request.UpdateRoomRequest;
import com.careflow.directory.dto.request.StaffAssignmentRequest;
import com.careflow.directory.model.Department;
import com.careflow.directory.model.DoctorProfile;
import com.careflow.directory.model.Room;
import com.careflow.directory.model.StaffAssignment;
import com.careflow.directory.repository.DepartmentRepository;
import com.careflow.directory.repository.DoctorProfileRepository;
import com.careflow.directory.repository.RoomRepository;
import com.careflow.directory.repository.StaffAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DirectoryService {

    private final DepartmentRepository departmentRepository;
    private final RoomRepository roomRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;

    public boolean hasStaffRoomAccess(UUID userId, String roomId) {
        return userId != null && roomId != null && !roomId.isBlank()
                && staffAssignmentRepository.existsByUserIdAndRoomIdAndIsActiveTrue(userId, roomId);
    }

    public List<StaffAssignmentResponse> getStaffAssignments(UUID userId, String roomId) {
        List<StaffAssignment> assignments;
        if (userId != null && roomId != null && !roomId.isBlank()) {
            assignments = staffAssignmentRepository.findByUserIdOrderByRoomIdAsc(userId).stream()
                    .filter(assignment -> roomId.equals(assignment.getRoomId()))
                    .toList();
        } else if (userId != null) {
            assignments = staffAssignmentRepository.findByUserIdOrderByRoomIdAsc(userId);
        } else if (roomId != null && !roomId.isBlank()) {
            assignments = staffAssignmentRepository.findByRoomIdOrderByUserIdAsc(roomId);
        } else {
            assignments = staffAssignmentRepository.findAllByOrderByRoomIdAscUserIdAsc();
        }
        return assignments.stream().map(StaffAssignmentResponse::from).toList();
    }

    @Transactional
    public StaffAssignmentResponse createStaffAssignment(StaffAssignmentRequest req) {
        String roomId = req.getRoomId().trim();
        String departmentCode = req.getDepartmentCode().trim();
        validateStaffAssignment(departmentCode, roomId);
        if (staffAssignmentRepository.findByUserIdAndRoomId(req.getUserId(), roomId).isPresent()) {
            throw new BusinessException(409, "Staff assignment đã tồn tại cho user và room này");
        }
        StaffAssignment assignment = StaffAssignment.builder()
                .userId(req.getUserId())
                .roomId(roomId)
                .departmentCode(departmentCode)
                .isActive(req.getIsActive() == null || req.getIsActive())
                .build();
        return StaffAssignmentResponse.from(staffAssignmentRepository.save(assignment));
    }

    @Transactional
    public StaffAssignmentResponse updateStaffAssignment(UUID id, StaffAssignmentRequest req) {
        StaffAssignment assignment = staffAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StaffAssignment", "id", id));
        String roomId = req.getRoomId().trim();
        String departmentCode = req.getDepartmentCode().trim();
        validateStaffAssignment(departmentCode, roomId);
        staffAssignmentRepository.findByUserIdAndRoomId(req.getUserId(), roomId)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessException(409, "Staff assignment đã tồn tại cho user và room này");
                });
        assignment.setUserId(req.getUserId());
        assignment.setRoomId(roomId);
        assignment.setDepartmentCode(departmentCode);
        assignment.setIsActive(req.getIsActive() == null || req.getIsActive());
        return StaffAssignmentResponse.from(staffAssignmentRepository.save(assignment));
    }

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

    @Transactional
    public DepartmentResponse createDepartment(CreateDepartmentRequest req) {
        if (departmentRepository.existsById(req.getCode())) {
            throw new BusinessException(400, "Mã khoa đã tồn tại: " + req.getCode());
        }
        Department dept = Department.builder()
                .code(req.getCode())
                .id(UUID.randomUUID())
                .name(req.getName())
                .description(req.getDescription())
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .build();
        return toDepartmentResponse(departmentRepository.save(dept));
    }

    @Transactional
    public DepartmentResponse updateDepartment(String code, UpdateDepartmentRequest req) {
        Department dept = departmentRepository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "code", code));
        if (req.getName() != null && !req.getName().isBlank()) {
            dept.setName(req.getName());
        }
        if (req.getDescription() != null) {
            dept.setDescription(req.getDescription());
        }
        if (req.getIsActive() != null) {
            dept.setIsActive(req.getIsActive());
        }
        return toDepartmentResponse(departmentRepository.save(dept));
    }

    public List<RoomResponse> getAllRooms(String departmentCode, String roomType) {
        List<Room> rooms;
        boolean hasDepartmentFilter = departmentCode != null && !departmentCode.isBlank();
        boolean hasRoomTypeFilter = roomType != null && !roomType.isBlank();
        if (hasDepartmentFilter && hasRoomTypeFilter) {
            rooms = roomRepository.findByDepartmentCodeAndRoomTypeAndIsActiveTrue(
                    departmentCode, roomType);
        } else if (hasDepartmentFilter) {
            rooms = roomRepository.findByDepartmentCodeAndIsActiveTrue(departmentCode);
        } else if (hasRoomTypeFilter) {
            rooms = roomRepository.findByRoomTypeAndIsActiveTrue(roomType);
        } else {
            rooms = roomRepository.findByIsActiveTrue();
        }
        return rooms.stream().map(this::toRoomResponse).toList();
    }

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest req) {
        if (roomRepository.existsById(req.getId())) {
            throw new BusinessException(400, "Mã phòng đã tồn tại: " + req.getId());
        }
        if (!departmentRepository.existsById(req.getDepartmentCode())) {
            throw new ResourceNotFoundException("Department", "code", req.getDepartmentCode());
        }
        Room room = Room.builder()
                .id(req.getId())
                .departmentCode(req.getDepartmentCode())
                .displayName(req.getDisplayName())
                .roomType(req.getRoomType())
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .build();
        return toRoomResponse(roomRepository.save(room));
    }

    @Transactional
    public RoomResponse updateRoom(String id, UpdateRoomRequest req) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", id));
        if (req.getDepartmentCode() != null && !req.getDepartmentCode().isBlank()) {
            if (!departmentRepository.existsById(req.getDepartmentCode())) {
                throw new ResourceNotFoundException("Department", "code", req.getDepartmentCode());
            }
            room.setDepartmentCode(req.getDepartmentCode());
        }
        if (req.getDisplayName() != null && !req.getDisplayName().isBlank()) {
            room.setDisplayName(req.getDisplayName());
        }
        if (req.getRoomType() != null && !req.getRoomType().isBlank()) {
            room.setRoomType(req.getRoomType());
        }
        if (req.getIsActive() != null) {
            room.setIsActive(req.getIsActive());
        }
        return toRoomResponse(roomRepository.save(room));
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

    @Transactional
    public DoctorProfileResponse createDoctorProfile(CreateDoctorProfileRequest req) {
        if (doctorProfileRepository.findByUserId(req.getUserId()).isPresent()) {
            throw new BusinessException(400, "Bác sĩ đã có profile cho userId: " + req.getUserId());
        }
        validateDoctorAssignment(req.getDepartmentCode(), req.getAssignedRoomId());
        DoctorProfile profile = DoctorProfile.builder()
                .id(UUID.randomUUID())
                .userId(req.getUserId())
                .fullName(req.getFullName())
                .title(req.getTitle())
                .departmentCode(req.getDepartmentCode())
                .assignedRoomId(req.getAssignedRoomId())
                .specialization(req.getSpecialization())
                .licenseNumber(req.getLicenseNumber())
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .build();
        validateDoctorAssignment(profile.getDepartmentCode(), profile.getAssignedRoomId());
        return toDoctorProfileResponse(doctorProfileRepository.save(profile));
    }

    @Transactional
    public DoctorProfileResponse updateDoctorProfile(UUID id, UpdateDoctorProfileRequest req) {
        DoctorProfile profile = doctorProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DoctorProfile", "id", id));
        if (req.getFullName() != null && !req.getFullName().isBlank()) {
            profile.setFullName(req.getFullName());
        }
        if (req.getTitle() != null) {
            profile.setTitle(req.getTitle());
        }
        if (req.getDepartmentCode() != null) {
            profile.setDepartmentCode(req.getDepartmentCode());
        }
        if (req.getAssignedRoomId() != null) {
            profile.setAssignedRoomId(req.getAssignedRoomId());
        }
        if (req.getSpecialization() != null) {
            profile.setSpecialization(req.getSpecialization());
        }
        if (req.getLicenseNumber() != null) {
            profile.setLicenseNumber(req.getLicenseNumber());
        }
        if (req.getIsActive() != null) {
            profile.setIsActive(req.getIsActive());
        }
        validateDoctorAssignment(profile.getDepartmentCode(), profile.getAssignedRoomId());
        return toDoctorProfileResponse(doctorProfileRepository.save(profile));
    }

    private void validateDoctorAssignment(String departmentCode, String roomId) {
        if (departmentCode == null || departmentCode.isBlank()) {
            if (roomId != null && !roomId.isBlank()) {
                throw new BusinessException(400, "assignedRoomId requires a departmentCode");
            }
            return;
        }
        Department department = departmentRepository.findById(departmentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "code", departmentCode));
        if (!Boolean.TRUE.equals(department.getIsActive())) {
            throw new BusinessException(400, "Doctor cannot be assigned to an inactive department");
        }
        if (roomId == null || roomId.isBlank()) return;
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", roomId));
        if (!Boolean.TRUE.equals(room.getIsActive()) || !departmentCode.equals(room.getDepartmentCode())) {
            throw new BusinessException(400, "Doctor room must be active and belong to the selected department");
        }
    }

    private void validateStaffAssignment(String departmentCode, String roomId) {
        Department department = departmentRepository.findById(departmentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "code", departmentCode));
        if (!Boolean.TRUE.equals(department.getIsActive())) {
            throw new BusinessException(400, "Staff không thể được phân công vào khoa không active");
        }
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", roomId));
        if (!Boolean.TRUE.equals(room.getIsActive()) || !departmentCode.equals(room.getDepartmentCode())) {
            throw new BusinessException(400, "Staff room phải active và thuộc đúng department");
        }
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
