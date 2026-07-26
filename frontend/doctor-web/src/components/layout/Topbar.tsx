"use client";

import { useAuth } from "@/contexts/AuthContext";
import { usePathname } from "next/navigation";
import { useState, useEffect } from "react";

const BREADCRUMB_MAP: Record<string, string> = {
  "/dashboard/general": "Tổng quan",
  "/dashboard/queue": "Hàng đợi khám",
  "/dashboard/prescriptions": "Đơn thuốc",
};

export default function Topbar() {
  const { user } = useAuth();
  const pathname = usePathname();
  const [mounted, setMounted] = useState(false);

  useEffect(() => { setMounted(true); }, []);

  const pageTitle = BREADCRUMB_MAP[pathname] || "Dashboard";
  const initials = user?.fullName
    ? user.fullName.split(" ").map((n: string) => n[0]).slice(-2).join("").toUpperCase()
    : "BS";

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between px-6 bg-white border-b border-gray-200 shadow-sm">
      {/* Left — Breadcrumb */}
      <div className="flex items-center gap-2">
        <span className="text-[11px] text-gray-400">Dashboard</span>
        <svg className="w-3 h-3 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
        </svg>
        <span className="text-[11px] font-semibold text-indigo-500">{pageTitle}</span>
      </div>

      {/* Center — Search */}
      <div className="flex-1 max-w-sm mx-8">
        <div className="relative">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          <input
            type="text"
            placeholder="Tìm kiếm bệnh nhân, đơn thuốc..."
            className="w-full bg-gray-50 rounded-xl pl-9 pr-4 py-2 text-xs text-gray-700 placeholder-gray-400 border border-gray-200 outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100 transition-all"
          />
        </div>
      </div>

      {/* Right — Actions */}
      <div className="flex items-center gap-3">
        {/* Date */}
        <span className="text-[10px] text-gray-400 hidden md:block">
          {mounted && new Date().toLocaleDateString("vi-VN", {
            weekday: "short", day: "2-digit", month: "2-digit", year: "numeric",
          })}
        </span>

        {/* Notification bell */}
        <button
          className="relative flex items-center justify-center w-9 h-9 rounded-xl text-gray-400 hover:bg-indigo-50 hover:text-indigo-500 transition-colors"
          title="Thông báo"
        >
          <svg className="h-[18px] w-[18px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
          </svg>
          <span className="absolute top-1.5 right-1.5 h-2 w-2 rounded-full bg-red-500 border-2 border-white" />
        </button>

        {/* Divider */}
        <div className="h-6 w-px bg-gray-200" />

        {/* User profile */}
        <div className="flex items-center gap-2.5 cursor-pointer hover:bg-indigo-50 rounded-xl px-2 py-1.5 transition-colors">
          {/* Avatar */}
          <div className="relative">
            <div
              className="flex h-8 w-8 items-center justify-center rounded-full text-[11px] font-bold text-white shadow-sm"
              style={{ background: "linear-gradient(135deg, #6366F1, #3B82F6)" }}
            >
              {initials}
            </div>
            <span className="absolute bottom-0 right-0 block h-2 w-2 rounded-full bg-emerald-400 border-2 border-white" />
          </div>

          <div className="hidden sm:block">
            <p className="text-xs font-semibold text-gray-800 leading-tight">
              {user?.fullName || "BS. Nguyễn Văn An"}
            </p>
            <p className="text-[10px] text-gray-400 leading-tight">
              {user?.department || "Nội tổng quát"}
            </p>
          </div>

          {/* Dropdown chevron */}
          <svg className="w-3.5 h-3.5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </div>
      </div>
    </header>
  );
}
