"use client";

import { useState, useEffect } from "react";
import { directoryApi, DepartmentItem, RoomItem, DoctorProfileItem } from "@/lib/directory-api";

const ROOM_TYPE_LABELS: Record<string, string> = {
  CONSULTATION: "Phòng khám",
  LAB: "Phòng Xét nghiệm",
  PHARMACY: "Quầy Dược",
  IMAGING: "Chẩn đoán Hình ảnh",
};

export default function AdminDashboardPage() {
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  const [departments, setDepartments] = useState<DepartmentItem[] | null>(null);
  const [rooms, setRooms] = useState<RoomItem[] | null>(null);
  const [doctors, setDoctors] = useState<DoctorProfileItem[] | null>(null);

  useEffect(() => {
    async function loadRealData() {
      setIsLoading(true);
      setError("");
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
        setDepartments(null);
        setRooms(null);
        setDoctors(null);
        setError(
          "Không tải được dữ liệu danh mục từ Directory. Chưa thể xác định số lượng khoa, phòng và bác sĩ."
        );
      } finally {
        setIsLoading(false);
      }
    }
    loadRealData();
  }, [retry]);

  const totalDepts = departments?.length;
  const totalRooms = rooms?.length;
  const totalDoctors = doctors?.length;

  return (
    <div className="space-y-6 pb-12">
      {/* Header */}
      <div className="rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-xs">
        <div className="flex items-center gap-2">
          <span className="inline-block h-2.5 w-2.5 animate-pulse rounded-full bg-[#0277A8]" />
          <span className="text-xs font-bold uppercase tracking-wider text-[#0277A8]">
            Phân hệ Quản trị viên Bệnh viện
          </span>
        </div>
        <h1 className="mt-1 text-2xl font-extrabold text-[#17242D]">
          Tổng quan Bảng điều khiển Quản trị
        </h1>
        <p className="mt-0.5 text-xs text-[#667985]">
          Theo dõi dữ liệu thực tế về quy mô Khoa, Phòng khám và Đội ngũ Bác sĩ trên hệ thống CSDL
        </p>
      </div>

      {error && (
        <div
          role="alert"
          className="rounded-xl border border-[#A86100]/30 bg-[#A86100]/10 p-4 text-sm text-[#A86100]"
        >
          {error}{" "}
          <button className="ml-3 font-bold underline" onClick={() => setRetry((value) => value + 1)}>
            Thử lại danh mục
          </button>
        </div>
      )}

      {/* Real Master Data Stats Grid */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-3 lg:grid-cols-4">
        <div className="rounded-xl border border-[#D5E0E6] bg-white p-4 shadow-xs">
          <p className="text-[11px] font-bold uppercase text-[#667985]">Tổng số Khoa chuyên môn</p>
          {isLoading ? (
            <div className="mt-1 h-8 animate-pulse rounded-md bg-[#EDF2F5]" />
          ) : (
            <p className="mt-1 text-2xl font-extrabold text-[#00577E]">{totalDepts ?? "—"} khoa</p>
          )}
        </div>

        <div className="rounded-xl border border-[#D5E0E6] bg-white p-4 shadow-xs">
          <p className="text-[11px] font-bold uppercase text-[#667985]">Phòng khám & CLS</p>
          {isLoading ? (
            <div className="mt-1 h-8 animate-pulse rounded-md bg-[#EDF2F5]" />
          ) : (
            <p className="mt-1 text-2xl font-extrabold text-[#087F6A]">{totalRooms ?? "—"} phòng</p>
          )}
        </div>

        <div className="rounded-xl border border-[#D5E0E6] bg-white p-4 shadow-xs">
          <p className="text-[11px] font-bold uppercase text-[#667985]">Hồ sơ Bác sĩ</p>
          {isLoading ? (
            <div className="mt-1 h-8 animate-pulse rounded-md bg-[#EDF2F5]" />
          ) : (
            <p className="mt-1 text-2xl font-extrabold text-[#0277A8]">{totalDoctors ?? "—"} bác sĩ</p>
          )}
        </div>

        <div className="rounded-xl border border-[#D5E0E6] bg-white p-4 shadow-xs">
          <p className="text-[11px] font-bold uppercase text-[#667985]">Nguồn dữ liệu danh mục</p>
          <div className="mt-2 flex items-center gap-2">
            <span
              className={`h-2 w-2 rounded-full ${
                error || isLoading ? "bg-[#667985]" : "bg-[#1B7F4B]"
              }`}
            />
            <span className="text-xs font-bold text-[#1B7F4B]">
              {isLoading
                ? "Đang tải từ Directory"
                : error
                  ? "Chưa tải được danh mục"
                  : "Đã tải dữ liệu từ Directory API"}
            </span>
          </div>
        </div>
      </div>

      {/* Master Data Highlights */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Real Departments List */}
        <div className="space-y-3 rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-extrabold text-[#17242D]">
              Danh sách Khoa chuyên môn trong Hệ thống
            </h2>
            <span className="rounded-full bg-[#DDF3FC] px-2.5 py-0.5 text-xs font-bold text-[#00577E]">
              {totalDepts ?? "—"} khoa
            </span>
          </div>

          {isLoading ? (
            <div className="space-y-2">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-12 animate-pulse rounded-xl bg-[#F5F8FA]" />
              ))}
            </div>
          ) : error ? (
            <p className="text-sm text-[#A86100]">Chưa tải được danh sách khoa.</p>
          ) : totalDepts === 0 ? (
            <p className="py-4 text-center text-xs text-[#667985]">Chưa có khoa nào trong CSDL</p>
          ) : (
            <div className="max-h-72 space-y-2 overflow-y-auto text-xs">
              {(departments ?? []).map((dept) => (
                <div
                  key={dept.code}
                  className="flex items-center justify-between rounded-xl border border-[#8BD2EE]/50 bg-[#DDF3FC]/35 p-3"
                >
                  <div>
                    <p className="font-bold text-[#17242D]">{dept.name}</p>
                    <p className="mt-0.5 font-mono text-[10px] text-[#00577E]">Mã: {dept.code}</p>
                  </div>
                  <div className="flex items-center gap-1.5 text-xs font-semibold text-[#1B7F4B]">
                    <span className="h-1.5 w-1.5 rounded-full bg-[#1B7F4B]" />
                    <span>Hoạt động</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Real Rooms List */}
        <div className="space-y-3 rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-extrabold text-[#17242D]">
              Danh sách Phòng khám & Cận lâm sàng
            </h2>
            <span className="rounded-full bg-[#D9F3EC] px-2.5 py-0.5 text-xs font-bold text-[#087F6A]">
              {totalRooms ?? "—"} phòng
            </span>
          </div>

          {isLoading ? (
            <div className="space-y-2">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-12 animate-pulse rounded-xl bg-[#F5F8FA]" />
              ))}
            </div>
          ) : error ? (
            <p className="text-sm text-[#A86100]">Chưa tải được danh sách phòng.</p>
          ) : totalRooms === 0 ? (
            <p className="py-4 text-center text-xs text-[#667985]">Chưa có phòng nào trong CSDL</p>
          ) : (
            <div className="max-h-72 space-y-2 overflow-y-auto text-xs">
              {(rooms ?? []).map((room) => (
                <div
                  key={room.id}
                  className="flex items-center justify-between rounded-xl border border-[#D5E0E6] bg-[#F5F8FA] p-3"
                >
                  <div>
                    <p className="font-bold text-[#17242D]">{room.displayName}</p>
                    <p className="mt-0.5 font-mono text-[10px] text-[#667985]">
                      Mã phòng: {room.id} | Khoa: {room.departmentCode}
                    </p>
                  </div>
                  <span
                    className={`rounded-md px-2.5 py-1 text-[11px] font-bold ${
                      room.roomType === "CONSULTATION"
                        ? "bg-[#DDF3FC] text-[#00577E]"
                        : room.roomType === "LAB"
                          ? "bg-[#D9F3EC] text-[#087F6A]"
                          : "bg-[#EDF2F5] text-[#4D626E]"
                    }`}
                  >
                    {ROOM_TYPE_LABELS[room.roomType] || room.roomType}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
