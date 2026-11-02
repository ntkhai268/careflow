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
    <section className="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)]">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h2 className="text-[15px] font-semibold text-[#2B1D30]">QR check-in bệnh viện</h2>
            <p className="mt-1 text-[10px] text-slate-500">
              Bệnh nhân dùng Patient Mobile quét mã này tại bệnh viện.
            </p>
          </div>
          <button
            type="button"
            onClick={onRefreshQr}
            className="text-[10px] font-semibold text-[#7B4B94] hover:underline"
          >
            Tạo mã mới
          </button>
        </div>

        <div className="rounded-lg border border-dashed border-[#C7A6D1] bg-[#FCF8FD] p-4 text-center">
          {isLoadingQr ? (
            <div className="flex min-h-[230px] items-center justify-center">
              <LoadingSpinner size="md" />
            </div>
          ) : hospitalQr ? (
            <>
              <div className="mx-auto w-fit rounded-xl bg-white p-3 shadow-sm">
                <QRCodeSVG value={hospitalQr.qrToken} size={210} level="M" includeMargin />
              </div>
              <p className="mt-3 text-[11px] font-semibold text-[#2B1D30]">
                {hospitalQr.siteId} · {hospitalQr.sessionCode}
              </p>
              <p className="mt-1 text-[10px] text-slate-500">
                Có hiệu lực đến {new Date(hospitalQr.expiresAt).toLocaleTimeString("vi-VN")}
              </p>
              <div className="mt-4 text-left">
                <button
                  type="button"
                  className="w-full rounded-lg border border-[#C7A6D1] bg-white px-3 py-2 text-[11px] font-semibold text-[#6E2582] hover:bg-[#F3E8F5]"
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
                  <p role="status" className="mt-2 text-[11px] text-amber-700">
                    {copyError}
                  </p>
                )}
                <details className="mt-3 text-[11px] text-slate-600">
                  <summary className="cursor-pointer font-medium">
                    Nội dung QR để nhập trên web giả lập
                  </summary>
                  <p className="mt-2">
                    Dán mã này vào màn hình check-in trên điện thoại giả lập. Nhập vị trí kiểm thử theo cấu hình bệnh viện; máy chủ vẫn kiểm tra đầy đủ điều kiện.
                  </p>
                  <textarea
                    aria-label="Nội dung QR bệnh viện"
                    value={hospitalQr.qrToken}
                    readOnly
                    rows={4}
                    onFocus={(event) => event.currentTarget.select()}
                    className="mt-2 w-full resize-y rounded-lg border border-slate-200 bg-white p-2 font-mono text-[10px] outline-none focus:border-[#7B4B94]"
                  />
                </details>
              </div>
            </>
          ) : (
            <p className="min-h-[230px] content-center text-[11px] text-slate-500">
              {qrError || "Chưa có QR check-in bệnh viện."}
            </p>
          )}
        </div>

        <form onSubmit={onSubmitCheckIn} className="mt-5 space-y-4 border-t border-slate-100 pt-5">
          <div>
            <h3 className="text-[12px] font-semibold text-[#2B1D30]">Hỗ trợ tại quầy</h3>
            <p className="mt-1 text-[10px] text-slate-500">
              Dùng mã phiếu khám khi bệnh nhân không thể dùng Mobile.
            </p>
          </div>

          <div>
            <label htmlFor="qr-input" className="mb-1.5 block text-[10px] font-semibold text-slate-700">
              Mã phiếu khám
            </label>
            <input
              id="qr-input"
              type="text"
              value={qrInput}
              onChange={(e) => setQrInput(e.target.value)}
              placeholder="Nhập mã phiếu khám"
              autoComplete="off"
              className="h-12 w-full rounded-md border border-slate-200 bg-white px-3 font-mono text-xs text-slate-800 outline-none placeholder:font-sans placeholder:text-slate-400 focus:border-[#7B4B94] focus:ring-2 focus:ring-[#F3E8F5]"
            />
            {error && <p className="mt-2 text-[10px] text-[#D9381E]">{error}</p>}
          </div>

          <button
            type="submit"
            disabled={isSubmitting || !roomId}
            className="h-11 w-full rounded-md bg-[#6E2582] px-4 text-[11px] font-semibold text-white transition-colors hover:bg-[#561A66] disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isSubmitting ? "Đang tiếp nhận..." : "Hỗ trợ check-in"}
          </button>
        </form>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)]">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h2 className="text-[15px] font-semibold text-[#2B1D30]">Kết quả gần nhất</h2>
            <p className="mt-1 text-[10px] text-slate-500">
              Thông tin được cập nhật sau mỗi lượt tiếp nhận.
            </p>
          </div>
          {checkInResult && (
            <span className="text-[10px] font-medium text-emerald-600">Đã cập nhật</span>
          )}
        </div>

        {checkInResult ? (
          <div className="border-l-3 border-[#7B4B94] bg-[#F8FAFC] px-5 py-4">
            <div className="flex items-center justify-between gap-4">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-emerald-600">
                Tiếp nhận thành công
              </p>
              <ReceptionStatusText status={checkInResult.queueStatus} />
            </div>
            <div className="mt-5 flex items-end gap-3 border-b border-slate-200 pb-5">
              <span className="font-mono text-5xl font-bold leading-none text-[#2B1D30]">
                {checkInResult.queueNumber}
              </span>
              <div className="pb-1 text-[10px] text-slate-500">
                <p>Phòng {checkInResult.roomCode}</p>
                <p className="mt-1">{checkInResult.departmentName}</p>
              </div>
            </div>
            <dl className="mt-4 grid grid-cols-2 gap-4 text-[10px]">
              <div>
                <dt className="text-slate-400">Thời gian</dt>
                <dd className="mt-1 font-medium text-slate-700">
                  {checkInResult.checkedInAt
                    ? new Date(checkInResult.checkedInAt).toLocaleTimeString("vi-VN")
                    : "Vừa xong"}
                </dd>
              </div>
              <div>
                <dt className="text-slate-400">Loại lượt</dt>
                <dd className="mt-1 font-medium text-slate-700">
                  {PRIORITY_LABELS[checkInResult.priorityLevel] || checkInResult.priorityLevel}
                </dd>
              </div>
            </dl>
          </div>
        ) : (
          <div className="flex min-h-[220px] items-center justify-center border-l-3 border-slate-200 bg-slate-50 px-8 text-center text-[11px] text-slate-500">
            Kết quả check-in sẽ hiển thị tại đây sau khi quét mã.
          </div>
        )}
      </div>
    </section>
  );
}
