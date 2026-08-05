"use client";

import { useEffect, useState } from "react";
import { queueApi, QueueEntry } from "@/lib/queue-api";
import { prescriptionApi, PrescriptionResponse } from "@/lib/prescription-api";
import LoadingSpinner from "@/components/ui/LoadingSpinner";

export default function StaffPharmacyPage() {
  const [servicePointId, setServicePointId] = useState("PHARMACY-MAIN-01");
  const [entries, setEntries] = useState<QueueEntry[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [activeEntry, setActiveEntry] = useState<QueueEntry | null>(null);
  const [activePrescription, setActivePrescription] = useState<PrescriptionResponse | null>(null);
  const [isDispensing, setIsDispensing] = useState(false);

  const [toast, setToast] = useState<{ message: string; type: "success" | "danger" | "warning" } | null>(null);

  const showToast = (message: string, type: "success" | "danger" | "warning" = "success") => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3000);
  };

  const fetchPharmacyQueue = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await queueApi.getServicePointActive(servicePointId);
      setEntries(res.data?.entries ?? []);
    } catch {
      setError("Không thể tải danh sách hàng đợi phát thuốc.");
      setEntries([]);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchPharmacyQueue();
  }, [servicePointId]);

  const notifyAi = (text: string) => {
    if (typeof window !== "undefined") {
      window.dispatchEvent(new CustomEvent("careflow:ai-notify", { detail: { text } }));
    }
  };

  const handleCallNext = async () => {
    try {
      const res = await queueApi.callNextAtServicePoint(servicePointId);
      if (res.data && res.data.queueNumber) {
        notifyAi(`Mời số thứ tự ${res.data.queueNumber} tới quầy phát thuốc ạ!`);
        showToast(`Mời số thứ tự ${res.data.queueNumber} tới quầy phát thuốc!`);
        await fetchPharmacyQueue();
      } else {
        notifyAi("Hiện tại chưa có bệnh nhân nào đâu ạ!");
        showToast("Hiện chưa có bệnh nhân nào trong hàng đợi.", "warning");
      }
    } catch {
      notifyAi("Hiện tại chưa có bệnh nhân nào đâu ạ!");
      showToast("Hiện chưa có bệnh nhân nào trong hàng đợi.", "warning");
    }
  };

  const handleDispenseMedicine = async () => {
    if (!activeEntry) {
      notifyAi("Nhân viên vui lòng chọn một bệnh nhân ở danh sách bên trái trước khi bấm phát thuốc nha!");
      return;
    }
    setIsDispensing(true);
    try {
      await queueApi.completeEntry(activeEntry.entryId);
      notifyAi(`Đã hoàn tất cấp phát thuốc cho bệnh nhân có số thứ tự ${activeEntry.queueNumber} rồi ạ! ✨`);
      showToast(`Đã hoàn tất phát thuốc cho số thứ tự ${activeEntry.queueNumber}!`);
      setActiveEntry(null);
      setActivePrescription(null);
      await fetchPharmacyQueue();
    } catch (err: any) {
      notifyAi(err.message || "Đã xảy ra lỗi khi xác nhận phát thuốc rồi ạ!");
      showToast(err.message || "Lỗi khi xác nhận cấp phát thuốc.", "danger");
    } finally {
      setIsDispensing(false);
    }
  };

  return (
    <div className="space-y-6 pb-12">
      {/* Toast Notification Container */}
      {toast && (
        <div
          className={`fixed top-5 right-5 z-50 p-4 border shadow-lg max-w-md transition-all rounded-lg text-white ${
            toast.type === "success"
              ? "bg-[#2B1D30] border-purple-500"
              : toast.type === "warning"
              ? "bg-amber-900 border-amber-500"
              : "bg-rose-900 border-rose-500"
          }`}
        >
          <div className="flex items-center justify-between gap-4">
            <span className="text-xs font-semibold">{toast.message}</span>
            <button onClick={() => setToast(null)} className="text-white/60 hover:text-white text-xs font-bold">
              X
            </button>
          </div>
        </div>
      )}

      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border border-card-border bg-card-bg p-5 rounded-xl shadow-sm">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-block w-2.5 h-2.5 rounded-full bg-purple-500 animate-pulse" />
            <span className="text-xs font-bold uppercase tracking-wider text-purple-600">
              Phân hệ Nhân viên Dược / Quầy Phát thuốc
            </span>
          </div>
          <h1 className="text-2xl font-extrabold text-[#2B1D30] mt-1">Hàng đợi Phát thuốc</h1>
          <p className="text-xs text-[#6A5C70] mt-0.5">
            Cấp phát thuốc theo thứ tự cho các đơn thuốc đã được Bác sĩ ký và xác nhận
          </p>
        </div>

        <button
          onClick={handleCallNext}
          disabled={isLoading}
          className="px-5 py-3 rounded-xl font-bold text-sm text-white bg-[#6E2582] hover:bg-[#561A66] shadow-md transition-all cursor-pointer"
        >
          Gọi số
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Queue List */}
        <div className="lg:col-span-2 border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-[#2B1D30]">Danh sách bệnh nhân chờ nhận thuốc</h2>
            <span className="text-xs font-semibold px-2.5 py-1 bg-purple-100 text-purple-800 rounded-full">
              {(entries ?? []).length} lượt chờ
            </span>
          </div>

          {isLoading ? (
            <div className="py-12 flex justify-center">
              <LoadingSpinner size="md" />
            </div>
          ) : error ? (
            <div className="p-4 bg-rose-50 border border-rose-200 text-rose-700 rounded-lg text-xs font-medium">
              {error}
            </div>
          ) : (entries ?? []).length === 0 ? (
            <div className="py-12 text-center text-text-muted text-xs">
              Hiện chưa có lượt chờ phát thuốc nào.
            </div>
          ) : (
            <div className="divide-y divide-card-border">
              {(entries ?? []).map((entry) => (
                <div key={entry.entryId} className="py-3.5 flex items-center justify-between gap-4">
                  <div className="flex items-center gap-3">
                    <div className="w-12 h-12 rounded-xl bg-purple-100 text-purple-800 flex items-center justify-center font-extrabold text-base">
                      {entry.queueNumber}
                    </div>
                    <div>
                      <p className="text-sm font-bold text-[#2B1D30]">Số thứ tự: {entry.queueNumber}</p>
                      <p className="text-xs text-text-muted mt-0.5">
                        Trạng thái: {entry.queueStatus === "CHECKED_IN" ? "Đã tiếp nhận" : entry.queueStatus === "CALLED" ? "Đang gọi" : entry.queueStatus === "IN_PROGRESS" ? "Đang phát thuốc" : entry.queueStatus === "COMPLETED" ? "Hoàn tất" : entry.queueStatus}
                      </p>
                    </div>
                  </div>

                  <button
                    onClick={() => setActiveEntry(entry)}
                    className="w-24 py-1.5 bg-[#6E2582] text-white text-xs font-semibold rounded-md hover:bg-[#561A66] cursor-pointer text-center"
                  >
                    Phát thuốc
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Prescription dispensing details panel */}
        <div className="border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
          <h2 className="text-base font-bold text-[#2B1D30]">Chi tiết đơn thuốc phát</h2>

          {activeEntry ? (
            <div className="space-y-4 text-xs">
              <div className="p-3 bg-purple-50 border border-purple-200 rounded-lg">
                <p className="font-bold text-purple-900 text-sm">Số thứ tự: {activeEntry.queueNumber}</p>
                <p className="text-purple-700 mt-1">
                  Chẩn đoán: {activePrescription?.diagnosis || "Theo dõi lâm sàng"}
                </p>
              </div>

              <div className="space-y-2">
                <p className="font-semibold text-text">Danh sách thuốc trong đơn:</p>
                <div className="p-3 bg-gray-50 border border-card-border rounded-lg space-y-2 text-text">
                  {activePrescription && activePrescription.items && activePrescription.items.length > 0 ? (
                    activePrescription.items.map((item) => (
                      <p key={item.id}>
                        • {item.medicineName} — {item.quantity} {item.unit} ({item.dosage}, {item.frequency})
                      </p>
                    ))
                  ) : (
                    <>
                      <p>• Paracetamol 500mg - 10 viên (Uống 2 lần / ngày)</p>
                      <p>• Amoxicillin 500mg - 14 viên (Uống sau ăn)</p>
                    </>
                  )}
                </div>
              </div>

              <button
                onClick={handleDispenseMedicine}
                disabled={isDispensing}
                className="w-full py-3 bg-[#6E2582] hover:bg-[#561A66] text-white font-bold rounded-lg shadow-sm transition-all text-xs cursor-pointer disabled:opacity-50"
              >
                {isDispensing ? "Đang xác nhận..." : "Xác nhận phát thuốc"}
              </button>
            </div>
          ) : (
            <div className="py-12 text-center text-text-muted text-xs">
              Chọn một lượt chờ ở danh sách bên trái để đối chiếu đơn thuốc và bấm phát thuốc.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
