package com.careflow.appointment.config;

import com.careflow.appointment.model.Appointment;
import com.careflow.appointment.model.AppointmentStatus;
import com.careflow.appointment.model.Department;
import com.careflow.appointment.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AppointmentRepository appointmentRepository;

    public DataInitializer(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @Override
    public void run(String... args) {
        LocalDate today = LocalDate.now();
        if (appointmentRepository.findByDepartmentAndAppointmentDateOrderByTimeSlot(Department.NOI_TONG_QUAT, today).isEmpty()) {
            log.info("Seeding 5 patients into local appointment queue for today ({}) with status CONFIRMED (Paid & Waiting)...", today);

            Appointment a1 = Appointment.builder()
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000004"))
                    .patientName("Phạm Đức Anh")
                    .department(Department.NOI_TONG_QUAT)
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .doctorName("BS. Nguyễn Văn An")
                    .appointmentDate(today)
                    .timeSlot("08:00-08:30")
                    .status(AppointmentStatus.CONFIRMED)
                    .reason("Sốt cao, đau họng 2 ngày (Đã thanh toán tạm ứng khám)")
                    .queueNumber("001")
                    .build();

            Appointment a2 = Appointment.builder()
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000005"))
                    .patientName("Võ Thị Lan")
                    .department(Department.NOI_TONG_QUAT)
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .doctorName("BS. Nguyễn Văn An")
                    .appointmentDate(today)
                    .timeSlot("08:30-09:00")
                    .status(AppointmentStatus.CONFIRMED)
                    .reason("Tái khám đái tháo đường, kiểm tra huyết áp (Đã thanh toán tạm ứng khám)")
                    .queueNumber("002")
                    .build();

            Appointment a3 = Appointment.builder()
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000003"))
                    .patientName("Lê Thị Hoa")
                    .department(Department.NOI_TONG_QUAT)
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .doctorName("BS. Nguyễn Văn An")
                    .appointmentDate(today)
                    .timeSlot("09:00-09:30")
                    .status(AppointmentStatus.CONFIRMED)
                    .reason("Đau dạ dày, ợ chua sau ăn (Đã thanh toán tạm ứng khám)")
                    .queueNumber("003")
                    .build();

            Appointment a4 = Appointment.builder()
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000001"))
                    .patientName("Nguyễn Thị Mai")
                    .department(Department.NOI_TONG_QUAT)
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .doctorName("BS. Nguyễn Văn An")
                    .appointmentDate(today)
                    .timeSlot("09:30-10:00")
                    .status(AppointmentStatus.CONFIRMED)
                    .reason("Khám sức khỏe định kỳ (Đã thanh toán tạm ứng khám)")
                    .queueNumber("004")
                    .build();

            Appointment a5 = Appointment.builder()
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000002"))
                    .patientName("Trần Văn Hùng")
                    .department(Department.NOI_TONG_QUAT)
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .doctorName("BS. Nguyễn Văn An")
                    .appointmentDate(today)
                    .timeSlot("10:00-10:30")
                    .status(AppointmentStatus.CONFIRMED)
                    .reason("Đau khớp gối khi vận động (Đã thanh toán tạm ứng khám)")
                    .queueNumber("005")
                    .build();

            Appointment a6 = Appointment.builder()
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000006"))
                    .patientName("Đặng Hoàng Long")
                    .department(Department.NOI_TONG_QUAT)
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .doctorName("BS. Nguyễn Văn An")
                    .appointmentDate(today)
                    .timeSlot("10:30-11:00")
                    .status(AppointmentStatus.CONFIRMED)
                    .reason("Khám sưng đau cổ chân do đá bóng (Đã thanh toán tạm ứng khám)")
                    .queueNumber("006")
                    .build();

            appointmentRepository.saveAll(List.of(a1, a2, a3, a4, a5, a6));
            log.info("Successfully seeded 6 confirmed patients waiting for examination.");
        }
    }
}
