"use client";

import LoadingSpinner from "@/components/ui/LoadingSpinner";
import { PatientOperationalResponse } from "@/lib/patient-api";
import { QueueEntry } from "@/lib/queue-api";
import { PRIORITY_LABELS, ReceptionStatusText } from "./QueueBadges";

interface ReceptionQueueTableProps {
  roomId: string;
  roomError: string | null;
  queueError: string | null;
  isLoadingQueue: boolean;
  roomEntries: QueueEntry[] | null;
  patientDetails: Record<string, PatientOperationalResponse | null>;
  processingEntryId: string | null;
  onRefreshQueue: () => void;
  onCheckInEntry: (entry: QueueEntry) => void;
  onRecallEntry: (entry: QueueEntry) => void;
  onMissEntry: (entry: QueueEntry) => void;
}

export default function ReceptionQueueTable({
  roomId,
  roomError,
  queueError,
  isLoadingQueue,
  roomEntries,
  patientDetails,
  processingEntryId,
  onRefreshQueue,
  onCheckInEntry,
  onRecallEntry,
  onMissEntry,
}: ReceptionQueueTableProps) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white shadow-[0_1px_3px_rgba(110,37,130,0.06)]">
      <div className="flex flex-col gap-3 border-b border-slate-200 p-5 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 className="text-[15px] font-semibold text-[#2B1D30]">Danh sách tiếp nhận trong ngày</h2>
          <p className="mt-1 text-[10px] text-slate-500">
            Phòng đang chọn:{" "}
            <span className="font-medium text-slate-700">{roomId || "Đang tải phân công"}</span>. Gồm phiếu chờ tiếp nhận và lượt đã check-in, tự cập nhật mỗi 10 giây.
          </p>
        </div>
        <div className="flex w-full flex-col gap-2 sm:w-[280px] sm:flex-row sm:items-center">
          <button
            type="button"
            onClick={onRefreshQueue}
            className="h-9 shrink-0 rounded-md border border-slate-200 px-3 text-[10px] font-semibold text-[#7B4B94] hover:bg-[#F3E8F5]"
          >
            Tải lại
          </button>
        </div>
      </div>

      {roomError || queueError ? (
        <div role="alert" className="p-5 text-sm text-red-700">
          {roomError || queueError}
        </div>
      ) : isLoadingQueue || !roomId ? (
        <div className="flex min-h-[180px] items-center justify-center">
          <LoadingSpinner size="md" />
        </div>
      ) : (roomEntries ?? []).length === 0 ? (
        <div className="flex min-h-[180px] items-center justify-center px-5 text-center text-[11px] text-slate-500">
          Chưa có phiếu chờ tiếp nhận hoặc lượt đang khám tại phòng này hôm nay.
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] border-collapse text-left">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50 text-[9px] font-semibold uppercase tracking-wider text-slate-400">
                <th className="px-5 py-3">Số thứ tự</th>
                <th className="px-4 py-3">Bệnh nhân</th>
                <th className="px-4 py-3">Loại lượt</th>
                <th className="px-4 py-3">Trạng thái</th>
                <th className="px-4 py-3">Khung giờ hẹn</th>
                <th className="px-4 py-3">Thời gian tiếp nhận</th>
                <th className="px-5 py-3 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {(roomEntries ?? []).map((entry) => {
                const patient = patientDetails[entry.patientId];
                const isProcessing = processingEntryId === entry.entryId;
                const canCheckIn =
                  entry.queueStatus === "WAITING" || entry.queueStatus === "TICKET_ISSUED";
                const canRecall =
                  entry.queueStatus === "CHECKED_IN" ||
                  entry.queueStatus === "QUEUED" ||
                  (entry.queueStatus === "CALLED" && entry.callAttempts < 3);
                const canMiss = entry.queueStatus === "CALLED";

                return (
                  <tr
                    key={entry.entryId}
                    className="min-h-[44px] border-b border-slate-100 last:border-0 hover:bg-slate-50"
                  >
                    <td className="px-5 py-3.5">
                      <span className="font-mono text-sm font-bold text-[#2B1D30]">
                        {entry.queueNumber}
                      </span>
                    </td>
                    <td className="px-4 py-3.5">
                      {patient ? (
                        <div>
                          <p className="text-[11px] font-semibold text-[#2B1D30]">
                            {patient.fullName}
                          </p>
                          <p className="mt-1 text-[9px] text-slate-500">
                            Mã BN: {patient.id.slice(0, 8)} ·{" "}
                            {patient.gender || "Chưa rõ giới tính"}
                            {patient.dateOfBirth
                              ? ` · ${new Date(patient.dateOfBirth).getFullYear()}`
                              : ""}
                          </p>
                        </div>
                      ) : (
                        <div>
                          <p className="text-[11px] font-medium text-slate-400">
                            Đang tải thông tin
                          </p>
                          <p className="mt-1 text-[9px] text-slate-400">
                            Mã BN: {entry.patientId.slice(0, 8)}
                          </p>
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3.5 text-[10px] text-slate-600">
                      {PRIORITY_LABELS[entry.priorityLevel] || entry.priorityLevel}
                    </td>
                    <td className="px-4 py-3.5">
                      <ReceptionStatusText status={entry.queueStatus} />
                    </td>
                    <td className="px-4 py-3.5 text-[10px] text-slate-500">
                      {new Date(entry.scheduledStartAt).toLocaleTimeString("vi-VN", {
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </td>
                    <td className="px-4 py-3.5 text-[10px] text-slate-500">
                      {entry.checkedInAt
                        ? new Date(entry.checkedInAt).toLocaleTimeString("vi-VN")
                        : "Chưa tiếp nhận"}
                    </td>
                    <td className="px-5 py-3.5">
                      <div className="flex justify-end gap-2">
                        {canCheckIn && (
                          <button
                            onClick={() => onCheckInEntry(entry)}
                            disabled={isProcessing}
                            className="h-8 w-24 rounded-md text-[10px] font-semibold text-[#7B4B94] hover:bg-[#F3E8F5] disabled:opacity-50"
                          >
                            {isProcessing ? "Đang xử lý" : "Duyệt vào"}
                          </button>
                        )}
                        <button
                          onClick={() => onRecallEntry(entry)}
                          disabled={isProcessing || !canRecall}
                          title={
                            entry.queueStatus === "CALLED" && entry.callAttempts >= 3
                              ? "Đã gọi đủ 3 lần; hãy đánh dấu vắng mặt nếu bệnh nhân không đến."
                              : !canRecall
                                ? "Chỉ gọi lượt đã tiếp nhận hoặc đang được gọi."
                                : "Gọi bệnh nhân"
                          }
                          className="h-8 w-20 rounded-md border border-slate-200 text-[10px] font-semibold text-slate-600 hover:border-[#7B4B94] hover:text-[#7B4B94] disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          Gọi lại
                        </button>
                        <button
                          onClick={() => onMissEntry(entry)}
                          disabled={isProcessing || !canMiss}
                          className="h-8 w-20 rounded-md text-[10px] font-medium text-slate-500 hover:bg-rose-50 hover:text-rose-600 disabled:opacity-50"
                        >
                          Vắng mặt
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
