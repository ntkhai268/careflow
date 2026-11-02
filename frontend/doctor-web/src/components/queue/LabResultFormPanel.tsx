"use client";

import { useState } from "react";
import { getErrorMessage } from "@/lib/error-utils";
import { labApi, LabOrderResponse } from "@/lib/lab-api";
import { queueApi, QueueEntry } from "@/lib/queue-api";

interface LabResultFormPanelProps {
  activeEntry: QueueEntry | null;
  activeLabOrder: LabOrderResponse | null;
  onCompleted: (queueNumber: string) => Promise<void>;
  showToast: (message: string, type?: "success" | "danger" | "warning") => void;
}

export default function LabResultFormPanel({
  activeEntry,
  activeLabOrder,
  onCompleted,
  showToast,
}: LabResultFormPanelProps) {
  const [resultValues, setResultValues] = useState<Record<string, string>>({});
  const [referenceRanges, setReferenceRanges] = useState<Record<string, string>>({});
  const [units, setUnits] = useState<Record<string, string>>({});

  return (
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
                  onChange={(event) =>
                    setResultValues((current) => ({ ...current, [item.id]: event.target.value }))
                  }
                  type="text"
                  placeholder="Giá trị kết quả"
                  className="w-full p-2 border border-card-border rounded-lg bg-card-bg text-text"
                />
                <input
                  value={referenceRanges[item.id] ?? ""}
                  onChange={(event) =>
                    setReferenceRanges((current) => ({ ...current, [item.id]: event.target.value }))
                  }
                  type="text"
                  placeholder="Khoảng tham chiếu"
                  className="w-full p-2 border border-card-border rounded-lg bg-card-bg text-text"
                />
                <input
                  value={units[item.id] ?? ""}
                  onChange={(event) =>
                    setUnits((current) => ({ ...current, [item.id]: event.target.value }))
                  }
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
                  setResultValues({});
                  setReferenceRanges({});
                  setUnits({});
                  await onCompleted(activeEntry.queueNumber);
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
  );
}
