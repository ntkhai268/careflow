package com.careflow.ai.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceClinicalFieldsResponse {
    private String transcriptText;
    private VoiceExtractedFields extractedFields;
}
