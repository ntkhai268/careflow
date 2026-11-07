package com.careflow.ai.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalCitationDto {
    private String sourceId;
    private String title;
    private String revision;
    private String url;
    private String contentHash;
    private String reviewedAt;
    private String quote;
    private boolean synthetic;
}
