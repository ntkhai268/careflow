import { create } from "zustand";
import { fixWebmDuration } from "@fix-webm-duration/fix";
import {
  voicePrescriptionApi,
  VoiceJob,
  VoiceResult,
} from "@/lib/voice-prescription-api";
import { PrescriptionResponse } from "@/lib/prescription-api";

export const formatVoiceTime = (seconds: number) => {
  const value = Math.max(0, Math.round(seconds));
  return `${Math.floor(value / 60)
    .toString()
    .padStart(2, "0")}:${(value % 60).toString().padStart(2, "0")}`;
};

export const validVoiceDuration = (value: unknown): value is number =>
  typeof value === "number" && Number.isFinite(value) && value > 0 && value <= 181;

export interface VoicePrescriptionState {
  // Capability & Availability
  available: boolean;
  capError: string;

  // Recording State
  recording: boolean;
  starting: boolean;
  finishing: boolean;
  seconds: number;
  level: number | null;

  // Processing & Input State
  busy: string;
  error: string;
  text: string;
  hasAudio: boolean;
  audioUrl: string;
  clipDuration: number | null;
  savedDuration: number | null;
  warnings: string[];
  textMode: boolean;
  inputPending: boolean;
  review: VoiceResult | null;

  // Internal references & tracking
  currentJob: VoiceJob | null;
  createdPrescription: PrescriptionResponse | null;

  // Actions
  checkCapabilities: () => Promise<boolean>;
  startRecording: (options: {
    disabled?: boolean;
    hasPrescription?: boolean;
    canEdit?: boolean;
  }) => Promise<void>;
  stopRecording: () => Promise<Blob | null>;
  cancelRecording: () => void;
  uploadAudio: (file: File) => void;
  setText: (text: string) => void;
  setTextMode: (mode: boolean) => void;
  resetInput: (pending: boolean, mode?: boolean) => void;
  setError: (error: string) => void;
  setClipDuration: (duration: number | null) => void;
  executeDraft: (params: {
    consultationId: string;
    prescriptionId: string | null;
    actorId: string;
    replaceItems?: boolean;
  }) => Promise<PrescriptionResponse | null>;
  initSessionMetadata: (params: {
    consultationId: string;
    prescriptionId: string | null;
    actorId: string;
  }) => void;
  cleanup: () => void;
}

// Module-level Web API holders to keep the Zustand state clean and serializable
let activeStream: MediaStream | null = null;
let activeRecorder: MediaRecorder | null = null;
let activeAudioChunks: Blob[] = [];
let activeAudioBlob: Blob | null = null;
let activeMeter: AudioContext | null = null;
let activeAnalyser: AnalyserNode | null = null;
let timerInterval: ReturnType<typeof setInterval> | null = null;
let abortController: AbortController = new AbortController();
let currentIdempotencyKey: string = typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : "";
let submittedTranscript: string = "";
let recordingStartTime: number = 0;
let stopPromiseResolver: ((blob: Blob | null) => void) | null = null;

const stopMeter = () => {
  if (activeMeter && activeMeter.state !== "closed") {
    void activeMeter.close().catch(() => {});
  }
  activeMeter = null;
  activeAnalyser = null;
};

const stopTimer = () => {
  if (timerInterval) {
    clearInterval(timerInterval);
    timerInterval = null;
  }
};

const revokeAudioUrl = (url: string) => {
  if (url && url.startsWith("blob:")) {
    URL.revokeObjectURL(url);
  }
};

export const useVoicePrescriptionStore = create<VoicePrescriptionState>((set, get) => ({
  available: false,
  capError: "",
  recording: false,
  starting: false,
  finishing: false,
  seconds: 0,
  level: null,
  busy: "",
  error: "",
  text: "",
  hasAudio: false,
  audioUrl: "",
  clipDuration: null,
  savedDuration: null,
  warnings: [],
  textMode: false,
  inputPending: false,
  review: null,
  currentJob: null,
  createdPrescription: null,

  checkCapabilities: async () => {
    try {
      const res = await voicePrescriptionApi.capabilities();
      const enabled = res.data.voicePrescription;
      set({ available: enabled, capError: "" });
      return enabled;
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Chưa kiểm tra được tính năng giọng nói.";
      set({ available: false, capError: msg });
      return false;
    }
  },

  startRecording: async ({ disabled, hasPrescription, canEdit }) => {
    const state = get();
    if (
      state.starting ||
      state.finishing ||
      state.recording ||
      disabled ||
      !!state.busy ||
      !state.available ||
      (hasPrescription && !canEdit)
    ) {
      return;
    }

    set({ starting: true, error: "" });

    try {
      if (
        !window.isSecureContext ||
        !navigator.mediaDevices?.getUserMedia ||
        typeof MediaRecorder === "undefined"
      ) {
        throw new Error("Trình duyệt cần HTTPS/localhost và hỗ trợ ghi âm.");
      }

      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      activeStream = stream;

      if (!stream.getAudioTracks().some((t) => t.readyState === "live" && t.enabled)) {
        throw new Error("Micro chưa sẵn sàng. Hãy kiểm tra quyền mic của trình duyệt.");
      }

      // Audio level meter
      try {
        const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
        const ctx = new AudioCtx();
        const source = ctx.createMediaStreamSource(stream);
        const analyser = ctx.createAnalyser();
        analyser.fftSize = 256;
        source.connect(analyser);
        activeMeter = ctx;
        activeAnalyser = analyser;
      } catch {
        // Meter is optional
      }

      const mimeType = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
        ? "audio/webm;codecs=opus"
        : "audio/webm";

      const recorder = new MediaRecorder(stream, { mimeType });
      activeRecorder = recorder;
      activeAudioChunks = [];
      activeAudioBlob = null;

      recorder.ondataavailable = (e) => {
        if (e.data && e.data.size > 0) {
          activeAudioChunks.push(e.data);
        }
      };

      recorder.onstop = async () => {
        stopTimer();
        stopMeter();
        if (activeStream) {
          activeStream.getTracks().forEach((t) => t.stop());
          activeStream = null;
        }

        set({ recording: false, finishing: true, level: null });

        try {
          const rawBlob = new Blob(activeAudioChunks, { type: mimeType });
          activeAudioChunks = [];

          let durationSec = Math.max(0.1, (performance.now() - recordingStartTime) / 1000);
          durationSec = Math.min(180, durationSec);

          let finalBlob = rawBlob;
          try {
            finalBlob = await fixWebmDuration(rawBlob, durationSec * 1000);
          } catch {
            finalBlob = rawBlob;
          }

          activeAudioBlob = finalBlob;
          revokeAudioUrl(get().audioUrl);
          const newUrl = URL.createObjectURL(finalBlob);

          set({
            hasAudio: true,
            audioUrl: newUrl,
            clipDuration: durationSec,
            finishing: false,
            inputPending: true,
          });

          if (stopPromiseResolver) {
            stopPromiseResolver(finalBlob);
            stopPromiseResolver = null;
          }
        } catch {
          set({ finishing: false, error: "Lỗi xử lý file âm thanh." });
          if (stopPromiseResolver) {
            stopPromiseResolver(null);
            stopPromiseResolver = null;
          }
        }
      };

      recordingStartTime = performance.now();
      recorder.start(250);

      set({
        recording: true,
        starting: false,
        seconds: 0,
        inputPending: true,
        error: "",
      });

      // Start timer & meter loop
      stopTimer();
      timerInterval = setInterval(() => {
        const sec = Math.floor((performance.now() - recordingStartTime) / 1000);
        set({ seconds: sec });

        if (sec >= 180) {
          void get().stopRecording();
          return;
        }

        if (activeAnalyser) {
          const values = new Uint8Array(activeAnalyser.fftSize);
          activeAnalyser.getByteTimeDomainData(values);
          let sum = 0;
          for (const val of values) {
            sum += ((val - 128) / 128) ** 2;
          }
          set({ level: Math.sqrt(sum / values.length) });
        }
      }, 200);
    } catch (err: unknown) {
      stopMeter();
      if (activeStream) {
        activeStream.getTracks().forEach((t) => t.stop());
        activeStream = null;
      }
      const msg = err instanceof Error ? err.message : "Không thể bắt đầu ghi âm.";
      set({ starting: false, recording: false, error: msg });
    }
  },

  stopRecording: async () => {
    if (activeRecorder && activeRecorder.state === "recording") {
      return new Promise<Blob | null>((resolve) => {
        stopPromiseResolver = resolve;
        activeRecorder?.stop();
      });
    }
    return activeAudioBlob;
  },

  cancelRecording: () => {
    stopTimer();
    stopMeter();
    if (activeStream) {
      activeStream.getTracks().forEach((t) => t.stop());
      activeStream = null;
    }
    if (activeRecorder && activeRecorder.state === "recording") {
      try {
        activeRecorder.stop();
      } catch {}
    }
    activeRecorder = null;
    activeAudioChunks = [];
    activeAudioBlob = null;
    if (stopPromiseResolver) {
      stopPromiseResolver(null);
      stopPromiseResolver = null;
    }
    get().resetInput(false);
  },

  uploadAudio: (file: File) => {
    const state = get();
    if (
      state.recording ||
      state.starting ||
      state.finishing ||
      !state.available ||
      !!state.busy
    ) {
      return;
    }

    if (!file || !file.size) {
      set({ error: "File audio rỗng." });
      return;
    }
    if (file.size > 20 * 1024 * 1024) {
      set({ error: "Audio vượt quá dung lượng cho phép (tối đa 20 MiB)." });
      return;
    }

    get().resetInput(true, false);
    activeAudioBlob = file;
    revokeAudioUrl(state.audioUrl);
    const newUrl = URL.createObjectURL(file);

    set({
      hasAudio: true,
      audioUrl: newUrl,
      inputPending: true,
      error: "",
    });
  },

  setText: (text: string) => {
    set({
      text,
      inputPending: true,
      review: null,
      warnings: [],
      error: "",
    });
    if (get().currentJob && ["FAILED", "CANCELLED", "EXPIRED"].includes(get().currentJob!.state)) {
      set({ currentJob: null });
    }
    if (!get().currentJob) {
      currentIdempotencyKey = typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : "";
    }
  },

  setTextMode: (mode: boolean) => {
    set({ textMode: mode });
  },

  setClipDuration: (duration: number | null) => {
    set({ clipDuration: duration });
  },

  setError: (error: string) => {
    set({ error });
  },

  resetInput: (pending: boolean, mode = false) => {
    activeAudioBlob = null;
    revokeAudioUrl(get().audioUrl);
    submittedTranscript = "";
    currentIdempotencyKey = typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : "";

    set({
      inputPending: pending,
      hasAudio: false,
      audioUrl: "",
      text: "",
      textMode: mode,
      warnings: [],
      review: null,
      error: "",
      busy: "",
      currentJob: null,
      createdPrescription: null,
      seconds: 0,
      clipDuration: null,
      level: null,
    });
  },

  executeDraft: async ({ consultationId, prescriptionId, actorId, replaceItems = false }) => {
    const state = get();
    if (!state.inputPending || state.createdPrescription) return null;
    if (!activeAudioBlob && !activeRecorder && !state.text.trim()) return null;

    set({ error: "", busy: "Đang dừng ghi âm…" });
    const signal = abortController.signal;

    try {
      if (activeRecorder?.state === "recording") {
        await get().stopRecording();
      }
      if (signal.aborted) throw new DOMException("Phiên làm việc đã thay đổi", "AbortError");

      set({ busy: "Đang nhận dạng và đọc toa AI…" });

      let job = state.currentJob;

      // Revise transcript if edited
      if (job && state.text.trim() && state.text.trim() !== submittedTranscript) {
        job = (await voicePrescriptionApi.revise(job, state.text.trim(), signal)).data;
        submittedTranscript = state.text.trim();
        set({ currentJob: job });
      }

      // Create new job if not yet created
      if (!job) {
        const input = state.textMode ? state.text.trim() : activeAudioBlob || state.text.trim();
        if (!input) throw new Error("Chưa có bản ghi âm hoặc transcript.");

        if (!currentIdempotencyKey) {
          currentIdempotencyKey = typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : "";
        }

        job = (await voicePrescriptionApi.create(consultationId, currentIdempotencyKey, input, signal)).data;
        submittedTranscript = typeof input === "string" ? input : "";
        set({ currentJob: job });
      }

      // Poll job until SUCCEEDED or FAILED
      const deadline = Date.now() + 240000;
      while (["QUEUED", "RUNNING"].includes(job.state)) {
        if (Date.now() > deadline) {
          throw new Error("AI vẫn đang xử lý. Hãy bấm Lưu toa nháp để kiểm tra lại công việc hiện tại.");
        }

        await new Promise<void>((resolve, reject) => {
          const timer = setTimeout(onTick, 800);
          function onTick() {
            signal.removeEventListener("abort", onAbort);
            resolve();
          }
          function onAbort() {
            clearTimeout(timer);
            reject(new DOMException("Phiên làm việc đã thay đổi", "AbortError"));
          }
          signal.addEventListener("abort", onAbort, { once: true });
        });

        job = (await voicePrescriptionApi.get(job.jobId, signal)).data;
        set({ currentJob: job });
      }

      if (job.result) {
        set({
          text: job.result.transcript,
          warnings: job.result.warnings || [],
          review: job.result,
        });
        submittedTranscript = job.result.transcript;
      }

      if (job.state !== "SUCCEEDED") {
        throw new Error(
          `Không tạo được đề xuất (${job.errorCode || job.state}). Hãy ghi lại hoặc nhập transcript mới.`
        );
      }

      if (!job.result?.items.length) {
        throw new Error(
          "Chưa nhận dạng được tên thuốc nào. Hãy bổ sung tên thuốc trong transcript rồi bấm Lưu lại."
        );
      }

      set({ busy: prescriptionId ? "Đang thêm thuốc vào toa nháp…" : "Đang lưu toa nháp…" });

      const saved = (
        await voicePrescriptionApi.editableDraft(job, signal, prescriptionId, replaceItems)
      ).data;

      if (signal.aborted) throw new DOMException("Phiên làm việc đã thay đổi", "AbortError");

      const finalSavedDuration = validVoiceDuration(job.result?.timings?.audioDurationSeconds)
        ? job.result.timings.audioDurationSeconds
        : state.clipDuration;

      set({
        createdPrescription: saved,
        inputPending: false,
        savedDuration: finalSavedDuration,
        busy: "",
      });

      // Session storage metadata persistence
      const metadataKey = `careflow.voice.saved:${actorId}:${consultationId}`;
      try {
        sessionStorage.setItem(
          metadataKey,
          JSON.stringify({
            jobId: job.jobId,
            prescriptionId: saved.id,
            ...(validVoiceDuration(finalSavedDuration) ? { audioDurationSeconds: finalSavedDuration } : {}),
          })
        );
      } catch {
        // Storage is optional
      }

      return saved;
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Chưa xử lý được đơn thuốc.";
      set({ busy: "", error: msg });
      throw err;
    }
  },

  initSessionMetadata: async ({ consultationId, prescriptionId, actorId }) => {
    if (!prescriptionId) return;
    const metadataKey = `careflow.voice.saved:${actorId}:${consultationId}`;

    try {
      const savedRaw = sessionStorage.getItem(metadataKey);
      if (!savedRaw) return;
      const saved = JSON.parse(savedRaw);
      if (saved?.prescriptionId !== prescriptionId) return;

      if (!get().inputPending && validVoiceDuration(saved.audioDurationSeconds)) {
        set({ savedDuration: saved.audioDurationSeconds });
      }

      if (typeof saved.jobId !== "string" || !/^[0-9a-f-]{36}$/i.test(saved.jobId)) return;

      const res = await voicePrescriptionApi.get(saved.jobId, abortController.signal);
      const job = res.data;

      if (
        !get().inputPending &&
        job.consultationId === consultationId &&
        job.state === "SUCCEEDED" &&
        job.result
      ) {
        set({
          text: job.result.transcript,
          review: job.result,
          warnings: job.result.warnings || [],
          currentJob: job,
        });

        if (validVoiceDuration(job.result.timings?.audioDurationSeconds)) {
          set({ savedDuration: job.result.timings.audioDurationSeconds });
        }
      }
    } catch {
      // Optional restoration
    }
  },

  cleanup: () => {
    stopTimer();
    stopMeter();
    if (activeStream) {
      activeStream.getTracks().forEach((t) => t.stop());
      activeStream = null;
    }
    abortController.abort();
    abortController = new AbortController();
    revokeAudioUrl(get().audioUrl);
  },
}));
