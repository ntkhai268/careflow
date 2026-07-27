package com.careflow.patient.dto.response;

import com.careflow.patient.model.HealthRecordFile;
import lombok.Builder;

import java.util.UUID;

@Builder
public record HealthRecordFileResponse(
        UUID id,
        String fileName,
        String fileUrl,
        Long fileSize,
        String contentType
) {
    public static HealthRecordFileResponse from(HealthRecordFile file, String baseUrl) {
        String base = (baseUrl == null) ? "" : baseUrl;
        return HealthRecordFileResponse.builder()
                .id(file.getId())
                .fileName(file.getFileName())
                .fileUrl(base + "/api/health-records/files/" + file.getId())
                .fileSize(file.getFileSize())
                .contentType(file.getContentType())
                .build();
    }
}
