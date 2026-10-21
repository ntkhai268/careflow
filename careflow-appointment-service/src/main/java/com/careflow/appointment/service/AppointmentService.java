package com.careflow.appointment.service;

import com.careflow.appointment.client.DirectoryClient;
import com.careflow.appointment.client.AppointmentConsultationClient;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AppointmentService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.careflow.common.security.PatientOwnershipClient currentOwners;
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
    private final AppointmentConsultationClient consultations;
    private final RoomScheduleService schedules;
    private final AppointmentCommandStore commands;

    @org.springframework.beans.factory.annotation.Value("${appointment.max-appointments-per-slot:5}")
    private int maxAppointmentsPerSlot = 5;

    private final Map<String, AppointmentResponse> idempotencyStore = new java.util.concurrent.ConcurrentHashMap<>();

    public AppointmentService(AppointmentRepository appointmentRepository,
                              AppointmentEventService appointmentEvents,
                              DirectoryClient directoryClient,
                              PatientClient patientClient,
                              AppointmentSlotLockRepository appointmentSlotLockRepository,
                              AppointmentConsultationClient consultations) {
        this(appointmentRepository,appointmentEvents,directoryClient,patientClient,appointmentSlotLockRepository,consultations,null,null);
    }

    @Autowired
    public AppointmentService(AppointmentRepository appointmentRepository,AppointmentEventService appointmentEvents,
                              DirectoryClient directoryClient,PatientClient patientClient,
                              AppointmentSlotLockRepository appointmentSlotLockRepository,AppointmentConsultationClient consultations,
                              RoomScheduleService schedules,AppointmentCommandStore commands) {
        this.schedules=schedules;this.commands=commands;
        this.appointmentRepository = appointmentRepository;
        this.appointmentEvents = appointmentEvents;
        this.directoryClient = directoryClient;
        this.patientClient = patientClient;
        this.appointmentSlotLockRepository = appointmentSlotLockRepository;
        this.consultations = Objects.requireNonNull(consultations);
    }

    /**
     * Đặt lịch khám mới (hỗ trợ Idempotency-Key)
     */
    @Transactional
    public AppointmentResponse bookAppointment(CreateAppointmentRequest request, UUID actorUserId,
                                                String role, String correlationId, String idempotencyKey) {
        if (actorUserId == null) throw new BusinessException(401, "Authenticated user is required");
        if (!AppConstants.ROLE_PATIENT.equals(role) && !AppConstants.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(403, "Không có quyền đặt lịch khám");
        }
        ApiResponse<PatientClient.BookingContext> response;
        try {
            response = patientClient.getBookingContext(request.getPatientId());
        } catch (feign.FeignException exception) {
            int status = exception.status();
            throw new BusinessException(status == 401 || status == 403 || status == 404 ? status : 503,
                    "Không thể xác minh hồ sơ đặt lịch");
        }
        var patient = response == null || response.getStatus() != 200 ? null : response.getData();
        if (patient == null || !request.getPatientId().equals(patient.id())
                || patient.userId() == null || patient.fullName() == null || patient.fullName().isBlank()) {
            throw new BusinessException(503, "Patient Service không trả về hồ sơ đặt lịch hợp lệ");
        }
        if (AppConstants.ROLE_PATIENT.equals(role) && !actorUserId.equals(patient.userId())) {
            throw new BusinessException(403, "Không có quyền đặt lịch cho hồ sơ này");
        }
        request.setPatientName(patient.fullName());
        return createAppointment(request, patient.userId(), correlationId,
                idempotencyKey==null || idempotencyKey.isBlank()?null:actorUserId+":"+idempotencyKey);
    }

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
        AppointmentCommandStore.Command command=commands==null?null:commands.begin(ownerUserId,idempotencyKey,request,assignedDoctorId);
        if(command!=null && command.result()!=null)return AppointmentMapper.toResponse(findAppointmentOrThrow(command.result()));
        String cacheKey = ownerUserId + ":" + idempotencyKey;
        if (commands==null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            AppointmentResponse cached = idempotencyStore.get(cacheKey);
            if (cached != null) {
                if (!Objects.equals(cached.getPatientId(), request.getPatientId())
                        || !Objects.equals(cached.getAppointmentDate(), request.getAppointmentDate())
                        || !Objects.equals(cached.getTimeSlot(), normalizeTimeSlot(request.getTimeSlot()))
                        || !Objects.equals(cached.getDepartment(), parseDepartment(request.getDepartment()).name())
                        || !Objects.equals(cached.getServiceCode(), request.getServiceCode()==null?"GENERAL_CONSULTATION":request.getServiceCode())
                        || (request.getPriceVersion()!=null && !Objects.equals(cached.getPriceVersion(),request.getPriceVersion()))
                        || (assignedDoctorId != null && !Objects.equals(cached.getDoctorId(), assignedDoctorId))
                        || !Objects.equals(cached.getReason(), request.getReason())) {
                    throw new BusinessException(409, "Idempotency-Key đã được dùng cho yêu cầu khác");
                }
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

        DirectoryAssignment assignment = resolveBookingAssignment(department,request.getAppointmentDate(),request.getTimeSlot(),assignedDoctorId);
        var price = requireServicePrice(request);
        enforceSlotCapacity(assignment.roomId(), request.getAppointmentDate(), request.getTimeSlot());
        Appointment appointment = Appointment.builder()
                .serviceCode(price.code()).serviceName(price.name()).priceVersion(price.version())
                .quotedAmount(price.amount()).quotedCurrency(price.currency()).priceEffectiveAt(price.effectiveAt())
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

        if(commands!=null)commands.complete(command,saved.getId());
        if (commands==null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        idempotencyStore.put(cacheKey, response);
                    }
                });
            } else {
                idempotencyStore.put(cacheKey, response);
            }
        }

        return response;
    }

    private com.careflow.appointment.client.dto.ServicePriceResponse requireServicePrice(CreateAppointmentRequest request) {
        String code=request.getServiceCode()==null?"GENERAL_CONSULTATION":request.getServiceCode();
        com.careflow.appointment.client.dto.ServicePriceResponse price;
        try {
            var response=directoryClient==null?null:directoryClient.getServicePrice(code);
            price=response==null || response.getStatus()!=200?null:response.getData();
        } catch(feign.FeignException failure) {
            throw new BusinessException(failure.status()==404 || failure.status()==409?409:503,"Không thể xác nhận bảng giá dịch vụ. Vui lòng tải lại.");
        }
        if(price==null || !code.equals(price.code()) || !price.active() || price.name()==null || price.name().isBlank()
                || price.amount()<0 || price.amount()>1000000000000L || !"VND".equals(price.currency()) || price.version()<1
                || price.effectiveAt()==null || price.effectiveAt().isAfter(java.time.Instant.now()))
            throw new BusinessException(503,"Bảng giá dịch vụ chưa sẵn sàng.");
        if(request.getPriceVersion()!=null && request.getPriceVersion()!=price.version())
            throw new BusinessException(409,"Bảng giá đã thay đổi. Vui lòng tải lại và xác nhận giá mới.");
        return price;
    }

    @Transactional
    public AppointmentResponse createFollowUpAppointment(
            com.careflow.appointment.dto.request.CreateFollowUpRequest request,
            UUID doctorUserId, String correlationId) {
        if (request.getConsultationId() == null || request.getPatientId() == null
                || request.getRecommendedDate() == null) {
            throw new BusinessException(400, "Follow-up requires consultation, patient and date");
        }
        var patient = requireFollowUpPatient(request, doctorUserId);
        String departmentCode = request.getDepartment() == null || request.getDepartment().isBlank()
                ? getClinicalContext(doctorUserId, AppConstants.ROLE_DOCTOR).getDepartment() : request.getDepartment();
        if(request.getTimeSlot()==null || request.getTimeSlot().isBlank())
            throw new BusinessException(422,"Chọn ca tái khám còn chỗ trước khi tạo lịch.");
        CreateAppointmentRequest create = CreateAppointmentRequest.builder()
                .patientId(request.getPatientId())
                .patientName(patient.fullName())
                .department(departmentCode)
                .appointmentDate(request.getRecommendedDate())
                .timeSlot(request.getTimeSlot())
                .reason(request.getNote())
                .build();
        AppointmentResponse response = createAppointment(create, patient.userId(), correlationId,
                "follow-up-" + request.getConsultationId() + "-" + request.getRecommendedDate(), doctorUserId);
        Appointment appointment = findAppointmentOrThrow(response.getId());
        if (appointment.getSourceConsultationId() != null) {
            if (!request.getConsultationId().equals(appointment.getSourceConsultationId())) {
                throw new BusinessException(409, "Appointment is linked to a different consultation");
            }
            return AppointmentMapper.toResponse(appointment);
        }
        appointment.setSourceConsultationId(request.getConsultationId());
        appointmentRepository.saveAndFlush(appointment);
        appointmentEvents.followUpScheduled(appointment, request.getConsultationId(), correlationId);
        return AppointmentMapper.toResponse(appointment);
    }

    private PatientClient.ClinicalProfile requireFollowUpPatient(
            com.careflow.appointment.dto.request.CreateFollowUpRequest request, UUID doctor) {
        if (doctor == null) throw new BusinessException(401, "Authenticated doctor is required");
        try {
            var response = consultations.getById(request.getConsultationId());
            var context = response == null || response.getStatus() != 200 ? null : response.getData();
            if (context == null || !request.getConsultationId().equals(context.id()) || context.patientId() == null
                    || context.doctorId() == null || context.status() == null
                    || !java.util.Set.of("IN_PROGRESS", "AWAITING_CLS", "AWAITING_REVIEW", "READY_TO_COMPLETE",
                    "COMPLETED", "CANCELLED", "TRANSFERRED").contains(context.status())) {
                throw new BusinessException(503, "Consultation context is incomplete");
            }
            if (!request.getPatientId().equals(context.patientId()) || !doctor.equals(context.doctorId())) {
                throw new BusinessException(403, "Follow-up must match the assigned consultation and patient");
            }
            if (java.util.Set.of("CANCELLED", "TRANSFERRED").contains(context.status())) {
                throw new BusinessException(409, "Cannot schedule follow-up from this consultation state");
            }
            var profileResponse = patientClient.getClinicalProfile(request.getPatientId());
            var patient = profileResponse == null || profileResponse.getStatus() != 200 ? null : profileResponse.getData();
            if (patient == null || !request.getPatientId().equals(patient.id()) || patient.fullName() == null || patient.fullName().isBlank()) {
                throw new BusinessException(503, "Patient context is incomplete");
            }
            if (patient.userId() == null) throw new BusinessException(422, "Patient profile has no owner user");
            return patient;
        } catch (feign.FeignException exception) {
            int status = exception.status();
            throw new BusinessException(status == 401 || status == 403 || status == 404 ? status : 503,
                    "Cannot verify follow-up clinical access");
        }
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

        ApiResponse<DoctorProfileResponse> doctorApiResponse = directoryClient.getDoctorByUserId(userId);
        DoctorProfileResponse doctor = doctorApiResponse == null ? null : doctorApiResponse.getData();
        if (doctor == null) {
            throw new ResourceNotFoundException("DoctorProfile", "userId", userId);
        }
        if (Boolean.FALSE.equals(doctor.getIsActive())) {
            throw new BusinessException(403, "Bác sĩ không còn hoạt động");
        }
        if (doctor.getDepartmentCode() == null || doctor.getDepartmentCode().isBlank()
                || doctor.getAssignedRoomId() == null || doctor.getAssignedRoomId().isBlank()) {
            throw new BusinessException(409, "ROOM_CONFIGURATION_INVALID: Bác sĩ chưa được phân công phòng khám");
        }

        ApiResponse<List<RoomResponse>> roomsApiResponse = directoryClient.getAllRooms();
        List<RoomResponse> assignedRooms = roomsApiResponse == null || roomsApiResponse.getData() == null
                ? List.of()
                : roomsApiResponse.getData().stream()
                .filter(room -> Boolean.TRUE.equals(room.getIsActive()))
                .filter(room -> doctor.getAssignedRoomId().equals(room.getId()))
                .filter(room -> doctor.getDepartmentCode().equals(room.getDepartmentCode()))
                .toList();
        if (assignedRooms.size() != 1) {
            throw new BusinessException(409,
                    "ROOM_CONFIGURATION_INVALID: Không xác định được đúng một phòng khám đang hoạt động");
        }

        RoomResponse room = assignedRooms.get(0);
        return ClinicalContextResponse.builder()
                .userId(doctor.getUserId())
                .doctorId(doctor.getUserId())
                .doctorName(doctor.getFullName())
                .department(doctor.getDepartmentCode())
                .departmentDisplayName(doctor.getDepartmentName())
                .rooms(List.of(RoomAssignmentResponse.builder()
                        .roomId(room.getId())
                        .roomDisplayName(room.getDisplayName())
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
            if(currentOwners!=null){currentOwners.require(patientId,requesterUserId);appointments=appointmentRepository.findByPatientIdOrderByAppointmentDateDesc(patientId);}
            else appointments = appointmentRepository.findByPatientIdAndOwnerUserIdOrderByAppointmentDateDesc(
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

        java.util.List<UUID> superseded = java.util.List.of();
        if (AppConstants.ROLE_DOCTOR.equalsIgnoreCase(role)) {
            try {
                var response = consultations.patientAccess(patientId);
                if (response == null || response.getData() == null || response.getData().supersededAppointmentIds() == null) {
                    throw new BusinessException(503, "Không thể xác minh trách nhiệm chăm sóc hiện tại");
                }
                if (response.getData().allowed()) return com.careflow.appointment.dto.response.AssignmentAccessResponse.allowed();
                superseded = response.getData().supersededAppointmentIds();
            } catch (feign.FeignException exception) {
                throw new BusinessException(503, "Không thể xác minh trách nhiệm chăm sóc hiện tại");
            }
        }
        final var handedOver = superseded;

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
                        return !handedOver.contains(appointment.getId()) && requesterUserId.equals(getEffectiveDoctorUserId(appointment));
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
    public List<TimeSlotAvailabilityResponse> getTimeSlotAvailability(String departmentStr,LocalDate date){
        return getTimeSlotAvailability(departmentStr,date,null);
    }

    public List<TimeSlotAvailabilityResponse> getTimeSlotAvailability(
            String departmentStr, LocalDate date, UUID doctorId) {
        if (date == null) {
            throw new BusinessException(400, "Ngày khám không được để trống");
        }
        Department department = parseDepartment(departmentStr);
        List<DirectoryAssignment> assignments=resolveDirectoryAssignments(department,doctorId);
        var capacityBySlot=new java.util.TreeMap<String,Integer>();
        var bookedBySlot=new java.util.HashMap<String,Integer>();
        for(var assignment:assignments){
            var roomBooked=new HashMap<String,Integer>();
            appointmentRepository.findByRoomIdAndAppointmentDateAndStatusNot(assignment.roomId(),date,AppointmentStatus.CANCELLED).forEach(a->roomBooked.merge(normalizeTimeSlot(a.getTimeSlot()),1,Integer::sum));
            for(var slot:roomSlots(assignment.roomId(),date)){
                capacityBySlot.merge(slot.timeSlot(),slot.capacity(),Integer::sum);
                int booked=roomBooked.getOrDefault(slot.timeSlot(),0);
                bookedBySlot.merge(slot.timeSlot(),booked,Integer::sum);
            }
        }
        Clock clock=Clock.system(HOSPITAL_ZONE);
        return capacityBySlot.entrySet().stream().map(item->{
            int booked=bookedBySlot.getOrDefault(item.getKey(),0),remaining=Math.max(0,item.getValue()-booked);
            boolean past=isSlotStartInPast(date,item.getKey(),clock);
            return TimeSlotAvailabilityResponse.builder().timeSlot(item.getKey()).bookedCount(booked).capacity(item.getValue())
                    .remaining(remaining).available(!past && remaining>0).unavailableReason(past?"PAST":remaining==0?"FULL":null).build();
        }).toList();
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
        if (oldStatus == AppointmentStatus.COMPLETED && newStatus == AppointmentStatus.COMPLETED) {
            return AppointmentMapper.toResponse(appointment);
        }
        if (oldStatus == AppointmentStatus.CONFIRMED && newStatus == AppointmentStatus.COMPLETED) {
            requireCompletedConsultation(appointment);
        } else {
            validateStatusTransition(oldStatus, newStatus);
        }

        appointment.setStatus(newStatus);
        if (request.getNotes() != null) {
            appointment.setNotes(request.getNotes());
        }

        Appointment updated = appointmentRepository.save(appointment);
        log.info("Updated appointment {} status: {} → {}",
                id, oldStatus, newStatus);

        return AppointmentMapper.toResponse(updated);
    }

    private void requireCompletedConsultation(Appointment appointment) {
        try {
            var response = consultations.getByAppointment(appointment.getId());
            if (response == null || response.getData() == null) throw new BusinessException(503, "Không thể xác minh ca khám đã hoàn tất");
            if (response.getData().stream().noneMatch(c -> appointment.getPatientId().equals(c.patientId()) && "COMPLETED".equals(c.status()))) {
                throw new BusinessException(409, "Chỉ hoàn thành lịch khi ca khám liên quan đã hoàn tất");
            }
        } catch (feign.FeignException error) {
            throw new BusinessException(error.status() == 403 || error.status() == 404 ? 403 : 503, "Không thể xác minh ca khám đã hoàn tất");
        }
    }

    @Transactional
    public AppointmentResponse updateAppointmentStatus(UUID id,
                                                        UpdateAppointmentStatusRequest request,
                                                        UUID requesterUserId, String role) {
        Appointment appointment = findAppointmentOrThrow(id);
        if (!AppConstants.ROLE_ADMIN.equalsIgnoreCase(role)
                && (!AppConstants.ROLE_DOCTOR.equalsIgnoreCase(role)
                || requesterUserId == null
                || (!requesterUserId.equals(getEffectiveDoctorUserId(appointment))
                    && !isResponsibleHandoverDoctor(appointment, request, requesterUserId)))) {
            throw new BusinessException(403, "Chỉ bác sĩ được phân công hoặc ADMIN mới được cập nhật trạng thái lịch");
        }
        return updateAppointmentStatus(id, request);
    }

    private boolean isResponsibleHandoverDoctor(Appointment appointment, UpdateAppointmentStatusRequest request, UUID actor) {
        if (!"COMPLETED".equals(request.getStatus())) return false;
        try {
            var response = consultations.getByAppointment(appointment.getId());
            if (response == null || response.getData() == null) throw new BusinessException(503, "Không thể xác minh bàn giao ca khám");
            return response.getData().stream().anyMatch(c -> actor.equals(c.doctorId())
                    && appointment.getPatientId().equals(c.patientId())
                    && java.util.Set.of("IN_PROGRESS", "READY_TO_COMPLETE", "COMPLETED").contains(c.status()));
        } catch (feign.FeignException error) {
            if (error.status() == 403 || error.status() == 404) return false;
            throw new BusinessException(503, "Không thể xác minh bàn giao ca khám");
        }
    }

    /**
     * Hủy lịch khám
     */
    @Transactional
    public AppointmentResponse cancelAppointment(
            UUID id, UUID requesterUserId, String role, String correlationId) {
        Appointment appointment = findAppointmentOrThrow(id);
        requireOwnerOrClinical(appointment, requesterUserId, role);

        if (appointment.getStatus() == AppointmentStatus.COMPLETED || appointment.getStatus()==AppointmentStatus.IN_PROGRESS) {
            throw new BusinessException(409, "Không thể hủy lịch đang khám hoặc đã hoàn thành.");
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

    private List<DirectoryAssignment> resolveDirectoryAssignments(Department department,UUID requestedDoctor) {
        if(directoryClient==null)return List.of(new DirectoryAssignment(department.getId(),department.getRoomId(),department.getRoomDisplayName(),null,null));
        try {
            var departmentResponse=directoryClient.getDepartmentByCode(department.name());
            var master=departmentResponse==null?null:departmentResponse.getData();
            if(master==null || master.getId()==null || !Boolean.TRUE.equals(master.getIsActive()))throw new BusinessException(409,"DEPARTMENT_INACTIVE: Department is not active");
            var roomsResponse=directoryClient.getAllRooms();var doctorsResponse=directoryClient.getDoctors(department.name());
            if(roomsResponse==null || roomsResponse.getData()==null || doctorsResponse==null || doctorsResponse.getData()==null)throw new BusinessException(503,"Không thể xác minh phòng/bác sĩ.");
            List<RoomResponse> rooms=roomsResponse.getData().stream().filter(room->Boolean.TRUE.equals(room.getIsActive()) && department.name().equals(room.getDepartmentCode()) && "CONSULTATION".equalsIgnoreCase(room.getRoomType())).sorted(java.util.Comparator.comparing(RoomResponse::getId)).toList();
            List<DoctorProfileResponse> assignedDoctors=doctorsResponse.getData();
            if(requestedDoctor!=null){
                var requested=directoryClient.getDoctorByUserId(requestedDoctor);var profile=requested==null?null:requested.getData();
                if(profile==null || !requestedDoctor.equals(profile.getUserId()) || !Boolean.TRUE.equals(profile.getIsActive()) || !department.name().equals(profile.getDepartmentCode()))throw new BusinessException(409,"Bác sĩ yêu cầu chưa có phân công hợp lệ.");
                assignedDoctors=List.of(profile);
            }
            var result=new java.util.ArrayList<DirectoryAssignment>();
            for(var room:rooms){
                List<DoctorProfileResponse> doctors=assignedDoctors.stream().filter(doctor->Boolean.TRUE.equals(doctor.getIsActive()) && doctor.getUserId()!=null && department.name().equals(doctor.getDepartmentCode()) && room.getId().equals(doctor.getAssignedRoomId()) && (requestedDoctor==null || requestedDoctor.equals(doctor.getUserId()))).toList();
                if(doctors.size()>1)doctors=doctors.stream().filter(d->Boolean.TRUE.equals(d.getPrimaryForBooking())).toList();
                if(doctors.size()==1){var doctor=doctors.getFirst();result.add(new DirectoryAssignment(master.getId(),room.getId(),room.getDisplayName(),doctor.getUserId(),doctor.getFullName()));}
            }
            if(result.isEmpty())throw new BusinessException(409,"DOCTOR_CONFIGURATION_INVALID: Chưa có phòng hoạt động với đúng một bác sĩ nhận lịch.");
            return result;
        }catch(feign.FeignException unavailable){throw new BusinessException(503,"Không thể xác minh phân công phòng/bác sĩ.");}
    }

    private List<RoomScheduleService.Slot> roomSlots(String roomId,LocalDate date){
        return schedules==null?RoomScheduleService.slots(RoomScheduleService.defaults(Math.max(1,maxAppointmentsPerSlot)),date):schedules.slots(roomId,date);
    }

    private DirectoryAssignment resolveBookingAssignment(Department department,LocalDate date,String slot,UUID doctor){
        var candidates=resolveDirectoryAssignments(department,doctor);
        if(schedules!=null)schedules.lockRooms(candidates.stream().map(DirectoryAssignment::roomId).toList());
        DirectoryAssignment selected=null;long best=0;
        for(var candidate:candidates){
            int capacity=roomSlots(candidate.roomId(),date).stream().filter(item->item.timeSlot().equals(slot)).mapToInt(RoomScheduleService.Slot::capacity).findFirst().orElse(0);
            long booked=appointmentRepository.countByRoomIdAndAppointmentDateAndTimeSlotAndStatusNot(candidate.roomId(),date,slot,AppointmentStatus.CANCELLED);
            long remaining=capacity-booked;
            if(remaining>best){best=remaining;selected=candidate;}
        }
        if(selected==null)throw new BusinessException(409,"Khung giờ đã đóng hoặc hết chỗ ở các phòng đủ điều kiện. Vui lòng tải lại lịch.");
        return selected;
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
            return resolveDirectoryAssignments(appointment.getDepartment(),null).stream().filter(a->a.roomId().equals(appointment.getRoomId())).map(DirectoryAssignment::doctorUserId).findFirst().orElse(null);
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
        int capacity=roomSlots(roomId,date).stream().filter(item->item.timeSlot().equals(normalizedSlot)).mapToInt(RoomScheduleService.Slot::capacity).findFirst().orElse(0);
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
        if(AppConstants.ROLE_PATIENT.equalsIgnoreCase(role) && currentOwners!=null){currentOwners.require(appointment.getPatientId(),requesterUserId);return;}
        if (!AppConstants.ROLE_PATIENT.equalsIgnoreCase(role)
                || !appointment.getOwnerUserId().equals(requesterUserId)) {
            throw new BusinessException(403, "Không có quyền truy cập lịch khám này");
        }
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
            if (!LocalTime.now(clock).isBefore(slotStart)) {
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
        return !LocalTime.now(clock).isBefore(start);
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
