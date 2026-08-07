package com.careflow.ai.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalSuggestionResponse {
    private String interactionId;
    private String mode; // MOCK_RULE_BASED, LLM_PROVIDER
    private String summary;
    private List<ClinicalSuggestionItem> suggestions;
    private List<String> missingInformation;
    private List<String> warnings;
    private String disclaimer;
    private AiModelInfo model;
    private String generatedAt;
}
