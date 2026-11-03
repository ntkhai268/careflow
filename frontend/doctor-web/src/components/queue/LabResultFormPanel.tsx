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
    <div className="space-y-4 rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-xs">
      <h2 className="text-base font-extrabold text-[#17242D]">Chi tiết ca đang thực hiện</h2>
      {activeEntry ? (
        <div className="space-y-4 text-xs">
          <div className="rounded-xl border border-[#8BD2EE] bg-[#DDF3FC]/60 p-3.5">
            <p className="font-mono text-sm font-extrabold text-[#00577E]">
              Số thứ tự: {activeEntry.queueNumber}
            </p>
            <p className="mt-1 font-semibold text-[#087F6A]">Trạng thái: Đang thực hiện</p>
          </div>

          <div className="space-y-3">
            <label className="block font-bold text-[#17242D]">Nhập kết quả xét nghiệm:</label>
            {activeLabOrder?.items.map((item) => (
              <div
                key={item.id}
                className="space-y-2 border-b border-[#EDF2F5] pb-3 last:border-0"
              >
                <p className="font-bold text-[#17242D]">{item.serviceName}</p>
                <input
                  value={resultValues[item.id] ?? ""}
                  onChange={(event) =>
                    setResultValues((current) => ({ ...current, [item.id]: event.target.value }))
                  }
                  type="text"
                  placeholder="Giá trị kết quả"
                  className="h-10 w-full rounded-xl border border-[#D5E0E6] bg-white px-3 text-xs text-[#17242D] outline-none focus:border-[#0277A8]"
                />
                <input
                  value={referenceRanges[item.id] ?? ""}
                  onChange={(event) =>
                    setReferenceRanges((current) => ({
                      ...current,
                      [item.id]: event.target.value,
                    }))
                  }
                  type="text"
                  placeholder="Khoảng tham chiếu"
                  className="h-10 w-full rounded-xl border border-[#D5E0E6] bg-white px-3 text-xs text-[#17242D] outline-none focus:border-[#0277A8]"
                />
                <input
                  value={units[item.id] ?? ""}
                  onChange={(event) =>
                    setUnits((current) => ({ ...current, [item.id]: event.target.value }))
                  }
                  type="text"
                  placeholder="Đơn vị đo"
                  className="h-10 w-full rounded-xl border border-[#D5E0E6] bg-white px-3 text-xs text-[#17242D] outline-none focus:border-[#0277A8]"
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
              className="w-full cursor-pointer rounded-xl bg-[#087F6A] py-3 text-xs font-bold text-white shadow-xs transition-colors hover:bg-[#066554]"
            >
              Phát hành kết quả
            </button>
          </div>
        </div>
      ) : (
        <div className="py-8 text-center text-xs text-[#667985]">
          Vui lòng chọn hoặc bấm <b>&quot;Bắt đầu&quot;</b> cho một bệnh nhân để nhập kết quả xét
          nghiệm.
        </div>
      )}
    </div>
  );
}
