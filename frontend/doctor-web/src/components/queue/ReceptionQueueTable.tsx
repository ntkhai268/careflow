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
    <section className="rounded-xl border border-[#D5E0E6] bg-white shadow-xs">
      <div className="flex flex-col gap-3 border-b border-[#D5E0E6] p-5 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-[15px] font-extrabold text-[#17242D]">
            Danh sách tiếp nhận trong ngày
          </h2>
          <p className="mt-0.5 text-xs text-[#667985]">
            Phòng đang chọn:{" "}
            <span className="font-mono font-bold text-[#00577E]">
              {roomId || "Đang tải phân công"}
            </span>
            . Gồm phiếu chờ tiếp nhận và lượt đã check-in, tự cập nhật mỗi 10 giây.
          </p>
        </div>
        <button
          type="button"
          onClick={onRefreshQueue}
          className="h-9 shrink-0 rounded-xl border border-[#D5E0E6] bg-[#F5F8FA] px-3.5 text-xs font-bold text-[#00577E] transition-colors hover:border-[#0277A8] hover:bg-[#DDF3FC]"
        >
          Tải lại danh sách
        </button>
      </div>

      {roomError || queueError ? (
        <div role="alert" className="p-5 text-xs font-semibold text-[#BA1A1A]">
          {roomError || queueError}
        </div>
      ) : isLoadingQueue || !roomId ? (
        <div className="flex min-h-[180px] items-center justify-center">
          <LoadingSpinner size="md" />
        </div>
      ) : (roomEntries ?? []).length === 0 ? (
        <div className="flex min-h-[180px] items-center justify-center px-5 text-center text-xs text-[#667985]">
          Chưa có phiếu chờ tiếp nhận hoặc lượt đang khám tại phòng này hôm nay.
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-left">
            <thead>
              <tr className="border-b border-[#D5E0E6] bg-[#F5F8FA] text-[10px] font-bold uppercase tracking-wider text-[#667985]">
                <th className="whitespace-nowrap px-5 py-3">Số thứ tự</th>
                <th className="whitespace-nowrap px-4 py-3">Bệnh nhân</th>
                <th className="whitespace-nowrap px-4 py-3">Loại lượt</th>
                <th className="whitespace-nowrap px-4 py-3">Trạng thái</th>
                <th className="whitespace-nowrap px-4 py-3">Khung giờ hẹn</th>
                <th className="whitespace-nowrap px-4 py-3">Thời gian tiếp nhận</th>
                <th className="whitespace-nowrap px-5 py-3 text-right">Thao tác</th>
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
                    className="min-h-[48px] border-b border-[#EDF2F5] last:border-0 hover:bg-[#F5F8FA]/80"
                  >
                    <td className="whitespace-nowrap px-5 py-3">
                      <span className="inline-flex whitespace-nowrap rounded-lg bg-[#DDF3FC] px-2.5 py-1 font-mono text-xs font-extrabold text-[#00577E]">
                        {entry.queueNumber}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3">
                      {patient ? (
                        <div className="flex items-baseline gap-2">
                          <span className="whitespace-nowrap text-xs font-bold text-[#17242D]">
                            {patient.fullName}
                          </span>
                          <span className="whitespace-nowrap font-mono text-[11px] text-[#667985]">
                            ({patient.id.slice(0, 8).toUpperCase()} ·{" "}
                            {patient.gender === "MALE"
                              ? "Nam"
                              : patient.gender === "FEMALE"
                                ? "Nữ"
                                : patient.gender || "Chưa rõ"}
                            {patient.dateOfBirth
                              ? ` · ${new Date(patient.dateOfBirth).getFullYear()}`
                              : ""}
                            )
                          </span>
                        </div>
                      ) : (
                        <div className="flex items-baseline gap-2">
                          <span className="whitespace-nowrap text-xs font-medium text-[#667985]">
                            Đang tải thông tin
                          </span>
                          <span className="whitespace-nowrap font-mono text-[11px] text-[#667985]">
                            ({entry.patientId.slice(0, 8).toUpperCase()})
                          </span>
                        </div>
                      )}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-xs font-medium text-[#4D626E]">
                      {PRIORITY_LABELS[entry.priorityLevel] || entry.priorityLevel}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <ReceptionStatusText status={entry.queueStatus} />
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 font-mono text-xs text-[#4D626E]">
                      {new Date(entry.scheduledStartAt).toLocaleTimeString("vi-VN", {
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 font-mono text-xs text-[#4D626E]">
                      {entry.checkedInAt
                        ? new Date(entry.checkedInAt).toLocaleTimeString("vi-VN", {
                            hour: "2-digit",
                            minute: "2-digit",
                          })
                        : "Chưa tiếp nhận"}
                    </td>
                    <td className="whitespace-nowrap px-5 py-3">
                      <div className="flex items-center justify-end gap-2">
                        {canCheckIn && (
                          <button
                            onClick={() => onCheckInEntry(entry)}
                            disabled={isProcessing}
                            className="h-8 w-24 whitespace-nowrap rounded-lg bg-[#0277A8] text-xs font-bold text-white transition-colors hover:bg-[#00577E] disabled:opacity-50"
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
                          className="h-8 w-20 whitespace-nowrap rounded-lg border border-[#D5E0E6] bg-white text-xs font-bold text-[#00577E] transition-colors hover:border-[#0277A8] hover:bg-[#DDF3FC] disabled:cursor-not-allowed disabled:opacity-40"
                        >
                          Gọi lại
                        </button>
                        <button
                          onClick={() => onMissEntry(entry)}
                          disabled={isProcessing || !canMiss}
                          className="h-8 w-20 whitespace-nowrap rounded-lg border border-[#D5E0E6] bg-white text-xs font-semibold text-[#667985] transition-colors hover:border-[#BA1A1A] hover:bg-[#BA1A1A]/10 hover:text-[#BA1A1A] disabled:cursor-not-allowed disabled:opacity-40"
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
