"use client";

import { useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import { QueueEntry } from "@/lib/queue-api";
import { PRIORITY_LABELS, ReceptionStatusText } from "./QueueBadges";

interface HospitalQrCheckInCardProps {
  hospitalQr: {
    siteId: string;
    qrToken: string;
    sessionCode: string;
    sessionDate: string;
    expiresAt: string;
  } | null;
  isLoadingQr: boolean;
  qrError: string | null;
  onRefreshQr: () => void;
  qrInput: string;
  setQrInput: (val: string) => void;
  error: string | null;
  isSubmitting: boolean;
  roomId: string;
  onSubmitCheckIn: (e: React.FormEvent) => void;
  checkInResult: QueueEntry | null;
}

export default function HospitalQrCheckInCard({
  hospitalQr,
  isLoadingQr,
  qrError,
  onRefreshQr,
  qrInput,
  setQrInput,
  error,
  isSubmitting,
  roomId,
  onSubmitCheckIn,
  checkInResult,
}: HospitalQrCheckInCardProps) {
  const [copiedQr, setCopiedQr] = useState<string | null>(null);
  const [copyError, setCopyError] = useState<string | null>(null);

  return (
    <section className="space-y-4">
      <div className="rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-xs">
        <div className="mb-4 flex items-start justify-between gap-4">
          <div>
            <h2 className="text-[15px] font-extrabold text-[#17242D]">QR check-in bệnh viện</h2>
            <p className="mt-0.5 text-[11px] text-[#667985]">
              Bệnh nhân dùng Patient Mobile quét mã này tại bệnh viện.
            </p>
          </div>
          <button
            type="button"
            onClick={onRefreshQr}
            className="rounded-lg border border-[#8BD2EE] bg-[#DDF3FC]/60 px-2.5 py-1 text-[11px] font-bold text-[#00577E] hover:bg-[#DDF3FC]"
          >
            Tạo mã mới
          </button>
        </div>

        <div className="rounded-xl border border-dashed border-[#8BD2EE] bg-[#F5F8FA] p-4 text-center">
          {isLoadingQr ? (
            <div className="flex min-h-[200px] items-center justify-center">
              <LoadingSpinner size="md" />
            </div>
          ) : hospitalQr ? (
            <>
              <div className="mx-auto w-fit rounded-xl border border-[#D5E0E6] bg-white p-3 shadow-xs">
                <QRCodeSVG value={hospitalQr.qrToken} size={184} level="M" includeMargin />
              </div>
              <p className="mt-2.5 font-mono text-xs font-bold text-[#17242D]">
                {hospitalQr.siteId} · {hospitalQr.sessionCode}
              </p>
              <p className="mt-0.5 text-[11px] text-[#667985]">
                Có hiệu lực đến {new Date(hospitalQr.expiresAt).toLocaleTimeString("vi-VN")}
              </p>
              <div className="mt-3 text-left">
                <button
                  type="button"
                  className="w-full rounded-xl border border-[#8BD2EE] bg-white px-3 py-2 text-xs font-bold text-[#00577E] transition-colors hover:bg-[#DDF3FC]"
                  onClick={async () => {
                    setCopyError(null);
                    try {
                      await navigator.clipboard.writeText(hospitalQr.qrToken);
                      setCopiedQr(hospitalQr.qrToken);
                    } catch {
                      setCopyError(
                        "Không thể truy cập clipboard. Mở nội dung QR bên dưới để chọn và sao chép thủ công."
                      );
                    }
                  }}
                >
                  {copiedQr === hospitalQr.qrToken
                    ? "Đã sao chép nội dung QR"
                    : "Sao chép nội dung QR"}
                </button>
                {copyError && (
                  <p role="status" className="mt-2 text-[11px] text-[#A86100]">
                    {copyError}
                  </p>
                )}
                <details className="mt-2.5 text-[11px] text-[#4D626E]">
                  <summary className="cursor-pointer font-semibold text-[#17242D]">
                    Nội dung QR để nhập trên web giả lập
                  </summary>
                  <p className="mt-1.5">
                    Dán mã này vào màn hình check-in trên điện thoại giả lập. Nhập vị trí kiểm thử
                    theo cấu hình bệnh viện; máy chủ vẫn kiểm tra đầy đủ điều kiện.
                  </p>
                  <textarea
                    aria-label="Nội dung QR bệnh viện"
                    value={hospitalQr.qrToken}
                    readOnly
                    rows={3}
                    onFocus={(event) => event.currentTarget.select()}
                    className="mt-2 w-full resize-y rounded-lg border border-[#D5E0E6] bg-white p-2 font-mono text-[10px] text-[#17242D] outline-none focus:border-[#0277A8]"
                  />
                </details>
              </div>
            </>
          ) : (
            <p className="min-h-[180px] content-center text-xs text-[#667985]">
              {qrError || "Chưa có QR check-in bệnh viện."}
            </p>
          )}
        </div>

        <form onSubmit={onSubmitCheckIn} className="mt-4 space-y-3 border-t border-[#EDF2F5] pt-4">
          <div>
            <h3 className="text-xs font-extrabold text-[#17242D]">Hỗ trợ check-in tại quầy</h3>
            <p className="mt-0.5 text-[11px] text-[#667985]">
              Dùng mã phiếu khám hoặc QR token khi bệnh nhân không dùng Mobile.
            </p>
          </div>

          <div className="flex gap-2">
            <input
              id="qr-input"
              type="text"
              value={qrInput}
              onChange={(e) => setQrInput(e.target.value)}
              placeholder="Nhập mã phiếu khám (VD: NOI-002)..."
              autoComplete="off"
              className="h-11 w-full rounded-xl border border-[#D5E0E6] bg-white px-3.5 font-mono text-xs text-[#17242D] outline-none placeholder:font-sans placeholder:text-[#667985] focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15"
            />
            <button
              type="submit"
              disabled={isSubmitting || !roomId}
              className="h-11 shrink-0 rounded-xl bg-[#0277A8] px-4 text-xs font-bold text-white transition-colors hover:bg-[#00577E] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {isSubmitting ? "Đang xử lý..." : "Hỗ trợ check-in"}
            </button>
          </div>
          {error && <p className="text-[11px] font-semibold text-[#BA1A1A]">{error}</p>}
        </form>

        {checkInResult && (
          <div className="mt-4 rounded-xl border-l-4 border-[#087F6A] bg-[#D9F3EC]/50 px-4 py-3">
            <div className="flex items-center justify-between gap-3">
              <p className="text-[11px] font-bold uppercase tracking-wider text-[#087F6A]">
                Tiếp nhận thành công
              </p>
              <ReceptionStatusText status={checkInResult.queueStatus} />
            </div>
            <div className="mt-2 flex items-baseline justify-between gap-3">
              <span className="font-mono text-2xl font-extrabold text-[#17242D]">
                {checkInResult.queueNumber}
              </span>
              <span className="text-xs font-semibold text-[#4D626E]">
                Phòng {checkInResult.roomCode} ·{" "}
                {PRIORITY_LABELS[checkInResult.priorityLevel] || checkInResult.priorityLevel}
              </span>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
