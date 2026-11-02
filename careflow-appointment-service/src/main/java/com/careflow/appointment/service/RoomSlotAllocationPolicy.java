package com.careflow.appointment.service;

import com.careflow.appointment.client.DirectoryClient;
import com.careflow.appointment.client.dto.DoctorProfileResponse;
import com.careflow.appointment.client.dto.RoomResponse;
import com.careflow.appointment.dto.response.TimeSlotAvailabilityResponse;
import com.careflow.appointment.model.Appointment;
import com.careflow.appointment.model.AppointmentStatus;
import com.careflow.appointment.model.Department;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.appointment.repository.AppointmentSlotLockRepository;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * Policy chịu trách nhiệm phân bổ phòng khám đang hoạt động, kiểm tra bác sĩ phụ trách
 * từ Directory, khóa slot DB chống đặt vượt sức chứa và tổng hợp tình trạng chỗ trống theo ca.
 */
@Slf4j
class RoomSlotAllocationPolicy {

    record DirectoryAssignment(
            UUID departmentId,
            String roomId,
            String roomDisplayName,
            UUID doctorUserId,
            String doctorName) {
    }

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotLockRepository slotLockRepository;
    private final DirectoryClient directoryClient;
    private final RoomScheduleService schedules;
    private final IntSupplier defaultCapacitySupplier;

    RoomSlotAllocationPolicy(AppointmentRepository appointmentRepository,
                             AppointmentSlotLockRepository slotLockRepository,
                             DirectoryClient directoryClient,
                             RoomScheduleService schedules,
                             IntSupplier defaultCapacitySupplier) {
        this.appointmentRepository = appointmentRepository;
        this.slotLockRepository = slotLockRepository;
        this.directoryClient = directoryClient;
        this.schedules = schedules;
        this.defaultCapacitySupplier = defaultCapacitySupplier;
    }

    DirectoryAssignment resolveBookingAssignment(Department department,
                                                 LocalDate date,
                                                 String slot,
                                                 UUID requestedDoctor) {
        List<DirectoryAssignment> candidates = resolveDirectoryAssignments(department, requestedDoctor);
        if (schedules != null) {
            schedules.lockRooms(candidates.stream().map(DirectoryAssignment::roomId).toList());
        }

        DirectoryAssignment selected = null;
        long bestRemaining = 0;
        for (DirectoryAssignment candidate : candidates) {
            int capacity = roomSlots(candidate.roomId(), date).stream()
                    .filter(item -> item.timeSlot().equals(slot))
                    .mapToInt(RoomScheduleService.Slot::capacity)
                    .findFirst()
                    .orElse(0);
            long booked = appointmentRepository.countByRoomIdAndAppointmentDateAndTimeSlotAndStatusNot(
                    candidate.roomId(), date, slot, AppointmentStatus.CANCELLED);
            long remaining = capacity - booked;
            if (remaining > bestRemaining) {
                bestRemaining = remaining;
                selected = candidate;
            }
        }
        if (selected == null) {
            throw new BusinessException(409,
                    "Khung giờ đã đóng hoặc hết chỗ ở các phòng đủ điều kiện. Vui lòng tải lại lịch.");
        }
        return selected;
    }

    void enforceSlotCapacity(String roomId, LocalDate date, String timeSlot) {
        // The lock repository is present in the Spring application. It is null
        // only for older unit-test constructors that intentionally bypass DB locking.
        if (slotLockRepository == null) {
            return;
        }

        String normalizedSlot = AppointmentService.normalizeTimeSlot(timeSlot);
        String slotKey = roomId + "|" + date + "|" + normalizedSlot;
        slotLockRepository.ensureSlotExists(slotKey);
        slotLockRepository.findBySlotKeyForUpdate(slotKey)
                .orElseThrow(() -> new BusinessException(500, "Không khởi tạo được khóa khung giờ"));

        long booked = appointmentRepository.countByRoomIdAndAppointmentDateAndTimeSlotAndStatusNot(
                roomId, date, normalizedSlot, AppointmentStatus.CANCELLED);
        int capacity = roomSlots(roomId, date).stream()
                .filter(item -> item.timeSlot().equals(normalizedSlot))
                .mapToInt(RoomScheduleService.Slot::capacity)
                .findFirst()
                .orElse(0);
        if (booked >= capacity) {
            throw new BusinessException(409,
                    "Khung giờ " + normalizedSlot + " đã đủ " + capacity + " người đặt khám");
        }
    }

    List<TimeSlotAvailabilityResponse> getTimeSlotAvailability(Department department,
                                                               LocalDate date,
                                                               UUID doctorId,
                                                               Clock clock) {
        List<DirectoryAssignment> assignments = resolveDirectoryAssignments(department, doctorId);
        var capacityBySlot = new TreeMap<String, Integer>();
        var bookedBySlot = new HashMap<String, Integer>();

        for (DirectoryAssignment assignment : assignments) {
            var roomBooked = new HashMap<String, Integer>();
            appointmentRepository
                    .findByRoomIdAndAppointmentDateAndStatusNot(
                            assignment.roomId(), date, AppointmentStatus.CANCELLED)
                    .forEach(a -> roomBooked.merge(
                            AppointmentService.normalizeTimeSlot(a.getTimeSlot()), 1, Integer::sum));

            for (RoomScheduleService.Slot slot : roomSlots(assignment.roomId(), date)) {
                capacityBySlot.merge(slot.timeSlot(), slot.capacity(), Integer::sum);
                int booked = roomBooked.getOrDefault(slot.timeSlot(), 0);
                bookedBySlot.merge(slot.timeSlot(), booked, Integer::sum);
            }
        }

        return capacityBySlot.entrySet().stream()
                .map(item -> {
                    int booked = bookedBySlot.getOrDefault(item.getKey(), 0);
                    int remaining = Math.max(0, item.getValue() - booked);
                    boolean past = AppointmentService.isSlotStartInPast(date, item.getKey(), clock);
                    String unavailableReason = past ? "PAST" : (remaining == 0 ? "FULL" : null);
                    return TimeSlotAvailabilityResponse.builder()
                            .timeSlot(item.getKey())
                            .bookedCount(booked)
                            .capacity(item.getValue())
                            .remaining(remaining)
                            .available(!past && remaining > 0)
                            .unavailableReason(unavailableReason)
                            .build();
                })
                .toList();
    }

    List<DirectoryAssignment> resolveDirectoryAssignments(Department department, UUID requestedDoctor) {
        if (directoryClient == null) {
            return List.of(new DirectoryAssignment(
                    department.getId(),
                    department.getRoomId(),
                    department.getRoomDisplayName(),
                    null,
                    null));
        }
        try {
            var departmentResponse = directoryClient.getDepartmentByCode(department.name());
            var master = departmentResponse == null ? null : departmentResponse.getData();
            if (master == null || master.getId() == null || !Boolean.TRUE.equals(master.getIsActive())) {
                throw new BusinessException(409, "DEPARTMENT_INACTIVE: Department is not active");
            }

            var roomsResponse = directoryClient.getAllRooms();
            var doctorsResponse = directoryClient.getDoctors(department.name());
            if (roomsResponse == null || roomsResponse.getData() == null
                    || doctorsResponse == null || doctorsResponse.getData() == null) {
                throw new BusinessException(503, "Không thể xác minh phòng/bác sĩ.");
            }

            List<RoomResponse> rooms = roomsResponse.getData().stream()
                    .filter(room -> Boolean.TRUE.equals(room.getIsActive())
                            && department.name().equals(room.getDepartmentCode())
                            && "CONSULTATION".equalsIgnoreCase(room.getRoomType()))
                    .sorted(Comparator.comparing(RoomResponse::getId))
                    .toList();

            List<DoctorProfileResponse> assignedDoctors = doctorsResponse.getData();
            if (requestedDoctor != null) {
                var requested = directoryClient.getDoctorByUserId(requestedDoctor);
                var profile = requested == null ? null : requested.getData();
                if (profile == null
                        || !requestedDoctor.equals(profile.getUserId())
                        || !Boolean.TRUE.equals(profile.getIsActive())
                        || !department.name().equals(profile.getDepartmentCode())) {
                    throw new BusinessException(409, "Bác sĩ yêu cầu chưa có phân công hợp lệ.");
                }
                assignedDoctors = List.of(profile);
            }

            var result = new ArrayList<DirectoryAssignment>();
            for (RoomResponse room : rooms) {
                List<DoctorProfileResponse> doctorsForRoom = filterDoctorsForRoom(
                        assignedDoctors, department, room.getId(), requestedDoctor);
                if (doctorsForRoom.size() == 1) {
                    DoctorProfileResponse doctor = doctorsForRoom.getFirst();
                    result.add(new DirectoryAssignment(
                            master.getId(),
                            room.getId(),
                            room.getDisplayName(),
                            doctor.getUserId(),
                            doctor.getFullName()));
                }
            }
            if (result.isEmpty()) {
                throw new BusinessException(409,
                        "DOCTOR_CONFIGURATION_INVALID: Chưa có phòng hoạt động với đúng một bác sĩ nhận lịch.");
            }
            return result;
        } catch (FeignException unavailable) {
            throw new BusinessException(503, "Không thể xác minh phân công phòng/bác sĩ.");
        }
    }

    UUID getEffectiveDoctorUserId(Appointment appointment) {
        if (appointment.getDoctorId() != null) {
            return appointment.getDoctorId();
        }
        if (appointment.getDepartment() == null) {
            return null;
        }
        try {
            return resolveDirectoryAssignments(appointment.getDepartment(), null).stream()
                    .filter(a -> a.roomId().equals(appointment.getRoomId()))
                    .map(DirectoryAssignment::doctorUserId)
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Cannot resolve current doctor assignment for legacy appointment {}", appointment.getId(), ex);
            return null;
        }
    }

    boolean hasStaffRoomAccess(UUID userId, String roomId) {
        if (directoryClient == null) {
            return true;
        }
        try {
            ApiResponse<Boolean> response = directoryClient.hasStaffRoomAccess(userId, roomId);
            return response != null && Boolean.TRUE.equals(response.getData());
        } catch (RuntimeException ex) {
            log.warn("Cannot verify staff room assignment for {} / {}", userId, roomId, ex);
            return false;
        }
    }

    private List<DoctorProfileResponse> filterDoctorsForRoom(List<DoctorProfileResponse> assignedDoctors,
                                                             Department department,
                                                             String roomId,
                                                             UUID requestedDoctor) {
        List<DoctorProfileResponse> doctors = assignedDoctors.stream()
                .filter(doctor -> Boolean.TRUE.equals(doctor.getIsActive())
                        && doctor.getUserId() != null
                        && department.name().equals(doctor.getDepartmentCode())
                        && roomId.equals(doctor.getAssignedRoomId())
                        && (requestedDoctor == null || requestedDoctor.equals(doctor.getUserId())))
                .toList();
        if (doctors.size() > 1) {
            doctors = doctors.stream()
                    .filter(d -> Boolean.TRUE.equals(d.getPrimaryForBooking()))
                    .toList();
        }
        return doctors;
    }

    private List<RoomScheduleService.Slot> roomSlots(String roomId, LocalDate date) {
        if (schedules == null) {
            int fallbackCapacity = Math.max(1, defaultCapacitySupplier.getAsInt());
            return RoomScheduleService.slots(RoomScheduleService.defaults(fallbackCapacity), date);
        }
        return schedules.slots(roomId, date);
    }
}
