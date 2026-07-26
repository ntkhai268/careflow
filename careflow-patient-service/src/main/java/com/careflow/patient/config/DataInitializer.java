package com.careflow.patient.config;

import com.careflow.patient.model.Gender;
import com.careflow.patient.model.Patient;
import com.careflow.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final PatientRepository patientRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        log.info("Checking and seeding required test patient records...");

        Patient p1 = Patient.builder()
                .userId(UUID.fromString("00000001-0000-0000-0000-000000000001"))
                .fullName("Nguyễn Thị Mai")
                .dateOfBirth(LocalDate.of(1981, 5, 12))
                .gender(Gender.FEMALE)
                .phone("0912345671")
                .idCardNumber("001181000001")
                .insuranceNumber("DN4010000000001")
                .occupation("Kế toán")
                .address("123 Nguyễn Trãi, Thanh Xuân, Hà Nội")
                .build();
        p1.setId(UUID.fromString("f0000001-0000-0000-0000-000000000001"));
        jdbcTemplate.update("DELETE FROM patients WHERE user_id = ? AND id <> ?", p1.getUserId(), p1.getId());
        if (!patientRepository.existsById(p1.getId())) {
            jdbcTemplate.update(
                "INSERT INTO patients (id, user_id, full_name, date_of_birth, gender, phone, id_card_number, insurance_number, occupation, address, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                p1.getId(), p1.getUserId(), p1.getFullName(), p1.getDateOfBirth(), p1.getGender().name(), p1.getPhone(), p1.getIdCardNumber(), p1.getInsuranceNumber(), p1.getOccupation(), p1.getAddress(), java.sql.Timestamp.from(java.time.Instant.now()), java.sql.Timestamp.from(java.time.Instant.now())
            );
            log.info("Seeded patient: Nguyễn Thị Mai");
        }

        Patient p2 = Patient.builder()
                .userId(UUID.fromString("00000001-0000-0000-0000-000000000002"))
                .fullName("Trần Văn Hùng")
                .dateOfBirth(LocalDate.of(1964, 8, 20))
                .gender(Gender.MALE)
                .phone("0912345672")
                .idCardNumber("001164000002")
                .insuranceNumber("DN4010000000002")
                .occupation("Cán bộ hưu trí")
                .address("45 Lê Lợi, Cầu Giấy, Hà Nội")
                .build();
        p2.setId(UUID.fromString("f0000001-0000-0000-0000-000000000002"));
        jdbcTemplate.update("DELETE FROM patients WHERE user_id = ? AND id <> ?", p2.getUserId(), p2.getId());
        if (!patientRepository.existsById(p2.getId())) {
            jdbcTemplate.update(
                "INSERT INTO patients (id, user_id, full_name, date_of_birth, gender, phone, id_card_number, insurance_number, occupation, address, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                p2.getId(), p2.getUserId(), p2.getFullName(), p2.getDateOfBirth(), p2.getGender().name(), p2.getPhone(), p2.getIdCardNumber(), p2.getInsuranceNumber(), p2.getOccupation(), p2.getAddress(), java.sql.Timestamp.from(java.time.Instant.now()), java.sql.Timestamp.from(java.time.Instant.now())
            );
            log.info("Seeded patient: Trần Văn Hùng");
        }

        Patient p3 = Patient.builder()
                .userId(UUID.fromString("00000001-0000-0000-0000-000000000003"))
                .fullName("Lê Thị Hoa")
                .dateOfBirth(LocalDate.of(1998, 3, 15))
                .gender(Gender.FEMALE)
                .phone("0912345673")
                .idCardNumber("001198000003")
                .insuranceNumber("DN4010000000003")
                .occupation("Kỹ sư phần mềm")
                .address("88 Trần Duy Hưng, Cầu Giấy, Hà Nội")
                .build();
        p3.setId(UUID.fromString("f0000001-0000-0000-0000-000000000003"));
        jdbcTemplate.update("DELETE FROM patients WHERE user_id = ? AND id <> ?", p3.getUserId(), p3.getId());
        if (!patientRepository.existsById(p3.getId())) {
            jdbcTemplate.update(
                "INSERT INTO patients (id, user_id, full_name, date_of_birth, gender, phone, id_card_number, insurance_number, occupation, address, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                p3.getId(), p3.getUserId(), p3.getFullName(), p3.getDateOfBirth(), p3.getGender().name(), p3.getPhone(), p3.getIdCardNumber(), p3.getInsuranceNumber(), p3.getOccupation(), p3.getAddress(), java.sql.Timestamp.from(java.time.Instant.now()), java.sql.Timestamp.from(java.time.Instant.now())
            );
            log.info("Seeded patient: Lê Thị Hoa");
        }

        Patient p4 = Patient.builder()
                .userId(UUID.fromString("00000001-0000-0000-0000-000000000004"))
                .fullName("Phạm Đức Anh")
                .dateOfBirth(LocalDate.of(2019, 11, 4))
                .gender(Gender.MALE)
                .phone("0912345674")
                .idCardNumber("001219000004")
                .insuranceNumber("TE1010000000004")
                .occupation("Học sinh")
                .address("12 Giải Phóng, Hai Bà Trưng, Hà Nội")
                .build();
        p4.setId(UUID.fromString("f0000001-0000-0000-0000-000000000004"));
        jdbcTemplate.update("DELETE FROM patients WHERE user_id = ? AND id <> ?", p4.getUserId(), p4.getId());
        if (!patientRepository.existsById(p4.getId())) {
            jdbcTemplate.update(
                "INSERT INTO patients (id, user_id, full_name, date_of_birth, gender, phone, id_card_number, insurance_number, occupation, address, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                p4.getId(), p4.getUserId(), p4.getFullName(), p4.getDateOfBirth(), p4.getGender().name(), p4.getPhone(), p4.getIdCardNumber(), p4.getInsuranceNumber(), p4.getOccupation(), p4.getAddress(), java.sql.Timestamp.from(java.time.Instant.now()), java.sql.Timestamp.from(java.time.Instant.now())
            );
            log.info("Seeded patient: Phạm Đức Anh");
        }

        Patient p5 = Patient.builder()
                .userId(UUID.fromString("00000001-0000-0000-0000-000000000005"))
                .fullName("Võ Thị Lan")
                .dateOfBirth(LocalDate.of(1971, 9, 28))
                .gender(Gender.FEMALE)
                .phone("0912345675")
                .idCardNumber("001171000005")
                .insuranceNumber("DN4010000000005")
                .occupation("Giáo viên")
                .address("56 Hoàng Hoa Thám, Ba Đình, Hà Nội")
                .build();
        p5.setId(UUID.fromString("f0000001-0000-0000-0000-000000000005"));
        jdbcTemplate.update("DELETE FROM patients WHERE user_id = ? AND id <> ?", p5.getUserId(), p5.getId());
        if (!patientRepository.existsById(p5.getId())) {
            jdbcTemplate.update(
                "INSERT INTO patients (id, user_id, full_name, date_of_birth, gender, phone, id_card_number, insurance_number, occupation, address, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                p5.getId(), p5.getUserId(), p5.getFullName(), p5.getDateOfBirth(), p5.getGender().name(), p5.getPhone(), p5.getIdCardNumber(), p5.getInsuranceNumber(), p5.getOccupation(), p5.getAddress(), java.sql.Timestamp.from(java.time.Instant.now()), java.sql.Timestamp.from(java.time.Instant.now())
            );
            log.info("Seeded patient: Võ Thị Lan");
        }
    }
}
