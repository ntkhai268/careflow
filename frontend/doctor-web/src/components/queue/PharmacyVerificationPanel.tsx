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
          <div className="rounded-xl border border-[#8BD2EE] bg-[#DDF3FC]/60 p-3.5">
            <div className="flex items-center justify-between gap-2">
              <p className="font-mono text-sm font-extrabold text-[#00577E]">
                Số thứ tự: {activeEntry.queueNumber}
              </p>
              <span className="rounded-md bg-white px-2 py-0.5 text-[11px] font-bold text-[#0277A8]">
                {pharmacyStatusLabel(activeEntry.queueStatus)}
              </span>
            </div>
            <p className="mt-1.5 font-mono text-[11px] text-[#4D626E]" title={activeEntry.patientId}>
              Mã bệnh nhân: {activeEntry.patientId.slice(0, 8).toUpperCase()}
            </p>
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
                className={`rounded-xl border p-3.5 ${
                  activePatientMatches
                    ? "border-[#1B7F4B]/30 bg-[#D9F3EC] text-[#1B7F4B]"
                    : "border-[#BA1A1A]/30 bg-[#BA1A1A]/10 text-[#BA1A1A]"
                }`}
              >
                <p className="font-bold">
                  {activePatientMatches
                    ? "Đã khớp hồ sơ bệnh nhân với toa thuốc"
                    : "Không khớp mã bệnh nhân"}
                </p>
                <div className="mt-1.5 flex flex-wrap items-center justify-between gap-2 text-[11px]">
                  <span className="font-mono" title={activePrescription.patientId}>
                    Mã BN trên toa: {activePrescription.patientId.slice(0, 8).toUpperCase()}
                  </span>
                  <span className="rounded-md bg-white/80 px-2 py-0.5 font-mono font-bold">
                    {activePrescription.status === "CONFIRMED"
                      ? "ĐÃ KÝ XÁC NHẬN"
                      : activePrescription.status}
                  </span>
                </div>
              </div>

              <div className="space-y-1.5 rounded-xl border border-[#D5E0E6] bg-[#F5F8FA] p-3.5">
                <p className="font-bold text-[#17242D]">
                  Chẩn đoán: {activePrescription.diagnosis || "Không có chẩn đoán"}
                </p>
                <div className="flex flex-wrap gap-x-4 gap-y-1 font-mono text-[11px] text-[#667985]">
                  <span title={activePrescription.id}>
                    Mã toa: {activePrescription.id.slice(0, 8).toUpperCase()}
                  </span>
                  <span title={activePrescription.consultationId}>
                    Phiên khám: {activePrescription.consultationId.slice(0, 8).toUpperCase()}
                  </span>
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
