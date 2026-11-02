"use client";

import { useState } from "react";
import { aiApi, ClinicalSuggestionResponse, VoiceExtractedFields } from "@/lib/ai-api";
import { getErrorMessage } from "@/lib/error-utils";
import { PatientAllergyResponse } from "@/lib/patient-api";
import { PrescriptionItemRequest } from "@/lib/prescription-api";
import { useVoiceStore } from "@/stores/voice-store";

interface UseClinicalAiParams {
  consultationId: string;
  symptoms: string;
  diagnosis: string;
  clinicalNotes: string;
  patientName: string;
  patientGender: string;
  patientAge: number | null;
  patientAllergies: PatientAllergyResponse[];
  patientMedicalHistory: string | null;
  bloodPressure: string;
  heartRate: string;
  temperature: string;
  prescriptionItems: PrescriptionItemRequest[];
  showToast: (message: string, type?: "success" | "danger" | "warning") => void;
  setHeartRate: (val: string) => void;
  setBloodPressure: (val: string) => void;
  setTemperature: (val: string) => void;
  setSpo2: (val: string) => void;
  setHeight: (val: string) => void;
  setWeight: (val: string) => void;
  setSymptoms: React.Dispatch<React.SetStateAction<string>>;
  setClinicalNotes: React.Dispatch<React.SetStateAction<string>>;
  setDiagnosis: (val: string) => void;
  setIcd10Code: (val: string) => void;
  setIcd10Name: (val: string) => void;
}

export function useClinicalAi({
  consultationId,
  symptoms,
  diagnosis,
  clinicalNotes,
  patientName,
  patientGender,
  patientAge,
  patientAllergies,
  patientMedicalHistory,
  bloodPressure,
  heartRate,
  temperature,
  prescriptionItems,
  showToast,
  setHeartRate,
  setBloodPressure,
  setTemperature,
  setSpo2,
  setHeight,
  setWeight,
  setSymptoms,
  setClinicalNotes,
  setDiagnosis,
  setIcd10Code,
  setIcd10Name,
}: UseClinicalAiParams) {
  const [aiLoading, setAiLoading] = useState(false);
  const [aiResponse, setAiResponse] = useState<ClinicalSuggestionResponse | null>(null);
  const [aiQueryInput, setAiQueryInput] = useState("");

  const {
    transcript: voiceTranscript,
    extractedFields: voiceExtractedFields,
    isPreviewOpen: isVoicePreviewOpen,
    closePreview: closeVoicePreview,
    openPreview: openVoicePreview,
    reset: resetVoiceStore,
    setIsProcessing: setVoiceIsProcessing,
  } = useVoiceStore();

  const compileLiveConsultationContext = () => {
    const contextParts: string[] = [];
    if (symptoms?.trim()) contextParts.push(`Triệu chứng: ${symptoms.trim()}`);
    if (diagnosis?.trim()) contextParts.push(`Chẩn đoán: ${diagnosis.trim()}`);
    if (clinicalNotes?.trim()) contextParts.push(`Bệnh sử / Diễn tiến: ${clinicalNotes.trim()}`);
    if (patientName) {
      contextParts.push(`Bệnh nhân: ${patientName} (${patientGender}, ${patientAge} tuổi)`);
    }
    if (patientAllergies.length > 0) {
      const allergyStr = patientAllergies
        .map((a) => `${a.allergyName} (${a.severity || "Dị ứng"})`)
        .join(", ");
      contextParts.push(`Dị ứng: ${allergyStr}`);
    }
    if (patientMedicalHistory) {
      contextParts.push(`Tiền sử bệnh: ${patientMedicalHistory}`);
    }
    const vitalsStr: string[] = [];
    if (bloodPressure) vitalsStr.push(`Huyết áp: ${bloodPressure}`);
    if (heartRate) vitalsStr.push(`Mạch: ${heartRate} lần/phút`);
    if (temperature) vitalsStr.push(`Nhiệt độ: ${temperature}°C`);
    if (vitalsStr.length > 0) contextParts.push(`Sinh hiệu: ${vitalsStr.join(", ")}`);
    if (prescriptionItems.length > 0) {
      const medNames = prescriptionItems.map((p) => `${p.medicineName} (${p.dosage || ""})`).join("; ");
      contextParts.push(`Thuốc đang chỉ định: ${medNames}`);
    }
    return contextParts.join(" | ");
  };

  const handleGetAiSuggestions = async (promptQuestion?: string) => {
    const query = (promptQuestion || aiQueryInput).trim();
    if (!query || aiLoading) return;

    setAiLoading(true);
    if (!promptQuestion) {
      setAiQueryInput("");
    }

    try {
      const liveContext = {
        pageRoute: `/consultation/${consultationId}`,
        pageTitle: "Khám bệnh Chi tiết",
        pageData: compileLiveConsultationContext(),
      };
      const res = await aiApi.getClinicalSuggestions(consultationId, query, liveContext);
      if (res.data) {
        setAiResponse(res.data);
      }
    } catch (error) {
      setAiResponse(null);
      showToast(getErrorMessage(error, "Trợ lý chưa sẵn sàng."), "danger");
    } finally {
      setAiLoading(false);
    }
  };

  const handleVoiceRecorded = async (audioBlob: Blob) => {
    setVoiceIsProcessing(true);
    try {
      const res = await aiApi.processVoiceToFields(audioBlob);
      if (res && res.data) {
        openVoicePreview(res.data.transcriptText || "", res.data.extractedFields || {});
      } else {
        showToast("AI không nhận diện được nội dung từ bản ghi âm.", "warning");
        resetVoiceStore();
      }
    } catch (err) {
      console.error("Voice STT error:", err);
      showToast(getErrorMessage(err, "Không thể xử lý âm thanh từ AI Service."), "danger");
      resetVoiceStore();
    } finally {
      setVoiceIsProcessing(false);
    }
  };

  const applyVoiceFields = (selectedFields: Partial<VoiceExtractedFields>) => {
    let appliedCount = 0;
    if (selectedFields.heartRate) {
      setHeartRate(selectedFields.heartRate);
      appliedCount++;
    }
    if (selectedFields.bloodPressure) {
      setBloodPressure(selectedFields.bloodPressure);
      appliedCount++;
    }
    if (selectedFields.temperature) {
      setTemperature(selectedFields.temperature);
      appliedCount++;
    }
    if (selectedFields.spo2) {
      setSpo2(selectedFields.spo2);
      appliedCount++;
    }
    if (selectedFields.height) {
      setHeight(selectedFields.height);
      appliedCount++;
    }
    if (selectedFields.weight) {
      setWeight(selectedFields.weight);
      appliedCount++;
    }
    if (selectedFields.symptoms) {
      setSymptoms((prev) => (prev ? `${prev}\n${selectedFields.symptoms}` : selectedFields.symptoms || ""));
      appliedCount++;
    }
    if (selectedFields.clinicalNotes) {
      setClinicalNotes((prev) =>
        prev ? `${prev}\n${selectedFields.clinicalNotes}` : selectedFields.clinicalNotes || ""
      );
      appliedCount++;
    }
    if (selectedFields.diagnosis) {
      setDiagnosis(selectedFields.diagnosis);
      appliedCount++;
    }
    if (selectedFields.icd10Code) {
      setIcd10Code(selectedFields.icd10Code);
      appliedCount++;
    }
    if (selectedFields.icd10Name) {
      setIcd10Name(selectedFields.icd10Name);
      appliedCount++;
    }

    closeVoicePreview();
    showToast(`Đã áp dụng thành công ${appliedCount} trường thông tin vào ca khám.`, "success");
  };

  return {
    aiLoading,
    aiResponse,
    aiQueryInput,
    setAiQueryInput,
    handleGetAiSuggestions,
    voiceTranscript,
    voiceExtractedFields,
    isVoicePreviewOpen,
    closeVoicePreview,
    handleVoiceRecorded,
    applyVoiceFields,
  };
}
