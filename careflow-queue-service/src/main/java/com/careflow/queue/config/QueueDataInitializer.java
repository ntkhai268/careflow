package com.careflow.queue.config;

import com.careflow.queue.domain.PriorityLevel;
import com.careflow.queue.domain.QueueConfig;
import com.careflow.queue.domain.QueueEntry;
import com.careflow.queue.domain.QueueStatus;
import com.careflow.queue.domain.QueueType;
import com.careflow.queue.repository.IdempotencyRecordRepository;
import com.careflow.queue.repository.QueueConfigRepository;
import com.careflow.queue.repository.QueueEntryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Component
@Slf4j
public class QueueDataInitializer implements CommandLineRunner {

    private final QueueConfigRepository configRepository;
    private final QueueEntryRepository entryRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public QueueDataInitializer(QueueConfigRepository configRepository,
                                QueueEntryRepository entryRepository,
                                IdempotencyRecordRepository idempotencyRecordRepository) {
        this.configRepository = configRepository;
        this.entryRepository = entryRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Override
    public void run(String... args) {
        LocalDate today = LocalDate.now();
        log.info("Checking Queue Service DataInitializer for today ({})", today);

        // 1. Cấu hình QueueConfig mặc định cho ROOM-01 (Nội tổng quát)
        UUID deptId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        QueueConfig config = configRepository.findByRoomCodeAndActiveTrue("ROOM-01")
                .orElseGet(() -> {
                    QueueConfig cfg = new QueueConfig();
                    cfg.setDepartmentId(deptId);
                    cfg.setDepartmentNameSnapshot("Nội tổng quát");
                    cfg.setQueuePrefix("NOI");
                    cfg.setRoomCode("ROOM-01");
                    cfg.setActive(true);
                    return configRepository.save(cfg);
                });

        // 2. Clear các IdempotencyRecord và QueueEntry cũ để reset lại danh sách chờ khám sạch khi khởi động server
        log.info("Resetting/Seeding clean Active QueueEntries for date {}...", today);
        try {
            idempotencyRecordRepository.deleteAll();
            idempotencyRecordRepository.flush();
            entryRepository.deleteAll();
            entryRepository.flush();
        } catch (Exception e) {
            log.warn("Lỗi khi reset QueueEntries: {}", e.getMessage());
        }

        // 3. Seed 6 Bệnh nhân đã Check-in ở các làn khác nhau (Priority, Normal, Result Review) cho ROOM-01
        Instant now = Instant.now();

        // Patient 1: Làn Cấp cứu / Ưu tiên (PRIORITY)
        createQueueEntry(config, deptId,
                UUID.fromString("a0000001-0000-0000-0000-000000000001"),
                UUID.fromString("f0000001-0000-0000-0000-000000000004"),
                UUID.fromString("00000001-0000-0000-0000-000000000004"),
                today, 1, "NOI-001", PriorityLevel.PRIORITY, QueueStatus.CHECKED_IN, now.minusSeconds(1800));

        // Patient 2: Làn Đọc kết quả CLS (PRIORITY)
        createQueueEntry(config, deptId,
                UUID.fromString("a0000001-0000-0000-0000-000000000002"),
                UUID.fromString("f0000001-0000-0000-0000-000000000005"),
                UUID.fromString("00000001-0000-0000-0000-000000000005"),
                today, 2, "NOI-002", PriorityLevel.PRIORITY, QueueStatus.CHECKED_IN, now.minusSeconds(1500));

        // Patient 3: Làn Khám thông thường (APPOINTMENT)
        createQueueEntry(config, deptId,
                UUID.fromString("a0000001-0000-0000-0000-000000000003"),
                UUID.fromString("f0000001-0000-0000-0000-000000000006"),
                UUID.fromString("00000001-0000-0000-0000-000000000006"),
                today, 3, "NOI-003", PriorityLevel.APPOINTMENT, QueueStatus.CHECKED_IN, now.minusSeconds(1200));

        // Patient 4: Làn Khám thông thường (APPOINTMENT)
        createQueueEntry(config, deptId,
                UUID.fromString("a0000001-0000-0000-0000-000000000007"),
                UUID.fromString("f0000001-0000-0000-0000-000000000001"),
                UUID.fromString("00000001-0000-0000-0000-000000000001"),
                today, 4, "NOI-004", PriorityLevel.APPOINTMENT, QueueStatus.CHECKED_IN, now.minusSeconds(900));

        // Patient 5: Làn Vãng lai (WALK_IN)
        createQueueEntry(config, deptId,
                UUID.fromString("a0000001-0000-0000-0000-000000000008"),
                UUID.fromString("f0000001-0000-0000-0000-000000000002"),
                UUID.fromString("00000001-0000-0000-0000-000000000002"),
                today, 5, "NOI-005", PriorityLevel.WALK_IN, QueueStatus.CHECKED_IN, now.minusSeconds(600));

        // 4. Seed Lab entries for LAB-HEMATOLOGY-01
        createServicePointEntry("LAB-HEMATOLOGY-01", QueueType.LAB_EXECUTION,
                UUID.fromString("f0000001-0000-0000-0000-000000000004"),
                today, 1, "XN-001", PriorityLevel.APPOINTMENT, QueueStatus.CHECKED_IN, now.minusSeconds(1400));
        createServicePointEntry("LAB-HEMATOLOGY-01", QueueType.LAB_EXECUTION,
                UUID.fromString("f0000001-0000-0000-0000-000000000005"),
                today, 2, "XN-002", PriorityLevel.APPOINTMENT, QueueStatus.CHECKED_IN, now.minusSeconds(1100));

        // 5. Seed Pharmacy entries for PHARMACY-MAIN-01
        createServicePointEntry("PHARMACY-MAIN-01", QueueType.PHARMACY_DISPENSING,
                UUID.fromString("f0000001-0000-0000-0000-000000000006"),
                today, 1, "DUOC-001", PriorityLevel.APPOINTMENT, QueueStatus.CHECKED_IN, now.minusSeconds(1000));
        createServicePointEntry("PHARMACY-MAIN-01", QueueType.PHARMACY_DISPENSING,
                UUID.fromString("f0000001-0000-0000-0000-000000000001"),
                today, 2, "DUOC-002", PriorityLevel.APPOINTMENT, QueueStatus.CHECKED_IN, now.minusSeconds(800));

        log.info("Successfully seeded ROOM-01 and ServicePoint active entries for today ({})", today);
    }

    private void createQueueEntry(QueueConfig config, UUID deptId, UUID apptId, UUID patientId, UUID userId,
                                  LocalDate date, int seq, String queueNo, PriorityLevel priority,
                                  QueueStatus status, Instant checkInTime) {
        QueueEntry entry = new QueueEntry();
        entry.setQueueConfigId(config.getId());
        entry.setDepartmentId(deptId);
        entry.setDepartmentCode("NOI_TONG_QUAT");
        entry.setAppointmentId(apptId);
        entry.setPatientId(patientId);
        entry.setUserId(userId);
        entry.setQueueDate(date);
        entry.setSequenceNumber(seq);
        entry.setQueueNumber(queueNo);
        entry.setPriorityLevel(priority);
        entry.setStatus(status);
        entry.setCheckedInAt(checkInTime);
        entry.setEligibleSinceAt(checkInTime);
        entryRepository.save(entry);
    }

    private void createServicePointEntry(String spId, QueueType type, UUID patientId,
                                         LocalDate date, int seq, String queueNo, PriorityLevel priority,
                                         QueueStatus status, Instant checkInTime) {
        QueueEntry entry = new QueueEntry();
        entry.setServicePointId(spId);
        entry.setQueueType(type);
        entry.setConsultationPhase(null);
        entry.setPatientId(patientId);
        entry.setQueueDate(date);
        entry.setSequenceNumber(seq);
        entry.setQueueNumber(queueNo);
        entry.setPriorityLevel(priority);
        entry.setStatus(status);
        entry.setCheckedInAt(checkInTime);
        entry.setEligibleSinceAt(checkInTime);
        entryRepository.save(entry);
    }
}
