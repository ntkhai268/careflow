import { api, ApiResponse } from "./api";


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
  disclaimer: string;
  model: AiModelInfo;
  generatedAt: string;
}

export interface ScreenContext {
  pageRoute?: string;
  pageTitle?: string;
  pageData?: string;
}

export interface VoiceExtractedFields {
  heartRate?: string;
  bloodPressure?: string;
  temperature?: string;
  spo2?: string;
  height?: string;
  weight?: string;
  symptoms?: string;
  clinicalNotes?: string;
  icd10Code?: string;
  icd10Name?: string;
  diagnosis?: string;
}

export interface VoiceClinicalFieldsResponse {
  transcriptText: string;
  extractedFields: VoiceExtractedFields;
}

export const aiApi = {
  getClinicalSuggestions: async (consultationId: string, question?: string, context?: ScreenContext) => {
    return api.post<ClinicalSuggestionResponse>("/api/ai/clinical-suggestions", {
      consultationId,
      question: question || "Gợi ý chẩn đoán phân biệt và dữ liệu còn thiếu",
      pageRoute: context?.pageRoute,
      pageTitle: context?.pageTitle,
      pageData: context?.pageData,
    });
  },

  sendClinicalChat: async (
    consultationId: string,
    message: string,
    contextOrParentId?: ScreenContext | string,
    parentInteractionId?: string
  ) => {
    const context = typeof contextOrParentId === "object" ? contextOrParentId : undefined;
    const parentId = typeof contextOrParentId === "string" ? contextOrParentId : parentInteractionId;
    return api.post<ClinicalSuggestionResponse>("/api/ai/clinical-chat", {
      consultationId,
      message,
      parentInteractionId: parentId,
      pageRoute: context?.pageRoute,
      pageTitle: context?.pageTitle,
      pageData: context?.pageData,
    });
  },

  getInteractionById: async (interactionId: string) => {
    return api.get<ClinicalSuggestionResponse>(`/api/ai/interactions/${interactionId}`);
  },

  processVoiceToFields: async (audioBlob: Blob): Promise<ApiResponse<VoiceClinicalFieldsResponse>> => {
    const formData = new FormData();
    const filename = audioBlob.type.includes("wav") ? "voice-record.wav" : "voice-record.webm";
    formData.append("file", audioBlob, filename);
    return api.upload<VoiceClinicalFieldsResponse>("/api/ai/voice-to-clinical-fields", formData);
  },

  processTextToFields: async (transcriptText: string): Promise<ApiResponse<VoiceClinicalFieldsResponse>> => {
    return api.post<VoiceClinicalFieldsResponse>("/api/ai/text-to-clinical-fields", { transcriptText });
  },
};

