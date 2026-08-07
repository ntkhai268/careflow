"use client";

import Link from "next/link";
import { useAuth } from "@/contexts/AuthContext";
import { usePathname } from "next/navigation";
import { useState, useEffect, useRef, useSyncExternalStore } from "react";
import ProfileModal from "../modals/ProfileModal";
import SettingsModal from "../modals/SettingsModal";
import HelpModal from "../modals/HelpModal";

const BREADCRUMB_MAP: Record<string, string> = {
  "/dashboard/general": "Tổng quan Khám bệnh",
  "/dashboard/queue": "Hàng đợi Khám bệnh",
  "/dashboard/prescriptions": "Quản lý Đơn thuốc",
  "/admin": "Tổng quan Quản trị",
  "/admin/accounts": "Quản lý Tài khoản",
  "/admin/facility": "Cấu hình Cơ sở Y tế",
  "/admin/schedule": "Cấu hình Lịch & Khung giờ",
  "/lab/queue": "Hàng đợi Cận lâm sàng",
  "/staff/checkin": "Quét QR Check-in",
  "/staff/pharmacy": "Hàng đợi Phát thuốc",
};

export default function Topbar() {
  const { user, logout } = useAuth();
  const pathname = usePathname();
  const mounted = useSyncExternalStore(
    () => () => {},
    () => true,
    () => false,
  );
  
  // Dropdown & Modal States
  const [showUserDropdown, setShowUserDropdown] = useState(false);
  const [showProfileModal, setShowProfileModal] = useState(false);
  const [showSettingsModal, setShowSettingsModal] = useState(false);
  const [showHelpModal, setShowHelpModal] = useState(false);
  const [doctorStatus, setDoctorStatus] = useState<"READY" | "PAUSED">("READY");

  const dropdownRef = useRef<HTMLDivElement>(null);

  // Listen to custom events from Sidebar
  useEffect(() => {
    const handleOpenHelp = () => setShowHelpModal(true);
    const handleOpenSettings = () => setShowSettingsModal(true);

    window.addEventListener("careflow:open-help", handleOpenHelp);
    window.addEventListener("careflow:open-settings", handleOpenSettings);

    return () => {
      window.removeEventListener("careflow:open-help", handleOpenHelp);
      window.removeEventListener("careflow:open-settings", handleOpenSettings);
    };
  }, []);

  // Close dropdown on outside click
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setShowUserDropdown(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const pageTitle = BREADCRUMB_MAP[pathname] || "Dashboard";
  const rootHref = user?.role?.toUpperCase().includes("ADMIN") ? "/admin" : "/dashboard/general";
  const initials = user?.fullName
    ? user.fullName.split(" ").map((n: string) => n[0]).slice(-2).join("").toUpperCase()
    : "BS";

  return (
    <>
      <header className="sticky top-0 z-30 flex h-16 items-center justify-between px-6 bg-white border-b border-gray-200 shadow-sm">
        {/* Left — Breadcrumb */}
        <div className="flex items-center gap-2 text-[11px]">
          <Link
            href={rootHref}
            className="text-gray-400 hover:text-indigo-600 transition-colors font-medium"
          >
            Bảng điều khiển
          </Link>
          <svg className="w-3 h-3 text-gray-300 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
          </svg>
          <Link
            href={pathname}
            className="font-bold text-indigo-600 hover:text-indigo-800 transition-colors"
          >
            {pageTitle}
          </Link>
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

          {/* Help button */}
          <button
            onClick={() => setShowHelpModal(true)}
            className="flex items-center justify-center w-9 h-9 rounded-xl text-gray-400 hover:bg-indigo-50 hover:text-indigo-500 transition-colors"
            title="Trợ giúp & Phím tắt"
          >
            <svg className="h-[18px] w-[18px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </button>

          {/* Settings button */}
          <button
            onClick={() => setShowSettingsModal(true)}
            className="flex items-center justify-center w-9 h-9 rounded-xl text-gray-400 hover:bg-indigo-50 hover:text-indigo-500 transition-colors"
            title="Cài đặt"
          >
            <svg className="h-[18px] w-[18px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
          </button>

          {/* Divider */}
          <div className="h-6 w-px bg-gray-200" />

          {/* User Profile Dropdown Container */}
          <div className="relative" ref={dropdownRef}>
            <div
              onClick={() => setShowUserDropdown(!showUserDropdown)}
              className="flex items-center gap-2.5 cursor-pointer hover:bg-indigo-50 rounded-xl px-2 py-1.5 transition-colors"
            >
              {/* Avatar */}
              <div className="relative">
                <div
                  className="flex h-8 w-8 items-center justify-center rounded-full text-[11px] font-bold text-white shadow-xs"
                  style={{ background: "linear-gradient(135deg, #6366F1, #3B82F6)" }}
                >
                  {initials}
                </div>
                <span
                  className="absolute bottom-0 right-0 block h-2.5 w-2.5 rounded-full border-2 border-white"
                  style={{ background: doctorStatus === "READY" ? "#10B981" : "#F59E0B" }}
                />
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
              <svg
                className={`w-3.5 h-3.5 text-gray-400 transition-transform duration-200 ${showUserDropdown ? "rotate-180" : ""}`}
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </div>

            {/* Dropdown Menu */}
            {showUserDropdown && (
              <div className="absolute right-0 mt-2 w-64 rounded-xl bg-white shadow-xl border border-gray-100 py-2 text-left z-50 animate-in fade-in slide-in-from-top-2 duration-150">
                {/* Doctor Brief Info */}
                <div className="px-4 py-2.5 border-b border-gray-100">
                  <p className="text-xs font-bold text-gray-900">{user?.fullName || "BS. Nguyễn Văn An"}</p>
                  <p className="text-[10px] text-indigo-600 font-medium">{user?.title || "BS. CKI - Khoa Nội tổng quát"}</p>
                  <div className="mt-2 flex items-center justify-between pt-2 border-t border-gray-50">
                    <span className="text-[10px] text-gray-400">Trạng thái làm việc:</span>
                    <button
                      onClick={() => setDoctorStatus(doctorStatus === "READY" ? "PAUSED" : "READY")}
                      className={`text-[9px] font-bold px-2 py-0.5 rounded-full transition-colors ${
                        doctorStatus === "READY" ? "bg-emerald-100 text-emerald-800" : "bg-amber-100 text-amber-800"
                      }`}
                    >
                      {doctorStatus === "READY" ? "🟢 Đang khám" : "🟡 Tạm dừng"}
                    </button>
                  </div>
                </div>

                {/* Menu items */}
                <div className="py-1">
                  <button
                    onClick={() => { setShowUserDropdown(false); setShowProfileModal(true); }}
                    className="w-full px-4 py-2 text-left text-xs text-gray-700 hover:bg-indigo-50 hover:text-indigo-600 flex items-center gap-2.5 transition-colors"
                  >
                    <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                    </svg>
                    Hồ sơ & Chuyên môn Bác sĩ
                  </button>

                  <button
                    onClick={() => { setShowUserDropdown(false); setShowSettingsModal(true); }}
                    className="w-full px-4 py-2 text-left text-xs text-gray-700 hover:bg-indigo-50 hover:text-indigo-600 flex items-center gap-2.5 transition-colors"
                  >
                    <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                    </svg>
                    Cài đặt hệ thống
                  </button>

                  <button
                    onClick={() => { setShowUserDropdown(false); setShowHelpModal(true); }}
                    className="w-full px-4 py-2 text-left text-xs text-gray-700 hover:bg-indigo-50 hover:text-indigo-600 flex items-center gap-2.5 transition-colors"
                  >
                    <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    Hướng dẫn & Phím tắt
                  </button>
                </div>

                <div className="border-t border-gray-100 pt-1">
                  <button
                    onClick={() => { setShowUserDropdown(false); logout(); }}
                    className="w-full px-4 py-2 text-left text-xs text-slate-500 hover:text-rose-600 hover:bg-rose-50 flex items-center gap-2.5 transition-colors font-medium"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                    </svg>
                    Đăng xuất khỏi hệ thống
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Modals */}
      <ProfileModal isOpen={showProfileModal} onClose={() => setShowProfileModal(false)} />
      <SettingsModal isOpen={showSettingsModal} onClose={() => setShowSettingsModal(false)} />
      <HelpModal isOpen={showHelpModal} onClose={() => setShowHelpModal(false)} />
    </>
  );
}

