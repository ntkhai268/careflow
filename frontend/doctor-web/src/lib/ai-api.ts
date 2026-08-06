import { api } from "./api";

export interface EvidenceRef {
  sourceType: string;
  sourceId: string;
  field?: string;
}

export interface ClinicalSuggestionItem {
  type: "DIFFERENTIAL_DIAGNOSIS" | "WARNING" | "TREATMENT_REFERRAL" | "MISSING_DATA";
  text: string;
  evidenceRefs?: EvidenceRef[];
}

export interface AiModelInfo {
  provider: string;
  name: string;
  version: string;
}

export interface ClinicalSuggestionResponse {
  interactionId: string;
  mode: string;
  summary: string;
  suggestions: ClinicalSuggestionItem[];
  missingInformation: string[];
  warnings: string[];
  disclaimer: String;
  model: AiModelInfo;
  generatedAt: string;
}

export const aiApi = {
  getClinicalSuggestions: async (consultationId: string, question?: string) => {
    return api.post<ClinicalSuggestionResponse>("/api/ai/clinical-suggestions", {
      consultationId,
      question: question || "Gợi ý chẩn đoán phân biệt và dữ liệu còn thiếu",
    });
  },

  sendClinicalChat: async (consultationId: string, message: string, parentInteractionId?: string) => {
    return api.post<ClinicalSuggestionResponse>("/api/ai/clinical-chat", {
      consultationId,
      message,
      parentInteractionId,
    });
  },

  getInteractionById: async (interactionId: string) => {
    return api.get<ClinicalSuggestionResponse>(`/api/ai/interactions/${interactionId}`);
  },
};
