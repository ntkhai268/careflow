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
public class ClinicalChatRequest {
    @NotBlank(message = "consultationId không được để trống")
    private String consultationId;

    private String parentInteractionId;

    @NotBlank(message = "message không được để trống")
    private String message;

    private String pageRoute;
    private String pageTitle;
    private String pageData;
}
