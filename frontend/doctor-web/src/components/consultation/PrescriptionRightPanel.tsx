"use client";

import FollowUpFields from "@/components/FollowUpFields";
import NamedMedicineReview from "@/components/NamedMedicineReview";
import VoicePrescriptionPanel, { VoiceDraftHandle } from "@/components/VoicePrescriptionPanel";
import VoicePrescriptionPreview from "@/components/VoicePrescriptionPreview";
import { getErrorMessage } from "@/lib/error-utils";
import {
  prescriptionApi,
  PrescriptionItemRequest,
  PrescriptionResponse,
} from "@/lib/prescription-api";

interface PrescriptionRightPanelProps {
  consultationId: string;
  userId?: string;
  voiceDraftRef: React.RefObject<VoiceDraftHandle | null>;
  prescriptionId: string | null;
  prescriptionStatus: "DRAFT" | "CONFIRMED" | "DISPENSED" | "CANCELLED" | "CANCELLED_BY_AMENDMENT" | null;
  prescriptionItems: PrescriptionItemRequest[];
  setPrescriptionItems: React.Dispatch<React.SetStateAction<PrescriptionItemRequest[]>>;
  editPrescriptionItems: (items: PrescriptionItemRequest[]) => void;
  prescriptionNotes: string;
  setPrescriptionNotes: (val: string) => void;
  showPrescriptionNotes: boolean;
  setShowPrescriptionNotes: (val: boolean) => void;
  followUpDate: string;
  setFollowUpDate: (val: string) => void;
  followUpDepartment: string;
  setFollowUpDepartment: (val: string) => void;
  followUpTimeSlot: string;
  setFollowUpTimeSlot: (val: string) => void;
  followUpDelivery: PrescriptionResponse | null;
  setFollowUpDelivery: (val: PrescriptionResponse | null) => void;
  isConsultationLocked: boolean;
  isPrescriptionLocked: boolean;
  isSaving: boolean;
  setIsSaving: (val: boolean) => void;
  saveDraft: () => Promise<boolean>;
  openAddMedicineDrawer: () => void;
  openEditMedicineDrawer: (item: PrescriptionItemRequest) => void;
  removeMedicine: (index: number, name: string) => void;
  clearAllMedicines: () => void;
  showToast: (message: string, type?: "success" | "danger" | "warning") => void;
}

export default function PrescriptionRightPanel({
  consultationId,
  userId,
  voiceDraftRef,
  prescriptionId,
  prescriptionStatus,
  prescriptionItems,
  setPrescriptionItems,
  editPrescriptionItems,
  prescriptionNotes,
  setPrescriptionNotes,
  showPrescriptionNotes,
  setShowPrescriptionNotes,
  followUpDate,
  setFollowUpDate,
  followUpDepartment,
  setFollowUpDepartment,
  followUpTimeSlot,
  setFollowUpTimeSlot,
  followUpDelivery,
  setFollowUpDelivery,
  isConsultationLocked,
  isPrescriptionLocked,
  isSaving,
  setIsSaving,
  saveDraft,
  openAddMedicineDrawer,
  openEditMedicineDrawer,
  removeMedicine,
  clearAllMedicines,
  showToast,
}: PrescriptionRightPanelProps) {
  return (
    <div className="lg:col-span-2 space-y-6">
      <VoicePrescriptionPanel
        key={`${userId}:${consultationId}`}
        ref={voiceDraftRef}
        consultationId={consultationId}
        actorId={userId || ""}
        prescriptionId={prescriptionId}
        disabled={isConsultationLocked || isSaving}
        hasPrescription={!!prescriptionId}
        canEdit={prescriptionStatus === "DRAFT"}
        onSaveDraft={saveDraft}
      >
        <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] rounded-lg flex flex-col justify-between min-h-[420px]">
          <div>
            <div className="flex justify-between items-center border-b border-card-border pb-3 mb-4">
              <div>
                <h2 className="text-xs font-bold text-[#2B1D30] uppercase tracking-wide">Đơn thuốc</h2>
                <p className="text-[10px] text-[#6A5C70]">Tổng số thuốc: {prescriptionItems.length}</p>
              </div>
              <div className="flex items-center gap-3">
                {prescriptionItems.length > 0 && !isPrescriptionLocked && (
                  <button
                    type="button"
                    onClick={clearAllMedicines}
                    disabled={isSaving}
                    className="text-[11px] font-medium text-slate-400 hover:text-rose-600 transition-colors hover:underline flex items-center gap-1 cursor-pointer"
                  >
                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={1.5}
                        d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                      />
                    </svg>
                    <span>Xóa toàn bộ đơn</span>
                  </button>
                )}
                {!isPrescriptionLocked && (
                  <button
                    type="button"
                    onClick={openAddMedicineDrawer}
                    disabled={isSaving}
                    className="bg-primary-600 hover:bg-primary-700 text-white font-bold px-3 py-1.5 text-xs transition-all flex items-center gap-1.5 rounded-md shadow-sm"
                  >
                    + THÊM THUỐC
                  </button>
                )}
              </div>
            </div>

            <VoicePrescriptionPreview
              result={null}
              saved
              items={prescriptionItems}
              disabled={isSaving || isPrescriptionLocked}
              canEdit={!isPrescriptionLocked}
              onChange={(index, item) =>
                editPrescriptionItems(prescriptionItems.map((row, i) => (i === index ? item : row)))
              }
              onRemove={(index) => removeMedicine(index, prescriptionItems[index].medicineName)}
              onEditCatalog={openEditMedicineDrawer}
            />
            {!isPrescriptionLocked && (
              <NamedMedicineReview
                items={prescriptionItems}
                disabled={isSaving}
                onChange={setPrescriptionItems}
              />
            )}
          </div>

          <div className="pt-4 border-t border-card-border mt-4">
            {!showPrescriptionNotes && !prescriptionNotes ? (
              <button
                type="button"
                onClick={() => setShowPrescriptionNotes(true)}
                className="text-xs font-bold text-primary-600 hover:underline flex items-center gap-1"
              >
                + Thêm ghi chú đơn thuốc chung
              </button>
            ) : (
              <div>
                <div className="flex justify-between items-center mb-1">
                  <label className="block text-xs font-semibold text-[#6A5C70]">
                    Ghi chú đơn thuốc chung
                  </label>
                  {!isPrescriptionLocked && (
                    <button
                      type="button"
                      onClick={() => {
                        setShowPrescriptionNotes(false);
                        setPrescriptionNotes("");
                      }}
                      className="text-[10px] text-gray-400 hover:text-rose-600"
                    >
                      Thu gọn
                    </button>
                  )}
                </div>
                <textarea
                  disabled={isPrescriptionLocked || isSaving}
                  value={prescriptionNotes}
                  onChange={(e) => setPrescriptionNotes(e.target.value)}
                  rows={2}
                  placeholder="Ghi chú về chế độ ăn kiêng, dị ứng hoặc lời dặn của bác sĩ..."
                  className="w-full border border-input-border bg-input-bg px-3 py-1.5 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-md"
                />
              </div>
            )}
          </div>
        </div>
      </VoicePrescriptionPanel>

      <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] space-y-3 rounded-lg">
        <h2 className="text-xs font-bold text-[#2B1D30] border-b border-card-border pb-2 uppercase tracking-wide">
          Lịch Hẹn Tái khám
        </h2>
        {userId && (
          <FollowUpFields
            doctorId={userId}
            value={{ date: followUpDate, department: followUpDepartment, timeSlot: followUpTimeSlot }}
            onChange={(plan) => {
              setFollowUpDate(plan.date);
              setFollowUpDepartment(plan.department);
              setFollowUpTimeSlot(plan.timeSlot);
            }}
            disabled={isPrescriptionLocked || isSaving}
          />
        )}
        {followUpDelivery?.followUpDate &&
          (followUpDelivery.status === "CONFIRMED" || followUpDelivery.status === "DISPENSED") && (
            <div className="rounded-lg border p-3 text-xs space-y-2">
              <p>
                {followUpDelivery.followUpStatus === "SCHEDULED"
                  ? "Lịch tái khám đã được xác nhận."
                  : "Toa đã ký. Lịch tái khám chưa được xác nhận; không cần ký lại toa."}
              </p>
              {followUpDelivery.followUpStatus !== "SCHEDULED" &&
                followUpDelivery.followUpStatus !== "NEEDS_SELECTION" && (
                  <button
                    disabled={isSaving}
                    className="rounded-lg border px-3 py-2"
                    onClick={async () => {
                      if (!prescriptionId) return;
                      setIsSaving(true);
                      try {
                        setFollowUpDelivery((await prescriptionApi.retryFollowUp(prescriptionId)).data);
                      } catch (failure) {
                        showToast(
                          getErrorMessage(failure, "Chưa xác nhận được lịch tái khám."),
                          "warning"
                        );
                      } finally {
                        setIsSaving(false);
                      }
                    }}
                  >
                    Thử lại tạo lịch đã chọn
                  </button>
                )}
              {(followUpDelivery.followUpStatus === "FAILED" ||
                followUpDelivery.followUpStatus === "NEEDS_SELECTION") && (
                <p>
                  Nếu ca đã đầy/ngày đã qua, mở Tổng quan &amp; Tiền sử, đối chiếu lịch đã tạo rồi chọn lịch phù hợp trong Hẹn tái khám từ ca khám.
                </p>
              )}
            </div>
          )}
        <p className="text-[10px] text-[#6A5C70]">
          Chỉ báo tạo lịch thành công sau khi máy chủ xác nhận. Thông báo đẩy khi app ở nền được nghiệm thu riêng.
        </p>
      </div>
    </div>
  );
}
