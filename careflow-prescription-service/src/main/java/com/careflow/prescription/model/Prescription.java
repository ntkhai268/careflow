package com.careflow.prescription.model;

import com.careflow.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "prescriptions", indexes = {
        @Index(name = "idx_prescription_consultation", columnList = "consultationId"),
        @Index(name = "idx_prescription_patient", columnList = "patientId"),
        @Index(name = "idx_prescription_doctor", columnList = "doctorId"),
        @Index(name = "idx_prescription_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prescription extends BaseEntity {

    @Column(nullable = false)
    private UUID consultationId;

    @Column(nullable = false)
    private UUID patientId;

    @Column(nullable = false)
    private UUID doctorId;

    @Column(length = 500)
    private String diagnosis;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private LocalDate followUpDate;

    @Column(length = 80)
    private String dispensingServicePointId;

    private java.time.Instant confirmedAt;

    private java.time.Instant dispensedAt;

    private UUID dispensedByUserId;

    private UUID replacesPrescriptionId;

    @Column(length = 500)
    private String cancellationReason;

    private java.time.Instant cancelledAt;

    private UUID cancelledByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PrescriptionStatus status = PrescriptionStatus.DRAFT;

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PrescriptionItem> items = new ArrayList<>();

    public void addItem(PrescriptionItem item) {
        items.add(item);
        item.setPrescription(this);
    }

    public void clearItems() {
        items.forEach(item -> item.setPrescription(null));
        items.clear();
    }
}
