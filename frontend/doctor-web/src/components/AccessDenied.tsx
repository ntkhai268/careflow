"use client";

import Link from "next/link";
import { useAuth } from "@/contexts/AuthContext";
import { getDefaultRouteForRole } from "@/lib/role-utils";

interface AccessDeniedProps {
  requiredRoleLabel: string;
}

export default function AccessDenied({ requiredRoleLabel }: AccessDeniedProps) {
  const { user } = useAuth();
  const defaultRoute = getDefaultRouteForRole(user?.role);

  const roleNames: Record<string, string> = {
    ADMIN: "Quản trị viên Hệ thống",
    DOCTOR: "Bác sĩ Khám bệnh",
    LAB_TECHNICIAN: "Kỹ thuật viên Cận lâm sàng",
    STAFF: "Nhân viên Tiếp nhận & Quầy thuốc",
  };

  const userRoleName = user?.role ? (roleNames[user.role.toUpperCase()] || user.role) : "Chưa xác định";

  return (
    <div className="flex items-center justify-center min-h-[75vh] p-4 relative overflow-hidden">
      {/* Decorative Subtle Grid Background */}
      <div className="absolute inset-0 bg-[linear-gradient(to_right,#6E258206_1px,transparent_1px),linear-gradient(to_bottom,#6E258206_1px,transparent_1px)] bg-[size:24px_24px] pointer-events-none" />

      {/* Main Glassmorphic Card Container */}
      <div className="relative z-10 max-w-lg w-full bg-white border border-card-border rounded-2xl shadow-2xl overflow-hidden transition-all">
        {/* Top Accent Gradient Bar */}
        <div className="h-2 bg-gradient-to-r from-rose-500 via-purple-600 to-[#6E2582]" />

        <div className="p-8 space-y-6 text-center">
          {/* Header Status Badge */}
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-rose-50 border border-rose-200 text-rose-700 text-[11px] font-bold tracking-wide">
            <span className="w-2 h-2 rounded-full bg-rose-600 animate-pulse" />
            <span>403 - KHÔNG CÓ QUYỀN TRUY CẬP</span>
          </div>

          {/* Glowing Lock Icon */}
          <div className="w-20 h-20 mx-auto rounded-full bg-rose-50 border-4 border-rose-100 flex items-center justify-center text-rose-600 shadow-md">
            <svg className="w-10 h-10" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.75}
                d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
              />
            </svg>
          </div>

          {/* Titles */}
          <div className="space-y-2">
            <h1 className="text-xl font-extrabold text-[#2B1D30]">Truy cập bị từ chối</h1>
            <p className="text-xs text-[#6A5C70] leading-relaxed max-w-md mx-auto">
              Bạn đang cố gắng truy cập vào phân vùng chức năng không thuộc thẩm quyền của tài khoản hiện tại.
            </p>
          </div>

          {/* Detailed Account Context Box */}
          <div className="bg-gray-50 border border-card-border rounded-xl p-4 text-xs space-y-2.5 text-left shadow-inner">
            <div className="flex items-center justify-between">
              <span className="text-[#6A5C70] font-medium">Tài khoản:</span>
              <span className="font-bold text-[#2B1D30]">{user?.fullName || user?.email || "N/A"}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-[#6A5C70] font-medium">Vai trò hiện tại:</span>
              <span className="px-2.5 py-0.5 rounded-md bg-purple-100 text-[#6E2582] font-bold text-[11px]">
                {userRoleName}
              </span>
            </div>
            <div className="flex items-center justify-between pt-1 border-t border-gray-200">
              <span className="text-[#6A5C70] font-medium">Yêu cầu quyền:</span>
              <span className="font-bold text-rose-600">{requiredRoleLabel}</span>
            </div>
          </div>

          {/* Action CTA Button */}
          <div className="pt-2">
            <Link
              href={defaultRoute}
              className="w-full inline-flex items-center justify-center gap-2 px-6 py-3 bg-[#6E2582] text-white text-xs font-bold rounded-xl hover:bg-[#561A66] transition-all shadow-md hover:shadow-lg cursor-pointer active:scale-[0.99]"
            >
              <span>Quay về trang chính dành cho bạn</span>
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
              </svg>
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
