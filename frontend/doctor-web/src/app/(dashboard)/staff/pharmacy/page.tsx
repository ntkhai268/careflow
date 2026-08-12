"use client";

import { useCallback, useEffect, useState } from "react";
import { queueApi, QueueEntry } from "@/lib/queue-api";
import { prescriptionApi, PrescriptionResponse } from "@/lib/prescription-api";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import { getErrorMessage } from "@/lib/error-utils";

export default function StaffPharmacyPage() {
  const servicePointId = "PHARMACY-MAIN-01";
  
  // 3-State Data Fetching Pattern
  const [entries, setEntries] = useState<QueueEntry[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Active selection & details
  const [activeEntry, setActiveEntry] = useState<QueueEntry | null>(null);
  const [activePrescription, setActivePrescription] = useState<PrescriptionResponse | null>(null);
  const [isLoadingPrescription, setIsLoadingPrescription] = useState(false);
  const [isDispensing, setIsDispensing] = useState(false);
  const [isCalling, setIsCalling] = useState(false);
  const [isConfirmedCheck, setIsConfirmedCheck] = useState(false);

  // Notification Toast
  const [toast, setToast] = useState<{ message: string; type: "success" | "danger" | "warning" } | null>(null);

  const showToast = (message: string, type: "success" | "danger" | "warning" = "success") => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  };

  const fetchPharmacyQueue = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await queueApi.getServicePointActive(servicePointId);
      const queueEntries = res.data?.entries ?? [];
      setEntries(queueEntries);

      if (queueEntries.length > 0 && !activeEntry) {
        const inProgress = queueEntries.find(e => e.queueStatus === "IN_PROGRESS" || e.queueStatus === "CALLED");
        handleSelectEntry(inProgress || queueEntries[0]);
      }
    } catch {
      setError("Không thể tải danh sách hàng đợi phát thuốc.");
      setEntries([]);
    } finally {
      setIsLoading(false);
    }
  }, [servicePointId, activeEntry]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void fetchPharmacyQueue();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [fetchPharmacyQueue]);

  const handleSelectEntry = async (entry: QueueEntry) => {
    setActiveEntry(entry);
    setActivePrescription(null);
    setIsConfirmedCheck(false);

    if (entry.prescriptionId) {
      setIsLoadingPrescription(true);
      try {
        const res = await prescriptionApi.getPrescription(entry.prescriptionId);
        if (res.data) {
          setActivePrescription(res.data);
        }
      } catch (err: unknown) {
        showToast(getErrorMessage(err, "Không thể tải chi tiết đơn thuốc"), "warning");
      } finally {
        setIsLoadingPrescription(false);
      }
    }
  };

  const notifyAi = (text: string) => {
    if (typeof window !== "undefined") {
      window.dispatchEvent(new CustomEvent("careflow:ai-notify", { detail: { text } }));
    }
  };

  const handleCallNext = async () => {
    setIsCalling(true);
    try {
      const res = await queueApi.callNextAtServicePoint(servicePointId);
      if (res.data && res.data.queueNumber) {
        notifyAi(`Mời số thứ tự ${res.data.queueNumber} tới quầy phát thuốc!`);
        showToast(`Mời số thứ tự ${res.data.queueNumber} tới quầy phát thuốc!`);
        await fetchPharmacyQueue();
        handleSelectEntry(res.data);
      } else {
        notifyAi("Hiện tại chưa có bệnh nhân nào trong hàng đợi.");
        showToast("Hiện chưa có bệnh nhân nào trong hàng đợi.", "warning");
      }
    } catch {
      notifyAi("Hiện tại chưa có bệnh nhân nào trong hàng đợi.");
      showToast("Hiện chưa có bệnh nhân nào trong hàng đợi.", "warning");
    } finally {
      setIsCalling(false);
    }
  };

  const handleDispenseMedicine = async () => {
    if (!activeEntry) return;
    if (!isConfirmedCheck) {
      showToast("Vui lòng đánh dấu xác nhận đối chiếu thông tin đơn thuốc trước khi cấp phát.", "warning");
      return;
    }

    setIsDispensing(true);
    try {
      if (activeEntry.prescriptionId) {
        await prescriptionApi.dispensePrescription(activeEntry.prescriptionId);
      } else {
        await queueApi.completeEntry(activeEntry.entryId);
      }
      notifyAi(`Đã hoàn tất cấp phát thuốc cho số thứ tự ${activeEntry.queueNumber}!`);
      showToast(`Đã hoàn tất cấp phát thuốc cho số thứ tự ${activeEntry.queueNumber}!`);
      setActiveEntry(null);
      setActivePrescription(null);
      setIsConfirmedCheck(false);
      await fetchPharmacyQueue();
    } catch (err: unknown) {
      const message = getErrorMessage(err, "Đã xảy ra lỗi khi xác nhận phát thuốc!");
      notifyAi(message);
      showToast(message, "danger");
    } finally {
      setIsDispensing(false);
    }
  };

  const calledEntry = (entries ?? []).find(e => e.queueStatus === "CALLED" || e.queueStatus === "IN_PROGRESS");

  return (
    <div className="space-y-5 pb-12">
      {/* Toast Notification Container */}
      {toast && (
        <div
          className={`fixed top-5 right-5 z-50 p-4 border shadow-md max-w-md rounded-lg text-white transition-all flex items-start gap-3 ${
            toast.type === "success"
              ? "bg-[#0D0F1E] border-indigo-500"
              : toast.type === "warning"
              ? "bg-amber-950 border-amber-500"
              : "bg-rose-950 border-rose-500"
          }`}
        >
          <span className="text-xs font-medium leading-relaxed flex-1">{toast.message}</span>
          <button onClick={() => setToast(null)} className="text-white/60 hover:text-white text-xs font-bold shrink-0">
            ✕
          </button>
        </div>
      )}

      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border border-[#E2E8F0] bg-white p-5 rounded-lg shadow-sm">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="inline-block w-2 h-2 rounded-full bg-[#6366F1] animate-pulse" />
            <span className="text-[10px] font-bold uppercase tracking-wider text-[#6366F1]">
              Phân hệ Dược / Quầy phát thuốc
            </span>
          </div>
          <h1 className="text-xl font-bold text-[#1A1A1A]">Hàng đợi phát thuốc</h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Cấp phát thuốc theo thứ tự FIFO cho các đơn đã được Bác sĩ ký xác nhận
          </p>
        </div>

        <button
          onClick={handleCallNext}
          disabled={isLoading || isCalling}
          className="px-5 py-2.5 rounded-md font-semibold text-xs text-white bg-[#3B82F6] hover:bg-[#2563EB] transition-all cursor-pointer shadow-sm disabled:opacity-50"
        >
          {isCalling ? "Đang gọi số..." : "Gọi số"}
        </button>
      </div>

      {/* Active Call Status Indicator */}
      {calledEntry && (
        <div className="flex items-center justify-between border border-indigo-200 bg-[#EEF2FF] rounded-lg px-5 py-3 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-md bg-[#6366F1] text-white flex items-center justify-center font-bold text-sm shrink-0">
              {calledEntry.queueNumber}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="w-1.5 h-1.5 rounded-full bg-[#6366F1] animate-ping" />
                <span className="text-[10px] font-bold text-[#6366F1] uppercase tracking-wider">
                  Đang phát thuốc tại quầy
                </span>
              </div>
              <p className="text-sm font-bold text-[#1A1A1A] mt-0.5">
                Số thứ tự: {calledEntry.queueNumber}
              </p>
            </div>
          </div>
          <span className="text-xs text-slate-600 font-medium">
            Mã lượt: {calledEntry.entryId.slice(0, 8)}
          </span>
        </div>
      )}

      {/* Main Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-5">
        {/* Queue List Panel */}
        <div className="lg:col-span-2 border border-[#E2E8F0] bg-white rounded-lg p-5 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-[#1A1A1A]">Danh sách lượt chờ</h2>
            <span className="text-xs font-semibold px-2 py-0.5 bg-[#EEF2FF] text-[#6366F1] rounded">
              {(entries ?? []).length} lượt chờ
            </span>
          </div>

          {isLoading ? (
            <div className="py-12 flex justify-center">
              <LoadingSpinner size="md" />
            </div>
          ) : error ? (
            <div className="p-3 bg-rose-50 border-l-2 border-rose-500 text-rose-700 rounded text-xs font-medium">
              {error}
            </div>
          ) : (entries ?? []).length === 0 ? (
            <div className="py-12 text-center text-slate-400 text-xs">
              Hiện chưa có lượt chờ phát thuốc nào.
            </div>
          ) : (
            <div className="divide-y divide-[#E2E8F0]">
              {(entries ?? []).map((entry) => {
                const isActive = activeEntry?.entryId === entry.entryId;
                const isCurrentCall = entry.queueStatus === "CALLED" || entry.queueStatus === "IN_PROGRESS";
                
                return (
                  <div
                    key={entry.entryId}
                    onClick={() => handleSelectEntry(entry)}
                    className={`py-3 px-2 flex items-center justify-between gap-3 cursor-pointer transition-colors rounded-md ${
                      isActive
                        ? "bg-[#EEF2FF]"
                        : "hover:bg-slate-50"
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <div className={`w-10 h-10 rounded-md flex items-center justify-center font-bold text-xs shrink-0 ${
                        isCurrentCall ? "bg-[#6366F1] text-white" : "bg-slate-100 text-slate-700"
                      }`}>
                        {entry.queueNumber}
                      </div>
                      <div>
                        <p className="text-xs font-bold text-[#1A1A1A]">Số thứ tự: {entry.queueNumber}</p>
                        <div className="flex items-center gap-1.5 mt-0.5">
                          <span className={`w-1.5 h-1.5 rounded-full ${
                            isCurrentCall ? "bg-[#6366F1]" : "bg-blue-500"
                          }`} />
                          <span className={`text-[11px] font-medium ${
                            isCurrentCall ? "text-[#6366F1]" : "text-blue-600"
                          }`}>
                            {entry.queueStatus === "CHECKED_IN"
                              ? "Đã tiếp nhận"
                              : entry.queueStatus === "CALLED"
                              ? "Đang gọi"
                              : entry.queueStatus === "IN_PROGRESS"
                              ? "Đang phát thuốc"
                              : entry.queueStatus}
                          </span>
                        </div>
                      </div>
                    </div>

                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        handleSelectEntry(entry);
                      }}
                      className="w-20 py-1.5 bg-white border border-[#E2E8F0] text-[#1A1A1A] text-xs font-medium rounded-md hover:bg-slate-50 cursor-pointer text-center"
                    >
                      Chọn
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Prescription Details Panel */}
        <div className="lg:col-span-3 border border-[#E2E8F0] bg-white rounded-lg p-5 shadow-sm space-y-4">
          <h2 className="text-sm font-semibold text-[#1A1A1A]">Chi tiết đơn thuốc phát</h2>

          {activeEntry ? (
            <div className="space-y-4 text-xs">
              {/* Summary Box */}
              <div className="p-3.5 bg-slate-50 border border-[#E2E8F0] rounded-md space-y-2">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-sm text-[#1A1A1A]">
                    Số thứ tự: {activeEntry.queueNumber}
                  </span>
                  <span className="text-[11px] font-medium text-slate-500">
                    Mã đơn: {activePrescription?.id || activeEntry.prescriptionId || "Đang cập nhật"}
                  </span>
                </div>
                <div className="text-slate-700">
                  <span className="font-semibold">Chẩn đoán: </span>
                  {activePrescription?.diagnosis || "Theo dõi lâm sàng (Không ghi nhận bất thường)"}
                </div>
                {activePrescription?.notes && (
                  <div className="text-slate-600 italic border-t border-[#E2E8F0] pt-1.5 mt-1.5">
                    Ghi chú bác sĩ: {activePrescription.notes}
                  </div>
                )}
              </div>

              {/* Medicine List */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <p className="font-semibold text-[#1A1A1A]">Danh sách thuốc trong đơn:</p>
                  {activePrescription?.items && (
                    <span className="text-slate-500 font-medium">
                      {activePrescription.items.length} loại thuốc
                    </span>
                  )}
                </div>

                {isLoadingPrescription ? (
                  <div className="py-8 flex justify-center">
                    <LoadingSpinner size="sm" />
                  </div>
                ) : activePrescription && activePrescription.items && activePrescription.items.length > 0 ? (
                  <div className="border border-[#E2E8F0] rounded-md divide-y divide-[#E2E8F0]">
                    {activePrescription.items.map((item, idx) => (
                      <div key={item.id} className="p-3 flex items-start justify-between gap-3 bg-white">
                        <div>
                          <p className="font-bold text-[#1A1A1A] text-xs">
                            {idx + 1}. {item.medicineName}
                          </p>
                          <p className="text-slate-500 text-[11px] mt-0.5">
                            Mã: {item.medicineCode} | Cách dùng: {item.dosage}, {item.frequency}
                          </p>
                          {item.timing && (
                            <p className="text-slate-500 text-[11px]">Thời điểm: {item.timing}</p>
                          )}
                        </div>
                        <div className="text-right shrink-0">
                          <span className="font-bold text-[#6366F1] text-xs">
                            {item.quantity} {item.unit}
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="p-4 bg-slate-50 border border-[#E2E8F0] rounded-md text-slate-500 italic text-center">
                    Không tìm thấy dữ liệu chi tiết thuốc trong hệ thống.
                  </div>
                )}
              </div>

              {/* Confirmation Checkbox */}
              <label className="flex items-start gap-2.5 p-3 bg-[#EEF2FF] border border-indigo-200 rounded-md cursor-pointer">
                <input
                  type="checkbox"
                  checked={isConfirmedCheck}
                  onChange={(e) => setIsConfirmedCheck(e.target.checked)}
                  className="w-4 h-4 mt-0.5 accent-[#6366F1] cursor-pointer shrink-0"
                />
                <span className="text-slate-700 text-[11px] leading-relaxed">
                  Xác nhận đã kiểm tra đối chiếu danh sách thuốc và tên bệnh nhân khớp với hồ sơ.
                </span>
              </label>

              {/* Action Button */}
              <button
                onClick={handleDispenseMedicine}
                disabled={isDispensing || isLoadingPrescription || !isConfirmedCheck}
                className="w-full py-2.5 bg-[#3B82F6] hover:bg-[#2563EB] text-white font-semibold rounded-md shadow-sm transition-all text-xs cursor-pointer disabled:opacity-50"
              >
                {isDispensing ? "Đang xác nhận phát thuốc..." : "Xác nhận phát thuốc"}
              </button>
            </div>
          ) : (
            <div className="py-16 text-center text-slate-400 text-xs">
              Chọn một lượt chờ ở danh sách bên trái để đối chiếu thông tin đơn thuốc và thực hiện cấp phát.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
