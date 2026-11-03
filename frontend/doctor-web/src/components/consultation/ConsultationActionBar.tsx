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
        className="fixed bottom-0 left-0 md:left-64 right-0 bg-[#0B2534] border-t border-[#163A50] min-h-16 px-5 py-3 flex flex-wrap justify-between items-center gap-3 z-30 shadow-2xl"
      >
        <div className="flex min-w-0 items-center gap-2.5">
          <span className="w-2 h-2 bg-[#1B7F4B] rounded-full inline-block" />
          <span className="truncate text-xs text-[#94B4C7] font-medium">
            Bác sĩ phụ trách:{" "}
            <strong className="text-white">
              {consultation.doctorName || userDisplayName}
            </strong>
          </span>
        </div>

        <div className="ml-auto flex max-w-full flex-wrap justify-end gap-2.5">
          {consultation.status === "COMPLETED" && (
            <button
              onClick={() => void executeCompleteConsultation()}
              disabled={isSaving}
              className="rounded-xl border border-white/30 px-3.5 py-2 text-xs font-bold text-white hover:bg-white/10 disabled:opacity-50 cursor-pointer"
            >
              {completionPending ? "THỬ LẠI ĐỒNG BỘ" : "ĐỐI SOÁT HOÀN TẤT"}
            </button>
          )}
          {!isConsultationLocked && (
            <button
              onClick={saveDraft}
              disabled={isSaving}
              className="whitespace-nowrap border border-[#8BD2EE]/40 hover:bg-[#12354A] text-white font-bold px-4 py-2 text-xs transition-all disabled:opacity-50 rounded-xl cursor-pointer"
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
                className="whitespace-nowrap bg-[#087F6A] hover:bg-[#066352] text-white font-bold px-4 py-2 text-xs transition-all disabled:opacity-50 rounded-xl shadow-xs cursor-pointer"
              >
                {isSaving ? "ĐANG KÝ..." : "KÝ & XÁC NHẬN ĐƠN"}
              </button>
            )}

          {!isConsultationLocked ? (
            <button
              onClick={completeConsultation}
              disabled={isSaving}
              className="whitespace-nowrap bg-[#0277A8] hover:bg-[#00577E] text-white font-bold px-4 py-2 text-xs transition-all disabled:opacity-50 rounded-xl shadow-sm cursor-pointer"
            >
              {isSaving ? "ĐANG XỬ LÝ..." : "HOÀN TẤT PHIÊN KHÁM"}
            </button>
          ) : (
            <button
              onClick={onNavigateDashboard}
              className="bg-[#0277A8] hover:bg-[#00577E] text-white font-bold px-6 py-2 text-xs transition-all rounded-xl cursor-pointer"
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
            <div className="text-[11px] font-bold text-[#17242D] uppercase border-b border-[#D5E0E6] pb-1.5 flex justify-between items-center">
              <span>Danh sách thuốc trong đơn ({prescriptionItems.length} loại):</span>
              {prescriptionItems.length > 5 && (
                <span className="text-[10px] text-[#0277A8] font-normal italic">
                  Cuộn xuống để xem hết
                </span>
              )}
            </div>
            <div className="max-h-[160px] overflow-y-auto space-y-1.5 pr-1 custom-scrollbar">
              {prescriptionItems.map((item, idx) => (
                <div key={idx} className="text-xs text-[#4D626E] font-medium leading-relaxed">
                  <span className="font-bold text-[#17242D]">
                    {idx + 1}. {item.medicineName}
                  </span>{" "}
                  <span className="text-[#00577E] font-bold font-mono">
                    x{item.quantity} {item.unit}
                  </span>{" "}
                  <span className="text-[#4D626E]">({item.frequency})</span>{" "}
                  <span className="text-[#667985] font-mono text-[11px]">
                    - {item.medicineCode}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}
      </ConfirmModal>
    </>
  );
}
