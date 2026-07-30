package com.careflow.patient.model;

import com.careflow.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "health_record_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthRecordFile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "health_record_id", nullable = false)
    private HealthRecord healthRecord;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "stored_path", nullable = false, length = 500)
    private String storedPath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type", length = 100)
    private String contentType;
}
