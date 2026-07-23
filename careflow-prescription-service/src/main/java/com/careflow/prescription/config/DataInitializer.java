package com.careflow.prescription.config;

import com.careflow.prescription.model.Drug;
import com.careflow.prescription.repository.DrugRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final DrugRepository drugRepository;

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
    }
}
