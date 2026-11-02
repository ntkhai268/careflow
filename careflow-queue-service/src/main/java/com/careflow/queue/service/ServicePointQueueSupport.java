package com.careflow.queue.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.queue.domain.PriorityLevel;
import com.careflow.queue.domain.QueueEntry;
import com.careflow.queue.domain.QueueStatus;
import com.careflow.queue.domain.QueueType;
import com.careflow.queue.domain.ServicePointSequence;
import com.careflow.queue.dto.QueueEntryResponse;
import com.careflow.queue.dto.ServicePointQueueResponse;
import com.careflow.queue.repository.QueueEntryRepository;
import com.careflow.queue.repository.ServicePointSequenceRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

final class ServicePointQueueSupport {
    private static final Set<QueueStatus> SERVING_STATUSES = EnumSet.of(
            QueueStatus.CALLED,
            QueueStatus.IN_PROGRESS
    );

    private final QueueEntryRepository entries;
    private final ServicePointSequenceRepository servicePointSequences;
    private final QueueEventService events;
    private final ZoneId businessZone;
    private final Supplier<LocalDate> businessDateSupplier;

    ServicePointQueueSupport(QueueEntryRepository entries,
                             ServicePointSequenceRepository servicePointSequences,
                             QueueEventService events,
                             ZoneId businessZone,
                             Supplier<LocalDate> businessDateSupplier) {
        this.entries = entries;
        this.servicePointSequences = servicePointSequences;
        this.events = events;
        this.businessZone = businessZone;
        this.businessDateSupplier = businessDateSupplier;
    }

    ServicePointQueueResponse servicePointDashboard(String servicePointId, LocalDate requestedDate) {
        String normalized = QueueAccessValidator.normalizeServicePointId(servicePointId);
        LocalDate date = requestedDate == null ? businessDateSupplier.get() : requestedDate;
        List<QueueEntry> active = entries
                .findByServicePointIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                        normalized,
                        date,
                        EnumSet.of(QueueStatus.QUEUED, QueueStatus.CALLED, QueueStatus.IN_PROGRESS));
        List<QueueEntryResponse> response = new ArrayList<>();
        active.stream()
                .filter(entry -> SERVING_STATUSES.contains(entry.getStatus()))
                .sorted(Comparator.comparing(QueueEntry::getCalledAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(entry -> response.add(QueueEntryResponse.fromServicePoint(entry, 0, 0)));
        int position = 1;
        QueueEntryResponse recommended = null;
        for (QueueEntry entry : active) {
            if (entry.getStatus() != QueueStatus.QUEUED) {
                continue;
            }
            QueueEntryResponse item = QueueEntryResponse.fromServicePoint(entry, position++, null);
            if (recommended == null) {
                recommended = item;
            }
            response.add(item);
        }
        return new ServicePointQueueResponse(normalized, date, response, recommended);
    }

    QueueEntry createPharmacyEntry(UUID prescriptionId,
                                   UUID consultationId,
                                   UUID patientId,
                                   String servicePointId,
                                   Instant queuedAt) {
        Optional<QueueEntry> existing = entries.findByPrescriptionId(prescriptionId);
        if (existing.isPresent()) {
            return existing.get();
        }
        String normalizedServicePoint = QueueAccessValidator.normalizeServicePointId(servicePointId);
        LocalDate date = LocalDate.ofInstant(queuedAt, businessZone);
        ServicePointSequence sequence = nextServicePointSequence(normalizedServicePoint, date);

        QueueEntry entry = buildServicePointEntry(
                consultationId,
                patientId,
                QueueType.PHARMACY_DISPENSING,
                normalizedServicePoint,
                date,
                sequence.getLastNumber(),
                "RX-" + String.format("%03d", sequence.getLastNumber()),
                queuedAt);
        entry.setPrescriptionId(prescriptionId);
        return entry;
    }

    QueueEntry createLabExecutionEntry(UUID labOrderId,
                                       UUID consultationId,
                                       UUID patientId,
                                       String servicePointId,
                                       Instant queuedAt) {
        String normalizedServicePoint = QueueAccessValidator.normalizeServicePointId(servicePointId);
        Optional<QueueEntry> existing = entries.findByLabOrderIdAndServicePointId(labOrderId, normalizedServicePoint);
        if (existing.isPresent()) {
            return existing.get();
        }
        LocalDate date = LocalDate.ofInstant(queuedAt, businessZone);
        ServicePointSequence sequence = nextServicePointSequence(normalizedServicePoint, date);

        QueueEntry entry = buildServicePointEntry(
                consultationId,
                patientId,
                QueueType.LAB_EXECUTION,
                normalizedServicePoint,
                date,
                sequence.getLastNumber(),
                "LAB-" + String.format("%03d", sequence.getLastNumber()),
                queuedAt);
        entry.setLabOrderId(labOrderId);
        return entry;
    }

    QueueEntryResponse currentLabExecution(UUID labOrderId, String servicePointId) {
        String point = QueueAccessValidator.normalizeServicePointId(servicePointId);
        QueueEntry entry = entries.findByLabOrderIdAndServicePointId(labOrderId, point)
                .orElseThrow(() -> new ResourceNotFoundException("Lab queue entry", "labOrderId/servicePointId", labOrderId));
        if (entry.getQueueType() != QueueType.LAB_EXECUTION) {
            throw new BusinessException(409, "Queue entry is not a lab execution");
        }
        return QueueEntryResponse.fromServicePoint(entry, null, null);
    }

    QueueEntryResponse currentLabExecution(UUID labOrderId) {
        QueueEntry entry = entries.findFirstByLabOrderIdOrderByCreatedAtDesc(labOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab queue entry", "labOrderId", labOrderId));
        return QueueEntryResponse.fromServicePoint(entry, null, null);
    }

    QueueEntryResponse currentPharmacyEntry(UUID prescriptionId) {
        QueueEntry entry = entries.findByPrescriptionId(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy QueueEntry", "prescriptionId", prescriptionId));
        if (entry.getQueueType() != QueueType.PHARMACY_DISPENSING) {
            throw new BusinessException(422, "Queue entry is not a pharmacy dispensing entry");
        }
        Integer position = entry.getStatus() == QueueStatus.QUEUED ? 1 : 0;
        return QueueEntryResponse.fromServicePoint(entry, position, position == 0 ? 0 : null);
    }

    void cancelPharmacyEntry(UUID prescriptionId, String correlationId) {
        QueueEntry snapshot = entries.findByPrescriptionId(prescriptionId).orElse(null);
        if (snapshot == null
                || snapshot.getStatus() == QueueStatus.COMPLETED
                || snapshot.getStatus() == QueueStatus.CANCELLED) {
            return;
        }
        QueueEntry entry = entries.findFirstById(snapshot.getId())
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", snapshot.getId()));
        if (entry.getStatus() == QueueStatus.COMPLETED || entry.getStatus() == QueueStatus.CANCELLED) {
            return;
        }
        entry.setStatus(QueueStatus.CANCELLED);
        entry.setCancelledAt(Instant.now());
        entries.saveAndFlush(entry);
        events.append(entry, null, "QueueEntryCancelled", "queue.cancelled", correlationId,
                Map.of("prescriptionId", prescriptionId));
    }

    void completePharmacyEntry(UUID prescriptionId, Instant dispensedAt, String correlationId) {
        QueueEntry snapshot = entries.findByPrescriptionId(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy QueueEntry", "prescriptionId", prescriptionId));
        if (snapshot.getStatus() == QueueStatus.COMPLETED) {
            return;
        }
        QueueEntry entry = entries.findFirstById(snapshot.getId())
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", snapshot.getId()));
        if (entry.getStatus() == QueueStatus.COMPLETED) {
            return;
        }
        if (entry.getStatus() != QueueStatus.IN_PROGRESS) {
            throw new BusinessException(409, "Lượt phát thuốc phải ở IN_PROGRESS trước khi xác nhận đã phát");
        }
        entry.setStatus(QueueStatus.COMPLETED);
        entry.setCompletedAt(dispensedAt);
        entries.saveAndFlush(entry);
        events.append(entry, null, "QueueEntryCompleted", AppConstants.RK_QUEUE_COMPLETED,
                correlationId, Map.of("prescriptionId", prescriptionId, "dispensed", true));
    }

    int servicePointPosition(QueueEntry entry) {
        List<QueueEntry> waiting = entries
                .findByServicePointIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
                        entry.getServicePointId(),
                        entry.getQueueDate(),
                        Set.of(QueueStatus.QUEUED));
        for (int index = 0; index < waiting.size(); index++) {
            if (waiting.get(index).getId().equals(entry.getId())) {
                return index + 1;
            }
        }
        return 0;
    }

    private ServicePointSequence nextServicePointSequence(String normalizedServicePoint, LocalDate date) {
        ServicePointSequence sequence = servicePointSequences
                .findByServicePointIdAndQueueDate(normalizedServicePoint, date)
                .orElseGet(() -> {
                    ServicePointSequence created = new ServicePointSequence();
                    created.setServicePointId(normalizedServicePoint);
                    created.setQueueDate(date);
                    return servicePointSequences.saveAndFlush(created);
                });
        sequence.setLastNumber(sequence.getLastNumber() + 1);
        servicePointSequences.save(sequence);
        return sequence;
    }

    private QueueEntry buildServicePointEntry(UUID consultationId,
                                              UUID patientId,
                                              QueueType queueType,
                                              String normalizedServicePoint,
                                              LocalDate date,
                                              int sequenceNumber,
                                              String queueNumber,
                                              Instant queuedAt) {
        QueueEntry entry = new QueueEntry();
        entry.setQueueConfigId(null);
        entry.setDepartmentId(null);
        entry.setDepartmentCode(null);
        entry.setConsultationId(consultationId);
        entry.setPatientId(patientId);
        entry.setUserId(entries.findFirstByPatientIdAndUserIdIsNotNullOrderByCreatedAtDesc(patientId)
                .map(QueueEntry::getUserId)
                .orElse(null));
        entry.setQueueType(queueType);
        entry.setConsultationPhase(null);
        entry.setQueueClass(null);
        entry.setServicePointId(normalizedServicePoint);
        entry.setQueueDate(date);
        entry.setSequenceNumber(sequenceNumber);
        entry.setQueueNumber(queueNumber);
        entry.setPriorityLevel(PriorityLevel.WALK_IN);
        entry.setStatus(QueueStatus.QUEUED);
        entry.setEligibleSinceAt(queuedAt);
        return entry;
    }
}
