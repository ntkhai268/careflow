"use client";

import {
  forwardRef,
  useEffect,
  useImperativeHandle,
  useRef,
  ReactNode,
} from "react";
import { PrescriptionResponse } from "@/lib/prescription-api";
import {
  useVoicePrescriptionStore,
  formatVoiceTime,
  validVoiceDuration,
} from "@/stores/voice-prescription-store";

export interface VoiceDraftHandle {
  save: (replaceItems?: boolean) => Promise<PrescriptionResponse | null>;
  pending: () => boolean;
  open: (message?: string) => void;
  stop: () => Promise<void>;
  collapse: () => void;
}

interface Props {
  consultationId: string;
  actorId: string;
  prescriptionId: string | null;
  disabled: boolean;
  hasPrescription: boolean;
  canEdit: boolean;
  onSaveDraft: () => Promise<boolean>;
  children: ReactNode;
}

const VoicePrescriptionPanel = forwardRef<VoiceDraftHandle, Props>(
  function VoicePrescriptionPanel(
    {
      consultationId,
      actorId,
      prescriptionId,
      disabled,
      hasPrescription,
      canEdit,
      onSaveDraft,
      children,
    },
    ref
  ) {
    const panelRef = useRef<HTMLElement | null>(null);

    const {
      available,
      capError,
      recording,
      starting,
      finishing,
      seconds,
      level,
      busy,
      error,
      text,
      hasAudio,
      audioUrl,
      clipDuration,
      savedDuration,
      warnings,
      textMode,
      inputPending,
      review,
      checkCapabilities,
      startRecording,
      stopRecording,
      uploadAudio,
      setText,
      setTextMode,
      resetInput,
      setError,
      setClipDuration,
      executeDraft,
      initSessionMetadata,
      cleanup,
    } = useVoicePrescriptionStore();

    // Check capabilities on mount & cleanup on unmount
    useEffect(() => {
      void checkCapabilities();
      return () => {
        cleanup();
      };
    }, [checkCapabilities, cleanup]);

    // Restore previous session metadata if any
    useEffect(() => {
      void initSessionMetadata({ consultationId, prescriptionId, actorId });
    }, [consultationId, prescriptionId, actorId, initSessionMetadata]);

    // Expose imperative handle for parent components
    useImperativeHandle(
      ref,
      () => ({
        save: async (replaceItems = false) => {
          return executeDraft({
            consultationId,
            prescriptionId,
            actorId,
            replaceItems,
          });
        },
        pending: () => inputPending,
        open: (message?: string) => {
          if (message) setError(message);
          panelRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
        },
        stop: async () => {
          await stopRecording();
        },
        collapse: () => {
          resetInput(false);
        },
      }),
      [
        executeDraft,
        consultationId,
        prescriptionId,
        actorId,
        inputPending,
        setError,
        stopRecording,
        resetInput,
      ]
    );

    if (!available && !hasPrescription) {
      return (
        <>
          {children}
          <div className="rounded-xl border border-[#D5E0E6] bg-white p-3 text-xs text-[#667985]">
            Nhập toa giọng nói chưa được bật.{capError && <p role="alert">{capError}</p>}
          </div>
        </>
      );
    }

    const hasActiveDetails =
      recording ||
      finishing ||
      hasAudio ||
      textMode ||
      !!text ||
      !!busy ||
      !!error ||
      warnings.length > 0 ||
      inputPending ||
      (!inputPending && hasPrescription && savedDuration !== null);

    return (
      <section
        ref={panelRef}
        aria-label="Nhập toa bằng giọng nói"
        className="space-y-4"
      >
        {/* Compact Voice Toolbar — keeps Prescription list visible above the fold */}
        <div className="rounded-xl border border-[#8BD2EE]/70 bg-gradient-to-r from-[#EBF8FE] via-white to-[#D9F3EC]/40 p-3.5 shadow-[0_2px_8px_rgba(2,119,168,0.06)] space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2.5">
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="inline-flex h-2 w-2 rounded-full bg-[#0277A8]" />
                <h3 className="text-xs font-bold text-[#17242D] uppercase tracking-wider">
                  Nhập toa bằng giọng nói AI
                </h3>
              </div>
              <p className="text-[11px] text-[#4D626E] mt-0.5">
                Đọc toa thuốc hoặc tải file âm thanh (tối đa 3 phút / 20 MiB) để tự động trích xuất dòng thuốc
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-1.5">
              <button
                type="button"
                disabled={
                  disabled ||
                  !!busy ||
                  starting ||
                  finishing ||
                  !available ||
                  (hasPrescription && !canEdit)
                }
                onClick={() => {
                  if (recording) {
                    void stopRecording().catch((e) =>
                      setError(e instanceof Error ? e.message : "Chưa dừng được micro.")
                    );
                  } else {
                    void startRecording({ disabled, hasPrescription, canEdit });
                  }
                }}
                className={`rounded-lg px-3 py-1.5 text-xs font-bold text-white shadow-xs transition-all disabled:opacity-50 cursor-pointer ${
                  recording
                    ? "bg-[#BA1A1A] hover:bg-[#93000A]"
                    : "bg-[#0277A8] hover:bg-[#00577E]"
                }`}
              >
                {starting
                  ? "Đang mở micro…"
                  : finishing
                    ? "Đang chuẩn bị bản ghi…"
                    : recording
                      ? `Dừng ghi âm (${formatVoiceTime(seconds)})`
                      : hasAudio
                        ? "Ghi lại"
                        : hasPrescription
                          ? "Ghi thêm vào toa nháp"
                          : "Bật ghi âm AI"}
              </button>

              <label className="cursor-pointer rounded-lg border border-[#D5E0E6] bg-white hover:bg-[#F5F8FA] px-2.5 py-1.5 text-xs font-semibold text-[#17242D] transition-colors">
                Tải audio
                <input
                  aria-label="Tải audio"
                  type="file"
                  accept="audio/*"
                  className="hidden"
                  disabled={
                    disabled ||
                    !!busy ||
                    recording ||
                    starting ||
                    finishing ||
                    !available ||
                    (hasPrescription && !canEdit)
                  }
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) uploadAudio(file);
                    e.target.value = "";
                  }}
                />
              </label>

              <button
                type="button"
                disabled={
                  recording ||
                  starting ||
                  finishing ||
                  !!busy ||
                  !available ||
                  (hasPrescription && !canEdit) ||
                  disabled
                }
                onClick={() => {
                  resetInput(true, true);
                  panelRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
                }}
                className="rounded-lg border border-[#D5E0E6] bg-white hover:bg-[#F5F8FA] px-2.5 py-1.5 text-xs font-semibold text-[#17242D] transition-colors cursor-pointer disabled:opacity-50"
              >
                {hasPrescription ? "Nhập đoạn mới" : "Nhập transcript"}
              </button>

              {inputPending && !recording && (
                <button
                  type="button"
                  disabled={!!busy || starting || finishing}
                  onClick={() => resetInput(false)}
                  className="rounded-lg border border-[#D5E0E6] bg-white hover:bg-[#FFDAD6]/40 hover:text-[#BA1A1A] px-2.5 py-1.5 text-xs font-semibold text-[#4D626E] transition-colors cursor-pointer"
                >
                  Bỏ đoạn mới
                </button>
              )}
            </div>
          </div>

          {hasActiveDetails && (
            <div className="space-y-2.5 pt-2 border-t border-[#D5E0E6]/70">
              {recording && (
                <div className="rounded-lg border border-[#BA1A1A]/30 bg-[#FFDAD6]/40 p-2.5">
                  <p role="status" className="text-xs font-bold text-[#BA1A1A]">
                    Đang ghi âm · {formatVoiceTime(seconds)} / 03:00
                  </p>
                  {level !== null && (
                    <>
                      <meter
                        aria-label="Mức âm thanh micro"
                        min={0}
                        max={1}
                        value={Math.min(1, level * 5)}
                        className="mt-1.5 w-full"
                      />
                      <p className="text-[11px] text-[#4D626E]">
                        {level > 0.01
                          ? "Micro đang thu âm thanh"
                          : seconds >= 2
                            ? "Chưa phát hiện tiếng rõ — kiểm tra micro và thử nói gần hơn."
                            : "Micro đang mở…"}
                      </p>
                    </>
                  )}
                </div>
              )}

              {finishing && (
                <p role="status" className="text-xs font-medium text-[#00577E]">
                  Đang chuẩn bị bản ghi{" "}
                  {clipDuration !== null ? formatVoiceTime(clipDuration) : ""} để nghe lại…
                </p>
              )}

              {hasAudio && !recording && !finishing && (
                <div
                  aria-label="Bản ghi chưa thêm vào toa"
                  className="rounded-lg border border-[#A86100]/30 bg-[#FFEBCB] p-2.5"
                >
                  <p className="text-xs font-bold text-[#A86100]">
                    {clipDuration !== null
                      ? `Đã ghi ${formatVoiceTime(clipDuration)}`
                      : "Đã có audio"}{" "}
                    · Chưa thêm vào toa nháp
                  </p>
                  <p className="mt-0.5 text-[11px] text-[#A86100]">
                    Nghe lại rồi bấm Lưu toa nháp để xử lý. Ghi lại sẽ thay đoạn chưa lưu này.
                  </p>
                </div>
              )}

              {!inputPending && hasPrescription && savedDuration !== null && (
                <p
                  aria-label="Thời lượng bản ghi đã xử lý"
                  className="rounded-lg bg-[#DDF4E7] px-3 py-2 text-xs font-semibold text-[#1B7F4B]"
                >
                  Đã xử lý đoạn ghi âm · {formatVoiceTime(savedDuration)}
                </p>
              )}

              {hasPrescription && !canEdit && (
                <p className="text-[11px] text-[#667985]">Toa đã ký hoặc bị khóa.</p>
              )}

              {audioUrl && (
                <audio
                  aria-label="Nghe lại bản ghi"
                  controls
                  preload="metadata"
                  src={audioUrl}
                  onLoadedMetadata={(e) => {
                    const value = e.currentTarget.duration;
                    if (validVoiceDuration(value)) setClipDuration(value);
                  }}
                  className="w-full h-9"
                />
              )}

              {(textMode || !!text || hasAudio) && (
                <label className="block text-xs font-medium text-[#4D626E]">
                  {hasPrescription && !inputPending
                    ? "Đoạn đọc đã lưu gần nhất — chỉnh thuốc bên dưới"
                    : "Transcript đoạn mới — có thể sửa trước khi lưu"}
                  <textarea
                    aria-label="Transcript toa thuốc"
                    placeholder={
                      hasAudio && !text
                        ? "Transcript sẽ hiển thị sau khi nhận dạng audio."
                        : "Nhập lời đọc hoặc ghi âm để nhận transcript…"
                    }
                    value={text}
                    disabled={
                      !!busy ||
                      starting ||
                      finishing ||
                      (hasPrescription && !inputPending) ||
                      disabled ||
                      recording ||
                      (hasAudio && !text)
                    }
                    maxLength={12000}
                    onChange={(e) => {
                      setText(e.target.value);
                    }}
                    className="mt-1 block w-full rounded-lg border border-[#D5E0E6] bg-white p-2.5 text-xs text-[#17242D] min-h-20 focus:border-[#0277A8] focus:outline-none"
                  />
                </label>
              )}

              {busy && (
                <p
                  role="status"
                  aria-live="polite"
                  className="text-xs font-semibold text-[#0277A8]"
                >
                  {busy}
                </p>
              )}

              {error && (
                <p
                  role="alert"
                  className="rounded-lg bg-[#FFDAD6] border border-[#BA1A1A]/25 p-2.5 text-xs font-semibold text-[#BA1A1A]"
                >
                  {error}
                </p>
              )}

              {review?.timings && (
                <p className="text-[11px] text-[#667985]">
                  {review.timings.asrMs !== undefined
                    ? `Nhận dạng ${(review.timings.asrMs / 1000).toFixed(2)} giây · `
                    : ""}
                  Đọc toa {(review.timings.extractionMs / 1000).toFixed(2)} giây
                </p>
              )}

              {warnings.length > 0 && (
                <details className="rounded-lg border border-[#A86100]/30 bg-[#FFEBCB] p-2.5 text-xs text-[#A86100]">
                  <summary className="cursor-pointer font-semibold">
                    {warnings.length} đoạn cần kiểm tra trong transcript
                  </summary>
                  <ul className="mt-2 space-y-1.5">
                    {warnings.map((w, i) => (
                      <li key={i}>{w}</li>
                    ))}
                  </ul>
                </details>
              )}

              {inputPending && (
                <button
                  type="button"
                  disabled={
                    disabled ||
                    !!busy ||
                    starting ||
                    finishing ||
                    (hasPrescription && !canEdit) ||
                    (!hasPrescription && !recording && !hasAudio && !text.trim())
                  }
                  onClick={() => void onSaveDraft()}
                  className="w-full rounded-xl bg-[#0277A8] hover:bg-[#00577E] px-4 py-2.5 text-xs font-bold text-white disabled:opacity-50 transition-colors cursor-pointer"
                >
                  {busy ? "ĐANG LƯU..." : "XỬ LÝ & LƯU TOA NHÁP"}
                </button>
              )}
            </div>
          )}
        </div>

        {children}
      </section>
    );
  }
);

export default VoicePrescriptionPanel;
