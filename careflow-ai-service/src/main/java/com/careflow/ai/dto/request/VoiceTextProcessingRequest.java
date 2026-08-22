package com.careflow.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceTextProcessingRequest {
    @NotBlank(message = "Transcript text must not be blank")
    private String transcriptText;
}
