"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/contexts/AuthContext";

const getNavItems = (role?: string) => {
  const normalizedRole = role?.toUpperCase() || "";

  if (normalizedRole.includes("ADMIN")) {
    return [
      { label: "Tổng quan Quản trị", href: "/admin" },
      { label: "Quản lý Tài khoản", href: "/admin/accounts" },
      { label: "Cấu hình Cơ sở Y tế", href: "/admin/facility" },
      { label: "Cấu hình Lịch & Khung giờ", href: "/admin/schedule" },
    ];
  }

  if (normalizedRole.includes("LAB")) {
    return [
      { label: "Tổng quan", href: "/dashboard/general" },
      { label: "Hàng đợi Cận lâm sàng", href: "/lab/queue" },
    ];
  }

  if (normalizedRole.includes("STAFF")) {
    return [
      { label: "Tổng quan", href: "/dashboard/general" },
      { label: "Quét QR Check-in", href: "/staff/checkin" },
      { label: "Hàng đợi phát thuốc", href: "/staff/pharmacy" },
    ];
  }

  // Default: Doctor
  return [
    { label: "Tổng quan", href: "/dashboard/general" },
    { label: "Hàng đợi khám", href: "/dashboard/queue" },
    { label: "Đơn thuốc", href: "/dashboard/prescriptions" },
    { label: "Quản trị hệ thống", href: "/admin" },
  ];
};

interface SidebarProps {
  isCollapsed: boolean;
  onToggleCollapse: () => void;
}

export default function Sidebar({ isCollapsed, onToggleCollapse }: SidebarProps) {
  const pathname = usePathname();
  const { user, logout } = useAuth();
  const navItems = getNavItems(user?.role);


  const initials = user?.fullName
    ? user.fullName.split(" ").map((n: string) => n[0]).slice(-2).join("").toUpperCase()
    : "BS";

  return (
    <aside
      className={`fixed left-0 top-0 z-40 flex h-screen flex-col transition-all duration-200 ${
        isCollapsed ? "w-16" : "w-64"
      }`}
      style={{
        background: "linear-gradient(180deg, #0D0F1E 0%, #111427 100%)",
        borderRight: "1px solid #1E2340",
        backgroundImage: `
          linear-gradient(180deg, #0D0F1E 0%, #111427 100%),
          linear-gradient(to right, rgba(99,102,241,0.04) 1px, transparent 1px),
          linear-gradient(to bottom, rgba(99,102,241,0.04) 1px, transparent 1px)
        `,
        backgroundSize: "100% 100%, 24px 24px, 24px 24px",
      }}
    >
      {/* Logo & Toggle */}
      <div
        className="flex h-16 items-center justify-between px-4"
        style={{ borderBottom: "1px solid #1E2340" }}
      >
        {!isCollapsed && (
          <div className="flex items-center gap-2.5">
            <img src="/logo.svg" alt="CareFlow" className="w-8 h-8 object-contain" />
            <div>
              <h1 className="text-sm font-bold text-white tracking-tight">CareFlow</h1>
              <p className="text-[10px] font-medium tracking-widest uppercase" style={{ color: "#4B5677" }}>
                Bảng điều khiển 
              </p>
            </div>
          </div>
        )}
        {isCollapsed && (
          <img src="/logo.svg" alt="CareFlow" className="w-8 h-8 object-contain mx-auto" />
        )}
        {!isCollapsed && (
          <button
            onClick={onToggleCollapse}
            title="Thu gọn"
            className="p-1.5 rounded-lg transition-colors"
            style={{ color: "#4B5677" }}
            onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.backgroundColor = "#161930"; (e.currentTarget as HTMLButtonElement).style.color = "#94A3B8"; }}
            onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.backgroundColor = "transparent"; (e.currentTarget as HTMLButtonElement).style.color = "#4B5677"; }}
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 19l-7-7 7-7m8 14l-7-7 7-7" />
            </svg>
          </button>
        )}
      </div>

      {/* Nav label */}
      {!isCollapsed && (
        <div className="px-5 pt-5 pb-1">
          <p className="text-[9px] font-bold tracking-widest uppercase" style={{ color: "#2E3462" }}>Danh mục bảng điều khiển</p>
        </div>
      )}

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto px-2.5 py-2 space-y-0.5">
        {navItems.map((item) => {
          const isActive = pathname === item.href || pathname.startsWith(item.href + "/");
          return (
            <Link
              key={item.href}
              href={item.href}
              title={isCollapsed ? item.label : undefined}
              className="flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-150"
              style={
                isActive
                  ? {
                      backgroundColor: "rgba(99,102,241,0.15)",
                      color: "#818CF8",
                      boxShadow: "inset 0 0 0 1px rgba(99,102,241,0.2)",
                    }
                  : { color: "#64748B" }
              }
              onMouseEnter={e => {
                if (!isActive) {
                  (e.currentTarget as HTMLAnchorElement).style.backgroundColor = "#161930";
                  (e.currentTarget as HTMLAnchorElement).style.color = "#94A3B8";
                }
              }}
              onMouseLeave={e => {
                if (!isActive) {
                  (e.currentTarget as HTMLAnchorElement).style.backgroundColor = "transparent";
                  (e.currentTarget as HTMLAnchorElement).style.color = "#64748B";
                }
              }}
            >
              {!isCollapsed && <span>{item.label}</span>}
              {isCollapsed && <span className="text-xs font-bold">{item.label.charAt(0)}</span>}
              {/* Active indicator dot */}
              {isActive && !isCollapsed && (
                <span className="ml-auto w-1.5 h-1.5 rounded-full" style={{ background: "#6366F1" }} />
              )}
            </Link>
          );
        })}
      </nav>

      {/* Bottom section */}
      <div style={{ borderTop: "1px solid #1E2340" }}>
        {isCollapsed && (
          <div className="px-2.5 py-2">
            <button
              onClick={onToggleCollapse}
              title="Mở rộng"
              className="w-full flex items-center justify-center p-2 rounded-xl transition-colors"
              style={{ color: "#475569" }}
              onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.backgroundColor = "#161930"; (e.currentTarget as HTMLButtonElement).style.color = "#94A3B8"; }}
              onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.backgroundColor = "transparent"; (e.currentTarget as HTMLButtonElement).style.color = "#475569"; }}
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 5l7 7-7 7M5 5l7 7-7 7" />
              </svg>
            </button>
          </div>
        )}

        {/* Help */}
        <div className="px-2.5 pt-2 pb-0.5">
          <button
            onClick={() => window.dispatchEvent(new CustomEvent("careflow:open-help"))}
            className={`w-full flex items-center gap-3 px-3 py-2 rounded-xl transition-colors ${isCollapsed ? "justify-center" : ""}`}
            style={{ color: "#475569" }}
            title="Trợ giúp & Phím tắt"
            onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.backgroundColor = "#161930"; (e.currentTarget as HTMLButtonElement).style.color = "#94A3B8"; }}
            onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.backgroundColor = "transparent"; (e.currentTarget as HTMLButtonElement).style.color = "#475569"; }}
          >
            <svg className="w-[18px] h-[18px] flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            {!isCollapsed && <span className="text-xs font-medium">Trợ giúp</span>}
          </button>
        </div>

        {/* Settings */}
        <div className="px-2.5 pb-2">
          <button
            onClick={() => window.dispatchEvent(new CustomEvent("careflow:open-settings"))}
            className={`w-full flex items-center gap-3 px-3 py-2 rounded-xl transition-colors ${isCollapsed ? "justify-center" : ""}`}
            style={{ color: "#475569" }}
            title="Cài đặt"
            onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.backgroundColor = "#161930"; (e.currentTarget as HTMLButtonElement).style.color = "#94A3B8"; }}
            onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.backgroundColor = "transparent"; (e.currentTarget as HTMLButtonElement).style.color = "#475569"; }}
          >
            <svg className="w-[18px] h-[18px] flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            {!isCollapsed && <span className="text-xs font-medium">Cài đặt</span>}
          </button>
        </div>

        {/* User info */}
        <div className="px-2.5 pb-3 pt-2" style={{ borderTop: "1px solid #1E2340" }}>
          <div
            className={`flex items-center gap-3 px-3 py-2 rounded-xl cursor-pointer transition-colors ${isCollapsed ? "justify-center" : ""}`}
            onMouseEnter={e => { (e.currentTarget as HTMLDivElement).style.backgroundColor = "#161930"; }}
            onMouseLeave={e => { (e.currentTarget as HTMLDivElement).style.backgroundColor = "transparent"; }}
          >
            {/* Avatar with online dot */}
            <div className="relative flex-shrink-0">
              <div
                className="flex h-8 w-8 items-center justify-center rounded-full text-[11px] font-bold text-white shadow-md"
                style={{ background: "linear-gradient(135deg, #6366F1, #3B82F6)" }}
              >
                {initials}
              </div>
              <span
                className="absolute bottom-0 right-0 block h-2.5 w-2.5 rounded-full border-2"
                style={{ background: "#10B981", borderColor: "#0D0F1E" }}
              />
            </div>

            {!isCollapsed && (
              <div className="min-w-0 flex-1">
                <p className="truncate text-xs font-semibold text-white">{user?.fullName || "BS. Nguyễn Văn An"}</p>
                <p className="truncate text-[10px]" style={{ color: "#475569" }}>{user?.department || "Khoa Nội tổng quát"}</p>
              </div>
            )}

            {!isCollapsed && (
              <button
                onClick={logout}
                title="Đăng xuất"
                className="p-1 rounded-lg transition-colors flex-shrink-0"
                style={{ color: "#334155" }}
                onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.backgroundColor = "rgba(239,68,68,0.1)"; (e.currentTarget as HTMLButtonElement).style.color = "#EF4444"; }}
                onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.backgroundColor = "transparent"; (e.currentTarget as HTMLButtonElement).style.color = "#334155"; }}
              >
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                </svg>
              </button>
            )}
          </div>
        </div>
      </div>
    </aside>
  );
}
