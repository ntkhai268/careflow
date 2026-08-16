package com.careflow.patient.model;

import com.careflow.common.model.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "patient_allergies", indexes = {
        @Index(name = "idx_patient_allergies_patient", columnList = "patient_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientAllergy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @JsonIgnore
    private Patient patient;

    @Column(nullable = false, length = 100)
    private String allergyName;

    @Column(length = 100)
    private String allergyGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AllergySeverity severity;

    @Column(length = 255)
    private String reaction;

    @Column(length = 100)
    private String confirmedBy;
}
