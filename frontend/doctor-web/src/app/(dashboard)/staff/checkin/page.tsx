"use client";

import { useState } from "react";
import { queueApi, QueueEntry } from "@/lib/queue-api";
import LoadingSpinner from "@/components/ui/LoadingSpinner";

export default function StaffCheckinPage() {
  const [qrInput, setQrInput] = useState("");
  const [roomId, setRoomId] = useState("ROOM-01");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [checkInResult, setCheckInResult] = useState<QueueEntry | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: "success" | "danger" } | null>(null);

  const showToast = (message: string, type: "success" | "danger" = "success") => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3000);
  };

  const notifyAi = (text: string) => {
    if (typeof window !== "undefined") {
      window.dispatchEvent(new CustomEvent("careflow:ai-notify", { detail: { text } }));
    }
  };

  const handleCheckIn = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!qrInput.trim()) {
      setError("Vui lòng nhập hoặc quét mã QR phiếu khám.");
      notifyAi("Nhân viên vui lòng nhập hoặc quét mã QR trên phiếu khám điện tử trước khi bấm xác nhận check-in nha!");
      return;
    }

    setIsSubmitting(true);
    setError(null);
    setCheckInResult(null);

    try {
      const res = await queueApi.checkIn({
        qrToken: qrInput.trim(),
        roomId: roomId,
      });

      if (res.data) {
        setCheckInResult(res.data);
        notifyAi(`Tiếp nhận thành công bệnh nhân! Số thứ tự cấp: ${res.data.queueNumber} tại ${res.data.roomCode} ✨`);
        showToast(`Tiếp nhận thành công bệnh nhân! STT: ${res.data.queueNumber}`);
        setQrInput("");
      }
    } catch (err: any) {
      const errorMsg = err.message || "Không thể check-in ticket này. Mã QR không hợp lệ hoặc đã quá hạn.";
      setError(errorMsg);
      notifyAi(`Rất tiếc! ${errorMsg}`);
      showToast("Lỗi khi tiếp nhận bệnh nhân", "danger");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6 pb-12">
      {/* Toast Notification Container */}
      {toast && (
        <div
          className={`fixed top-5 right-5 z-50 p-4 border shadow-lg max-w-md transition-all rounded-lg text-white ${
            toast.type === "success" ? "bg-[#2B1D30] border-emerald-500" : "bg-rose-900 border-rose-500"
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
      <div className="border border-card-border bg-card-bg p-5 rounded-xl shadow-sm">
        <div className="flex items-center gap-2">
          <span className="inline-block w-2.5 h-2.5 rounded-full bg-purple-500 animate-pulse" />
          <span className="text-xs font-bold uppercase tracking-wider text-purple-600">
            Phân hệ Nhân viên Quầy Tiếp nhận
          </span>
        </div>
        <h1 className="text-2xl font-extrabold text-[#2B1D30] mt-1">Quét QR & Tiếp nhận Bệnh nhân (Check-in)</h1>
        <p className="text-xs text-text-muted mt-0.5">
          Nhập hoặc quét mã QR trên phiếu khám điện tử của bệnh nhân để kích hoạt lượt khám vào Active Queue.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Form Input Section */}
        <div className="border border-card-border bg-card-bg p-6 rounded-xl shadow-sm space-y-4">
          <h2 className="text-base font-bold text-[#2B1D30]">Thông tin Check-in</h2>

          <form onSubmit={handleCheckIn} className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-text mb-1">Chọn phòng khám tiếp nhận:</label>
              <select
                value={roomId}
                onChange={(e) => setRoomId(e.target.value)}
                className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg text-text text-xs font-medium focus:ring-2 focus:ring-purple-500"
              >
                <option value="ROOM-01">Phòng khám Nội 01 (ROOM-01)</option>
                <option value="ROOM-02">Phòng khám Nội 02 (ROOM-02)</option>
                <option value="ROOM-03">Phòng khám Nhi 01 (ROOM-03)</option>
                <option value="ROOM-04">Phòng khám Ngoại 01 (ROOM-04)</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-bold text-text mb-1">
                Mã QR / Token Phiếu khám (Scan hoặc nhập tay):
              </label>
              <input
                type="text"
                value={qrInput}
                onChange={(e) => setQrInput(e.target.value)}
                placeholder="Ví dụ: TICKET-2026-00125 hoặc careflow:ticket:xyz..."
                className="w-full p-3 border border-card-border rounded-lg bg-card-bg text-text text-sm font-mono focus:ring-2 focus:ring-purple-500"
              />
            </div>

            {error && (
              <div className="p-3 bg-rose-50 border border-rose-200 text-rose-700 rounded-lg text-xs font-medium">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full py-3 bg-[#6E2582] hover:bg-[#561A66] text-white font-bold rounded-xl shadow-md transition-all text-xs cursor-pointer disabled:opacity-50"
            >
              {isSubmitting ? "Đang xử lý..." : "Xác nhận check-in"}
            </button>
          </form>
        </div>

        {/* Check-in Result Card */}
        <div className="border border-card-border bg-card-bg p-6 rounded-xl shadow-sm space-y-4">
          <h2 className="text-base font-bold text-[#2B1D30]">Kết quả Tiếp nhận Gần nhất</h2>

          {checkInResult ? (
            <div className="p-5 bg-purple-50 border border-purple-200 rounded-xl space-y-3">
              <div className="flex items-center justify-between border-b border-purple-200 pb-3">
                <span className="text-xs font-bold text-purple-900 uppercase tracking-wider">Trạng thái: CHECKED_IN</span>
                <span className="text-xs font-bold px-2.5 py-1 bg-[#6E2582] text-white rounded-full">
                  Thành công
                </span>
              </div>

              <div className="text-center py-4">
                <p className="text-xs text-purple-700">Số thứ tự khám</p>
                <p className="text-5xl font-extrabold text-purple-900 mt-1">{checkInResult.queueNumber}</p>
                <p className="text-xs font-semibold text-purple-800 mt-2">Phòng: {checkInResult.roomCode}</p>
              </div>

              <div className="text-xs space-y-1 text-purple-800 pt-2 border-t border-purple-200">
                <p>Khoa: {checkInResult.departmentName}</p>
                <p>Thời gian check-in: {new Date(checkInResult.checkedInAt || Date.now()).toLocaleTimeString("vi-VN")}</p>
                <p>Loại lượt: {checkInResult.priorityLevel}</p>
              </div>
            </div>
          ) : (
            <div className="py-16 text-center text-text-muted text-xs">
              Quét mã QR hoặc nhập phiếu khám ở cột bên trái để thực hiện tiếp nhận bệnh nhân.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
