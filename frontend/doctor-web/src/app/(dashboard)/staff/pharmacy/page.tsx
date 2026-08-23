"use client";

import { useCallback, useEffect, useState } from "react";
import { queueApi, QueueEntry } from "@/lib/queue-api";
import { prescriptionApi, PrescriptionResponse } from "@/lib/prescription-api";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import { getErrorMessage } from "@/lib/error-utils";

const PHARMACY_SERVICE_POINT = "PHARMACY-MAIN-01";

const statusLabel = (status: QueueEntry["queueStatus"]) => {
  switch (status) {
    case "QUEUED":
      return "Đang chờ gọi";
    case "CALLED":
      return "Đã gọi số";
    case "IN_PROGRESS":
      return "Đang đối chiếu / phát thuốc";
    case "COMPLETED":
      return "Hoàn tất";
    case "CANCELLED":
      return "Đã hủy";
    default:
      return status;
  }
};

export default function StaffPharmacyPage() {
  const [entries, setEntries] = useState<QueueEntry[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [activeEntry, setActiveEntry] = useState<QueueEntry | null>(null);
  const [activePrescription, setActivePrescription] = useState<PrescriptionResponse | null>(null);
  const [isLoadingPrescription, setIsLoadingPrescription] = useState(false);
  const [prescriptionError, setPrescriptionError] = useState<string | null>(null);
  const [isStarting, setIsStarting] = useState(false);
  const [isDispensing, setIsDispensing] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: "success" | "danger" | "warning" } | null>(null);

  const showToast = useCallback((message: string, type: "success" | "danger" | "warning" = "success") => {
    setToast({ message, type });
    window.setTimeout(() => setToast(null), 3000);
  }, []);

  const notifyAi = (text: string) => {
    window.dispatchEvent(new CustomEvent("careflow:ai-notify", { detail: { text } }));
  };

  const replaceEntry = useCallback((updated: QueueEntry) => {
    setEntries(current => current?.map(entry => entry.entryId === updated.entryId ? updated : entry) ?? null);
    setActiveEntry(current => current?.entryId === updated.entryId ? updated : current);
  }, []);

  const fetchPharmacyQueue = useCallback(async (showLoading = true) => {
    if (showLoading) setIsLoading(true);
    setError(null);
    try {
      const response = await queueApi.getServicePointActive(PHARMACY_SERVICE_POINT);
      const nextEntries = response.data?.entries ?? [];
      setEntries(nextEntries);
      setActiveEntry(current => {
        if (current) {
          return nextEntries.find(entry => entry.entryId === current.entryId) ?? current;
        }
        return nextEntries.find(entry => entry.queueStatus === "IN_PROGRESS" || entry.queueStatus === "CALLED") ?? null;
      });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Không thể tải danh sách hàng đợi phát thuốc."));
      setEntries([]);
    } finally {
      if (showLoading) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void fetchPharmacyQueue(), 0);
    const pollingTimer = window.setInterval(() => void fetchPharmacyQueue(false), 10_000);
    return () => {
      window.clearTimeout(timer);
      window.clearInterval(pollingTimer);
    };
  }, [fetchPharmacyQueue]);

  useEffect(() => {
    if (!activeEntry?.prescriptionId) {
      setActivePrescription(null);
      setPrescriptionError(activeEntry ? "Lượt này chưa liên kết với toa thuốc." : null);
      setIsLoadingPrescription(false);
      return;
    }

    let cancelled = false;
    setActivePrescription(null);
    setPrescriptionError(null);
    setIsLoadingPrescription(true);

    void prescriptionApi.getPrescription(activeEntry.prescriptionId)
      .then(response => {
        if (cancelled) return;
        if (!response.data) {
          setPrescriptionError("Không tìm thấy chi tiết toa thuốc.");
          return;
        }
        if (response.data.patientId !== activeEntry.patientId) {
          setPrescriptionError("Mã bệnh nhân trong queue không khớp với mã bệnh nhân trên toa.");
          return;
        }
        setActivePrescription(response.data);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setPrescriptionError(getErrorMessage(err, "Không thể tải chi tiết toa thuốc."));
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoadingPrescription(false);
      });

    return () => {
      cancelled = true;
    };
  }, [activeEntry]);

  const handleCallNext = async () => {
    try {
      const response = await queueApi.callNextAtServicePoint(PHARMACY_SERVICE_POINT);
      if (!response.data?.queueNumber) {
        notifyAi("Hiện tại chưa có bệnh nhân nào đâu ạ!");
        showToast("Hiện chưa có bệnh nhân nào trong hàng đợi.", "warning");
        return;
      }
      replaceEntry(response.data);
      notifyAi(`Mời số thứ tự ${response.data.queueNumber} tới quầy phát thuốc ạ!`);
      showToast(`Mời số thứ tự ${response.data.queueNumber} tới quầy phát thuốc!`);
      await fetchPharmacyQueue();
    } catch (err: unknown) {
      const message = getErrorMessage(err, "Không thể gọi lượt phát thuốc tiếp theo.");
      notifyAi(message);
      showToast(message, "danger");
    }
  };

  const handleStartVerification = async () => {
    if (!activeEntry || activeEntry.queueStatus !== "CALLED") return;
    setIsStarting(true);
    try {
      const response = await queueApi.startEntry(activeEntry.entryId);
      replaceEntry(response.data);
      showToast("Đã bắt đầu đối chiếu bệnh nhân và toa thuốc.");
    } catch (err: unknown) {
      showToast(getErrorMessage(err, "Không thể bắt đầu lượt phát thuốc."), "danger");
    } finally {
      setIsStarting(false);
    }
  };

  const handleDispenseMedicine = async () => {
    if (!activeEntry || !activePrescription) return;
    if (activeEntry.queueStatus !== "IN_PROGRESS") {
      showToast("Cần bắt đầu đối chiếu trước khi xác nhận phát thuốc.", "warning");
      return;
    }
    if (activePrescription.status !== "CONFIRMED") {
      showToast("Chỉ được phát toa đang ở trạng thái CONFIRMED.", "warning");
      return;
    }
    if (activePrescription.patientId !== activeEntry.patientId) {
      showToast("Mã bệnh nhân trên queue và toa không khớp.", "danger");
      return;
    }

    setIsDispensing(true);
    try {
      await prescriptionApi.dispensePrescription(activePrescription.id);
      notifyAi(`Đã hoàn tất cấp phát thuốc cho số thứ tự ${activeEntry.queueNumber} rồi ạ!`);
      showToast(`Đã hoàn tất phát thuốc cho số thứ tự ${activeEntry.queueNumber}!`);
      setActiveEntry(null);
      setActivePrescription(null);
      await fetchPharmacyQueue();
    } catch (err: unknown) {
      const message = getErrorMessage(err, "Đã xảy ra lỗi khi xác nhận phát thuốc.");
      notifyAi(message);
      showToast(message, "danger");
    } finally {
      setIsDispensing(false);
    }
  };

  const activePatientMatches = Boolean(
    activeEntry && activePrescription && activeEntry.patientId === activePrescription.patientId,
  );
  const canDispense = Boolean(
    activeEntry?.queueStatus === "IN_PROGRESS"
      && activePrescription?.status === "CONFIRMED"
      && activePatientMatches
      && !isLoadingPrescription
      && !prescriptionError,
  );
  const hasServingEntry = activeEntry?.queueStatus === "CALLED" || activeEntry?.queueStatus === "IN_PROGRESS";

  return (
    <div className="space-y-6 pb-12">
      {toast && (
        <div className={`fixed top-5 right-5 z-50 p-4 border shadow-lg max-w-md rounded-lg text-white ${
          toast.type === "success" ? "bg-[#2B1D30] border-purple-500" :
          toast.type === "warning" ? "bg-amber-900 border-amber-500" : "bg-rose-900 border-rose-500"
        }`}>
          <div className="flex items-center justify-between gap-4">
            <span className="text-xs font-semibold">{toast.message}</span>
            <button onClick={() => setToast(null)} className="text-white/60 hover:text-white text-xs font-bold" aria-label="Đóng thông báo">X</button>
          </div>
        </div>
      )}

      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border border-card-border bg-card-bg p-5 rounded-xl shadow-sm">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-block w-2.5 h-2.5 rounded-full bg-purple-500 animate-pulse" />
            <span className="text-xs font-bold uppercase tracking-wider text-purple-600">Phân hệ Nhân viên Dược / Quầy Phát thuốc</span>
          </div>
          <h1 className="text-2xl font-extrabold text-[#2B1D30] mt-1">Hàng đợi Phát thuốc</h1>
          <p className="text-xs text-[#6A5C70] mt-0.5">Cấp phát theo FIFO cho toa đã được bác sĩ ký và xác nhận</p>
        </div>
        <button
          onClick={handleCallNext}
          disabled={isLoading || Boolean(hasServingEntry)}
          className="px-5 py-3 rounded-xl font-bold text-sm text-white bg-[#6E2582] hover:bg-[#561A66] shadow-md transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
        >
          Gọi số tiếp theo
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-[#2B1D30]">Danh sách bệnh nhân chờ nhận thuốc</h2>
            <span className="text-xs font-semibold px-2.5 py-1 bg-purple-100 text-purple-800 rounded-full">{(entries ?? []).length} lượt</span>
          </div>

          {isLoading ? (
            <div className="py-12 flex justify-center"><LoadingSpinner size="md" /></div>
          ) : error ? (
            <div className="p-4 bg-rose-50 border border-rose-200 text-rose-700 rounded-lg text-xs font-medium">{error}</div>
          ) : (entries ?? []).length === 0 ? (
            <div className="py-12 text-center text-text-muted text-xs">Hiện chưa có lượt chờ phát thuốc nào.</div>
          ) : (
            <div className="divide-y divide-card-border">
              {(entries ?? []).map(entry => {
                const selected = activeEntry?.entryId === entry.entryId;
                return (
                  <div key={entry.entryId} className={`py-3.5 flex items-center justify-between gap-4 ${selected ? "bg-purple-50/60 -mx-2 px-2 rounded-lg" : ""}`}>
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="w-12 h-12 shrink-0 rounded-xl bg-purple-100 text-purple-800 flex items-center justify-center font-extrabold text-base">{entry.queueNumber}</div>
                      <div className="min-w-0">
                        <p className="text-sm font-bold text-[#2B1D30]">Số thứ tự: {entry.queueNumber}</p>
                        <p className="text-xs text-text-muted mt-0.5">{statusLabel(entry.queueStatus)}</p>
                        <p className="text-[11px] text-text-muted truncate">Mã hồ sơ: {entry.patientId}</p>
                      </div>
                    </div>
                    <button
                      onClick={() => setActiveEntry(entry)}
                      className="shrink-0 w-28 py-1.5 bg-[#6E2582] text-white text-xs font-semibold rounded-md hover:bg-[#561A66] cursor-pointer text-center disabled:opacity-50"
                    >
                      {selected ? "Đang chọn" : "Đối chiếu toa"}
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        <div className="border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
          <h2 className="text-base font-bold text-[#2B1D30]">Đối chiếu và phát thuốc</h2>
          {activeEntry ? (
            <div className="space-y-4 text-xs">
              <div className="p-3 bg-purple-50 border border-purple-200 rounded-lg">
                <p className="font-bold text-purple-900 text-sm">Số thứ tự: {activeEntry.queueNumber}</p>
                <p className="text-purple-700 mt-1">Trạng thái: {statusLabel(activeEntry.queueStatus)}</p>
                <p className="text-purple-700 mt-1 break-all">Mã bệnh nhân: {activeEntry.patientId}</p>
              </div>

              {isLoadingPrescription ? (
                <div className="py-8 flex justify-center"><LoadingSpinner size="sm" /></div>
              ) : prescriptionError ? (
                <div className="p-3 bg-rose-50 border border-rose-200 text-rose-700 rounded-lg">{prescriptionError}</div>
              ) : activePrescription ? (
                <>
                  <div className={`p-3 rounded-lg border ${activePatientMatches ? "bg-emerald-50 border-emerald-200 text-emerald-800" : "bg-rose-50 border-rose-200 text-rose-700"}`}>
                    <p className="font-bold">{activePatientMatches ? "Đã khớp bệnh nhân với toa" : "Không khớp bệnh nhân"}</p>
                    <p className="mt-1 break-all">Mã bệnh nhân trên toa: {activePrescription.patientId}</p>
                    <p className="mt-1">Trạng thái toa: {activePrescription.status}</p>
                  </div>

                  <div className="p-3 bg-gray-50 border border-card-border rounded-lg space-y-1">
                    <p className="font-semibold text-text">Chẩn đoán: {activePrescription.diagnosis || "Không có chẩn đoán"}</p>
                    <p className="text-text-muted break-all">Mã toa: {activePrescription.id}</p>
                    <p className="text-text-muted break-all">Mã phiên khám: {activePrescription.consultationId}</p>
                  </div>

                  <div className="space-y-2">
                    <p className="font-semibold text-text">Danh sách thuốc trong toa:</p>
                    <div className="p-3 bg-gray-50 border border-card-border rounded-lg space-y-2 text-text">
                      {activePrescription.items.length > 0 ? activePrescription.items.map(item => (
                        <p key={item.id}>• {item.medicineName} — {item.quantity} {item.unit} ({item.dosage}, {item.frequency})</p>
                      )) : <p className="text-text-muted italic">Toa chưa có thuốc.</p>}
                    </div>
                  </div>

                  {activeEntry.queueStatus === "CALLED" && (
                    <button
                      onClick={handleStartVerification}
                      disabled={isStarting || !activePatientMatches}
                      className="w-full py-3 bg-[#6E2582] hover:bg-[#561A66] text-white font-bold rounded-lg shadow-sm text-xs cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {isStarting ? "Đang bắt đầu..." : "Bắt đầu đối chiếu"}
                    </button>
                  )}

                  {activeEntry.queueStatus === "QUEUED" && (
                    <p className="p-3 bg-amber-50 border border-amber-200 text-amber-800 rounded-lg">Lượt này đang chờ gọi theo thứ tự FIFO.</p>
                  )}

                  {activeEntry.queueStatus === "IN_PROGRESS" && (
                    <button
                      onClick={handleDispenseMedicine}
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
            <div className="py-12 text-center text-text-muted text-xs">Chọn một lượt chờ để tải toa và đối chiếu trước khi phát.</div>
          )}
        </div>
      </div>
    </div>
  );
}
