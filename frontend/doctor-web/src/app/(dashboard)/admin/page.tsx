"use client";

import { useState } from "react";

export default function AdminDashboardPage() {
  const [stats] = useState({
    todayVisits: 142,
    completedVisits: 98,
    activeRooms: 12,
    avgWaitTimeMinutes: 18,
    noShowRate: "4.2%",
    missedRate: "3.1%",
  });

  return (
    <div className="space-y-6 pb-12">
      {/* Header */}
      <div className="border border-card-border bg-card-bg p-5 rounded-xl shadow-sm">
        <div className="flex items-center gap-2">
          <span className="inline-block w-2.5 h-2.5 rounded-full bg-purple-500 animate-pulse" />
          <span className="text-xs font-bold uppercase tracking-wider text-purple-600">
            Phân hệ Quản trị viên Bệnh viện
          </span>
        </div>
        <h1 className="text-2xl font-extrabold text-[#2B1D30] mt-1">Tổng quan Bảng điều khiển Quản trị</h1>
        <p className="text-xs text-[#6A5C70] mt-0.5">
          Theo dõi chỉ số vận hành bệnh viện, công suất phòng khám và hiệu quả điều phối hàng đợi
        </p>
      </div>

      {/* Operational Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-6 gap-4">
        <div className="border border-card-border bg-card-bg p-4 rounded-xl shadow-sm">
          <p className="text-[11px] font-bold text-[#6A5C70] uppercase">Tổng lượt khám hôm nay</p>
          <p className="text-2xl font-extrabold text-[#2B1D30] mt-1">{stats.todayVisits}</p>
        </div>

        <div className="border border-card-border bg-card-bg p-4 rounded-xl shadow-sm">
          <p className="text-[11px] font-bold text-[#6A5C70] uppercase">Đã hoàn thành</p>
          <p className="text-2xl font-extrabold text-emerald-600 mt-1">{stats.completedVisits}</p>
        </div>

        <div className="border border-card-border bg-card-bg p-4 rounded-xl shadow-sm">
          <p className="text-[11px] font-bold text-[#6A5C70] uppercase">Phòng khám hoạt động</p>
          <p className="text-2xl font-extrabold text-purple-600 mt-1">{stats.activeRooms}</p>
        </div>

        <div className="border border-card-border bg-card-bg p-4 rounded-xl shadow-sm">
          <p className="text-[11px] font-bold text-[#6A5C70] uppercase">Thời gian chờ trung bình</p>
          <p className="text-2xl font-extrabold text-amber-600 mt-1">{stats.avgWaitTimeMinutes} phút</p>
        </div>

        <div className="border border-card-border bg-card-bg p-4 rounded-xl shadow-sm">
          <p className="text-[11px] font-bold text-[#6A5C70] uppercase">Tỷ lệ Vắng mặt</p>
          <p className="text-2xl font-extrabold text-rose-600 mt-1">{stats.missedRate}</p>
        </div>

        <div className="border border-card-border bg-card-bg p-4 rounded-xl shadow-sm">
          <p className="text-[11px] font-bold text-[#6A5C70] uppercase">Tỷ lệ Không đến</p>
          <p className="text-2xl font-extrabold text-gray-600 mt-1">{stats.noShowRate}</p>
        </div>
      </div>

      {/* Operational Highlights */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="border border-card-border bg-card-bg p-5 rounded-xl shadow-sm space-y-3">
          <h2 className="text-base font-bold text-[#2B1D30]">Trạng thái phòng khám theo Khoa</h2>
          <div className="space-y-2.5 text-xs">
            <div className="p-3 bg-purple-50 border border-purple-200 rounded-lg flex justify-between items-center">
              <div>
                <p className="font-bold text-purple-900">Khoa Nội tổng quát</p>
                <p className="text-purple-700">45 ca khám thành công · Chờ trung bình: 15 phút</p>
              </div>
              <div className="flex items-center gap-1.5 text-xs font-semibold text-emerald-700">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                <span>Đang hoạt động</span>
              </div>
            </div>

            <div className="p-3 bg-purple-50 border border-purple-200 rounded-lg flex justify-between items-center">
              <div>
                <p className="font-bold text-purple-900">Khoa Nhi</p>
                <p className="text-purple-700">28 ca khám thành công · Chờ trung bình: 12 phút</p>
              </div>
              <div className="flex items-center gap-1.5 text-xs font-semibold text-emerald-700">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                <span>Đang hoạt động</span>
              </div>
            </div>

            <div className="p-3 bg-purple-50 border border-purple-200 rounded-lg flex justify-between items-center">
              <div>
                <p className="font-bold text-purple-900">Khoa Ngoại</p>
                <p className="text-purple-700">25 ca khám thành công · Chờ trung bình: 22 phút</p>
              </div>
              <div className="flex items-center gap-1.5 text-xs font-semibold text-emerald-700">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                <span>Đang hoạt động</span>
              </div>
            </div>
          </div>
        </div>

        <div className="border border-card-border bg-card-bg p-5 rounded-xl shadow-sm space-y-3">
          <h2 className="text-base font-bold text-[#2B1D30]">Phân bổ Điểm phục vụ Cận lâm sàng</h2>
          <div className="space-y-2.5 text-xs">
            <div className="p-3 bg-gray-50 border border-card-border rounded-lg flex justify-between items-center">
              <div>
                <p className="font-bold text-[#2B1D30]">Phòng Xét nghiệm Huyết học 101</p>
                <p className="text-[#6A5C70]">32 lượt xét nghiệm đã phát hành kết quả</p>
              </div>
              <div className="flex items-center gap-1.5 text-xs font-semibold text-purple-700">
                <span className="w-1.5 h-1.5 rounded-full bg-purple-500" />
                <span>Hoạt động tốt</span>
              </div>
            </div>

            <div className="p-3 bg-gray-50 border border-card-border rounded-lg flex justify-between items-center">
              <div>
                <p className="font-bold text-[#2B1D30]">Phòng Siêu âm 201</p>
                <p className="text-[#6A5C70]">18 lượt siêu âm hoàn tất</p>
              </div>
              <div className="flex items-center gap-1.5 text-xs font-semibold text-purple-700">
                <span className="w-1.5 h-1.5 rounded-full bg-purple-500" />
                <span>Hoạt động tốt</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
