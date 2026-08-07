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
public class ClinicalSuggestionItem {
    private String type; // DIFFERENTIAL_DIAGNOSIS, WARNING, TREATMENT_REFERRAL, MISSING_DATA
    private String text;
    private List<EvidenceRef> evidenceRefs;
}
