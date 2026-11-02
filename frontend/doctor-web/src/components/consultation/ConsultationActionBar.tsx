"use client";

import ConfirmModal from "@/components/ConfirmModal";
import { ConsultationResponse } from "@/lib/consultation-api";
import { PrescriptionItemRequest } from "@/lib/prescription-api";

interface ConsultationActionBarProps {
  consultation: ConsultationResponse;
  userDisplayName?: string;
  isConsultationLocked: boolean;
  isSaving: boolean;
  completionPending: boolean;
  prescriptionId: string | null;
  prescriptionStatus: "DRAFT" | "CONFIRMED" | "DISPENSED" | "CANCELLED" | "CANCELLED_BY_AMENDMENT" | null;
  prescriptionItems: PrescriptionItemRequest[];
  saveDraft: () => Promise<boolean>;
  confirmPrescription: () => void;
  completeConsultation: () => void;
  executeCompleteConsultation: () => Promise<void>;
  onNavigateDashboard: () => void;
  confirmModalConfig: {
    isOpen: boolean;
    title: string;
    message: string;
    variant?: "primary" | "warning" | "danger" | "purple";
    showItemList?: boolean;
    onConfirm: () => void;
  };
  onCancelConfirmModal: () => void;
}

export default function ConsultationActionBar({
  consultation,
  userDisplayName,
  isConsultationLocked,
  isSaving,
  completionPending,
  prescriptionId,
  prescriptionStatus,
  prescriptionItems,
  saveDraft,
  confirmPrescription,
  completeConsultation,
  executeCompleteConsultation,
  onNavigateDashboard,
  confirmModalConfig,
  onCancelConfirmModal,
}: ConsultationActionBarProps) {
  return (
    <>
      <div
        aria-label="Thao tác phiên khám"
        className="fixed bottom-0 left-64 right-0 bg-[#2B1D30] border-t border-white/10 min-h-16 px-4 py-3 flex flex-wrap justify-between items-center gap-3 z-30 shadow-2xl"
      >
        <div className="flex min-w-0 items-center gap-2">
          <span className="w-2 h-2 bg-emerald-400 rounded-full inline-block"></span>
          <span className="truncate text-xs text-white/70 font-medium">
            Bác sĩ phụ trách:{" "}
            <strong className="text-white">
              {consultation.doctorName || userDisplayName}
            </strong>
          </span>
        </div>

        <div className="ml-auto flex max-w-full flex-wrap justify-end gap-2">
          {consultation.status === "COMPLETED" && (
            <button
              onClick={() => void executeCompleteConsultation()}
              disabled={isSaving}
              className="rounded-lg border border-white/40 px-3 py-2 text-xs text-white disabled:opacity-50"
            >
              {completionPending ? "THỬ LẠI ĐỒNG BỘ" : "ĐỐI SOÁT HOÀN TẤT"}
            </button>
          )}
          {!isConsultationLocked && (
            <button
              onClick={saveDraft}
              disabled={isSaving}
              className="whitespace-nowrap border border-white/30 hover:bg-white/10 text-white font-bold px-4 py-2 text-xs transition-all disabled:opacity-50 rounded-md"
            >
              {isSaving ? "ĐANG LƯU..." : "LƯU TOA NHÁP"}
            </button>
          )}

          {prescriptionId &&
            prescriptionStatus === "DRAFT" &&
            prescriptionItems.length > 0 &&
            !isConsultationLocked && (
              <button
                onClick={confirmPrescription}
                disabled={isSaving}
                className="whitespace-nowrap border border-white/30 hover:bg-white/10 text-white font-bold px-4 py-2 text-xs transition-all disabled:opacity-50 rounded-md"
              >
                {isSaving ? "ĐANG KÝ..." : "KÝ & XÁC NHẬN ĐƠN"}
              </button>
            )}

          {!isConsultationLocked ? (
            <button
              onClick={completeConsultation}
              disabled={isSaving}
              className="whitespace-nowrap bg-primary-600 hover:bg-primary-700 text-white font-bold px-4 py-2 text-xs transition-all disabled:opacity-50 rounded-md shadow-sm"
            >
              {isSaving ? "ĐANG XỬ LÝ..." : "HOÀN TẤT PHIÊN KHÁM"}
            </button>
          ) : (
            <button
              onClick={onNavigateDashboard}
              className="bg-primary-600 hover:bg-primary-700 text-white font-bold px-6 py-2 text-xs transition-all rounded-md"
            >
              QUAY LẠI TRANG CHỦ
            </button>
          )}
        </div>
      </div>

      <ConfirmModal
        isOpen={confirmModalConfig.isOpen}
        title={confirmModalConfig.title}
        message={confirmModalConfig.message}
        variant={confirmModalConfig.variant}
        onConfirm={confirmModalConfig.onConfirm}
        onCancel={onCancelConfirmModal}
      >
        {confirmModalConfig.showItemList && prescriptionItems.length > 0 && (
          <div className="mt-3 space-y-2">
            <div className="text-[11px] font-bold text-[#2B1D30] uppercase border-b border-card-border pb-1 flex justify-between items-center">
              <span>Danh sách thuốc trong đơn ({prescriptionItems.length} loại):</span>
              {prescriptionItems.length > 5 && (
                <span className="text-[10px] text-primary-600 font-normal italic">
                  Cuộn xuống để xem hết
                </span>
              )}
            </div>
            <div className="max-h-[160px] overflow-y-auto space-y-1.5 pr-1 custom-scrollbar">
              {prescriptionItems.map((item, idx) => (
                <div key={idx} className="text-xs text-[#6A5C70] font-medium leading-relaxed">
                  <span className="font-bold text-[#2B1D30]">
                    {idx + 1}. {item.medicineName}
                  </span>{" "}
                  <span className="text-gray-900 font-semibold">
                    x{item.quantity} {item.unit}
                  </span>{" "}
                  <span className="text-gray-500">({item.frequency})</span>{" "}
                  <span className="text-gray-400 font-mono text-[11px]">- {item.medicineCode}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </ConfirmModal>
    </>
  );
}
