package com.careflow.prescription.model;

import com.careflow.common.model.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "prescription_items", indexes = {
        @Index(name = "idx_item_prescription", columnList = "prescription_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrescriptionItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id", nullable = false)
    @JsonIgnore
    private Prescription prescription;

    @Column(nullable = false)
    private String medicineName;

    @Column(length = 50)
    private String medicineCode;

    @Column(length = 50)
    private String unit;

    @Column(length = 100)
    private String dosage;

    @Column(length = 100)
    private String frequency;

    @Column(length = 100)
    private String timing;

    private Integer duration;

    private Integer quantity;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
