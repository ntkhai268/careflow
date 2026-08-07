package com.careflow.lab.model;

import com.careflow.common.model.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lab_order_items", indexes = {
        @Index(name = "idx_lab_item_order", columnList = "lab_order_id"),
        @Index(name = "idx_lab_item_service_point", columnList = "service_point_id")
})
@Getter
@Setter
@NoArgsConstructor
public class LabOrderItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_order_id", nullable = false)
    @JsonIgnore
    private LabOrder order;

    @Column(name = "service_code", nullable = false, length = 50)
    private String serviceCode;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "service_point_id", nullable = false, length = 80)
    private String servicePointId;

    @Column(nullable = false)
    private boolean required = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LabOrderItemStatus status = LabOrderItemStatus.ORDERED;

    @Column(name = "result_value")
    private String resultValue;

    @Column(name = "result_unit", length = 50)
    private String resultUnit;

    @Column(name = "reference_range", length = 100)
    private String referenceRange;

    @Column(name = "result_flag", length = 30)
    private String resultFlag;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "performed_by_staff_id")
    private UUID performedByStaffId;

    @Column(name = "performed_at")
    private Instant performedAt;
}
