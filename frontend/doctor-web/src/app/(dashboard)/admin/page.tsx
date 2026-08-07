"use client";

import { useState, useEffect } from "react";
import { directoryApi, DepartmentItem, RoomItem, DoctorProfileItem } from "@/lib/directory-api";

export default function AdminDashboardPage() {
  const [isLoading, setIsLoading] = useState(true);
  const [departments, setDepartments] = useState<DepartmentItem[] | null>(null);
  const [rooms, setRooms] = useState<RoomItem[] | null>(null);
  const [doctors, setDoctors] = useState<DoctorProfileItem[] | null>(null);

  useEffect(() => {
    async function loadRealData() {
      setIsLoading(true);
      try {
        const [deptList, roomList, docList] = await Promise.all([
          directoryApi.getDepartments(),
          directoryApi.getRooms(),
          directoryApi.getDoctors(),
        ]);
        setDepartments(deptList ?? []);
        setRooms(roomList ?? []);
        setDoctors(docList ?? []);
      } catch {
        setDepartments([]);
        setRooms([]);
        setDoctors([]);
      } finally {
        setIsLoading(false);
      }
    }
    loadRealData();
  }, []);

  const totalDepts = (departments ?? []).length;
  const totalRooms = (rooms ?? []).length;
  const totalDoctors = (doctors ?? []).length;

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
          Theo dõi dữ liệu thực tế về quy mô Khoa, Phòng khám và Đội ngũ Bác sĩ trên hệ thống CSDL
        </p>
      </div>

      {/* Real Master Data Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-4 gap-4">
        <div className="border border-card-border bg-card-bg p-4 rounded-xl shadow-sm">
          <p className="text-[11px] font-bold text-[#6A5C70] uppercase">Tổng số Khoa chuyên môn</p>
          {isLoading ? (
            <div className="h-8 bg-gray-100 rounded-md animate-pulse mt-1" />
          ) : (
            <p className="text-2xl font-extrabold text-purple-700 mt-1">{totalDepts} khoa</p>
          )}
        </div>

        <div className="border border-card-border bg-card-bg p-4 rounded-xl shadow-sm">
          <p className="text-[11px] font-bold text-[#6A5C70] uppercase">Phòng khám & CLS</p>
          {isLoading ? (
            <div className="h-8 bg-gray-100 rounded-md animate-pulse mt-1" />
          ) : (
            <p className="text-2xl font-extrabold text-emerald-600 mt-1">{totalRooms} phòng</p>
          )}
        </div>

        <div className="border border-card-border bg-card-bg p-4 rounded-xl shadow-sm">
          <p className="text-[11px] font-bold text-[#6A5C70] uppercase">Hồ sơ Bác sĩ</p>
          {isLoading ? (
            <div className="h-8 bg-gray-100 rounded-md animate-pulse mt-1" />
          ) : (
            <p className="text-2xl font-extrabold text-[#6E2582] mt-1">{totalDoctors} bác sĩ</p>
          )}
        </div>

        <div className="border border-card-border bg-card-bg p-4 rounded-xl shadow-sm">
          <p className="text-[11px] font-bold text-[#6A5C70] uppercase">Trạng thái CSDL Remote</p>
          <div className="flex items-center gap-2 mt-2">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
            <span className="text-xs font-bold text-emerald-700">Đã kết nối PostgreSQL</span>
          </div>
        </div>
      </div>

      {/* Master Data Highlights */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Real Departments List */}
        <div className="border border-card-border bg-card-bg p-5 rounded-xl shadow-sm space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-[#2B1D30]">Danh sách Khoa chuyên môn trong Hệ thống</h2>
            <span className="text-xs font-bold text-purple-700">{totalDepts} khoa</span>
          </div>

          {isLoading ? (
            <div className="space-y-2">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-12 bg-purple-50 rounded-lg animate-pulse" />
              ))}
            </div>
          ) : totalDepts === 0 ? (
            <p className="text-xs text-gray-500 text-center py-4">Chưa có khoa nào trong CSDL</p>
          ) : (
            <div className="space-y-2 text-xs max-h-72 overflow-y-auto">
              {(departments ?? []).map((dept) => (
                <div key={dept.code} className="p-3 bg-purple-50 border border-purple-200 rounded-lg flex justify-between items-center">
                  <div>
                    <p className="font-bold text-purple-900">{dept.name}</p>
                    <p className="text-purple-700 text-[10px]">Mã: {dept.code}</p>
                  </div>
                  <div className="flex items-center gap-1.5 text-xs font-semibold text-emerald-700">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                    <span>Hoạt động</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Real Rooms List */}
        <div className="border border-card-border bg-card-bg p-5 rounded-xl shadow-sm space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-[#2B1D30]">Danh sách Phòng khám & Cận lâm sàng</h2>
            <span className="text-xs font-bold text-emerald-700">{totalRooms} phòng</span>
          </div>

          {isLoading ? (
            <div className="space-y-2">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-12 bg-gray-50 rounded-lg animate-pulse" />
              ))}
            </div>
          ) : totalRooms === 0 ? (
            <p className="text-xs text-gray-500 text-center py-4">Chưa có phòng nào trong CSDL</p>
          ) : (
            <div className="space-y-2 text-xs max-h-72 overflow-y-auto">
              {(rooms ?? []).map((room) => (
                <div key={room.id} className="p-3 bg-gray-50 border border-card-border rounded-lg flex justify-between items-center">
                  <div>
                    <p className="font-bold text-[#2B1D30]">{room.displayName}</p>
                    <p className="text-[#6A5C70] text-[10px]">Mã phòng: {room.id} | Khoa: {room.departmentCode}</p>
                  </div>
                  <div className="flex items-center gap-1.5 text-xs font-semibold text-purple-700">
                    <span className="w-1.5 h-1.5 rounded-full bg-purple-500" />
                    <span>{room.roomType}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
