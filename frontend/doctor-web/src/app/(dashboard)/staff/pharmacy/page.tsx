"use client";

import { useState } from "react";

const MOCK_QUEUE = [
  {
    entryId: "ph-001",
    queueNumber: "TH-001",
    patientName: "Võ Thị Lan",
    patientDob: "12/05/1975",
    status: "CALLED",
    calledAt: "22:20",
    prescriptionId: "RX-2026-0042",
    prescriptionStatus: "CONFIRMED",
    paymentStatus: "NOT_REQUIRED",
    dispensed: false,
    diagnosis: "E11.9 — Đái tháo đường tuýp 2",
    doctorName: "BS. Nguyễn Văn A",
    items: [
      { id: "i1", name: "Metformin 500mg", quantity: 60, unit: "viên", dosage: "500mg", frequency: "2 lần/ngày, sau ăn", flag: null },
      { id: "i2", name: "Glibenclamide 5mg", quantity: 30, unit: "viên", dosage: "5mg", frequency: "1 lần/ngày, trước ăn sáng", flag: "CAUTION" },
    ],
    warning: "Glibenclamide — Thuốc hạ đường huyết mạnh. Xác nhận BN không dị ứng Sulfonylurea.",
  },
  {
    entryId: "ph-002",
    queueNumber: "TH-002",
    patientName: "Trần Văn Bình",
    patientDob: "03/11/1988",
    status: "CHECKED_IN",
    calledAt: null,
    prescriptionId: "RX-2026-0043",
    prescriptionStatus: "CONFIRMED",
    paymentStatus: "NOT_REQUIRED",
    dispensed: false,
    diagnosis: "J06.9 — Nhiễm khuẩn hô hấp trên",
    doctorName: "BS. Lê Thị Hoa",
    items: [
      { id: "i3", name: "Amoxicillin 500mg", quantity: 21, unit: "viên", dosage: "500mg", frequency: "3 lần/ngày, sau ăn", flag: null },
      { id: "i4", name: "Paracetamol 500mg", quantity: 15, unit: "viên", dosage: "500mg", frequency: "Khi sốt, cách tối thiểu 4 giờ", flag: null },
      { id: "i5", name: "Loratadin 10mg", quantity: 10, unit: "viên", dosage: "10mg", frequency: "1 lần/ngày", flag: null },
    ],
    warning: null,
  },
  {
    entryId: "ph-003",
    queueNumber: "TH-003",
    patientName: "Nguyễn Thị Cúc",
    patientDob: "22/07/1962",
    status: "CHECKED_IN",
    calledAt: null,
    prescriptionId: "RX-2026-0044",
    prescriptionStatus: "CONFIRMED",
    paymentStatus: "NOT_REQUIRED",
    dispensed: false,
    diagnosis: "I10 — Tăng huyết áp nguyên phát",
    doctorName: "BS. Nguyễn Văn A",
    items: [
      { id: "i6", name: "Amlodipine 5mg", quantity: 30, unit: "viên", dosage: "5mg", frequency: "1 lần/ngày, buổi sáng", flag: null },
    ],
    warning: null,
  },
];

type MockEntry = typeof MOCK_QUEUE[0];

export default function StaffPharmacyPage() {
  const [entries, setEntries] = useState<MockEntry[]>(MOCK_QUEUE);
  const [activeEntry, setActiveEntry] = useState<MockEntry | null>(MOCK_QUEUE[0]);
  const [isDispensing, setIsDispensing] = useState(false);
  const [callingNext, setCallingNext] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: "success" | "danger" | "warning" } | null>(null);
  const [confirmed, setConfirmed] = useState(false);

  const showToast = (message: string, type: "success" | "danger" | "warning" = "success") => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  };

  const handleCallNext = async () => {
    setCallingNext(true);
    const waiting = entries.find((e) => e.status === "CHECKED_IN");
    await new Promise((r) => setTimeout(r, 600));
    if (waiting) {
      setEntries((prev) => prev.map((e) => e.entryId === waiting.entryId ? { ...e, status: "CALLED", calledAt: new Date().toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }) } : e));
      showToast(`Mời số thứ tự ${waiting.queueNumber} tới quầy phát thuốc!`);
      setActiveEntry({ ...waiting, status: "CALLED" });
      setConfirmed(false);
    } else {
      showToast("Hiện chưa có bệnh nhân nào trong hàng đợi.", "warning");
    }
    setCallingNext(false);
  };

  const handleSelect = (entry: MockEntry) => {
    setActiveEntry(entry);
    setConfirmed(false);
  };

  const handleDispense = async () => {
    if (!activeEntry) return;
    if (!confirmed) {
      showToast("Vui lòng xác nhận đã đối chiếu toa thuốc trước khi phát.", "warning");
      return;
    }
    setIsDispensing(true);
    await new Promise((r) => setTimeout(r, 900));
    setEntries((prev) => prev.filter((e) => e.entryId !== activeEntry.entryId));
    showToast(`Đã hoàn tất cấp phát thuốc cho số thứ tự ${activeEntry.queueNumber}!`);
    setActiveEntry(null);
    setConfirmed(false);
    setIsDispensing(false);
  };

  const calledEntry = entries.find((e) => e.status === "CALLED");

  return (
    <div className="space-y-5 pb-12">
      {/* Toast */}
      {toast && (
        <div className={`fixed top-5 right-5 z-50 p-4 border shadow-xl max-w-sm rounded-xl text-white flex items-start gap-3 transition-all ${
          toast.type === "success" ? "bg-[#2B1D30] border-purple-500" : toast.type === "warning" ? "bg-amber-900 border-amber-500" : "bg-rose-900 border-rose-500"
        }`}>
          <span className="text-sm font-medium flex-1">{toast.message}</span>
          <button onClick={() => setToast(null)} className="text-white/50 hover:text-white text-xs font-bold shrink-0">✕</button>
        </div>
      )}

      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border border-card-border bg-card-bg p-5 rounded-xl shadow-sm">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="inline-block w-2 h-2 rounded-full bg-purple-500 animate-pulse" />
            <span className="text-xs font-bold uppercase tracking-widest text-purple-600">Phân hệ Nhân viên Dược / Quầy Phát thuốc</span>
          </div>
          <h1 className="text-2xl font-extrabold text-[#2B1D30]">Hàng đợi Phát thuốc</h1>
          <p className="text-xs text-[#6A5C70] mt-0.5">Cấp phát thuốc theo thứ tự cho các đơn thuốc đã được Bác sĩ ký và xác nhận</p>
        </div>
        <button
          onClick={handleCallNext}
          disabled={callingNext}
          className="px-6 py-3 rounded-xl font-bold text-sm text-white bg-[#6E2582] hover:bg-[#561A66] shadow-md transition-all cursor-pointer disabled:opacity-60"
        >
          {callingNext ? "Đang gọi..." : "📢 Gọi số tiếp theo"}
        </button>
      </div>

      {/* Số đang gọi banner */}
      {calledEntry && (
        <div className="flex items-center gap-4 border border-amber-300 bg-amber-50 rounded-xl px-5 py-3 shadow-sm">
          <div className="w-14 h-14 rounded-2xl bg-amber-500 text-white flex items-center justify-center font-black text-lg animate-pulse shrink-0">
            {calledEntry.queueNumber}
          </div>
          <div>
            <p className="text-xs font-semibold text-amber-700 uppercase tracking-wide">Đang gọi</p>
            <p className="text-lg font-extrabold text-amber-900">{calledEntry.patientName}</p>
            <p className="text-xs text-amber-700">Toa: {calledEntry.prescriptionId} · Gọi lúc {calledEntry.calledAt}</p>
          </div>
          <div className="ml-auto flex gap-2">
            <span className="px-2 py-1 bg-green-100 text-green-800 text-xs font-bold rounded-full border border-green-300">✓ CONFIRMED</span>
            <span className="px-2 py-1 bg-blue-100 text-blue-800 text-xs font-bold rounded-full border border-blue-300">✓ Đã thanh toán</span>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-5">
        {/* Queue list */}
        <div className="lg:col-span-2 border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-3">
          <div className="flex items-center justify-between mb-1">
            <h2 className="text-base font-bold text-[#2B1D30]">Danh sách chờ phát thuốc</h2>
            <span className="text-xs font-semibold px-2.5 py-1 bg-purple-100 text-purple-800 rounded-full">{entries.length} lượt</span>
          </div>

          {entries.length === 0 ? (
            <div className="py-10 text-center text-text-muted text-xs">Hàng đợi trống. Chưa có lượt nào.</div>
          ) : (
            <div className="space-y-2">
              {entries.map((entry) => {
                const isCalled = entry.status === "CALLED";
                const isActive = activeEntry?.entryId === entry.entryId;
                return (
                  <button
                    key={entry.entryId}
                    onClick={() => handleSelect(entry)}
                    className={`w-full text-left p-3 rounded-xl border transition-all cursor-pointer ${
                      isActive
                        ? "border-purple-400 bg-purple-50 shadow-sm"
                        : "border-card-border bg-white hover:border-purple-200 hover:bg-purple-50/40"
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <div className={`w-12 h-12 rounded-xl flex items-center justify-center font-black text-sm shrink-0 ${
                        isCalled ? "bg-amber-500 text-white animate-pulse" : "bg-purple-100 text-purple-700"
                      }`}>
                        {entry.queueNumber}
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-bold text-[#2B1D30] truncate">{entry.patientName}</p>
                        <p className="text-xs text-text-muted truncate">{entry.diagnosis.split("—")[0].trim()}</p>
                        <div className="flex items-center gap-1.5 mt-0.5">
                          <span className={`w-1.5 h-1.5 rounded-full ${isCalled ? "bg-amber-500" : "bg-blue-400"}`} />
                          <span className={`text-xs font-semibold ${isCalled ? "text-amber-700" : "text-blue-700"}`}>
                            {isCalled ? "Đang gọi" : "Đã tiếp nhận"}
                          </span>
                        </div>
                      </div>
                      {entry.warning && (
                        <span className="text-amber-500 text-base shrink-0" title="Cảnh báo đối chiếu">⚠️</span>
                      )}
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* Prescription detail */}
        <div className="lg:col-span-3 border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
          <h2 className="text-base font-bold text-[#2B1D30]">Chi tiết đơn thuốc & Đối chiếu</h2>

          {activeEntry ? (
            <div className="space-y-4 text-sm">
              {/* Patient + Rx info */}
              <div className="grid grid-cols-2 gap-3">
                <div className="p-3 bg-purple-50 border border-purple-200 rounded-xl space-y-1">
                  <p className="text-xs font-semibold text-purple-500 uppercase tracking-wide">Bệnh nhân</p>
                  <p className="font-bold text-purple-900">{activeEntry.patientName}</p>
                  <p className="text-xs text-purple-700">NS: {activeEntry.patientDob}</p>
                  <p className="text-xs text-purple-700">Số thứ tự: <span className="font-bold">{activeEntry.queueNumber}</span></p>
                </div>
                <div className="p-3 bg-green-50 border border-green-200 rounded-xl space-y-1">
                  <p className="text-xs font-semibold text-green-500 uppercase tracking-wide">Đơn thuốc</p>
                  <p className="font-bold text-green-900">{activeEntry.prescriptionId}</p>
                  <p className="text-xs text-green-700">{activeEntry.doctorName}</p>
                  <div className="flex gap-1 mt-1 flex-wrap">
                    <span className="px-1.5 py-0.5 bg-green-200 text-green-900 text-xs font-bold rounded">✓ CONFIRMED</span>
                    <span className="px-1.5 py-0.5 bg-blue-100 text-blue-800 text-xs font-bold rounded">✓ Thanh toán OK</span>
                    <span className="px-1.5 py-0.5 bg-gray-100 text-gray-700 text-xs font-bold rounded">✗ Chưa phát</span>
                  </div>
                </div>
              </div>

              {/* Diagnosis */}
              <div className="px-3 py-2 bg-gray-50 border border-card-border rounded-lg text-xs text-[#2B1D30]">
                <span className="font-semibold text-[#6A5C70]">Chẩn đoán: </span>{activeEntry.diagnosis}
              </div>

              {/* Warning banner */}
              {activeEntry.warning && (
                <div className="flex items-start gap-2 p-3 bg-amber-50 border border-amber-300 rounded-xl">
                  <span className="text-amber-500 text-base shrink-0 mt-0.5">⚠️</span>
                  <div>
                    <p className="text-xs font-bold text-amber-800 mb-0.5">Cảnh báo đối chiếu</p>
                    <p className="text-xs text-amber-700">{activeEntry.warning}</p>
                  </div>
                </div>
              )}

              {/* Drug list */}
              <div>
                <p className="text-xs font-bold text-[#2B1D30] mb-2">Danh sách thuốc ({activeEntry.items.length} loại):</p>
                <div className="space-y-2">
                  {activeEntry.items.map((item) => (
                    <div key={item.id} className={`flex items-start gap-3 p-3 rounded-xl border ${item.flag === "CAUTION" ? "bg-amber-50 border-amber-300" : "bg-white border-card-border"}`}>
                      <div className="w-8 h-8 rounded-lg bg-purple-100 flex items-center justify-center shrink-0">
                        <span className="text-sm">💊</span>
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <p className="text-sm font-bold text-[#2B1D30]">{item.name}</p>
                          {item.flag === "CAUTION" && (
                            <span className="px-1.5 py-0.5 bg-amber-200 text-amber-800 text-xs font-bold rounded">⚠ Thận trọng</span>
                          )}
                        </div>
                        <p className="text-xs text-text-muted">{item.quantity} {item.unit} · {item.frequency}</p>
                      </div>
                      <div className="text-right shrink-0">
                        <p className="text-xs font-bold text-[#2B1D30]">{item.quantity} {item.unit}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Confirmation checkbox */}
              <label className="flex items-start gap-3 p-3 bg-gray-50 border border-card-border rounded-xl cursor-pointer hover:bg-purple-50 hover:border-purple-300 transition-all">
                <input
                  type="checkbox"
                  checked={confirmed}
                  onChange={(e) => setConfirmed(e.target.checked)}
                  className="w-4 h-4 mt-0.5 accent-purple-600 shrink-0 cursor-pointer"
                />
                <span className="text-xs text-[#2B1D30] font-medium leading-relaxed">
                  Tôi đã đối chiếu đầy đủ danh sách thuốc, số lượng, tên bệnh nhân và đơn thuốc hợp lệ. Xác nhận cấp phát.
                </span>
              </label>

              {/* Dispense button */}
              <button
                onClick={handleDispense}
                disabled={isDispensing || !confirmed}
                className={`w-full py-3.5 font-bold rounded-xl shadow-sm transition-all text-sm ${
                  confirmed
                    ? "bg-[#6E2582] hover:bg-[#561A66] text-white cursor-pointer"
                    : "bg-gray-200 text-gray-400 cursor-not-allowed"
                } disabled:opacity-60`}
              >
                {isDispensing ? "Đang xác nhận phát thuốc..." : "✓ Xác nhận cấp phát thuốc"}
              </button>
            </div>
          ) : (
            <div className="py-16 text-center text-text-muted text-xs">
              <p className="text-4xl mb-3">💊</p>
              <p className="font-semibold text-sm text-[#2B1D30] mb-1">Chọn lượt để đối chiếu</p>
              <p>Chọn một lượt chờ ở danh sách bên trái để xem chi tiết đơn thuốc và thực hiện cấp phát.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
