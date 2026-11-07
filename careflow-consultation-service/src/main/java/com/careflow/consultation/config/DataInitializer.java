package com.careflow.consultation.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Slf4j
@org.springframework.context.annotation.Profile("demo | test")
public class DataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("Checking historical consultation records for test patients...");
        alignStatusConstraint();

        UUID c1Id = UUID.fromString("c0000001-0000-0000-0000-000000000001");
        Integer c1Count = jdbcTemplate.queryForObject("SELECT count(*) FROM consultations WHERE id=?", Integer.class, c1Id);
        if (c1Count == null || c1Count == 0) {
            jdbcTemplate.update("""
                INSERT INTO consultations (
                    id, patient_id, patient_name, doctor_id, doctor_name, department_code, department_name,
                    appointment_id, temperature, blood_pressure, heart_rate, spo2, height, weight,
                    symptoms, clinical_notes, icd10_code, icd10_name, diagnosis, status,
                    started_at, completed_at, version, created_at, updated_at
                ) VALUES (
                    ?, ?, 'Phạm Đức Anh', ?, 'BS. CKI Nguyễn Văn An', 'NOI_TONG_QUAT', 'Khoa Nội Tổng Quát',
                    ?, 38.2, '110/70', 95, 98, 110.0, 18.5,
                    'Sốt 38.2C, ho hắng nhẹ, đau họng khi nuốt 2 ngày',
                    'Họng đỏ, sưng nhẹ 2 amydal, phổi thông khí rõ không rale',
                    'J00', 'Viêm mũi họng cấp (cảm thường)', 'Viêm mũi họng cấp / Sốt siêu vi', 'COMPLETED',
                    now() - interval '2 hours', now() - interval '1 hour', 0, now(), now()
                )
                """,
                c1Id,
                UUID.fromString("f0000001-0000-0000-0000-000000000004"),
                UUID.fromString("d0000001-0000-0000-0000-000000000001"),
                UUID.fromString("a0000001-0000-0000-0000-000000000001")
            );
            log.info("Seeded historical completed consultation for Phạm Đức Anh via JdbcTemplate");
        }

        UUID c2Id = UUID.fromString("c0000001-0000-0000-0000-000000000002");
        Integer c2Count = jdbcTemplate.queryForObject("SELECT count(*) FROM consultations WHERE id=?", Integer.class, c2Id);
        if (c2Count == null || c2Count == 0) {
            jdbcTemplate.update("""
                INSERT INTO consultations (
                    id, patient_id, patient_name, doctor_id, doctor_name, department_code, department_name,
                    appointment_id, temperature, blood_pressure, heart_rate, spo2, height, weight,
                    symptoms, clinical_notes, icd10_code, icd10_name, diagnosis, status,
                    started_at, completed_at, version, created_at, updated_at
                ) VALUES (
                    ?, ?, 'Nguyễn Thị Mai', ?, 'BS. CKI Nguyễn Văn An', 'NOI_TONG_QUAT', 'Khoa Nội Tổng Quát',
                    ?, 36.6, '125/82', 76, 99, 158.0, 54.0,
                    'Ợ chua, nóng rát vùng sau xương ức, đau thượng vị nhẹ sau ăn',
                    'Ấn đau nhẹ vùng thượng vị, không phản ứng thành bụng',
                    'K21.9', 'Bệnh trào ngược dạ dày - thực quản không có viêm thực quản', 'Trào ngược dạ dày thực quản (GERD) / Viêm dạ dày nhẹ', 'COMPLETED',
                    now() - interval '3 hours', now() - interval '2 hours', 0, now(), now()
                )
                """,
                c2Id,
                UUID.fromString("f0000001-0000-0000-0000-000000000001"),
                UUID.fromString("d0000001-0000-0000-0000-000000000001"),
                UUID.fromString("a0000001-0000-0000-0000-000000000002")
            );
            log.info("Seeded historical completed consultation for Nguyễn Thị Mai via JdbcTemplate");
        }

        // 3. Ca khám mẫu CỐ ĐỊNH ở trạng thái IN_PROGRESS phục vụ kiểm thử AI lâm sàng & Voice
        UUID aiTestConsultationId = UUID.fromString("c0000001-0000-0000-0000-000000000099");
        Integer aiCount = jdbcTemplate.queryForObject("SELECT count(*) FROM consultations WHERE id=?", Integer.class, aiTestConsultationId);
        if (aiCount == null || aiCount == 0) {
            jdbcTemplate.update("""
                INSERT INTO consultations (
                    id, patient_id, patient_name, doctor_id, doctor_name, department_code, department_name,
                    appointment_id, temperature, blood_pressure, heart_rate, spo2, height, weight,
                    symptoms, clinical_notes, status, started_at, version, created_at, updated_at
                ) VALUES (
                    ?, ?, 'Phạm Đức Anh', ?, 'BS. CKI Nguyễn Văn An', 'NOI_TONG_QUAT', 'Khoa Nội Tổng Quát',
                    ?, 37.8, '135/85', 88, 97, 168.0, 65.0,
                    'Bệnh nhân đau tức ngực trái âm ỉ 2 ngày nay, khó thở nhẹ khi gắng sức, hồi hộp đánh trống ngực.',
                    'Tim đều, T1 T2 rõ, chưa nghe âm thổi bệnh lý. Phổi trong không rale. Huyết áp hơi cao.',
                    'IN_PROGRESS', now() - interval '15 minutes', 0, now(), now()
                )
                """,
                aiTestConsultationId,
                UUID.fromString("f0000001-0000-0000-0000-000000000004"),
                UUID.fromString("d0000001-0000-0000-0000-000000000001"),
                UUID.fromString("a0000001-0000-0000-0000-000000000001")
            );
            log.info("Seeded active IN_PROGRESS consultation for AI Testing via JdbcTemplate: {}", aiTestConsultationId);
        }
    }

    private void alignStatusConstraint() {
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1 FROM pg_constraint
                        WHERE conrelid = 'consultations'::regclass
                          AND conname = 'consultations_status_check'
                    ) THEN
                        ALTER TABLE consultations DROP CONSTRAINT consultations_status_check;
                    END IF;
                    ALTER TABLE consultations
                        ADD CONSTRAINT consultations_status_check
                        CHECK (status IN ('IN_PROGRESS', 'AWAITING_CLS', 'AWAITING_REVIEW',
                                          'READY_TO_COMPLETE', 'COMPLETED', 'CANCELLED', 'TRANSFERRED'));
                END $$;
                """);
    }
}
