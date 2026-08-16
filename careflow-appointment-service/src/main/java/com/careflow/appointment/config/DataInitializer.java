package com.careflow.appointment.config;

import com.careflow.appointment.model.Appointment;
import com.careflow.appointment.model.AppointmentStatus;
import com.careflow.appointment.model.Department;
import com.careflow.appointment.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AppointmentRepository appointmentRepository;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(AppointmentRepository appointmentRepository, JdbcTemplate jdbcTemplate) {
        this.appointmentRepository = appointmentRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        // Seed only an empty database. Runtime appointments must never be deleted on restart.
        long countToday = appointmentRepository.count();
        if (countToday == 0) {
            appointmentRepository.deleteAll();
            log.info("Cleared old duplicate appointments. Seeding clean 6 distinct patients for today ({}) with status CHECKED_IN...", today);

            Appointment a1 = Appointment.builder()
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000004"))
                    .ownerUserId(UUID.fromString("f0000001-0000-0000-0000-000000000004"))
                    .patientName("Phạm Đức Anh")
                    .department(Department.NOI_TONG_QUAT)
                    .departmentId(UUID.fromString("de000001-0000-0000-0000-000000000001"))
                    .roomId("ROOM-01")
                    .roomDisplayName("Phòng Khám Nội Tổng Quát 1")
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .doctorName("BS. Nguyễn Văn An")
                    .appointmentDate(today)
                    .timeSlot("08:00-08:30")
                    .status(AppointmentStatus.CHECKED_IN)
                    .reason("Sốt cao, đau họng 2 ngày (Đã thanh toán tạm ứng khám)")
                    .queueNumber("001")
                    .build();
            a1.setId(UUID.fromString("a0000001-0000-0000-0000-000000000001"));

            Appointment a2 = Appointment.builder()
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000005"))
                    .ownerUserId(UUID.fromString("f0000001-0000-0000-0000-000000000005"))
                    .patientName("Võ Thị Lan")
                    .department(Department.NOI_TONG_QUAT)
                    .departmentId(UUID.fromString("de000001-0000-0000-0000-000000000001"))
                    .roomId("ROOM-01")
                    .roomDisplayName("Phòng Khám Nội Tổng Quát 1")
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .doctorName("BS. Nguyễn Văn An")
                    .appointmentDate(today)
                    .timeSlot("08:30-09:00")
                    .status(AppointmentStatus.CHECKED_IN)
                    .reason("Tái khám đái tháo đường, kiểm tra huyết áp (Đã thanh toán tạm ứng khám)")
                    .queueNumber("002")
                    .build();
            a2.setId(UUID.fromString("a0000001-0000-0000-0000-000000000002"));

            Appointment a3 = Appointment.builder()
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000003"))
                    .ownerUserId(UUID.fromString("f0000001-0000-0000-0000-000000000003"))
                    .patientName("Lê Thị Hoa")
                    .department(Department.NOI_TONG_QUAT)
                    .departmentId(UUID.fromString("de000001-0000-0000-0000-000000000001"))
                    .roomId("ROOM-01")
                    .roomDisplayName("Phòng Khám Nội Tổng Quát 1")
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .doctorName("BS. Nguyễn Văn An")
                    .appointmentDate(today)
                    .timeSlot("09:00-09:30")
                    .status(AppointmentStatus.CHECKED_IN)
                    .reason("Đau dạ dày, ợ chua sau ăn (Đã thanh toán tạm ứng khám)")
                    .queueNumber("003")
                    .build();
            a3.setId(UUID.fromString("a0000001-0000-0000-0000-000000000003"));

            Appointment a4 = Appointment.builder()
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000001"))
                    .ownerUserId(UUID.fromString("f0000001-0000-0000-0000-000000000001"))
                    .patientName("Nguyễn Thị Mai")
                    .department(Department.NOI_TONG_QUAT)
                    .departmentId(UUID.fromString("de000001-0000-0000-0000-000000000001"))
                    .roomId("ROOM-01")
                    .roomDisplayName("Phòng Khám Nội Tổng Quát 1")
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .doctorName("BS. Nguyễn Văn An")
                    .appointmentDate(today)
                    .timeSlot("09:30-10:00")
                    .status(AppointmentStatus.CHECKED_IN)
                    .reason("Khám sức khỏe định kỳ (Đã thanh toán tạm ứng khám)")
                    .queueNumber("004")
                    .build();
            a4.setId(UUID.fromString("a0000001-0000-0000-0000-000000000007"));

            Appointment a5 = Appointment.builder()
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000002"))
                    .ownerUserId(UUID.fromString("f0000001-0000-0000-0000-000000000002"))
                    .patientName("Trần Văn Hùng")
                    .department(Department.NOI_TONG_QUAT)
                    .departmentId(UUID.fromString("de000001-0000-0000-0000-000000000001"))
                    .roomId("ROOM-01")
                    .roomDisplayName("Phòng Khám Nội Tổng Quát 1")
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .doctorName("BS. Nguyễn Văn An")
                    .appointmentDate(today)
                    .timeSlot("10:00-10:30")
                    .status(AppointmentStatus.CHECKED_IN)
                    .reason("Đau khớp gối khi vận động (Đã thanh toán tạm ứng khám)")
                    .queueNumber("005")
                    .build();
            a5.setId(UUID.fromString("a0000001-0000-0000-0000-000000000008"));

            Appointment a6 = Appointment.builder()
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000006"))
                    .ownerUserId(UUID.fromString("f0000001-0000-0000-0000-000000000006"))
                    .patientName("Đặng Hoàng Long")
                    .department(Department.NOI_TONG_QUAT)
                    .departmentId(UUID.fromString("de000001-0000-0000-0000-000000000001"))
                    .roomId("ROOM-01")
                    .roomDisplayName("Phòng Khám Nội Tổng Quát 1")
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .doctorName("BS. Nguyễn Văn An")
                    .appointmentDate(today)
                    .timeSlot("10:30-11:00")
                    .status(AppointmentStatus.CHECKED_IN)
                    .reason("Khám sưng đau cổ chân do đá bóng (Đã thanh toán tạm ứng khám)")
                    .queueNumber("006")
                    .build();
            a6.setId(UUID.fromString("a0000001-0000-0000-0000-000000000009"));

            List.of(a1, a2, a3, a4, a5, a6).forEach(this::insertFixture);
            log.info("Successfully seeded 6 confirmed patients waiting for examination.");
        }
    }

    private void insertFixture(Appointment appointment) {
        jdbcTemplate.update("""
                        INSERT INTO appointments (
                            id, patient_id, owner_user_id, patient_name, department, department_id,
                            room_id, room_display_name, doctor_id, doctor_name, appointment_date,
                            time_slot, status, reason, queue_number
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                appointment.getId(), appointment.getPatientId(), appointment.getOwnerUserId(),
                appointment.getPatientName(), appointment.getDepartment().name(), appointment.getDepartmentId(),
                appointment.getRoomId(), appointment.getRoomDisplayName(), appointment.getDoctorId(),
                appointment.getDoctorName(), appointment.getAppointmentDate(), appointment.getTimeSlot(),
                appointment.getStatus().name(), appointment.getReason(), appointment.getQueueNumber());
    }
}
