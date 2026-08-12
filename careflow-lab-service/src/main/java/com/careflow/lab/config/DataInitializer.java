package com.careflow.lab.config;

import com.careflow.lab.model.*;
import com.careflow.lab.repository.LabOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final LabOrderRepository labOrderRepository;

    @Override
    @Transactional
    public void run(String... args) {
        UUID orderId = UUID.fromString("b0000001-0000-0000-0000-000000000001");
        if (labOrderRepository.existsById(orderId)) {
            log.info("LabOrder seed data already present.");
            return;
        }

        log.info("Seeding sample LabOrder for patient Vo Thi Lan (5.4.6 E2E Flow test)...");

        LabOrder order = new LabOrder();
        order.setId(orderId);
        order.setConsultationId(UUID.fromString("c0000001-0000-0000-0000-000000000002"));
        order.setPatientId(UUID.fromString("f0000001-0000-0000-0000-000000000005"));
        order.setOrderedByDoctorId(UUID.fromString("d0000001-0000-0000-0000-000000000001"));
        order.setClinicalNote("Kiem tra chi so duong huyet HbA1c va cong thuc mau tong quat");
        order.setStatus(LabOrderStatus.ORDERED);
        order.setPaymentStatus(PaymentStatus.NOT_REQUIRED);

        LabOrderItem item1 = new LabOrderItem();
        item1.setId(UUID.fromString("b0000002-0000-0000-0000-000000000001"));
        item1.setServiceCode("CBC-001");
        item1.setServiceName("Tổng phân tích tế bào máu ngoại vi (Công thức máu)");
        item1.setServicePointId("LAB-HEMATOLOGY-01");
        item1.setRequired(true);
        item1.setStatus(LabOrderItemStatus.ORDERED);
        item1.setReferenceRange("4.0 - 10.0 K/uL");
        order.addItem(item1);

        LabOrderItem item2 = new LabOrderItem();
        item2.setId(UUID.fromString("b0000002-0000-0000-0000-000000000002"));
        item2.setServiceCode("BIO-002");
        item2.setServiceName("Định lượng Glucose & HbA1c trong máu");
        item2.setServicePointId("LAB-HEMATOLOGY-01");
        item2.setRequired(true);
        item2.setStatus(LabOrderItemStatus.ORDERED);
        item2.setReferenceRange("4.1 - 5.9 %");
        order.addItem(item2);

        labOrderRepository.save(order);
        log.info("Successfully seeded LabOrder {} with 2 required test items.", orderId);
    }
}
