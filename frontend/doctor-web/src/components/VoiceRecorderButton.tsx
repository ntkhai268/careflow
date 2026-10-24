"use client";

import { useEffect, useRef, useCallback } from "react";
import { useVoiceStore } from "@/stores/voice-store";

interface VoiceRecorderButtonProps {
  onAudioReady?: (blob: Blob) => void;
  disabled?: boolean;
}

export default function VoiceRecorderButton({
  onAudioReady,
  disabled = false,
}: VoiceRecorderButtonProps) {
  const {
    isRecording,
    isProcessing,
    recordingDuration,
    startRecording: setStoreRecording,
    stopRecording: setStoreStop,
    cancelRecording: setStoreCancel,
    incrementDuration,
  } = useVoiceStore();

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const isCancelledRef = useRef<boolean>(false);

  // Timer interval effect
  useEffect(() => {
    let timer: NodeJS.Timeout | null = null;
    if (isRecording) {
      timer = setInterval(() => {
        incrementDuration();
      }, 1000);
    }
    return () => {
      if (timer) clearInterval(timer);
    };
  }, [isRecording, incrementDuration]);

  // Clean up streams on unmount
  useEffect(() => {
    return () => {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((track) => track.stop());
      }
    };
  }, []);

  const handleStartRecording = useCallback(async () => {
    try {
      isCancelledRef.current = false;
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;

      const mimeType = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
        ? "audio/webm;codecs=opus"
        : "audio/webm";

      const recorder = new MediaRecorder(stream, { mimeType });
      chunksRef.current = [];

      recorder.ondataavailable = (e) => {
        if (!isCancelledRef.current && e.data && e.data.size > 0) {
          chunksRef.current.push(e.data);
        }
      };

      recorder.onstop = () => {
        stream.getTracks().forEach((track) => track.stop());
        streamRef.current = null;

        if (isCancelledRef.current) {
          chunksRef.current = [];
          return;
        }

        const audioBlob = new Blob(chunksRef.current, { type: mimeType });
        if (audioBlob.size > 0 && onAudioReady) {
          onAudioReady(audioBlob);
        }
      };

      recorder.start(100);
      mediaRecorderRef.current = recorder;
      setStoreRecording();
    } catch (err) {
      console.error("Failed to access microphone:", err);
      alert("Không thể truy cập microphone. Vui lòng cấp quyền micro cho trình duyệt.");
      setStoreCancel();
    }
  }, [onAudioReady, setStoreRecording, setStoreCancel]);

  const handleStopRecording = useCallback(() => {
    isCancelledRef.current = false;
    setStoreStop();
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
      mediaRecorderRef.current.stop();
    }
  }, [setStoreStop]);

  const handleCancelRecording = useCallback(() => {
    isCancelledRef.current = true;
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
      try {
        mediaRecorderRef.current.stop();
      } catch {
        // ignore
      }
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    chunksRef.current = [];
    setStoreCancel();
  }, [setStoreCancel]);

  const formatSeconds = (sec: number) => {
    const m = Math.floor(sec / 60).toString().padStart(2, "0");
    const s = (sec % 60).toString().padStart(2, "0");
    return `${m}:${s}`;
  };

  // 1. Processing state with Cancel option
  if (isProcessing) {
    return (
      <div className="flex items-center gap-2">
        <div className="flex items-center gap-2 px-3 py-1.5 text-xs font-semibold bg-purple-100 text-[#6E2582] rounded-md border border-purple-200 shadow-xs">
          <span className="animate-spin inline-block w-3.5 h-3.5 border-2 border-[#6E2582] border-t-transparent rounded-full" />
          <span>AI đang bóc tách lời khám...</span>
        </div>
        <button
          type="button"
          onClick={handleCancelRecording}
          className="px-2.5 py-1.5 text-xs font-medium text-slate-600 hover:text-slate-900 bg-white hover:bg-slate-100 border border-card-border rounded-md transition-all cursor-pointer"
          title="Hủy xử lý và ghi âm lại"
        >
          Hủy
        </button>
      </div>
    );
  }

  // 2. Recording state with Stop (Done) AND Cancel buttons
  if (isRecording) {
    return (
      <div className="flex items-center gap-2">
        <div className="flex items-center gap-2 px-2.5 py-1.5 bg-rose-50 border border-rose-200 rounded-md">
          <span className="w-2.5 h-2.5 rounded-full bg-rose-600 animate-pulse" />
          <span className="font-mono text-xs font-bold text-rose-700">
            {formatSeconds(recordingDuration)}
          </span>
        </div>

        {/* Done / Finish recording button */}
        <button
          type="button"
          onClick={handleStopRecording}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold bg-rose-600 hover:bg-rose-700 text-white rounded-md shadow-xs transition-all cursor-pointer"
        >
          <span>Xong (Bóc tách)</span>
        </button>

        {/* Cancel button to record again immediately */}
        <button
          type="button"
          onClick={handleCancelRecording}
          className="px-2.5 py-1.5 text-xs font-medium text-slate-600 hover:text-rose-700 bg-white hover:bg-slate-100 border border-card-border rounded-md transition-all cursor-pointer"
          title="Hủy đoạn ghi âm này để nói lại từ đầu"
        >
          Hủy & Nói lại
        </button>
      </div>
    );
  }

  // 3. Idle state
  return (
    <button
      type="button"
      onClick={handleStartRecording}
      disabled={disabled}
      className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold bg-white hover:bg-purple-50 text-[#6E2582] border border-purple-200 hover:border-purple-300 rounded-md disabled:opacity-40 disabled:cursor-not-allowed transition-all shadow-xs cursor-pointer"
      title="Nói để AI tự động điền các chỉ số sinh hiệu, triệu chứng và chẩn đoán"
    >
      <span className="text-sm">🎙️</span>
      <span>Ghi âm lời khám</span>
    </button>
  );
}
