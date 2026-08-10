"use client";

import { useCallback, useEffect, useState } from "react";
import { queueApi, QueueEntry } from "@/lib/queue-api";
import { labApi, LabOrderResponse } from "@/lib/lab-api";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import ConfirmModal from "@/components/ConfirmModal";
import { getErrorMessage } from "@/lib/error-utils";

export default function LabQueuePage() {
  const [selectedServicePoint, setSelectedServicePoint] = useState("LAB-HEMATOLOGY-01");
  const [entries, setEntries] = useState<QueueEntry[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isCallingNext, setIsCallingNext] = useState(false);

  // Active executing item
  const [activeEntry, setActiveEntry] = useState<QueueEntry | null>(null);
  const [activeLabOrder, setActiveLabOrder] = useState<LabOrderResponse | null>(null);
  const [resultValues, setResultValues] = useState<{ [itemId: string]: string }>({});
  const [referenceRanges, setReferenceRanges] = useState<{ [itemId: string]: string }>({});
  const [units, setUnits] = useState<{ [itemId: string]: string }>({});

  const [toast, setToast] = useState<{ message: string; type: "success" | "danger" | "warning" } | null>(null);

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
    setIsLoading(true);
    setError(null);
    try {
      const res = await queueApi.getServicePointActive(selectedServicePoint);
      const list = res.data?.entries ?? [];
      setEntries(list);

      const inProgress = list.find((e) => e.queueStatus === "IN_PROGRESS");
      if (inProgress && inProgress.labOrderId) {
        try {
          const order = await labApi.getById(inProgress.labOrderId);
          setActiveEntry(inProgress);
          setActiveLabOrder(order.data);
        } catch {
          /* ignore background load */
        }
      }
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

  const notifyAi = (text: string) => {
    if (typeof window !== "undefined") {
      window.dispatchEvent(new CustomEvent("careflow:ai-notify", { detail: { text } }));
    }
  };

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
      if (entry.queueStatus !== "IN_PROGRESS") {
        try {
          await queueApi.startEntry(entry.entryId);
        } catch {
          /* ignore if already in progress */
        }
      }
      if (order.data && order.data.status === "ORDERED") {
        try {
          const started = await labApi.startOrder(entry.labOrderId);
          if (started.data) order.data = started.data;
        } catch {
          /* ignore if already started */
        }
      }
      setActiveEntry(entry);
      setActiveLabOrder(order.data);
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

  const servicePoints = [
    { id: "LAB-HEMATOLOGY-01", name: "Phòng Xét nghiệm Huyết học 101" },
    { id: "LAB-BIOCHEM-01", name: "Phòng Xét nghiệm Sinh hóa 102" },
    { id: "US-ROOM-01", name: "Phòng Siêu âm 201" },
    { id: "XRAY-ROOM-01", name: "Phòng X-Quang 202" },
  ];

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
            <span className="text-xs font-bold uppercase tracking-wider text-purple-400">
              Phân hệ Kỹ thuật viên Cận lâm sàng
            </span>
          </div>
          <h1 className="text-2xl font-extrabold text-[#2B1D30] mt-1">Hàng đợi Thực hiện Xét nghiệm</h1>
          <p className="text-xs text-text-muted mt-0.5">
            Điều phối và nhập kết quả xét nghiệm, chẩn đoán hình ảnh theo Điểm phục vụ
          </p>
        </div>

        <button
          onClick={handleCallNext}
          disabled={isCallingNext || isLoading || (entries ?? []).length === 0}
          className="px-5 py-3 rounded-xl font-bold text-sm text-white bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed shadow-md transition-all cursor-pointer"
        >
          {isCallingNext ? "Đang gọi..." : "Gọi số"}
        </button>
      </div>

      {/* Service Point Filter Tabs */}
      <div className="flex flex-wrap gap-2 border-b border-card-border pb-3">
        {servicePoints.map((sp) => {
          const isActive = selectedServicePoint === sp.id;
          return (
            <button
              key={sp.id}
              onClick={() => setSelectedServicePoint(sp.id)}
              className={`px-4 py-2 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
                isActive
                  ? "bg-purple-600 text-white shadow-sm"
                  : "bg-card-bg text-text-muted hover:bg-purple-50 hover:text-purple-700 border border-card-border"
              }`}
            >
              {sp.name}
            </button>
          );
        })}
      </div>

      {/* Queue Items Section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Active Queue List */}
        <div className="lg:col-span-2 border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-[#2B1D30]">Danh sách chờ thực hiện tại phòng</h2>
            <span className="text-xs font-semibold px-2.5 py-1 bg-purple-100 text-purple-700 rounded-full">
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
              <svg className="w-12 h-12 mx-auto mb-2 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              Hiện chưa có bệnh nhân nào trong hàng đợi của điểm phục vụ này.
            </div>
          ) : (
            <div className="divide-y divide-card-border">
              {(entries ?? []).map((entry) => {
                const isCalled = entry.queueStatus === "CALLED";
                const isInProgress = entry.queueStatus === "IN_PROGRESS";
                const isMissed = entry.queueStatus === "MISSED";

                return (
                  <div key={entry.entryId} className="py-3.5 flex items-center justify-between gap-4">
                    <div className="flex items-center gap-3">
                      <div
                        className={`w-12 h-12 rounded-xl flex items-center justify-center font-extrabold text-base ${
                          isCalled
                            ? "bg-amber-500 text-white animate-pulse"
                            : isInProgress
                            ? "bg-emerald-600 text-white"
                            : isMissed
                            ? "bg-rose-500 text-white"
                            : "bg-purple-100 text-purple-700"
                        }`}
                      >
                        {entry.queueNumber}
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-bold text-[#2B1D30]">Mã lượt: {entry.queueNumber}</span>
                          <div className="flex items-center gap-1.5 text-xs font-semibold">
                            <span className={`w-1.5 h-1.5 rounded-full ${isCalled ? "bg-amber-500" : isInProgress ? "bg-emerald-500" : isMissed ? "bg-rose-500" : "bg-blue-500"}`} />
                            <span className={isCalled ? "text-amber-700" : isInProgress ? "text-emerald-700" : isMissed ? "text-rose-700" : "text-blue-700"}>
                              {entry.queueStatus === "CHECKED_IN" ? "Đã tiếp nhận" : entry.queueStatus === "CALLED" ? "Đang gọi" : entry.queueStatus === "IN_PROGRESS" ? "Đang thực hiện" : entry.queueStatus === "MISSED" ? "Vắng mặt" : entry.queueStatus === "COMPLETED" ? "Hoàn tất" : entry.queueStatus}
                            </span>
                          </div>
                        </div>
                        <p className="text-xs text-text-muted mt-0.5">
                          Thời gian vào hàng: {entry.scheduledStartAt
                            ? new Date(entry.scheduledStartAt).toLocaleTimeString("vi-VN")
                            : "—"}
                        </p>
                      </div>
                    </div>

                    {/* Action buttons */}
                    <div className="flex items-center gap-2">
                      {entry.queueStatus === "CHECKED_IN" && (
                        <button
                          onClick={() => handleCallEntry(entry.entryId, entry.queueNumber)}
                          className="w-20 py-1.5 bg-purple-600 text-white text-xs font-semibold rounded-md hover:bg-purple-700 cursor-pointer text-center"
                        >
                          Gọi số
                        </button>
                      )}

                      {isCalled && (
                        <>
                          <button
                            onClick={() => handleStartEntry(entry)}
                            className="w-20 py-1.5 bg-emerald-600 text-white text-xs font-semibold rounded-md hover:bg-emerald-700 cursor-pointer text-center"
                          >
                            Bắt đầu
                          </button>
                          <button
                            onClick={() => handleMissEntry(entry.entryId, entry.queueNumber)}
                            className="w-20 py-1.5 bg-rose-600 text-white text-xs font-semibold rounded-md hover:bg-rose-700 cursor-pointer text-center"
                          >
                            Vắng mặt
                          </button>
                        </>
                      )}

                      {isInProgress && (
                        <button
                          onClick={() => handleStartEntry(entry)}
                          className="px-3 py-1.5 bg-emerald-600 text-white text-xs font-semibold rounded-md hover:bg-emerald-700 cursor-pointer text-center"
                        >
                          Nhập KQ
                        </button>
                      )}

                      {isMissed && (
                        <button
                          onClick={() => handleRecallEntry(entry.entryId, entry.queueNumber)}
                          className="w-20 py-1.5 bg-blue-600 text-white text-xs font-semibold rounded-md hover:bg-blue-700 cursor-pointer text-center"
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
        <div className="border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
          <h2 className="text-base font-bold text-[#2B1D30]">Chi tiết ca đang thực hiện</h2>
          {activeEntry ? (
            <div className="space-y-4 text-xs">
              <div className="p-3 bg-purple-50 border border-purple-200 rounded-lg">
                <p className="font-bold text-purple-900 text-sm">Số thứ tự: {activeEntry.queueNumber}</p>
                <p className="text-purple-700 mt-1">Trạng thái: Đang thực hiện</p>
              </div>

              <div className="space-y-3">
                <label className="block font-bold text-text">Nhập kết quả xét nghiệm:</label>
                {activeLabOrder?.items.map((item) => (
                  <div key={item.id} className="space-y-2 border-b border-card-border pb-3 last:border-0">
                    <p className="font-semibold text-text">{item.serviceName}</p>
                    <input
                      value={resultValues[item.id] ?? ""}
                      onChange={(event) => setResultValues((current) => ({ ...current, [item.id]: event.target.value }))}
                      type="text"
                      placeholder="Giá trị kết quả"
                      className="w-full p-2 border border-card-border rounded-lg bg-card-bg text-text"
                    />
                    <input
                      value={referenceRanges[item.id] ?? ""}
                      onChange={(event) => setReferenceRanges((current) => ({ ...current, [item.id]: event.target.value }))}
                      type="text"
                      placeholder="Khoảng tham chiếu"
                      className="w-full p-2 border border-card-border rounded-lg bg-card-bg text-text"
                    />
                    <input
                      value={units[item.id] ?? ""}
                      onChange={(event) => setUnits((current) => ({ ...current, [item.id]: event.target.value }))}
                      type="text"
                      placeholder="Đơn vị đo"
                      className="w-full p-2 border border-card-border rounded-lg bg-card-bg text-text"
                    />
                  </div>
                ))}

                <button
                  onClick={async () => {
                    try {
                      if (!activeLabOrder) throw new Error("Chưa tải được chỉ định xét nghiệm.");
                      for (const item of activeLabOrder.items) {
                        const value = resultValues[item.id]?.trim();
                        if (item.status !== "COMPLETED" && !value) {
                          throw new Error(`Chưa nhập kết quả cho ${item.serviceName}.`);
                        }
                        if (item.status !== "COMPLETED") {
                          await labApi.submitItemResult(activeLabOrder.id, item.id, {
                            resultValue: value!,
                            referenceRange: referenceRanges[item.id],
                            unit: units[item.id],
                          });
                        }
                      }
                      await labApi.finalizeOrder(activeLabOrder.id);
                      await queueApi.completeEntry(activeEntry.entryId);
                      showToast(`Hoàn tất xét nghiệm cho lượt ${activeEntry.queueNumber}.`);
                      setActiveEntry(null);
                      setActiveLabOrder(null);
                      setResultValues({});
                      setReferenceRanges({});
                      setUnits({});
                      await fetchQueue();
                    } catch (err: unknown) {
                      showToast(getErrorMessage(err, "Lỗi khi hoàn tất lượt."), "danger");
                    }
                  }}
                  className="w-full py-2.5 bg-gradient-to-r from-purple-600 to-indigo-600 text-white font-bold rounded-lg shadow-sm hover:from-purple-500 hover:to-indigo-500 cursor-pointer text-xs"
                >
                  Phát hành kết quả
                </button>
              </div>
            </div>
          ) : (
            <div className="py-8 text-center text-text-muted text-xs">
              Vui lòng chọn hoặc bấm <b>&quot;Bắt đầu thực hiện&quot;</b> cho một bệnh nhân để nhập kết quả xét nghiệm.
            </div>
          )}
        </div>
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
