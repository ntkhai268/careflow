"use client";

import { useCallback, useEffect, useState } from "react";
import { queueApi, QueueEntry } from "@/lib/queue-api";
import { directoryApi } from "@/lib/directory-api";
import { labApi, LabOrderResponse } from "@/lib/lab-api";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import ConfirmModal from "@/components/ConfirmModal";
import { getErrorMessage } from "@/lib/error-utils";
import LabResultFormPanel from "@/components/queue/LabResultFormPanel";
import { notifyAi } from "@/components/queue/useQueueOperations";

export default function LabQueuePage() {
  const [selectedServicePoint, setSelectedServicePoint] = useState("");
  const [entries, setEntries] = useState<QueueEntry[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isCallingNext, setIsCallingNext] = useState(false);

  // Active executing item
  const [activeEntry, setActiveEntry] = useState<QueueEntry | null>(null);
  const [activeLabOrder, setActiveLabOrder] = useState<LabOrderResponse | null>(null);

  const [toast, setToast] = useState<{
    message: string;
    type: "success" | "danger" | "warning";
  } | null>(null);

  const [modalConfig, setModalConfig] = useState<{
    isOpen: boolean;
    title: string;
    message: string;
    onConfirm: () => void;
  }>({
    isOpen: false,
    title: "",
    message: "",
    onConfirm: () => {},
  });

  const showToast = (message: string, type: "success" | "danger" | "warning" = "success") => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3000);
  };

  const fetchQueue = useCallback(async () => {
    if (!selectedServicePoint) {
      setEntries([]);
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      const res = await queueApi.getServicePointActive(selectedServicePoint);
      setEntries(res.data?.entries ?? []);
    } catch {
      setError("Không thể tải danh sách hàng đợi Cận lâm sàng. Vui lòng thử lại.");
      setEntries([]);
    } finally {
      setIsLoading(false);
    }
  }, [selectedServicePoint]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void fetchQueue();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [fetchQueue]);

  const handleCallNext = async () => {
    setIsCallingNext(true);
    try {
      const res = await queueApi.callNextAtServicePoint(selectedServicePoint);
      if (res.data && res.data.queueNumber) {
        notifyAi(`Mời số thứ tự ${res.data.queueNumber} vào phòng thực hiện Cận lâm sàng ạ!`);
        showToast(`Đã gọi số thứ tự ${res.data.queueNumber}!`);
        await fetchQueue();
      } else {
        notifyAi("Hiện tại chưa có bệnh nhân nào đâu ạ!");
        showToast("Hiện chưa có bệnh nhân nào trong hàng đợi.", "warning");
      }
    } catch {
      notifyAi("Hiện tại chưa có bệnh nhân nào đâu ạ!");
      showToast("Hiện chưa có bệnh nhân nào trong hàng đợi.", "warning");
    } finally {
      setIsCallingNext(false);
    }
  };

  const handleCallEntry = async (entryId: string, num: string) => {
    try {
      await queueApi.callEntry(entryId);
      notifyAi(`Mời số thứ tự ${num} vào phòng thực hiện ạ!`);
      showToast(`Đã gọi số ${num}`);
      await fetchQueue();
    } catch (err: unknown) {
      const message = getErrorMessage(err, "Lỗi khi gọi lượt ạ!");
      notifyAi(message);
      showToast(message, "danger");
    }
  };

  const handleStartEntry = async (entry: QueueEntry) => {
    try {
      if (!entry.labOrderId) {
        throw new Error("Lượt xét nghiệm chưa liên kết với chỉ định xét nghiệm.");
      }
      const order = await labApi.getById(entry.labOrderId);
      await queueApi.startEntry(entry.entryId);
      const started = await labApi.startOrder(entry.labOrderId);
      setActiveEntry(entry);
      setActiveLabOrder(started.data ?? order.data);
      notifyAi(`Đã bắt đầu thực hiện ca Cận lâm sàng cho số thứ tự ${entry.queueNumber}!`);
      showToast(`Đã bắt đầu thực hiện cho lượt ${entry.queueNumber}`);
      await fetchQueue();
    } catch (err: unknown) {
      const message = getErrorMessage(err, "Lỗi khi bắt đầu thực hiện ca Cận lâm sàng!");
      notifyAi(message);
      showToast(message, "danger");
    }
  };

  const handleMissEntry = async (entryId: string, num: string) => {
    try {
      await queueApi.missEntry(entryId);
      notifyAi(`Đã đánh dấu VẮNG MẶT cho bệnh nhân số thứ tự ${num}!`);
      showToast(`Đã đánh dấu vắng mặt cho số ${num}`, "warning");
      if (activeEntry?.entryId === entryId) {
        setActiveEntry(null);
        setActiveLabOrder(null);
      }
      await fetchQueue();
    } catch (err: unknown) {
      const message = getErrorMessage(err, "Lỗi khi đánh dấu vắng mặt ạ!");
      notifyAi(message);
      showToast(message, "danger");
    }
  };

  const handleRecallEntry = async (entryId: string, num: string) => {
    try {
      await queueApi.recallEntry(entryId);
      notifyAi(`Đã gọi lại số thứ tự ${num}!`);
      showToast(`Đã gọi lại số ${num}`);
      await fetchQueue();
    } catch (err: unknown) {
      const message = getErrorMessage(err, "Lỗi khi gọi lại ạ!");
      notifyAi(message);
      showToast(message, "danger");
    }
  };

  const [servicePoints, setServicePoints] = useState<{ id: string; name: string }[]>([]);
  useEffect(() => {
    let active = true;
    directoryApi
      .getRooms(undefined, "LAB")
      .then((rooms) => {
        if (!active) return;
        const labRooms = rooms.filter(
          (room) =>
            room.isActive !== false &&
            (room.roomType === "LAB" || room.roomType === "IMAGING" || room.id.startsWith("LAB"))
        );
        const points = (labRooms.length > 0 ? labRooms : rooms.filter((r) => r.isActive)).map(
          (room) => ({
            id: room.id,
            name: `${room.id} · ${room.displayName}`,
          })
        );
        setServicePoints(points);
        setSelectedServicePoint((current) =>
          points.some((point) => point.id === current) ? current : points[0]?.id || ""
        );
      })
      .catch((err) => {
        if (active) setError(getErrorMessage(err, "Không tải được danh mục phòng LAB."));
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="space-y-6 pb-12">
      {/* Toast Notification Container */}
      {toast && (
        <div
          className={`fixed top-5 right-5 z-50 max-w-md rounded-xl border p-4 text-white shadow-lg transition-all ${
            toast.type === "success"
              ? "border-[#0277A8] bg-[#0B2534]"
              : toast.type === "warning"
                ? "border-[#A86100] bg-[#A86100]"
                : "border-[#BA1A1A] bg-[#BA1A1A]"
          }`}
        >
          <div className="flex items-center justify-between gap-4">
            <span className="text-xs font-semibold">{toast.message}</span>
            <button
              onClick={() => setToast(null)}
              className="text-xs font-bold text-white/70 hover:text-white"
            >
              X
            </button>
          </div>
        </div>
      )}

      {/* Header */}
      <div className="flex flex-col justify-between gap-4 rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-xs md:flex-row md:items-center">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-block h-2.5 w-2.5 animate-pulse rounded-full bg-[#087F6A]" />
            <span className="text-xs font-bold uppercase tracking-wider text-[#087F6A]">
              Phân hệ Kỹ thuật viên Cận lâm sàng
            </span>
          </div>
          <h1 className="mt-1 text-2xl font-extrabold text-[#17242D]">
            Hàng đợi Thực hiện Xét nghiệm
          </h1>
          <p className="mt-0.5 text-xs text-[#667985]">
            Điều phối và nhập kết quả xét nghiệm tại phòng Cận lâm sàng đang hoạt động
          </p>
        </div>

        <button
          onClick={handleCallNext}
          disabled={isCallingNext || isLoading || (entries ?? []).length === 0}
          className="cursor-pointer rounded-xl bg-[#0277A8] px-5 py-3 text-sm font-bold text-white shadow-xs transition-all hover:bg-[#00577E] disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isCallingNext ? "Đang gọi..." : "Gọi số tiếp theo"}
        </button>
      </div>

      {/* Service Point Filter Tabs */}
      <div className="flex flex-wrap gap-2 border-b border-[#D5E0E6] pb-3">
        {servicePoints.map((sp) => {
          const isActive = selectedServicePoint === sp.id;
          return (
            <button
              key={sp.id}
              onClick={() => {
                setSelectedServicePoint(sp.id);
                setActiveEntry(null);
                setActiveLabOrder(null);
              }}
              className={`cursor-pointer rounded-xl px-4 py-2 text-xs font-bold transition-all ${
                isActive
                  ? "bg-[#0277A8] text-white shadow-xs"
                  : "border border-[#D5E0E6] bg-white text-[#4D626E] hover:border-[#0277A8] hover:bg-[#DDF3FC] hover:text-[#00577E]"
              }`}
            >
              {sp.name}
            </button>
          );
        })}
      </div>

      {/* Queue Items Section */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Active Queue List */}
        <div className="space-y-4 rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-xs lg:col-span-2">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-extrabold text-[#17242D]">
              Danh sách chờ thực hiện tại phòng
            </h2>
            <span className="rounded-full bg-[#DDF3FC] px-3 py-1 text-xs font-bold text-[#00577E]">
              {(entries ?? []).length} lượt chờ
            </span>
          </div>

          {isLoading ? (
            <div className="flex justify-center py-12">
              <LoadingSpinner size="md" />
            </div>
          ) : error ? (
            <div className="rounded-xl border border-[#BA1A1A]/30 bg-[#BA1A1A]/10 p-4 text-xs font-semibold text-[#BA1A1A]">
              {error}
            </div>
          ) : (entries ?? []).length === 0 ? (
            <div className="py-12 text-center text-xs text-[#667985]">
              <svg
                className="mx-auto mb-2 h-12 w-12 text-[#D5E0E6]"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.5}
                  d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                />
              </svg>
              Hiện chưa có bệnh nhân nào trong hàng đợi của điểm phục vụ này.
            </div>
          ) : (
            <div className="divide-y divide-[#EDF2F5]">
              {(entries ?? []).map((entry) => {
                const isCalled = entry.queueStatus === "CALLED";
                const isInProgress = entry.queueStatus === "IN_PROGRESS";
                const isMissed = entry.queueStatus === "MISSED";

                return (
                  <div
                    key={entry.entryId}
                    className="flex items-center justify-between gap-4 py-3.5"
                  >
                    <div className="flex items-center gap-3.5">
                      <div
                        className={`flex h-12 w-14 items-center justify-center rounded-xl font-mono text-sm font-extrabold ${
                          isCalled
                            ? "animate-pulse bg-[#A86100] text-white"
                            : isInProgress
                              ? "bg-[#1B7F4B] text-white"
                              : isMissed
                                ? "bg-[#BA1A1A] text-white"
                                : "border border-[#8BD2EE] bg-[#DDF3FC] text-[#00577E]"
                        }`}
                      >
                        {entry.queueNumber}
                      </div>
                      <div>
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="text-sm font-bold text-[#17242D]">
                            Mã lượt: {entry.queueNumber}
                          </span>
                          <div className="flex items-center gap-1.5 text-xs font-bold">
                            <span
                              className={`h-2 w-2 rounded-full ${
                                isCalled
                                  ? "bg-[#A86100]"
                                  : isInProgress
                                    ? "bg-[#1B7F4B]"
                                    : isMissed
                                      ? "bg-[#BA1A1A]"
                                      : "bg-[#0277A8]"
                              }`}
                            />
                            <span
                              className={
                                isCalled
                                  ? "text-[#A86100]"
                                  : isInProgress
                                    ? "text-[#1B7F4B]"
                                    : isMissed
                                      ? "text-[#BA1A1A]"
                                      : "text-[#00577E]"
                              }
                            >
                              {entry.queueStatus === "CHECKED_IN" || entry.queueStatus === "WAITING"
                                ? "Đã tiếp nhận"
                                : entry.queueStatus === "CALLED"
                                  ? "Đang gọi"
                                  : entry.queueStatus === "IN_PROGRESS"
                                    ? "Đang thực hiện"
                                    : entry.queueStatus === "MISSED"
                                      ? "Vắng mặt"
                                      : entry.queueStatus === "COMPLETED"
                                        ? "Hoàn tất"
                                        : entry.queueStatus}
                            </span>
                          </div>
                        </div>
                        <p className="mt-0.5 text-xs text-[#667985]">
                          Thời gian vào hàng:{" "}
                          <span className="font-mono font-medium text-[#4D626E]">
                            {entry.scheduledStartAt
                              ? new Date(entry.scheduledStartAt).toLocaleTimeString("vi-VN")
                              : "—"}
                          </span>
                        </p>
                      </div>
                    </div>

                    {/* Action buttons */}
                    <div className="flex items-center gap-2">
                      {(entry.queueStatus === "CHECKED_IN" ||
                        entry.queueStatus === "WAITING" ||
                        entry.queueStatus === "QUEUED") && (
                        <button
                          onClick={() => handleCallEntry(entry.entryId, entry.queueNumber)}
                          className="w-24 cursor-pointer rounded-xl bg-[#0277A8] py-2 text-center text-xs font-bold text-white transition-colors hover:bg-[#00577E]"
                        >
                          Gọi số
                        </button>
                      )}

                      {isCalled && (
                        <>
                          <button
                            onClick={() => handleStartEntry(entry)}
                            className="w-24 cursor-pointer rounded-xl bg-[#087F6A] py-2 text-center text-xs font-bold text-white transition-colors hover:bg-[#066554]"
                          >
                            Bắt đầu
                          </button>
                          <button
                            onClick={() => handleMissEntry(entry.entryId, entry.queueNumber)}
                            className="w-24 cursor-pointer rounded-xl border border-[#D5E0E6] bg-white py-2 text-center text-xs font-semibold text-[#667985] transition-colors hover:border-[#BA1A1A] hover:bg-[#BA1A1A]/10 hover:text-[#BA1A1A]"
                          >
                            Vắng mặt
                          </button>
                        </>
                      )}

                      {isMissed && (
                        <button
                          onClick={() => handleRecallEntry(entry.entryId, entry.queueNumber)}
                          className="w-24 cursor-pointer rounded-xl border border-[#0277A8] bg-[#DDF3FC] py-2 text-center text-xs font-bold text-[#00577E] transition-colors hover:bg-[#8BD2EE]/50"
                        >
                          Gọi lại
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Right Pane — Result Form Panel */}
        <LabResultFormPanel
          activeEntry={activeEntry}
          activeLabOrder={activeLabOrder}
          showToast={showToast}
          onCompleted={async (queueNumber) => {
            showToast(`Hoàn tất xét nghiệm cho lượt ${queueNumber}.`);
            setActiveEntry(null);
            setActiveLabOrder(null);
            await fetchQueue();
          }}
        />
      </div>

      <ConfirmModal
        isOpen={modalConfig.isOpen}
        title={modalConfig.title}
        message={modalConfig.message}
        onConfirm={modalConfig.onConfirm}
        onCancel={() => setModalConfig((prev) => ({ ...prev, isOpen: false }))}
      />
    </div>
  );
}
