"use client";

import { useEffect, useState } from "react";
import { queueApi, QueueEntry } from "@/lib/queue-api";
import LoadingSpinner from "@/components/ui/LoadingSpinner";

export default function StaffCheckinPage() {
  const [qrInput, setQrInput] = useState("");
  const [roomId, setRoomId] = useState("ROOM-01");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [checkInResult, setCheckInResult] = useState<QueueEntry | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: "success" | "danger" | "warning" } | null>(null);

  // Today's Room Queue List
  const [roomEntries, setRoomEntries] = useState<QueueEntry[] | null>(null);
  const [isLoadingQueue, setIsLoadingQueue] = useState(false);

  const showToast = (message: string, type: "success" | "danger" | "warning" = "success") => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3000);
  };

  const notifyAi = (text: string) => {
    if (typeof window !== "undefined") {
      window.dispatchEvent(new CustomEvent("careflow:ai-notify", { detail: { text } }));
    }
  };

  const fetchRoomQueue = async () => {
    setIsLoadingQueue(true);
    try {
      const res = await queueApi.getRoomActive(roomId);
      setRoomEntries(res.data?.entries ?? []);
    } catch {
      setRoomEntries([]);
    } finally {
      setIsLoadingQueue(false);
    }
  };

  useEffect(() => {
    fetchRoomQueue();
  }, [roomId]);

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
        await fetchRoomQueue();
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

  const handleRecall = async (entryId: string, num: string) => {
    try {
      await queueApi.recallEntry(entryId);
      notifyAi(`Đã gọi lại số thứ tự ${num}!`);
      showToast(`Đã gọi lại số ${num}`);
      await fetchRoomQueue();
    } catch (err: any) {
      showToast(err.message || "Lỗi khi gọi lại.", "danger");
    }
  };

  const handleMiss = async (entryId: string, num: string) => {
    try {
      await queueApi.missEntry(entryId);
      notifyAi(`Đã đánh dấu vắng mặt cho số thứ tự ${num}!`);
      showToast(`Đã đánh dấu vắng mặt cho số ${num}`, "warning");
      await fetchRoomQueue();
    } catch (err: any) {
      showToast(err.message || "Lỗi khi đánh dấu vắng mặt.", "danger");
    }
  };

  return (
    <div className="space-y-6 pb-12">
      {/* Toast Notification Container */}
      {toast && (
        <div
          className={`fixed top-5 right-5 z-50 p-4 border shadow-lg max-w-md transition-all rounded-lg text-white ${
            toast.type === "success"
              ? "bg-[#2B1D30] border-[#6E2582]"
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
      <div className="border border-card-border bg-card-bg p-5 rounded-xl shadow-sm">
        <div className="flex items-center gap-2">
          <span className="inline-block w-2.5 h-2.5 rounded-full bg-purple-500 animate-pulse" />
          <span className="text-xs font-bold uppercase tracking-wider text-purple-600">
            Phân hệ Nhân viên Quầy Tiếp nhận
          </span>
        </div>
        <h1 className="text-2xl font-extrabold text-[#2B1D30] mt-1">Quét QR & Tiếp nhận Bệnh nhân</h1>
        <p className="text-xs text-text-muted mt-0.5">
          Nhập hoặc quét mã QR trên phiếu khám điện tử của bệnh nhân để kích hoạt lượt khám vào hàng đợi.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Form Input Section */}
        <div className="border border-card-border bg-card-bg p-6 rounded-xl shadow-sm space-y-4">
          <h2 className="text-base font-bold text-[#2B1D30]">Thông tin tiếp nhận</h2>

          <form onSubmit={handleCheckIn} className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-text mb-1">Chọn phòng khám tiếp nhận:</label>
              <select
                value={roomId}
                onChange={(e) => setRoomId(e.target.value)}
                className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg text-text text-xs font-medium focus:ring-2 focus:ring-purple-500"
              >
                <option value="ROOM-01">Phòng khám Nội 01</option>
                <option value="ROOM-02">Phòng khám Nội 02</option>
                <option value="ROOM-03">Phòng khám Nhi 01</option>
                <option value="ROOM-04">Phòng khám Ngoại 01</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-bold text-text mb-1">
                Mã QR / Chuỗi ký tự phiếu khám (Quét mã hoặc nhập tay):
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
                <span className="text-xs font-bold text-purple-900 uppercase tracking-wider">Trạng thái: Đã tiếp nhận</span>
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

      {/* Today's Active Queue & Missed Handling Section */}
      <div className="border border-card-border bg-card-bg p-6 rounded-xl shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-bold text-[#2B1D30]">Danh sách lượt khám trong ngày ({roomId})</h2>
          <button
            onClick={fetchRoomQueue}
            className="text-xs font-semibold text-purple-700 hover:text-purple-900 cursor-pointer"
          >
            Tải lại
          </button>
        </div>

        {isLoadingQueue ? (
          <div className="py-8 flex justify-center">
            <LoadingSpinner size="md" />
          </div>
        ) : (roomEntries ?? []).length === 0 ? (
          <div className="py-8 text-center text-text-muted text-xs">
            Chưa có lượt chờ nào tại {roomId}.
          </div>
        ) : (
          <div className="divide-y divide-card-border">
            {(roomEntries ?? []).map((entry) => (
              <div key={entry.entryId} className="py-3 flex items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg bg-purple-100 text-purple-800 font-extrabold flex items-center justify-center text-sm">
                    {entry.queueNumber}
                  </div>
                  <div>
                    <span className="text-xs font-bold text-[#2B1D30]">Số thứ tự: {entry.queueNumber}</span>
                    <span className="ml-2 text-[11px] font-semibold text-purple-700">({entry.queueStatus})</span>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  {(entry.queueStatus === "WAITING" || entry.queueStatus === "TICKET_ISSUED" as any) && (
                    <button
                      onClick={async () => {
                        try {
                          let token = entry.appointmentId;
                          try {
                            const qrRes = await queueApi.getQr(entry.appointmentId);
                            if (qrRes.data?.qrToken) token = qrRes.data.qrToken;
                          } catch {
                            /* Fallback to appointmentId */
                          }
                          const res = await queueApi.checkIn({ qrToken: token, roomId });
                          if (res.data) {
                            setCheckInResult(res.data);
                            showToast(`Duyệt tiếp nhận thành công STT ${res.data.queueNumber}!`);
                            notifyAi(`Tiếp nhận thành công bệnh nhân STT ${res.data.queueNumber}!`);
                            await fetchRoomQueue();
                          }
                        } catch (err: any) {
                          showToast(err.message || "Lỗi khi duyệt tiếp nhận.", "danger");
                        }
                      }}
                      className="px-3 py-1 bg-emerald-600 text-white text-xs font-semibold rounded-md hover:bg-emerald-700 cursor-pointer"
                    >
                      Duyệt vào hàng chờ
                    </button>
                  )}
                  <button
                    onClick={() => handleRecall(entry.entryId, entry.queueNumber)}
                    className="px-3 py-1 bg-purple-600 text-white text-xs font-semibold rounded-md hover:bg-purple-700 cursor-pointer"
                  >
                    Gọi lại
                  </button>
                  <button
                    onClick={() => handleMiss(entry.entryId, entry.queueNumber)}
                    className="px-3 py-1 bg-rose-600 text-white text-xs font-semibold rounded-md hover:bg-rose-700 cursor-pointer"
                  >
                    Vắng mặt
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
