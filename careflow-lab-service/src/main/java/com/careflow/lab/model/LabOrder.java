package com.careflow.lab.model;

import com.careflow.common.model.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "lab_orders", indexes = {
        @Index(name = "idx_lab_order_consultation", columnList = "consultation_id"),
        @Index(name = "idx_lab_order_patient", columnList = "patient_id"),
        @Index(name = "idx_lab_order_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class LabOrder extends BaseEntity {

    @Column(name = "consultation_id", nullable = false)
    private UUID consultationId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "ordered_by_doctor_id", nullable = false)
    private UUID orderedByDoctorId;

    @Column(name = "source_queue_entry_id")
    private UUID sourceQueueEntryId;

    @Column(name = "source_verified_by")
    private UUID sourceVerifiedBy;

    @Column(name = "source_verified_at")
    private Instant sourceVerifiedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "clinical_note", columnDefinition = "TEXT")
    private String clinicalNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LabOrderStatus status = LabOrderStatus.ORDERED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.NOT_REQUIRED;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LabOrderItem> items = new ArrayList<>();

    public void addItem(LabOrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void recordSourceVerification(UUID sourceQueueEntryId, UUID verifiedBy, Instant verifiedAt) {
        this.sourceQueueEntryId = sourceQueueEntryId;
        this.sourceVerifiedBy = verifiedBy;
        this.sourceVerifiedAt = verifiedAt;
    }

    public boolean hasAllRequiredResults() {
        return items.stream()
                .filter(LabOrderItem::isRequired)
                .allMatch(LabOrderItem::hasValidResult);
    }

    public void cancelOrderedItems() {
        items.stream()
                .filter(item -> item.getStatus() == LabOrderItemStatus.ORDERED)
                .forEach(item -> item.setStatus(LabOrderItemStatus.CANCELLED));
    }

    public boolean isPublished() {
        return status == LabOrderStatus.RESULT_AVAILABLE || status == LabOrderStatus.REVIEWED;
    }
}
