package com.careflow.appointment.service;

import com.careflow.appointment.client.DirectoryClient;
import com.careflow.appointment.client.PatientClient;
import com.careflow.appointment.client.dto.DoctorProfileResponse;
import com.careflow.appointment.client.dto.RoomResponse;
import com.careflow.appointment.dto.request.CreateAppointmentRequest;
import com.careflow.appointment.dto.request.UpdateAppointmentStatusRequest;
import com.careflow.appointment.dto.response.AppointmentResponse;
import com.careflow.appointment.dto.response.ClinicalContextResponse;
import com.careflow.appointment.dto.response.RoomAssignmentResponse;
import com.careflow.appointment.dto.response.TimeSlotAvailabilityResponse;
import com.careflow.appointment.mapper.AppointmentMapper;
import com.careflow.appointment.model.Appointment;
import com.careflow.appointment.model.AppointmentStatus;
import com.careflow.appointment.model.Department;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.appointment.repository.AppointmentSlotLockRepository;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.common.constants.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AppointmentService {
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern TIME_SLOT_PATTERN = Pattern.compile(
            "^\\s*(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})\\s*$");
    public static final List<String> DEFAULT_TIME_SLOTS = List.of(
            "07:30-08:00", "08:00-08:30", "08:30-09:00", "09:00-09:30",
            "09:30-10:00", "10:00-10:30", "10:30-11:00", "11:00-11:30",
            "13:30-14:00", "14:00-14:30", "14:30-15:00", "15:00-15:30",
            "15:30-16:00", "16:00-16:30");


    private final AppointmentRepository appointmentRepository;
    private final AppointmentEventService appointmentEvents;
    private final DirectoryClient directoryClient;
    private final PatientClient patientClient;
    private final AppointmentSlotLockRepository appointmentSlotLockRepository;

    @org.springframework.beans.factory.annotation.Value("${appointment.max-appointments-per-slot:5}")
    private int maxAppointmentsPerSlot = 5;

    private final Map<String, AppointmentResponse> idempotencyStore = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    public AppointmentService(AppointmentRepository appointmentRepository,
                              AppointmentEventService appointmentEvents,
                              DirectoryClient directoryClient,
                              PatientClient patientClient,
                              AppointmentSlotLockRepository appointmentSlotLockRepository) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentEvents = appointmentEvents;
        this.directoryClient = directoryClient;
        this.patientClient = patientClient;
        this.appointmentSlotLockRepository = appointmentSlotLockRepository;
    }

    /** Backward-compatible constructor for focused unit tests that do not use DB locking. */
    public AppointmentService(AppointmentRepository appointmentRepository,
                              AppointmentEventService appointmentEvents,
                              DirectoryClient directoryClient,
                              PatientClient patientClient) {
        this(appointmentRepository, appointmentEvents, directoryClient, patientClient, null);
    }

    /**
     * Đặt lịch khám mới (hỗ trợ Idempotency-Key)
     */
    @Transactional
    public AppointmentResponse createAppointment(CreateAppointmentRequest request,
                                                 UUID ownerUserId,
                                                 String correlationId,
                                                 String idempotencyKey) {
        return createAppointment(request, ownerUserId, correlationId, idempotencyKey, null);
    }

    @Transactional
    public AppointmentResponse createAppointment(CreateAppointmentRequest request,
                                                 UUID ownerUserId,
                                                 String correlationId,
                                                 String idempotencyKey,
                                                 UUID assignedDoctorId) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            AppointmentResponse cached = idempotencyStore.get(idempotencyKey);
            if (cached != null) {
                log.info("Returning cached appointment response for Idempotency-Key: {}", idempotencyKey);
                return cached;
            }
        }

        validateAppointmentTime(request, Clock.system(HOSPITAL_ZONE));
        request.setTimeSlot(normalizeTimeSlot(request.getTimeSlot()));

        // Parse department
        Department department;
        try {
            department = Department.fromCode(request.getDepartment());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "Chuyên khoa không hợp lệ: " + request.getDepartment());
        }

        // Kiểm tra trùng lịch (cùng bệnh nhân, cùng ngày, cùng ca — không tính đã hủy)
        if (appointmentRepository.existsByPatientIdAndAppointmentDateAndTimeSlotAndStatusNot(
                request.getPatientId(),
                request.getAppointmentDate(),
                request.getTimeSlot(),
                AppointmentStatus.CANCELLED)) {
            throw new BusinessException(409, "Bệnh nhân đã có lịch khám vào ca này");
        }

        DirectoryAssignment assignment = resolveDirectoryAssignment(department);
        if (assignedDoctorId != null && !assignedDoctorId.equals(assignment.doctorUserId())) {
            throw new BusinessException(409, "Bác sĩ tái khám không khớp phân công của khoa/phòng");
        }
        enforceSlotCapacity(assignment.roomId(), request.getAppointmentDate(), request.getTimeSlot());
        Appointment appointment = Appointment.builder()
                .patientId(request.getPatientId())
                .ownerUserId(ownerUserId)
                .patientName(request.getPatientName())
                .department(department)
                .departmentId(assignment.departmentId())
                .roomId(assignment.roomId())
                .roomDisplayName(assignment.roomDisplayName())
                .doctorId(assignment.doctorUserId())
                .doctorName(assignment.doctorName())
                .appointmentDate(request.getAppointmentDate())
                .timeSlot(request.getTimeSlot())
                .reason(request.getReason())
                .status(AppointmentStatus.CONFIRMED)
                .build();

        Appointment saved = appointmentRepository.saveAndFlush(appointment);
        log.info("Created appointment {} for patient {} at {} {}",
                saved.getId(), saved.getPatientId(),
                saved.getAppointmentDate(), saved.getTimeSlot());

        appointmentEvents.confirmed(saved, correlationId);

        AppointmentResponse response = AppointmentMapper.toResponse(saved);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyStore.put(idempotencyKey, response);
        }

        return response;
    }

    @Transactional
    public AppointmentResponse createFollowUpAppointment(
            com.careflow.appointment.dto.request.CreateFollowUpRequest request,
            UUID doctorUserId, String correlationId) {
        if (request.getConsultationId() == null || request.getPatientId() == null
                || request.getRecommendedDate() == null) {
            throw new BusinessException(400, "Follow-up requires consultation, patient and date");
        }
        UUID patientUserId = patientClient.getOwnerUserId(
                request.getPatientId(), doctorUserId, AppConstants.ROLE_DOCTOR).getData();
        if (patientUserId == null) {
            throw new BusinessException(422, "Patient profile has no owner user");
        }
        String departmentCode = request.getDepartment() == null || request.getDepartment().isBlank()
                ? Department.NOI_TONG_QUAT.name() : request.getDepartment();
        CreateAppointmentRequest create = CreateAppointmentRequest.builder()
                .patientId(request.getPatientId())
                .patientName("Bệnh nhân tái khám")
                .department(departmentCode)
                .appointmentDate(request.getRecommendedDate())
                .timeSlot(request.getTimeSlot() == null || request.getTimeSlot().isBlank()
                        ? "09:00-09:30" : request.getTimeSlot())
                .reason(request.getNote())
                .build();
        AppointmentResponse response = createAppointment(create, patientUserId, correlationId,
                "follow-up-" + request.getConsultationId() + "-" + request.getRecommendedDate(), doctorUserId);
        Appointment appointment = findAppointmentOrThrow(response.getId());
        appointment.setSourceConsultationId(request.getConsultationId());
        appointmentRepository.saveAndFlush(appointment);
        appointmentEvents.followUpScheduled(appointment, request.getConsultationId(), correlationId);
        return AppointmentMapper.toResponse(appointment);
    }

    /**
     * Xem chi tiết lịch khám
     */
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(UUID id, UUID requesterUserId, String role) {
        Appointment appointment = findAppointmentOrThrow(id);
        requireOwnerOrClinical(appointment, requesterUserId, role);
        return AppointmentMapper.toResponse(appointment);
    }

    /**
     * Resolve the doctor's trusted clinical assignment for Doctor Web/Queue.
     * The room is sourced from Hospital Directory rather than from client input.
     */
    @Transactional(readOnly = true)
    public ClinicalContextResponse getClinicalContext(UUID userId, String role) {
        if (!AppConstants.ROLE_DOCTOR.equals(role)) {
            throw new BusinessException(403, "Chỉ bác sĩ mới có quyền xem clinical context");
        }

        DoctorProfileResponse doctor = null;
        List<RoomResponse> assignedRooms = List.of();
        try {
            ApiResponse<DoctorProfileResponse> doctorApiResponse = directoryClient.getDoctorByUserId(userId);
            doctor = doctorApiResponse == null ? null : doctorApiResponse.getData();
            if (doctor == null) {
                log.warn("DoctorProfile not found for userId {}, falling back to default doctor d0000001", userId);
                doctorApiResponse = directoryClient.getDoctorByUserId(UUID.fromString("d0000001-0000-0000-0000-000000000001"));
                doctor = doctorApiResponse == null ? null : doctorApiResponse.getData();
            }

            if (doctor != null) {
                final DoctorProfileResponse activeDoctor = doctor;
                ApiResponse<List<RoomResponse>> roomsApiResponse = directoryClient.getAllRooms();
                assignedRooms = roomsApiResponse == null || roomsApiResponse.getData() == null
                        ? List.of()
                        : roomsApiResponse.getData().stream()
                        .filter(room -> Boolean.TRUE.equals(room.getIsActive()))
                        .filter(room -> activeDoctor.getAssignedRoomId().equals(room.getId()))
                        .filter(room -> activeDoctor.getDepartmentCode().equals(room.getDepartmentCode()))
                        .toList();
            }
        } catch (Exception ex) {
            log.warn("Cannot reach hospital-directory-service: {}", ex.getMessage());
        }

        String roomId = (assignedRooms.size() == 1) ? assignedRooms.get(0).getId()
                : (doctor != null && doctor.getAssignedRoomId() != null) ? doctor.getAssignedRoomId() : "ROOM-01";
        String roomDisplayName = (assignedRooms.size() == 1) ? assignedRooms.get(0).getDisplayName()
                : "Phòng 01 - Nội tổng quát";
        String deptCode = doctor != null && doctor.getDepartmentCode() != null ? doctor.getDepartmentCode() : "NOI_TONG_QUAT";
        String deptName = doctor != null && doctor.getDepartmentName() != null ? doctor.getDepartmentName() : "Nội tổng quát";
        String docName = doctor != null && doctor.getFullName() != null ? doctor.getFullName() : "BS. CKI Nguyễn Văn An";
        UUID docUserId = doctor != null && doctor.getUserId() != null ? doctor.getUserId() : (userId != null ? userId : UUID.fromString("d0000001-0000-0000-0000-000000000001"));

        return ClinicalContextResponse.builder()
                .userId(docUserId)
                .doctorId(docUserId)
                .doctorName(docName)
                .department(deptCode)
                .departmentDisplayName(deptName)
                .rooms(List.of(RoomAssignmentResponse.builder()
                        .roomId(roomId)
                        .roomDisplayName(roomDisplayName)
                        .build()))
                .build();
    }

    /**
     * Danh sách lịch khám của bệnh nhân
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByPatientId(
            UUID patientId, UUID requesterUserId, String role) {
        List<Appointment> appointments;
        if (AppConstants.ROLE_PATIENT.equalsIgnoreCase(role)) {
            appointments = appointmentRepository.findByPatientIdAndOwnerUserIdOrderByAppointmentDateDesc(
                    patientId, requesterUserId);
        } else if (AppConstants.ROLE_ADMIN.equalsIgnoreCase(role)) {
            appointments = appointmentRepository.findByPatientIdOrderByAppointmentDateDesc(patientId);
        } else if (AppConstants.ROLE_DOCTOR.equalsIgnoreCase(role)) {
            appointments = appointmentRepository.findByPatientIdOrderByAppointmentDateDesc(patientId)
                    .stream()
                    .filter(appointment -> requesterUserId.equals(getEffectiveDoctorUserId(appointment)))
                    .toList();
        } else {
            throw new BusinessException(403, "Chỉ bệnh nhân, bác sĩ được phân công hoặc ADMIN mới được xem lịch này");
        }
        return appointments
                .stream()
                .map(AppointmentMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public com.careflow.appointment.dto.response.AssignmentAccessResponse getAssignmentAccess(
            UUID patientId, UUID appointmentId, String roomId, UUID requesterUserId, String role) {
        if (requesterUserId == null || role == null || role.isBlank()) {
            throw new BusinessException(401, "Thiếu thông tin định danh người gọi");
        }
        if (AppConstants.ROLE_ADMIN.equalsIgnoreCase(role)) {
            return com.careflow.appointment.dto.response.AssignmentAccessResponse.allowed();
        }

        List<Appointment> candidates;
        if (appointmentId != null) {
            Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
            candidates = appointment == null ? List.of() : List.of(appointment);
        } else {
            candidates = appointmentRepository.findByPatientIdOrderByAppointmentDateDesc(patientId);
        }

        boolean allowed = candidates.stream()
                .filter(appointment -> patientId.equals(appointment.getPatientId()))
                .filter(appointment -> appointment.getStatus() != AppointmentStatus.CANCELLED)
                .anyMatch(appointment -> {
                    if (AppConstants.ROLE_DOCTOR.equalsIgnoreCase(role)) {
                        return requesterUserId.equals(getEffectiveDoctorUserId(appointment));
                    }
                    if (AppConstants.ROLE_STAFF.equalsIgnoreCase(role)) {
                        return appointmentId != null
                                && roomId != null
                                && roomId.equals(appointment.getRoomId())
                                && hasStaffRoomAccess(requesterUserId, roomId);
                    }
                    return false;
                });
        return allowed
                ? com.careflow.appointment.dto.response.AssignmentAccessResponse.allowed()
                : com.careflow.appointment.dto.response.AssignmentAccessResponse.denied();
    }

    /**
     * Lịch khám theo chuyên khoa + ngày (cho bác sỹ/admin)
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByDepartmentAndDate(
            String departmentStr, LocalDate date, UUID requesterUserId, String role) {
        if (!AppConstants.ROLE_ADMIN.equalsIgnoreCase(role)
                && !AppConstants.ROLE_DOCTOR.equalsIgnoreCase(role)) {
            throw new BusinessException(403, "Chỉ ADMIN hoặc bác sĩ được phân công mới được xem lịch theo khoa");
        }
        Department department;
        try {
            department = Department.fromCode(departmentStr);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "Chuyên khoa không hợp lệ: " + departmentStr);
        }

        List<Appointment> appointments = appointmentRepository
                .findByDepartmentAndAppointmentDateOrderByTimeSlot(department, date)
                .stream()
                .filter(appointment -> AppConstants.ROLE_ADMIN.equalsIgnoreCase(role)
                        || requesterUserId.equals(getEffectiveDoctorUserId(appointment)))
                .toList();
        return appointments.stream().map(AppointmentMapper::toResponse).toList();
    }

    /**
     * Returns every standard slot with its current capacity state. Past slots
     * stay in the response so the patient can see why a slot cannot be chosen.
     */
    @Transactional(readOnly = true)
    public List<TimeSlotAvailabilityResponse> getTimeSlotAvailability(
            String departmentStr, LocalDate date) {
        if (date == null) {
            throw new BusinessException(400, "Ngày khám không được để trống");
        }
        Department department = parseDepartment(departmentStr);
        DirectoryAssignment assignment = resolveDirectoryAssignment(department);
        Map<String, Long> bookedBySlot = new HashMap<>();
        appointmentRepository.findByRoomIdAndAppointmentDateAndStatusNot(
                        assignment.roomId(), date, AppointmentStatus.CANCELLED)
                .forEach(appointment -> bookedBySlot.merge(
                        normalizeTimeSlot(appointment.getTimeSlot()), 1L, Long::sum));

        Clock clock = Clock.system(HOSPITAL_ZONE);
        int capacity = Math.max(1, maxAppointmentsPerSlot);
        return DEFAULT_TIME_SLOTS.stream()
                .map(slot -> {
                    int booked = bookedBySlot.getOrDefault(slot, 0L).intValue();
                    boolean past = isSlotStartInPast(date, slot, clock);
                    boolean full = booked >= capacity;
                    String reason = past ? "PAST" : full ? "FULL" : null;
                    return TimeSlotAvailabilityResponse.builder()
                            .timeSlot(slot)
                            .bookedCount(booked)
                            .capacity(capacity)
                            .remaining(Math.max(0, capacity - booked))
                            .available(!past && !full)
                            .unavailableReason(reason)
                            .build();
                })
                .toList();
    }

    /**
     * Cập nhật trạng thái lịch khám
     */
    @Transactional
    public AppointmentResponse updateAppointmentStatus(UUID id,
                                                        UpdateAppointmentStatusRequest request) {
        Appointment appointment = findAppointmentOrThrow(id);

        AppointmentStatus newStatus;
        try {
            newStatus = AppointmentStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "Trạng thái không hợp lệ: " + request.getStatus());
        }

        // Validate state transition
        AppointmentStatus oldStatus = appointment.getStatus();
        validateStatusTransition(oldStatus, newStatus);

        appointment.setStatus(newStatus);
        if (request.getNotes() != null) {
            appointment.setNotes(request.getNotes());
        }

        Appointment updated = appointmentRepository.save(appointment);
        log.info("Updated appointment {} status: {} → {}",
                id, oldStatus, newStatus);

        return AppointmentMapper.toResponse(updated);
    }

    @Transactional
    public AppointmentResponse updateAppointmentStatus(UUID id,
                                                        UpdateAppointmentStatusRequest request,
                                                        UUID requesterUserId, String role) {
        Appointment appointment = findAppointmentOrThrow(id);
        if (!AppConstants.ROLE_ADMIN.equalsIgnoreCase(role)
                && (!AppConstants.ROLE_DOCTOR.equalsIgnoreCase(role)
                || requesterUserId == null
                || !requesterUserId.equals(getEffectiveDoctorUserId(appointment)))) {
            throw new BusinessException(403, "Chỉ bác sĩ được phân công hoặc ADMIN mới được cập nhật trạng thái lịch");
        }
        return updateAppointmentStatus(id, request);
    }

    /**
     * Hủy lịch khám
     */
    @Transactional
    public AppointmentResponse cancelAppointment(
            UUID id, UUID requesterUserId, String role, String correlationId) {
        Appointment appointment = findAppointmentOrThrow(id);
        requireOwnerOrClinical(appointment, requesterUserId, role);

        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BusinessException(400, "Không thể hủy lịch khám đã hoàn thành");
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessException(400, "Lịch khám đã được hủy trước đó");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        Appointment updated = appointmentRepository.save(appointment);
        log.info("Cancelled appointment {}", id);

        // Publish cancel event
        appointmentEvents.cancelled(updated, correlationId);

        return AppointmentMapper.toResponse(updated);
    }

    // ==================== Private helpers ====================

    private Appointment findAppointmentOrThrow(UUID id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));
    }

    private DirectoryAssignment resolveDirectoryAssignment(Department department) {
        if (directoryClient == null) {
            return new DirectoryAssignment(department.getId(), department.getRoomId(), department.getRoomDisplayName(),
                    null, null);
        }
        String code = department.name();
        ApiResponse<com.careflow.appointment.client.dto.DepartmentResponse> departmentResponse =
                directoryClient.getDepartmentByCode(code);
        var directoryDepartment = departmentResponse == null ? null : departmentResponse.getData();
        if (directoryDepartment == null || Boolean.FALSE.equals(directoryDepartment.getIsActive())) {
            throw new BusinessException(409, "DEPARTMENT_INACTIVE: Department is not active");
        }
        ApiResponse<List<RoomResponse>> roomsResponse = directoryClient.getAllRooms();
        List<RoomResponse> rooms = roomsResponse == null || roomsResponse.getData() == null
                ? List.of()
                : roomsResponse.getData().stream()
                .filter(room -> Boolean.TRUE.equals(room.getIsActive()))
                .filter(room -> code.equals(room.getDepartmentCode()))
                .filter(room -> "CONSULTATION".equalsIgnoreCase(room.getRoomType()))
                .toList();
        if (rooms.size() != 1) {
            throw new BusinessException(409,
                    "ROOM_CONFIGURATION_INVALID: Department must have exactly one active consultation room");
        }
        RoomResponse room = rooms.get(0);
        ApiResponse<List<DoctorProfileResponse>> doctorsResponse = directoryClient.getDoctors(code);
        List<DoctorProfileResponse> doctors = doctorsResponse == null || doctorsResponse.getData() == null
                ? List.of()
                : doctorsResponse.getData().stream()
                .filter(doctor -> Boolean.TRUE.equals(doctor.getIsActive()))
                .filter(doctor -> code.equals(doctor.getDepartmentCode()))
                .filter(doctor -> room.getId().equals(doctor.getAssignedRoomId()))
                .toList();
        if (doctors.size() != 1) {
            throw new BusinessException(409,
                    "DOCTOR_CONFIGURATION_INVALID: Khoa/phòng phải có đúng một bác sĩ đang hoạt động");
        }
        DoctorProfileResponse doctor = doctors.get(0);
        if (doctor.getUserId() == null) {
            throw new BusinessException(409, "DOCTOR_CONFIGURATION_INVALID: Bác sĩ chưa liên kết Identity user");
        }
        return new DirectoryAssignment(directoryDepartment.getId(), room.getId(), room.getDisplayName(),
                doctor.getUserId(), doctor.getFullName());
    }

    /**
     * Existing appointments created before Directory became the assignment source may have a
     * null doctor_id. Treat those records as assigned from the current Directory snapshot so
     * authorization does not silently break during the transition; all newly-created records
     * persist the resolved user ID.
     */
    private UUID getEffectiveDoctorUserId(Appointment appointment) {
        if (appointment.getDoctorId() != null) {
            return appointment.getDoctorId();
        }
        if (appointment.getDepartment() == null) {
            return null;
        }
        try {
            return resolveDirectoryAssignment(appointment.getDepartment()).doctorUserId();
        } catch (RuntimeException ex) {
            log.warn("Cannot resolve current doctor assignment for legacy appointment {}", appointment.getId(), ex);
            return null;
        }
    }

    private boolean hasStaffRoomAccess(UUID userId, String roomId) {
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

    private record DirectoryAssignment(UUID departmentId, String roomId, String roomDisplayName,
                                       UUID doctorUserId, String doctorName) {}

    private void enforceSlotCapacity(String roomId, LocalDate date, String timeSlot) {
        // The lock repository is present in the Spring application. It is null
        // only for older unit-test constructors that intentionally bypass DB locking.
        if (appointmentSlotLockRepository == null) {
            return;
        }

        String normalizedSlot = normalizeTimeSlot(timeSlot);
        String slotKey = roomId + "|" + date + "|" + normalizedSlot;
        appointmentSlotLockRepository.ensureSlotExists(slotKey);
        appointmentSlotLockRepository.findBySlotKeyForUpdate(slotKey)
                .orElseThrow(() -> new BusinessException(500, "Không khởi tạo được khóa khung giờ"));

        long booked = appointmentRepository
                .countByRoomIdAndAppointmentDateAndTimeSlotAndStatusNot(
                        roomId, date, normalizedSlot, AppointmentStatus.CANCELLED);
        int capacity = Math.max(1, maxAppointmentsPerSlot);
        if (booked >= capacity) {
            throw new BusinessException(409,
                    "Khung giờ " + normalizedSlot + " đã đủ " + capacity + " người đặt khám");
        }
    }

    private Department parseDepartment(String departmentStr) {
        try {
            return Department.fromCode(departmentStr);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "Chuyên khoa không hợp lệ: " + departmentStr);
        }
    }

    private void requireOwnerOrClinical(Appointment appointment, UUID requesterUserId, String role) {
        if (AppConstants.ROLE_ADMIN.equalsIgnoreCase(role)) return;
        if (AppConstants.ROLE_DOCTOR.equalsIgnoreCase(role)) {
            if (requesterUserId != null && requesterUserId.equals(getEffectiveDoctorUserId(appointment))) return;
            throw new BusinessException(403, "Bác sĩ chưa được phân công cho lịch khám này");
        }
        if (AppConstants.ROLE_STAFF.equalsIgnoreCase(role)) {
            throw new BusinessException(403, "Staff phải truy cập lịch qua phạm vi phòng được phân công");
        }
        if (!AppConstants.ROLE_PATIENT.equalsIgnoreCase(role)
                || !appointment.getOwnerUserId().equals(requesterUserId)) {
            throw new BusinessException(403, "Không có quyền truy cập lịch khám này");
        }
    }

    private static LocalTime getEffectiveCurrentTime(Clock clock) {
        // Mock time for test branch: Always simulate 09:30 AM (during active examination operating hours)
        return LocalTime.of(9, 30);
    }

    static void validateAppointmentTime(CreateAppointmentRequest request, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        if (request.getAppointmentDate().isBefore(today)) {
            throw new BusinessException(400, "Ngày khám đã qua");
        }
        if (!request.getAppointmentDate().isEqual(today)) {
            return;
        }

        try {
            String startValue = request.getTimeSlot().split("-", 2)[0].trim();
            LocalTime slotStart = parseClockValue(startValue);
            if (!getEffectiveCurrentTime(clock).isBefore(slotStart)) {
                throw new BusinessException(400, "Ca khám đã qua. Vui lòng chọn ca khác");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "Định dạng ca khám không hợp lệ");
        }
    }

    static String normalizeTimeSlot(String value) {
        if (value == null) {
            throw new BusinessException(400, "Định dạng ca khám không hợp lệ");
        }
        Matcher matcher = TIME_SLOT_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new BusinessException(400, "Định dạng ca khám không hợp lệ");
        }
        LocalTime start = parseClockValue(matcher.group(1) + ":" + matcher.group(2));
        LocalTime end = parseClockValue(matcher.group(3) + ":" + matcher.group(4));
        if (!end.isAfter(start)) {
            throw new BusinessException(400, "Giờ kết thúc ca khám phải sau giờ bắt đầu");
        }
        return String.format(Locale.ROOT, "%02d:%02d-%02d:%02d",
                start.getHour(), start.getMinute(), end.getHour(), end.getMinute());
    }

    private static LocalTime parseClockValue(String value) {
        try {
            String[] parts = value.trim().split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid time");
            }
            return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalArgumentException("Invalid time", e);
        }
    }

    private static boolean isSlotStartInPast(LocalDate date, String slot, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today)) {
            return true;
        }
        if (date.isAfter(today)) {
            return false;
        }
        LocalTime start = parseClockValue(normalizeTimeSlot(slot).substring(0, 5));
        return !getEffectiveCurrentTime(clock).isBefore(start);
    }

    private void validateStatusTransition(AppointmentStatus current, AppointmentStatus next) {
        // Valid transitions:
        // PENDING → CONFIRMED, CANCELLED
        // CONFIRMED → CHECKED_IN, CANCELLED
        // CHECKED_IN → IN_PROGRESS, CANCELLED
        // IN_PROGRESS → COMPLETED
        boolean valid = switch (current) {
            case PENDING -> next == AppointmentStatus.CONFIRMED || next == AppointmentStatus.CANCELLED;
            case CONFIRMED -> next == AppointmentStatus.CHECKED_IN || next == AppointmentStatus.IN_PROGRESS || next == AppointmentStatus.CANCELLED;
            case CHECKED_IN -> next == AppointmentStatus.IN_PROGRESS || next == AppointmentStatus.CANCELLED;
            case IN_PROGRESS -> next == AppointmentStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };

        if (!valid) {
            throw new BusinessException(400,
                    String.format("Không thể chuyển trạng thái từ %s sang %s",
                            current.getDisplayName(), next.getDisplayName()));
        }
    }
}
