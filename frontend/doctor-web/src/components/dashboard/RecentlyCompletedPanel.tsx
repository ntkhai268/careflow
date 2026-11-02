"use client";

import { ConsultationResponse } from "@/lib/consultation-api";

interface RecentlyCompletedPanelProps {
  isLoading: boolean;
  recentCompleted: ConsultationResponse[] | null;
  patientNames: Record<string, string>;
}

export default function RecentlyCompletedPanel({
  isLoading,
  recentCompleted,
  patientNames,
}: RecentlyCompletedPanelProps) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 shadow-sm flex flex-col h-[480px]">
      <div className="px-5 py-3.5 border-b border-gray-100 flex-shrink-0">
        <h2 className="text-sm font-bold text-gray-800">Vừa hoàn tất</h2>
        <p className="text-[10px] text-gray-400 mt-0.5">Ca khám hoàn thành hôm nay</p>
      </div>
      <div className="p-4 space-y-2 flex-1 overflow-y-auto custom-scrollbar">
        {isLoading ? (
          Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="p-3 rounded-xl border border-gray-100 bg-gray-50 space-y-2">
              <div className="h-3 w-28 rounded bg-gray-200 animate-pulse" />
              <div className="h-2.5 w-40 rounded bg-gray-100 animate-pulse" />
            </div>
          ))
        ) : (recentCompleted ?? []).length === 0 ? (
          <div className="text-center py-12">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gray-100 mx-auto mb-2">
              <svg className="w-5 h-5 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.5}
                  d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
            </div>
            <p className="text-[11px] text-gray-400 italic">Chưa có ca khám nào hoàn tất hôm nay</p>
          </div>
        ) : (
          (recentCompleted ?? []).map((c) => (
            <div
              key={c.id}
              className="p-3 rounded-xl border border-gray-100 bg-gray-50 hover:bg-indigo-50 hover:border-indigo-100 transition-colors"
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0 flex-1">
                  <p className="font-semibold text-gray-800 text-xs truncate">
                    {patientNames[c.patientId] || "Bệnh nhân"}
                  </p>
                  <p className="text-[10px] text-gray-400 mt-0.5 truncate">
                    {c.diagnosis || "Chẩn đoán chưa cập nhật"}
                  </p>
                </div>
                <span className="flex-shrink-0 inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 text-[10px] font-semibold">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 inline-block" />
                  Xong
                </span>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
