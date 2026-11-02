"use client";

import Link from "next/link";
import { QueueStatusDot } from "@/components/queue/QueueBadges";

export interface DisplayQueuePatient {
  entryId: string;
  queueNo: string;
  appointmentId: string | null;
  patientId: string;
  name: string;
  age: number | null;
  department: string;
  status: string;
  time: string;
}

interface GeneralQueuePanelProps {
  waitingCount: number | null;
  isLoading: boolean;
  isCalling: boolean;
  isLabTech: boolean;
  isStaff: boolean;
  searchQuery: string;
  onSearchQueryChange: (value: string) => void;
  filteredQueuePatients: DisplayQueuePatient[];
  nextCallablePatient: DisplayQueuePatient | undefined;
  onCallPatient: (patientId: string, appointmentId: string | null) => void;
  onEmptyCallNext: () => void;
}

const actionLabel = (status: string) => {
  if (status === "CALLED") return "Bắt đầu khám";
  if (status === "IN_PROGRESS") return "Vào khám";
  return "Gọi số";
};

export default function GeneralQueuePanel({
  waitingCount,
  isLoading,
  isCalling,
  isLabTech,
  isStaff,
  searchQuery,
  onSearchQueryChange,
  filteredQueuePatients,
  nextCallablePatient,
  onCallPatient,
  onEmptyCallNext,
}: GeneralQueuePanelProps) {
  return (
    <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 shadow-sm flex flex-col h-[480px]">
      <div className="flex items-center justify-between px-5 py-3 border-b border-gray-100 flex-wrap gap-2 flex-shrink-0">
        <div className="flex items-center gap-2">
          <h2 className="text-sm font-bold text-gray-800">
            {isLabTech
              ? "Hàng đợi Cận lâm sàng"
              : isStaff
              ? "Hàng đợi Phát thuốc"
              : "Hàng đợi khám cần xử lý"}
          </h2>
          {(waitingCount ?? 0) > 0 && (
            <span className="inline-flex items-center justify-center w-5 h-5 rounded-full bg-red-100 text-red-600 text-[10px] font-bold">
              {waitingCount}
            </span>
          )}
        </div>

        {/* Search Input Filter for Queue */}
        <div className="flex items-center gap-2">
          <div className="relative">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => onSearchQueryChange(e.target.value)}
              placeholder="Tìm bệnh nhân theo tên..."
              className="w-44 border border-gray-200 bg-gray-50 px-2.5 py-1 text-xs text-gray-800 rounded-lg focus:bg-white focus:border-indigo-400 focus:outline-none transition-all placeholder:text-gray-400"
            />
            {searchQuery && (
              <button
                onClick={() => onSearchQueryChange("")}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 text-xs font-bold"
              >
                ✕
              </button>
            )}
          </div>

          <Link
            href={isLabTech ? "/lab/queue" : isStaff ? "/staff/pharmacy" : "/dashboard/queue"}
            className="text-[11px] font-semibold text-indigo-500 hover:text-indigo-700 transition-colors flex items-center gap-0.5 whitespace-nowrap"
          >
            Xem tất cả
            <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>
        </div>
      </div>

      {/* Table Container with Mouse Scroll Wheel */}
      <div className="overflow-x-auto overflow-y-auto flex-1 custom-scrollbar">
        <table className="w-full min-w-[600px] text-left text-xs">
          <thead className="sticky top-0 z-10 bg-indigo-50 border-b border-gray-100 shadow-xs">
            <tr>
              <th className="px-5 py-3 font-semibold text-gray-600 w-14">STT</th>
              <th className="px-5 py-3 font-semibold text-gray-600">Bệnh nhân</th>
              <th className="px-5 py-3 font-semibold text-gray-600 w-20">Tuổi</th>
              <th className="px-5 py-3 font-semibold text-gray-600 w-24">Giờ vào</th>
              <th className="px-5 py-3 font-semibold text-gray-600 w-32">Trạng thái</th>
              <th className="px-5 py-3 font-semibold text-gray-600 w-28 text-right">Thao tác</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {isLoading ? (
              Array.from({ length: 4 }).map((_, i) => (
                <tr key={i} className="border-b border-gray-50">
                  {["w-10", "w-36", "w-10", "w-16", "w-20", "w-16"].map((w, j) => (
                    <td key={j} className="px-5 py-3.5">
                      <div className={`h-3 ${w} rounded bg-gray-100 animate-pulse`} />
                    </td>
                  ))}
                </tr>
              ))
            ) : filteredQueuePatients.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-5 py-12 text-center text-gray-400 text-xs italic">
                  {searchQuery
                    ? `Không tìm thấy bệnh nhân nào khớp với "${searchQuery}"`
                    : "Hiện chưa có bệnh nhân nào trong hàng đợi khám"}
                </td>
              </tr>
            ) : (
              filteredQueuePatients.map((patient) => (
                <tr
                  key={patient.queueNo}
                  className="hover:bg-indigo-50 cursor-pointer transition-colors"
                  onClick={() => onCallPatient(patient.patientId, patient.appointmentId)}
                >
                  <td className="px-5 py-3 font-mono font-bold text-gray-700">{patient.queueNo}</td>
                  <td className="px-5 py-3">
                    <div className="flex items-center gap-2.5">
                      <div
                        className="flex h-7 w-7 items-center justify-center rounded-full text-[10px] font-bold text-white flex-shrink-0"
                        style={{ background: "linear-gradient(135deg, #6366F1, #3B82F6)" }}
                      >
                        {patient.name.charAt(0)}
                      </div>
                      <span className="font-semibold text-gray-800">{patient.name}</span>
                    </div>
                  </td>
                  <td className="px-5 py-3 text-gray-500">{patient.age ?? "—"}</td>
                  <td className="px-5 py-3 text-gray-500 font-mono">{patient.time}</td>
                  <td className="px-5 py-3">
                    <QueueStatusDot status={patient.status} />
                  </td>
                  <td className="px-5 py-3 text-right">
                    <button
                      type="button"
                      onClick={(event) => {
                        event.stopPropagation();
                        onCallPatient(patient.patientId, patient.appointmentId);
                      }}
                      disabled={isCalling}
                      className="whitespace-nowrap rounded border border-gray-200 bg-white px-2.5 py-1.5 text-[10px] font-semibold text-[#2B1D30] hover:border-purple-200 hover:bg-purple-50 hover:text-[#6E2582] disabled:opacity-50"
                    >
                      {actionLabel(patient.status)}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Footer inside Left Card: Call Next Patient Button */}
      <div className="px-5 py-3 border-t border-gray-100 bg-gray-50/80 rounded-b-xl flex justify-end flex-shrink-0">
        <button
          onClick={() => {
            if (nextCallablePatient) {
              onCallPatient(nextCallablePatient.patientId, nextCallablePatient.appointmentId);
            } else {
              onEmptyCallNext();
            }
          }}
          disabled={isCalling || isLoading || !nextCallablePatient}
          className="flex items-center gap-2 text-white px-4 py-2 rounded-lg text-xs font-bold transition-all disabled:opacity-50 shadow-sm hover:shadow-md cursor-pointer"
          style={{ background: "linear-gradient(135deg, #6366F1 0%, #3B82F6 100%)" }}
        >
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M14.828 14.828a4 4 0 01-5.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
          {isCalling ? "Đang gọi..." : "Gọi bệnh nhân tiếp theo"}
        </button>
      </div>
    </div>
  );
}
