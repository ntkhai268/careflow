package com.careflow.emr.config;

import com.careflow.emr.model.MedicalRecord;
import com.careflow.emr.repository.MedicalRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final MedicalRecordRepository medicalRecordRepository;

    @Override
    public void run(String... args) throws Exception {
        if (medicalRecordRepository.count() == 0) {
            log.info("Seeding initial EMR master medical records...");

            seedRecord("f0000001-0000-0000-0000-000000000001", "EMR-2026-000001", "O Rh+", "Tiền sử viêm xoang mãn tính, dị ứng Penicillin nặng");
            seedRecord("f0000001-0000-0000-0000-000000000002", "EMR-2026-000002", "A Rh+", "Tiền sử tăng huyết áp nhẹ, dị ứng hải sản");
            seedRecord("f0000001-0000-0000-0000-000000000003", "EMR-2026-000003", "B Rh+", "Tiền sử đái tháo đường tuýp 2");
            seedRecord("f0000001-0000-0000-0000-000000000004", "EMR-2026-000004", "AB Rh+", "Tiền sử viêm mũi dị ứng");
            seedRecord("f0000001-0000-0000-0000-000000000005", "EMR-2026-000005", "O Rh-", "Không ghi nhận tiền sử bệnh đặc biệt");
            seedRecord("f0000001-0000-0000-0000-000000000006", "EMR-2026-000006", "A Rh-", "Tiền sử hen phế quản từ nhỏ");

            log.info("Successfully seeded 6 EMR master records!");
        }
    }

    private void seedRecord(String patientIdStr, String recordNumber, String bloodType, String medicalHistory) {
        UUID patientId = UUID.fromString(patientIdStr);
        MedicalRecord record = MedicalRecord.builder()
                .patientId(patientId)
                .recordNumber(recordNumber)
                .bloodType(bloodType)
                .medicalHistory(medicalHistory)
                .build();
        medicalRecordRepository.save(record);
    }
}
