package com.careflow.consultation.config;

import com.careflow.consultation.model.Consultation;
import com.careflow.consultation.model.ConsultationStatus;
import com.careflow.consultation.repository.ConsultationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final ConsultationRepository consultationRepository;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(ConsultationRepository consultationRepository, JdbcTemplate jdbcTemplate) {
        this.consultationRepository = consultationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("Checking historical consultation records for test patients...");
        alignStatusConstraint();

        UUID c1Id = UUID.fromString("c0000001-0000-0000-0000-000000000001");
        if (!consultationRepository.existsById(c1Id)) {
            Consultation c1 = Consultation.builder()
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000004")) // Phạm Đức Anh
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .appointmentId(UUID.fromString("a0000001-0000-0000-0000-000000000001"))
                    .temperature(new BigDecimal("38.2"))
                    .bloodPressure("110/70")
                    .heartRate(95)
                    .spo2(98)
                    .height(new BigDecimal("110.0"))
                    .weight(new BigDecimal("18.5"))
                    .symptoms("Sốt 38.2C, ho hắng nhẹ, đau họng khi nuốt 2 ngày")
                    .clinicalNotes("Họng đỏ, sưng nhẹ 2 amydal, phổi thông khí rõ không rale")
                    .icd10Code("J00")
                    .icd10Name("Viêm mũi họng cấp (cảm thường)")
                    .diagnosis("Viêm mũi họng cấp / Sốt siêu vi")
                    .status(ConsultationStatus.COMPLETED)
                    .startedAt(LocalDateTime.now().minusHours(2))
                    .completedAt(LocalDateTime.now().minusHours(1))
                    .build();
            c1.setId(c1Id);
            consultationRepository.save(c1);
            log.info("Seeded historical completed consultation for Phạm Đức Anh (J00)");
        }

        UUID c2Id = UUID.fromString("c0000001-0000-0000-0000-000000000002");
        if (!consultationRepository.existsById(c2Id)) {
            Consultation c2 = Consultation.builder()
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000001")) // Nguyễn Thị Mai
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .appointmentId(UUID.fromString("a0000001-0000-0000-0000-000000000002"))
                    .temperature(new BigDecimal("36.6"))
                    .bloodPressure("125/82")
                    .heartRate(76)
                    .spo2(99)
                    .height(new BigDecimal("158.0"))
                    .weight(new BigDecimal("54.0"))
                    .symptoms("Ợ chua, nóng rát vùng sau xương ức, đau thượng vị nhẹ sau ăn")
                    .clinicalNotes("Ấn đau nhẹ vùng thượng vị, không phản ứng thành bụng")
                    .icd10Code("K21.9")
                    .icd10Name("Bệnh trào ngược dạ dày - thực quản không có viêm thực quản")
                    .diagnosis("Trào ngược dạ dày thực quản (GERD) / Viêm dạ dày nhẹ")
                    .status(ConsultationStatus.COMPLETED)
                    .startedAt(LocalDateTime.now().minusHours(3))
                    .completedAt(LocalDateTime.now().minusHours(2))
                    .build();
            c2.setId(c2Id);
            consultationRepository.save(c2);
            log.info("Seeded historical completed consultation for Nguyễn Thị Mai (K21.9)");
        }

        UUID c3Id = UUID.fromString("c0000001-0000-0000-0000-000000000003");
        if (!consultationRepository.existsById(c3Id)) {
            Consultation c3 = Consultation.builder()
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000005")) // Võ Thị Lan
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .appointmentId(UUID.fromString("a0000001-0000-0000-0000-000000000002"))
                    .temperature(new BigDecimal("36.8"))
                    .bloodPressure("135/85")
                    .heartRate(82)
                    .spo2(98)
                    .height(new BigDecimal("156.0"))
                    .weight(new BigDecimal("58.0"))
                    .symptoms("Tái khám đái tháo đường type 2, kiểm tra chỉ số đường huyết và đo huyết áp định kỳ")
                    .clinicalNotes("Tiếp xúc tốt, da niêm mạc hồng, tim đều T1 T2 rõ, không phù")
                    .icd10Code("E11.9")
                    .icd10Name("Bệnh đái tháo đường không phụ thuộc insulin không có biến chứng")
                    .diagnosis("Đái tháo đường type 2 / Tăng huyết áp độ 1")
                    .status(ConsultationStatus.COMPLETED)
                    .startedAt(LocalDateTime.now().minusMinutes(20))
                    .completedAt(LocalDateTime.now().minusMinutes(5))
                    .build();
            c3.setId(c3Id);
            consultationRepository.save(c3);
            log.info("Seeded active IN_PROGRESS consultation for Võ Thị Lan (E11.9)");
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
