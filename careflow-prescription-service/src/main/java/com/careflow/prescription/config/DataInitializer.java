package com.careflow.prescription.config;

import com.careflow.prescription.model.Drug;
import com.careflow.prescription.repository.DrugRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@org.springframework.context.annotation.Profile("demo | test")
public class DataInitializer implements CommandLineRunner {

    private final DrugRepository drugRepository;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(DrugRepository drugRepository, JdbcTemplate jdbcTemplate) {
        this.drugRepository = drugRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        if (drugRepository.count() == 0) {
            log.info("Initializing default medicine catalog into drugs_dictionary table...");
            List<Drug> defaultDrugs = List.of(
                    Drug.builder().code("MED001").name("Paracetamol 500mg").unit("viên").description("Giảm đau, hạ sốt").active(true).build(),
                    Drug.builder().code("MED002").name("Amoxicillin 500mg").unit("viên").description("Kháng sinh").active(true).build(),
                    Drug.builder().code("MED003").name("Omeprazole 20mg").unit("viên").description("Ức chế bơm proton, trào ngược dạ dày").active(true).build(),
                    Drug.builder().code("MED004").name("Metformin 500mg").unit("viên").description("Điều trị đái tháo đường type 2").active(true).build(),
                    Drug.builder().code("MED005").name("Amlodipine 5mg").unit("viên").description("Hạ huyết áp").active(true).build(),
                    Drug.builder().code("MED006").name("Atorvastatin 10mg").unit("viên").description("Giảm cholesterol").active(true).build(),
                    Drug.builder().code("MED007").name("Cetirizine 10mg").unit("viên").description("Kháng histamin, chống dị ứng").active(true).build(),
                    Drug.builder().code("MED008").name("Ibuprofen 400mg").unit("viên").description("Giảm đau, kháng viêm").active(true).build(),
                    Drug.builder().code("MED009").name("Vitamin C 500mg").unit("viên").description("Bổ sung vitamin C").active(true).build(),
                    Drug.builder().code("MED010").name("Loperamide 2mg").unit("viên").description("Chống tiêu chảy").active(true).build(),
                    Drug.builder().code("MED011").name("Salbutamol 2mg").unit("viên").description("Giãn phế quản, hen suyễn").active(true).build(),
                    Drug.builder().code("MED012").name("Diclofenac 50mg").unit("viên").description("Kháng viêm không steroid").active(true).build(),
                    Drug.builder().code("MED013").name("Prednisolone 5mg").unit("viên").description("Corticosteroid, chống viêm").active(true).build(),
                    Drug.builder().code("MED014").name("Cephalexin 500mg").unit("viên").description("Kháng sinh cephalosporin").active(true).build(),
                    Drug.builder().code("MED015").name("Domperidone 10mg").unit("viên").description("Chống nôn, kích thích tiêu hóa").active(true).build()
            );

            drugRepository.saveAll(defaultDrugs);
            log.info("Successfully seeded {} drugs into database.", defaultDrugs.size());
        }

        UUID pr1Id = UUID.fromString("e0000001-0000-0000-0000-000000000001");
        Integer pr1Count = jdbcTemplate.queryForObject("SELECT count(*) FROM prescriptions WHERE id=?", Integer.class, pr1Id);
        if (pr1Count == null || pr1Count == 0) {
            jdbcTemplate.update(
                "INSERT INTO prescriptions(id, consultation_id, patient_id, doctor_id, diagnosis, notes, status, follow_up_status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'DISPENSED', 'NOT_REQUESTED', now(), now())",
                pr1Id,
                UUID.fromString("c0000001-0000-0000-0000-000000000001"),
                UUID.fromString("f0000001-0000-0000-0000-000000000004"),
                UUID.fromString("d0000001-0000-0000-0000-000000000001"),
                "Viêm mũi họng cấp / Sốt siêu vi",
                "Uống thuốc đúng liều, uống nhiều nước ấm, tái khám nếu sốt cao liên tục > 3 ngày"
            );
            jdbcTemplate.update(
                "INSERT INTO prescription_items(id, prescription_id, medicine_name, medicine_code, unit, dosage, frequency, timing, duration, quantity, notes, created_at, updated_at) " +
                "VALUES (?, ?, 'Paracetamol 500mg', 'MED001', 'viên', '1/2 viên / lần', '3 lần / ngày', 'Sau khi ăn', 3, 5, 'Uống khi sốt >= 38.5C', now(), now())",
                UUID.randomUUID(), pr1Id
            );
            jdbcTemplate.update(
                "INSERT INTO prescription_items(id, prescription_id, medicine_name, medicine_code, unit, dosage, frequency, timing, duration, quantity, notes, created_at, updated_at) " +
                "VALUES (?, ?, 'Cetirizine 10mg', 'MED007', 'viên', '1/2 viên / lần', '1 lần / ngày', 'Tối trước khi đi ngủ', 5, 3, 'Giảm ngứa họng, xì mũi', now(), now())",
                UUID.randomUUID(), pr1Id
            );
            log.info("Seeded historical prescription for Phạm Đức Anh via JdbcTemplate");
        }

        UUID pr2Id = UUID.fromString("e0000001-0000-0000-0000-000000000002");
        Integer pr2Count = jdbcTemplate.queryForObject("SELECT count(*) FROM prescriptions WHERE id=?", Integer.class, pr2Id);
        if (pr2Count == null || pr2Count == 0) {
            jdbcTemplate.update(
                "INSERT INTO prescriptions(id, consultation_id, patient_id, doctor_id, diagnosis, notes, status, follow_up_status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'DISPENSED', 'NOT_REQUESTED', now(), now())",
                pr2Id,
                UUID.fromString("c0000001-0000-0000-0000-000000000002"),
                UUID.fromString("f0000001-0000-0000-0000-000000000001"),
                UUID.fromString("d0000001-0000-0000-0000-000000000001"),
                "Trào ngược dạ dày thực quản (GERD)",
                "Tránh ăn đồ chua cay, không nằm ngay sau khi ăn"
            );
            jdbcTemplate.update(
                "INSERT INTO prescription_items(id, prescription_id, medicine_name, medicine_code, unit, dosage, frequency, timing, duration, quantity, notes, created_at, updated_at) " +
                "VALUES (?, ?, 'Omeprazole 20mg', 'MED003', 'viên', '1 viên / lần', '1 lần / ngày', 'Trước bữa ăn sáng 30 phút', 14, 14, 'Uống nguyên viên', now(), now())",
                UUID.randomUUID(), pr2Id
            );
            jdbcTemplate.update(
                "INSERT INTO prescription_items(id, prescription_id, medicine_name, medicine_code, unit, dosage, frequency, timing, duration, quantity, notes, created_at, updated_at) " +
                "VALUES (?, ?, 'Domperidone 10mg', 'MED015', 'viên', '1 viên / lần', '2 lần / ngày', 'Trước bữa ăn 15 phút', 7, 14, 'Chống đầy hơi, ợ chua', now(), now())",
                UUID.randomUUID(), pr2Id
            );
            log.info("Seeded historical prescription for Nguyễn Thị Mai via JdbcTemplate");
        }
    }
}
