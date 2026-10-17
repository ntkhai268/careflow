"use client";

import { useState, useRef, useCallback } from "react";

interface VoiceRecorderButtonProps {
  onAudioReady: (blob: Blob) => void;
  disabled?: boolean;
  isProcessing?: boolean;
}

export default function VoiceRecorderButton({
  onAudioReady,
  disabled = false,
  isProcessing = false,
}: VoiceRecorderButtonProps) {
  const [isRecording, setIsRecording] = useState(false);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);

  const startRecording = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
        ? "audio/webm;codecs=opus"
        : "audio/webm";

      const recorder = new MediaRecorder(stream, { mimeType });
      chunksRef.current = [];

      recorder.ondataavailable = (e) => {
        if (e.data && e.data.size > 0) {
          chunksRef.current.push(e.data);
        }
      };

      recorder.onstop = () => {
        const audioBlob = new Blob(chunksRef.current, { type: mimeType });
        stream.getTracks().forEach((track) => track.stop());
        if (audioBlob.size > 0) {
          onAudioReady(audioBlob);
        }
      };

      recorder.start(100);
      mediaRecorderRef.current = recorder;
      setIsRecording(true);
    } catch (err) {
      console.error("Failed to access microphone:", err);
      alert("Không thể truy cập microphone. Vui lòng cấp quyền micro cho trình duyệt.");
    }
  }, [onAudioReady]);

  const stopRecording = useCallback(() => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
      mediaRecorderRef.current.stop();
    }
    setIsRecording(false);
  }, []);

  if (isProcessing) {
    return (
      <button
        type="button"
        disabled
        className="flex items-center gap-2 px-3 py-1.5 text-xs font-semibold bg-purple-100 text-[#6E2582] rounded-lg border border-purple-200 opacity-80 cursor-wait shadow-sm"
      >
        <span className="animate-spin inline-block w-3.5 h-3.5 border-2 border-[#6E2582] border-t-transparent rounded-full" />
        AI đang nhận dạng giọng nói...
      </button>
    );
  }

  if (isRecording) {
    return (
      <button
        type="button"
        onClick={stopRecording}
        className="flex items-center gap-2 px-3.5 py-1.5 text-xs font-bold bg-rose-600 hover:bg-rose-700 text-white rounded-lg animate-pulse shadow-md transition-all"
      >
        <span className="inline-block w-2.5 h-2.5 bg-white rounded-sm" />
        Dừng ghi âm (Xong)
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={startRecording}
      disabled={disabled}
      className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold bg-purple-50 text-[#6E2582] hover:bg-purple-100 hover:text-purple-900 rounded-lg border border-purple-200 disabled:opacity-40 disabled:cursor-not-allowed transition-all shadow-sm"
      title="Nói để AI tự động điền các chỉ số sinh hiệu, triệu chứng và chẩn đoán"
    >
      <span>🎙️</span>
      <span>Ghi âm lời khám</span>
    </button>
  );
}
