import { create } from "zustand";
import { VoiceExtractedFields } from "@/lib/ai-api";

export interface VoiceState {
  isRecording: boolean;
  isProcessing: boolean;
  recordingDuration: number;
  transcript: string;
  extractedFields: VoiceExtractedFields | null;
  isPreviewOpen: boolean;
  error: string | null;

  // Actions
  startRecording: () => void;
  stopRecording: () => void;
  cancelRecording: () => void;
  setIsProcessing: (processing: boolean) => void;
  setRecordingDuration: (duration: number) => void;
  incrementDuration: () => void;
  setVoiceResult: (transcript: string, fields: VoiceExtractedFields) => void;
  openPreview: (transcript: string, fields: VoiceExtractedFields) => void;
  closePreview: () => void;
  setError: (error: string | null) => void;
  reset: () => void;
}

export const useVoiceStore = create<VoiceState>((set) => ({
  isRecording: false,
  isProcessing: false,
  recordingDuration: 0,
  transcript: "",
  extractedFields: null,
  isPreviewOpen: false,
  error: null,

  startRecording: () =>
    set({
      isRecording: true,
      isProcessing: false,
      recordingDuration: 0,
      error: null,
    }),

  stopRecording: () =>
    set({
      isRecording: false,
      isProcessing: true,
    }),

  cancelRecording: () =>
    set({
      isRecording: false,
      isProcessing: false,
      recordingDuration: 0,
      transcript: "",
      extractedFields: null,
      isPreviewOpen: false,
      error: null,
    }),

  setIsProcessing: (processing) =>
    set({
      isProcessing: processing,
    }),

  setRecordingDuration: (duration) =>
    set({
      recordingDuration: duration,
    }),

  incrementDuration: () =>
    set((state) => ({
      recordingDuration: state.recordingDuration + 1,
    })),

  setVoiceResult: (transcript, fields) =>
    set({
      transcript,
      extractedFields: fields,
      isProcessing: false,
      isPreviewOpen: true,
      error: null,
    }),

  openPreview: (transcript, fields) =>
    set({
      transcript,
      extractedFields: fields,
      isPreviewOpen: true,
      isProcessing: false,
    }),

  closePreview: () =>
    set({
      isPreviewOpen: false,
    }),

  setError: (error) =>
    set({
      error,
      isProcessing: false,
      isRecording: false,
    }),

  reset: () =>
    set({
      isRecording: false,
      isProcessing: false,
      recordingDuration: 0,
      transcript: "",
      extractedFields: null,
      isPreviewOpen: false,
      error: null,
    }),
}));
