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
    <div className="border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
      <h2 className="text-base font-bold text-[#2B1D30]">Đối chiếu và phát thuốc</h2>
      {activeEntry ? (
        <div className="space-y-4 text-xs">
          <div className="p-3 bg-purple-50 border border-purple-200 rounded-lg">
            <p className="font-bold text-purple-900 text-sm">Số thứ tự: {activeEntry.queueNumber}</p>
            <p className="text-purple-700 mt-1">Trạng thái: {pharmacyStatusLabel(activeEntry.queueStatus)}</p>
            <p className="text-purple-700 mt-1 break-all">Mã bệnh nhân: {activeEntry.patientId}</p>
          </div>

          {isLoadingPrescription ? (
            <div className="py-8 flex justify-center">
              <LoadingSpinner size="sm" />
            </div>
          ) : prescriptionError ? (
            <div className="p-3 bg-rose-50 border border-rose-200 text-rose-700 rounded-lg">
              {prescriptionError}
            </div>
          ) : activePrescription ? (
            <>
              <div
                className={`p-3 rounded-lg border ${
                  activePatientMatches
                    ? "bg-emerald-50 border-emerald-200 text-emerald-800"
                    : "bg-rose-50 border-rose-200 text-rose-700"
                }`}
              >
                <p className="font-bold">
                  {activePatientMatches ? "Đã khớp bệnh nhân với toa" : "Không khớp bệnh nhân"}
                </p>
                <p className="mt-1 break-all">Mã bệnh nhân trên toa: {activePrescription.patientId}</p>
                <p className="mt-1">Trạng thái toa: {activePrescription.status}</p>
              </div>

              <div className="p-3 bg-gray-50 border border-card-border rounded-lg space-y-1">
                <p className="font-semibold text-text">
                  Chẩn đoán: {activePrescription.diagnosis || "Không có chẩn đoán"}
                </p>
                <p className="text-text-muted break-all">Mã toa: {activePrescription.id}</p>
                <p className="text-text-muted break-all">
                  Mã phiên khám: {activePrescription.consultationId}
                </p>
              </div>

              <div className="space-y-2">
                <p className="font-semibold text-text">Danh sách thuốc trong toa:</p>
                <div className="p-3 bg-gray-50 border border-card-border rounded-lg space-y-2 text-text">
                  {activePrescription.items.length > 0 ? (
                    activePrescription.items.map((item) => (
                      <p key={item.id}>
                        • {item.medicineName} — {item.quantity} {item.unit} ({item.dosage},{" "}
                        {item.frequency})
                      </p>
                    ))
                  ) : (
                    <p className="text-text-muted italic">Toa chưa có thuốc.</p>
                  )}
                </div>
              </div>

              {activeEntry.queueStatus === "CALLED" && (
                <button
                  onClick={onStartVerification}
                  disabled={isStarting || !activePatientMatches}
                  className="w-full py-3 bg-[#6E2582] hover:bg-[#561A66] text-white font-bold rounded-lg shadow-sm text-xs cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {isStarting ? "Đang bắt đầu..." : "Bắt đầu đối chiếu"}
                </button>
              )}

              {activeEntry.queueStatus === "QUEUED" && (
                <p className="p-3 bg-amber-50 border border-amber-200 text-amber-800 rounded-lg">
                  Lượt này đang chờ gọi theo thứ tự FIFO.
                </p>
              )}

              {activeEntry.queueStatus === "IN_PROGRESS" && (
                <button
                  onClick={onDispenseMedicine}
                  disabled={isDispensing || !canDispense}
                  className="w-full py-3 bg-[#6E2582] hover:bg-[#561A66] text-white font-bold rounded-lg shadow-sm text-xs cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {isDispensing ? "Đang xác nhận..." : "Xác nhận đã phát thuốc"}
                </button>
              )}
            </>
          ) : null}
        </div>
      ) : (
        <div className="py-12 text-center text-text-muted text-xs">
          Chọn một lượt chờ để tải toa và đối chiếu trước khi phát.
        </div>
      )}
    </div>
  );
}
