"use client";

import LoadingSpinner from "@/components/ui/LoadingSpinner";
import { PrescriptionResponse } from "@/lib/prescription-api";
import { QueueEntry } from "@/lib/queue-api";
import { pharmacyStatusLabel } from "./QueueBadges";

interface PharmacyVerificationPanelProps {
  activeEntry: QueueEntry | null;
  activePrescription: PrescriptionResponse | null;
  isLoadingPrescription: boolean;
  prescriptionError: string | null;
  isStarting: boolean;
  isDispensing: boolean;
  activePatientMatches: boolean;
  canDispense: boolean;
  onStartVerification: () => void;
  onDispenseMedicine: () => void;
}

export default function PharmacyVerificationPanel({
  activeEntry,
  activePrescription,
  isLoadingPrescription,
  prescriptionError,
  isStarting,
  isDispensing,
  activePatientMatches,
  canDispense,
  onStartVerification,
  onDispenseMedicine,
}: PharmacyVerificationPanelProps) {
  return (
    <div className="space-y-4 rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-xs">
      <h2 className="text-base font-extrabold text-[#17242D]">Đối chiếu và phát thuốc</h2>
      {activeEntry ? (
        <div className="space-y-4 text-xs">
          <div className="rounded-xl border border-[#8BD2EE] bg-[#DDF3FC]/45 p-4">
            <div className="grid grid-cols-2 gap-3 border-b border-[#8BD2EE]/60 pb-3">
              <div>
                <p className="text-[10px] font-bold uppercase tracking-wider text-[#4D626E]">
                  Số thứ tự phát thuốc
                </p>
                <p className="mt-0.5 font-mono text-base font-extrabold text-[#00577E] whitespace-nowrap">
                  {activeEntry.queueNumber}
                </p>
              </div>
              <div className="text-right">
                <p className="text-[10px] font-bold uppercase tracking-wider text-[#4D626E]">
                  Mã bệnh nhân
                </p>
                <p
                  className="mt-0.5 font-mono text-sm font-extrabold text-[#17242D] whitespace-nowrap"
                  title={activeEntry.patientId}
                >
                  {activeEntry.patientId.slice(0, 8).toUpperCase()}
                </p>
              </div>
            </div>
            <div className="mt-3 flex items-center justify-between gap-2">
              <span className="text-[11px] font-semibold text-[#4D626E]">Trạng thái lượt:</span>
              <span
                className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-bold whitespace-nowrap shadow-2xs ${
                  activeEntry.queueStatus === "IN_PROGRESS"
                    ? "border border-[#087F6A]/25 bg-[#D9F3EC] text-[#087F6A]"
                    : activeEntry.queueStatus === "CALLED"
                      ? "border border-[#8BD2EE] bg-white text-[#0277A8]"
                      : "border border-[#D5E0E6] bg-white text-[#4D626E]"
                }`}
              >
                <span
                  className={`h-1.5 w-1.5 rounded-full ${
                    activeEntry.queueStatus === "IN_PROGRESS"
                      ? "bg-[#087F6A]"
                      : activeEntry.queueStatus === "CALLED"
                        ? "bg-[#0277A8]"
                        : "bg-[#667985]"
                  }`}
                />
                {pharmacyStatusLabel(activeEntry.queueStatus)}
              </span>
            </div>
          </div>

          {isLoadingPrescription ? (
            <div className="flex justify-center py-8">
              <LoadingSpinner size="sm" />
            </div>
          ) : prescriptionError ? (
            <div className="rounded-xl border border-[#BA1A1A]/30 bg-[#BA1A1A]/10 p-3 font-semibold text-[#BA1A1A]">
              {prescriptionError}
            </div>
          ) : activePrescription ? (
            <>
              <div
                className={`rounded-xl border p-4 ${
                  activePatientMatches
                    ? "border-[#1B7F4B]/30 bg-[#D9F3EC]/80 text-[#1B7F4B]"
                    : "border-[#BA1A1A]/30 bg-[#BA1A1A]/10 text-[#BA1A1A]"
                }`}
              >
                <div className="flex items-center gap-2">
                  <span
                    className={`h-2 w-2 shrink-0 rounded-full ${
                      activePatientMatches ? "bg-[#1B7F4B]" : "bg-[#BA1A1A]"
                    }`}
                  />
                  <p className="font-extrabold">
                    {activePatientMatches
                      ? "Đã khớp hồ sơ bệnh nhân với toa thuốc"
                      : "Cảnh báo: Không khớp mã bệnh nhân"}
                  </p>
                </div>
                <div className="mt-3 grid grid-cols-2 gap-2 border-t border-current/15 pt-2.5">
                  <div>
                    <p className="text-[10px] font-bold uppercase tracking-wider opacity-75">
                      Mã BN trên toa
                    </p>
                    <p
                      className="mt-0.5 font-mono text-xs font-extrabold whitespace-nowrap"
                      title={activePrescription.patientId}
                    >
                      {activePrescription.patientId.slice(0, 8).toUpperCase()}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-[10px] font-bold uppercase tracking-wider opacity-75">
                      Xác thực chữ ký
                    </p>
                    <span className="mt-0.5 inline-block rounded-md bg-white/90 px-2 py-0.5 font-mono text-[11px] font-extrabold whitespace-nowrap">
                      {activePrescription.status === "CONFIRMED"
                        ? "ĐÃ KÝ XÁC NHẬN"
                        : activePrescription.status}
                    </span>
                  </div>
                </div>
              </div>

              <div className="space-y-3 rounded-xl border border-[#D5E0E6] bg-[#F5F8FA] p-4">
                <div>
                  <p className="text-[10px] font-bold uppercase tracking-wider text-[#667985]">
                    Chẩn đoán lâm sàng
                  </p>
                  <p className="mt-0.5 text-xs font-extrabold text-[#17242D]">
                    {activePrescription.diagnosis || "Không có chẩn đoán"}
                  </p>
                </div>
                <div className="grid grid-cols-2 gap-3 border-t border-[#D5E0E6] pt-2.5">
                  <div>
                    <p className="text-[10px] font-bold uppercase tracking-wider text-[#667985]">
                      Mã toa thuốc
                    </p>
                    <p
                      className="mt-0.5 font-mono text-xs font-bold text-[#17242D] whitespace-nowrap"
                      title={activePrescription.id}
                    >
                      {activePrescription.id.slice(0, 8).toUpperCase()}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-[10px] font-bold uppercase tracking-wider text-[#667985]">
                      Mã phiên khám
                    </p>
                    <p
                      className="mt-0.5 font-mono text-xs font-bold text-[#17242D] whitespace-nowrap"
                      title={activePrescription.consultationId}
                    >
                      {activePrescription.consultationId.slice(0, 8).toUpperCase()}
                    </p>
                  </div>
                </div>
              </div>

              <div className="space-y-2">
                <p className="font-bold text-[#17242D]">
                  Danh sách thuốc trong toa ({activePrescription.items.length}):
                </p>
                <div className="space-y-2 rounded-xl border border-[#D5E0E6] bg-[#F5F8FA] p-3 text-[#17242D]">
                  {activePrescription.items.length > 0 ? (
                    activePrescription.items.map((item, idx) => (
                      <div
                        key={item.id}
                        className="rounded-lg border border-[#EDF2F5] bg-white p-2.5"
                      >
                        <div className="flex items-start justify-between gap-2">
                          <p className="font-bold text-[#17242D]">
                            {idx + 1}. {item.medicineName}
                          </p>
                          <span className="shrink-0 rounded-md bg-[#DDF3FC] px-2 py-0.5 font-mono text-[11px] font-extrabold text-[#00577E]">
                            {item.quantity} {item.unit}
                          </span>
                        </div>
                        <p className="mt-1 text-[11px] text-[#4D626E]">
                          {item.dosage} · {item.frequency}
                        </p>
                      </div>
                    ))
                  ) : (
                    <p className="italic text-[#667985]">Toa chưa có thuốc.</p>
                  )}
                </div>
              </div>

              {activeEntry.queueStatus === "CALLED" && (
                <button
                  onClick={onStartVerification}
                  disabled={isStarting || !activePatientMatches}
                  className="w-full cursor-pointer rounded-xl bg-[#0277A8] py-3 text-xs font-bold text-white shadow-xs transition-colors hover:bg-[#00577E] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {isStarting ? "Đang bắt đầu..." : "Bắt đầu đối chiếu"}
                </button>
              )}

              {(activeEntry.queueStatus === "QUEUED" ||
                activeEntry.queueStatus === "CHECKED_IN" ||
                activeEntry.queueStatus === "WAITING") && (
                <p className="rounded-xl border border-[#A86100]/30 bg-[#A86100]/10 p-3 font-medium text-[#A86100]">
                  Lượt này đang chờ gọi theo thứ tự FIFO. Bấm &quot;Gọi số tiếp theo&quot; để mời
                  bệnh nhân tới quầy.
                </p>
              )}

              {activeEntry.queueStatus === "IN_PROGRESS" && (
                <button
                  onClick={onDispenseMedicine}
                  disabled={isDispensing || !canDispense}
                  className="w-full cursor-pointer rounded-xl bg-[#087F6A] py-3 text-xs font-bold text-white shadow-xs transition-colors hover:bg-[#066554] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {isDispensing ? "Đang xác nhận..." : "Xác nhận đã phát thuốc"}
                </button>
              )}
            </>
          ) : null}
        </div>
      ) : (
        <div className="py-12 text-center text-xs text-[#667985]">
          Chọn một lượt chờ để tải toa và đối chiếu trước khi phát.
        </div>
      )}
    </div>
  );
}
