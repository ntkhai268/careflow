package com.careflow.prescription.config;

import com.careflow.prescription.model.Drug;
import com.careflow.prescription.model.Prescription;
import com.careflow.prescription.model.PrescriptionItem;
import com.careflow.prescription.model.PrescriptionStatus;
import com.careflow.prescription.repository.DrugRepository;
import com.careflow.prescription.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final DrugRepository drugRepository;
    private final PrescriptionRepository prescriptionRepository;

    public DataInitializer(DrugRepository drugRepository, PrescriptionRepository prescriptionRepository) {
        this.drugRepository = drugRepository;
        this.prescriptionRepository = prescriptionRepository;
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
        if (!prescriptionRepository.existsById(pr1Id)) {
            Prescription pr1 = Prescription.builder()
                    .consultationId(UUID.fromString("c0000001-0000-0000-0000-000000000001"))
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000004"))
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .diagnosis("Viêm mũi họng cấp / Sốt siêu vi")
                    .notes("Uống thuốc đúng liều, uống nhiều nước ấm, tái khám nếu sốt cao liên tục > 3 ngày")
                    .status(PrescriptionStatus.DISPENSED)
                    .build();
            pr1.setId(pr1Id);
            pr1.addItem(PrescriptionItem.builder()
                    .medicineName("Paracetamol 500mg")
                    .medicineCode("MED001")
                    .unit("viên")
                    .dosage("1/2 viên / lần")
                    .frequency("3 lần / ngày")
                    .timing("Sau khi ăn")
                    .duration(3)
                    .quantity(5)
                    .notes("Uống khi sốt >= 38.5C")
                    .build());
            pr1.addItem(PrescriptionItem.builder()
                    .medicineName("Cetirizine 10mg")
                    .medicineCode("MED007")
                    .unit("viên")
                    .dosage("1/2 viên / lần")
                    .frequency("1 lần / ngày")
                    .timing("Tối trước khi đi ngủ")
                    .duration(5)
                    .quantity(3)
                    .notes("Giảm ngứa họng, xì mũi")
                    .build());
            prescriptionRepository.save(pr1);
            log.info("Seeded historical prescription for Phạm Đức Anh");
        }

        UUID pr2Id = UUID.fromString("e0000001-0000-0000-0000-000000000002");
        if (!prescriptionRepository.existsById(pr2Id)) {
            Prescription pr2 = Prescription.builder()
                    .consultationId(UUID.fromString("c0000001-0000-0000-0000-000000000002"))
                    .patientId(UUID.fromString("f0000001-0000-0000-0000-000000000001"))
                    .doctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .diagnosis("Trào ngược dạ dày thực quản (GERD)")
                    .notes("Tránh ăn đồ chua cay, không nằm ngay sau khi ăn")
                    .status(PrescriptionStatus.DISPENSED)
                    .build();
            pr2.setId(pr2Id);
            pr2.addItem(PrescriptionItem.builder()
                    .medicineName("Omeprazole 20mg")
                    .medicineCode("MED003")
                    .unit("viên")
                    .dosage("1 viên / lần")
                    .frequency("1 lần / ngày")
                    .timing("Trước bữa ăn sáng 30 phút")
                    .duration(14)
                    .quantity(14)
                    .notes("Uống nguyên viên")
                    .build());
            pr2.addItem(PrescriptionItem.builder()
                    .medicineName("Domperidone 10mg")
                    .medicineCode("MED015")
                    .unit("viên")
                    .dosage("1 viên / lần")
                    .frequency("2 lần / ngày")
                    .timing("Trước bữa ăn 15 phút")
                    .duration(7)
                    .quantity(14)
                    .notes("Chống đầy hơi, ợ chua")
                    .build());
            prescriptionRepository.save(pr2);
            log.info("Seeded historical prescription for Nguyễn Thị Mai");
        }
    }
}
