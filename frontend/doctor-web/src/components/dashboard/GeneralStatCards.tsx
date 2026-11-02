"use client";

import Link from "next/link";
import { ConsultationResponse } from "@/lib/consultation-api";

interface GeneralStatCardsProps {
  waitingCount: number | null;
  recentCompleted: ConsultationResponse[] | null;
  isLabTech: boolean;
  isStaff: boolean;
}

export default function GeneralStatCards({
  waitingCount,
  recentCompleted,
  isLabTech,
  isStaff,
}: GeneralStatCardsProps) {
  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
      {/* Waiting */}
      <div className="bg-white rounded-xl border border-gray-200 px-4 py-3 shadow-sm flex items-center gap-3">
        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-indigo-50">
          <svg className="w-5 h-5 text-indigo-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <div>
          <p className="text-[10px] text-gray-400 font-medium">Đang chờ</p>
          <p className="text-lg font-bold text-gray-900">{waitingCount ?? "—"}</p>
        </div>
      </div>

      {/* Completed */}
      <div className="bg-white rounded-xl border border-gray-200 px-4 py-3 shadow-sm flex items-center gap-3">
        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-50">
          <svg className="w-5 h-5 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <div>
          <p className="text-[10px] text-gray-400 font-medium">Hoàn tất</p>
          <p className="text-lg font-bold text-gray-900">
            {isStaff || isLabTech || recentCompleted === null ? "—" : recentCompleted.length}
          </p>
        </div>
      </div>

      {/* Queue shortcut card depending on Role */}
      <div
        className="rounded-xl px-4 py-3 shadow-sm col-span-2 sm:col-span-1 hover:shadow-md transition-shadow"
        style={{ background: "linear-gradient(135deg, #6E2582 0%, #3B82F6 100%)" }}
      >
        <Link
          href={isLabTech ? "/lab/queue" : isStaff ? "/staff/pharmacy" : "/dashboard/queue"}
          className="flex items-center gap-3 w-full"
        >
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/20">
            <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.8}
                d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z"
              />
            </svg>
          </div>
          <div>
            <p className="text-[10px] text-white/70 font-medium">
              {isLabTech ? "Khu Cận lâm sàng" : isStaff ? "Quầy Phát thuốc" : "Phòng khám"}
            </p>
            <p className="text-xs font-bold text-white">
              {isLabTech ? "Đến hàng đợi CLS →" : isStaff ? "Hàng đợi Phát thuốc →" : "Vào hàng đợi →"}
            </p>
          </div>
        </Link>
      </div>
    </div>
  );
}
