package com.careflow.patient.config;

import com.careflow.patient.model.AllergySeverity;
import com.careflow.patient.model.Gender;
import com.careflow.patient.model.Patient;
import com.careflow.patient.model.PatientAllergy;
import com.careflow.patient.repository.PatientAllergyRepository;
import com.careflow.patient.repository.PatientRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final PatientRepository patientRepository;
    private final PatientAllergyRepository patientAllergyRepository;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(PatientRepository patientRepository, PatientAllergyRepository patientAllergyRepository, JdbcTemplate jdbcTemplate) {
        this.patientRepository = patientRepository;
        this.patientAllergyRepository = patientAllergyRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

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
                .allergyNotes("Dị ứng Penicillin, hải sản (tôm, cua)")
                .medicalHistory("Tiền sử viêm dạ dày HP (+), trào ngược dạ dày K21.9")
                .build();
        p1.setId(UUID.fromString("f0000001-0000-0000-0000-000000000001"));
        if (!patientRepository.existsById(p1.getId())
                && !patientRepository.existsByUserId(p1.getUserId())) {
            jdbcTemplate.update(
                    "INSERT INTO patients (id, user_id, full_name, date_of_birth, gender, phone, id_card_number, insurance_number, occupation, address, allergy_notes, medical_history, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    p1.getId(), p1.getUserId(), p1.getFullName(), p1.getDateOfBirth(), p1.getGender().name(),
                    p1.getPhone(), p1.getIdCardNumber(), p1.getInsuranceNumber(), p1.getOccupation(), p1.getAddress(),
                    p1.getAllergyNotes(), p1.getMedicalHistory(), java.sql.Timestamp.from(java.time.Instant.now()),
                    java.sql.Timestamp.from(java.time.Instant.now()));
            log.info("Seeded patient: Nguyễn Thị Mai");
        } else {
            updateExistingProfile(p1);
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
                .allergyNotes("Không có ghi nhận dị ứng")
                .medicalHistory("Tăng huyết áp 5 năm, Thoái hóa khớp gối M17.9")
                .build();
        p2.setId(UUID.fromString("f0000001-0000-0000-0000-000000000002"));
        if (!patientRepository.existsById(p2.getId())
                && !patientRepository.existsByUserId(p2.getUserId())) {
            jdbcTemplate.update(
                    "INSERT INTO patients (id, user_id, full_name, date_of_birth, gender, phone, id_card_number, insurance_number, occupation, address, allergy_notes, medical_history, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    p2.getId(), p2.getUserId(), p2.getFullName(), p2.getDateOfBirth(), p2.getGender().name(),
                    p2.getPhone(), p2.getIdCardNumber(), p2.getInsuranceNumber(), p2.getOccupation(), p2.getAddress(),
                    p2.getAllergyNotes(), p2.getMedicalHistory(), java.sql.Timestamp.from(java.time.Instant.now()),
                    java.sql.Timestamp.from(java.time.Instant.now()));
            log.info("Seeded patient: Trần Văn Hùng");
        } else {
            updateExistingProfile(p2);
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
                .allergyNotes("Dị ứng Aspirin, NSAID")
                .medicalHistory("Đau dạ dày, ợ chua sau ăn K29.7")
                .build();
        p3.setId(UUID.fromString("f0000001-0000-0000-0000-000000000003"));
        if (!patientRepository.existsById(p3.getId())
                && !patientRepository.existsByUserId(p3.getUserId())) {
            jdbcTemplate.update(
                    "INSERT INTO patients (id, user_id, full_name, date_of_birth, gender, phone, id_card_number, insurance_number, occupation, address, allergy_notes, medical_history, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    p3.getId(), p3.getUserId(), p3.getFullName(), p3.getDateOfBirth(), p3.getGender().name(),
                    p3.getPhone(), p3.getIdCardNumber(), p3.getInsuranceNumber(), p3.getOccupation(), p3.getAddress(),
                    p3.getAllergyNotes(), p3.getMedicalHistory(), java.sql.Timestamp.from(java.time.Instant.now()),
                    java.sql.Timestamp.from(java.time.Instant.now()));
            log.info("Seeded patient: Lê Thị Hoa");
        } else {
            updateExistingProfile(p3);
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
                .allergyNotes("CẢNH BÁO: Dị ứng Penicillin (nổi mề đai), dị ứng phấn hoa")
                .medicalHistory("Tiền sử sốt co giật hồi 2 tuổi, viêm họng cấp J00 nhiều đợt")
                .build();
        p4.setId(UUID.fromString("f0000001-0000-0000-0000-000000000004"));
        if (!patientRepository.existsById(p4.getId())
                && !patientRepository.existsByUserId(p4.getUserId())) {
            jdbcTemplate.update(
                    "INSERT INTO patients (id, user_id, full_name, date_of_birth, gender, phone, id_card_number, insurance_number, occupation, address, allergy_notes, medical_history, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    p4.getId(), p4.getUserId(), p4.getFullName(), p4.getDateOfBirth(), p4.getGender().name(),
                    p4.getPhone(), p4.getIdCardNumber(), p4.getInsuranceNumber(), p4.getOccupation(), p4.getAddress(),
                    p4.getAllergyNotes(), p4.getMedicalHistory(), java.sql.Timestamp.from(java.time.Instant.now()),
                    java.sql.Timestamp.from(java.time.Instant.now()));
            log.info("Seeded patient: Phạm Đức Anh");
        } else {
            updateExistingProfile(p4);
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
                .allergyNotes("Không có dị ứng thuốc")
                .medicalHistory("Đái tháo đường type 2 E11.9, kiểm tra định kỳ")
                .build();
        p5.setId(UUID.fromString("f0000001-0000-0000-0000-000000000005"));
        if (!patientRepository.existsById(p5.getId())
                && !patientRepository.existsByUserId(p5.getUserId())) {
            jdbcTemplate.update(
                    "INSERT INTO patients (id, user_id, full_name, date_of_birth, gender, phone, id_card_number, insurance_number, occupation, address, allergy_notes, medical_history, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    p5.getId(), p5.getUserId(), p5.getFullName(), p5.getDateOfBirth(), p5.getGender().name(),
                    p5.getPhone(), p5.getIdCardNumber(), p5.getInsuranceNumber(), p5.getOccupation(), p5.getAddress(),
                    p5.getAllergyNotes(), p5.getMedicalHistory(), java.sql.Timestamp.from(java.time.Instant.now()),
                    java.sql.Timestamp.from(java.time.Instant.now()));
            log.info("Seeded patient: Võ Thị Lan");
        } else {
            updateExistingProfile(p5);
        }

        Patient p6 = Patient.builder()
                .userId(UUID.fromString("00000001-0000-0000-0000-000000000006"))
                .fullName("Đặng Hoàng Long")
                .dateOfBirth(LocalDate.of(1992, 12, 10))
                .gender(Gender.MALE)
                .phone("0912345676")
                .idCardNumber("001192000006")
                .insuranceNumber("DN4010000000006")
                .occupation("Kiến trúc sư")
                .address("102 Nguyễn Chí Thanh, Đống Đa, Hà Nội")
                .allergyNotes("Không ghi nhận")
                .medicalHistory("Chấn thương dây chằng cổ chân nhẹ 2024")
                .build();
        p6.setId(UUID.fromString("f0000001-0000-0000-0000-000000000006"));
        if (!patientRepository.existsById(p6.getId())
                && !patientRepository.existsByUserId(p6.getUserId())) {
            jdbcTemplate.update(
                    "INSERT INTO patients (id, user_id, full_name, date_of_birth, gender, phone, id_card_number, insurance_number, occupation, address, allergy_notes, medical_history, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    p6.getId(), p6.getUserId(), p6.getFullName(), p6.getDateOfBirth(), p6.getGender().name(),
                    p6.getPhone(), p6.getIdCardNumber(), p6.getInsuranceNumber(), p6.getOccupation(), p6.getAddress(),
                    p6.getAllergyNotes(), p6.getMedicalHistory(), java.sql.Timestamp.from(java.time.Instant.now()),
                    java.sql.Timestamp.from(java.time.Instant.now()));
            log.info("Seeded patient: Đặng Hoàng Long");
        } else {
            updateExistingProfile(p6);
        }

        // --- Seed Structured Patient Allergy Records ---
        p1 = existingOrSeed(p1);
        p3 = existingOrSeed(p3);
        p4 = existingOrSeed(p4);
        seedPatientAllergies(p4, "Penicillin", "Beta-lactam", AllergySeverity.CRITICAL, "Nổi mề đai, sưng môi, nguy cơ sốc phản vệ", "BS. Nguyễn Văn An");
        seedPatientAllergies(p4, "Phấn hoa", "Environment", AllergySeverity.WARNING, "Hắt hơi, ngứa mũi, chảy nước mắt", "Khai báo bệnh nhân");

        seedPatientAllergies(p1, "Penicillin", "Beta-lactam", AllergySeverity.CRITICAL, "Nổi mề đai nặng toàn thân", "BS. Trần Đức Minh");
        seedPatientAllergies(p1, "Hải sản (tôm, cua)", "Food", AllergySeverity.WARNING, "Mẩn ngứa da ngực", "Khai báo bệnh nhân");

        seedPatientAllergies(p3, "Aspirin / NSAID", "NSAID", AllergySeverity.CRITICAL, "Đau bụng cấp, sưng phù nếp mi", "BS. Lê Hoàng");
    }

    private void updateExistingProfile(Patient seed) {
        Patient existing = existingOrSeed(seed);
        jdbcTemplate.update("UPDATE patients SET allergy_notes = ?, medical_history = ? WHERE id = ?",
                seed.getAllergyNotes(), seed.getMedicalHistory(), existing.getId());
    }

    private Patient existingOrSeed(Patient seed) {
        return patientRepository.findFirstByUserIdOrderByCreatedAtAsc(seed.getUserId()).orElse(seed);
    }

    private void seedPatientAllergies(Patient patient, String name, String group, AllergySeverity severity, String reaction, String confirmedBy) {
        boolean exists = patientAllergyRepository.findByPatientId(patient.getId()).stream()
                .anyMatch(a -> a.getAllergyName().equalsIgnoreCase(name));
        if (!exists) {
            PatientAllergy allergy = PatientAllergy.builder()
                    .patient(patient)
                    .allergyName(name)
                    .allergyGroup(group)
                    .severity(severity)
                    .reaction(reaction)
                    .confirmedBy(confirmedBy)
                    .build();
            patientAllergyRepository.save(allergy);
            log.info("Seeded structured allergy [{}] severity [{}] for patient [{}]", name, severity, patient.getFullName());
        }
    }
}
