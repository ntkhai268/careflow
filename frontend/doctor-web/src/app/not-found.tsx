"use client";

import Link from "next/link";
import { useAuth } from "@/contexts/AuthContext";
import { getDefaultRouteForRole } from "@/lib/role-utils";

export default function NotFound() {
  const { user } = useAuth();
  const defaultRoute = getDefaultRouteForRole(user?.role);

  return (
    <div className="flex items-center justify-center min-h-screen p-4 relative overflow-hidden bg-[#FDFBFD]">
      {/* Decorative Grid Background */}
      <div className="absolute inset-0 bg-[linear-gradient(to_right,#6E258208_1px,transparent_1px),linear-gradient(to_bottom,#6E258208_1px,transparent_1px)] bg-[size:24px_24px] pointer-events-none" />

      {/* Decorative Glow Elements */}
      <div className="absolute -top-24 -right-24 w-96 h-96 bg-purple-200/40 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute -bottom-24 -left-24 w-96 h-96 bg-rose-200/30 rounded-full blur-3xl pointer-events-none" />

      {/* Main Glassmorphic Container */}
      <div className="relative z-10 max-w-lg w-full bg-white border border-card-border rounded-2xl shadow-2xl overflow-hidden transition-all">
        {/* Top Gradient Accent Bar */}
        <div className="h-2 bg-gradient-to-r from-purple-500 via-[#6E2582] to-amber-500" />

        <div className="p-8 space-y-6 text-center">
          {/* Header Icon & Status Badge */}
          <div className="flex flex-col items-center gap-4">
            <div className="relative flex items-center justify-center w-20 h-20 rounded-2xl bg-purple-50 border-2 border-purple-100 shadow-inner">
              <span className="text-4xl">🔍</span>
              <span className="absolute -top-1 -right-1 flex h-3.5 w-3.5">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-purple-400 opacity-75" />
                <span className="relative inline-flex rounded-full h-3.5 w-3.5 bg-purple-600" />
              </span>
            </div>

            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-purple-50 border border-purple-200 text-purple-800 text-[11px] font-bold tracking-wide uppercase">
              <span>404 · KHÔNG TÌM THẤY TRANG</span>
            </div>
          </div>

          {/* Title & Explanation */}
          <div className="space-y-2">
            <h1 className="text-2xl font-extrabold text-[#2B1D30] tracking-tight">
              Đường Dẫn Không Tồn Tại
            </h1>
            <p className="text-xs text-[#6A5C70] leading-relaxed max-w-md mx-auto">
              Trang bạn đang truy cập không tồn tại hoặc đã bị di chuyển trong quá trình cập nhật hệ thống CareFlow. Vui lòng kiểm tra lại địa chỉ URL.
            </p>
          </div>

          {/* Large 404 Watermark Display */}
          <div className="py-2">
            <span className="text-7xl font-black text-transparent bg-clip-text bg-gradient-to-r from-purple-600 via-[#6E2582] to-amber-600 tracking-widest opacity-90 select-none">
              404
            </span>
          </div>

          {/* Action Buttons */}
          <div className="pt-2 flex flex-col sm:flex-row items-center justify-center gap-3">
            <Link
              href={defaultRoute}
              className="w-full sm:w-auto px-6 py-3 rounded-xl font-bold text-xs text-white bg-[#6E2582] hover:bg-[#561A66] shadow-md transition-all flex items-center justify-center gap-2 cursor-pointer"
            >
              <span>Về Trang Chủ Bảng Điều Khiển</span>
              <span className="text-base">→</span>
            </Link>

            <button
              onClick={() => history.back()}
              className="w-full sm:w-auto px-5 py-3 rounded-xl font-bold text-xs text-[#2B1D30] bg-gray-100 hover:bg-gray-200 border border-card-border transition-all cursor-pointer"
            >
              Quay Lại Trang Trước
            </button>
          </div>
        </div>

        {/* Footer info bar */}
        <div className="px-6 py-3 bg-gray-50 border-t border-card-border flex items-center justify-between text-[10px] text-[#6A5C70]">
          <span>Hệ thống Quản lý Bệnh viện CareFlow</span>
          <span className="font-semibold text-purple-700">ErrorCode: 404_NOT_FOUND</span>
        </div>
      </div>
    </div>
  );
}
