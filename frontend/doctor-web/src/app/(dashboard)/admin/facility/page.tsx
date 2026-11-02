"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  directoryApi,
  DepartmentItem,
  RoomItem,
  DoctorProfileItem,
} from "@/lib/directory-api";
import FacilityColumnsGrid from "@/components/admin/FacilityColumnsGrid";
import DepartmentFormModal from "@/components/admin/DepartmentFormModal";
import RoomFormModal from "@/components/admin/RoomFormModal";
import DoctorProfileFormModal from "@/components/admin/DoctorProfileFormModal";

export default function AdminFacilityPage() {
  const [departments, setDepartments] = useState<DepartmentItem[] | null>(null);
  const [rooms, setRooms] = useState<RoomItem[] | null>(null);
  const [doctors, setDoctors] = useState<DoctorProfileItem[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [showDeptModal, setShowDeptModal] = useState(false);
  const [editingDept, setEditingDept] = useState<DepartmentItem | null>(null);

  const [showRoomModal, setShowRoomModal] = useState(false);
  const [editingRoom, setEditingRoom] = useState<RoomItem | null>(null);

  const [showDoctorModal, setShowDoctorModal] = useState(false);
  const [editingDoctor, setEditingDoctor] = useState<DoctorProfileItem | null>(null);
  const [defaultLicense, setDefaultLicense] = useState("");

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
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
      setError("Không thể tải dữ liệu cấu hình cơ sở y tế. Vui lòng thử lại.");
      setDepartments([]);
      setRooms([]);
      setDoctors([]);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void fetchData();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [fetchData]);

  return (
    <div className="space-y-6 pb-12">
      {/* Header */}
      <div className="border border-card-border bg-card-bg p-5 rounded-xl shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-block w-2.5 h-2.5 rounded-full bg-purple-500 animate-pulse" />
            <span className="text-xs font-bold uppercase tracking-wider text-purple-600">
              Quản trị Cấu hình Bệnh viện
            </span>
          </div>
          <h1 className="text-2xl font-extrabold text-[#2B1D30] mt-1">
            Cấu hình Cơ sở Y tế & Danh bạ Bác sĩ
          </h1>
          <p className="text-xs text-[#6A5C70] mt-0.5">
            Quản lý danh mục Khoa chuyên môn, Danh sách Phòng khám và Hồ sơ Bác sĩ hành nghề
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Link
            href="/admin/services"
            className="rounded-xl bg-purple-100 px-3.5 py-2 text-xs font-bold text-purple-800 hover:bg-purple-200"
          >
            Dịch vụ & bảng giá
          </Link>
          <button
            onClick={() => {
              setEditingDept(null);
              setShowDeptModal(true);
            }}
            disabled={isLoading}
            className="px-3.5 py-2 rounded-xl font-bold text-xs text-white bg-[#6E2582] hover:bg-[#561A66] shadow-sm transition-all cursor-pointer disabled:opacity-50"
          >
            + Thêm khoa
          </button>
          <button
            onClick={() => {
              setEditingRoom(null);
              setShowRoomModal(true);
            }}
            disabled={isLoading}
            className="px-3.5 py-2 rounded-xl font-bold text-xs text-[#6E2582] bg-purple-100 hover:bg-purple-200 shadow-sm transition-all cursor-pointer disabled:opacity-50"
          >
            + Thêm phòng
          </button>
          <button
            onClick={() => {
              setEditingDoctor(null);
              setDefaultLicense("CCHN-" + Math.floor(100000 + Math.random() * 900000));
              setShowDoctorModal(true);
            }}
            disabled={isLoading}
            className="px-3.5 py-2 rounded-xl font-bold text-xs text-emerald-800 bg-emerald-100 hover:bg-emerald-200 shadow-sm transition-all cursor-pointer disabled:opacity-50"
          >
            + Thêm hồ sơ Bác sĩ
          </button>
        </div>
      </div>

      {error && (
        <div className="p-4 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-700 font-semibold">
          {error}
        </div>
      )}

      <FacilityColumnsGrid
        departments={departments}
        rooms={rooms}
        doctors={doctors}
        isLoading={isLoading}
        onEditDept={(dept) => {
          setEditingDept(dept);
          setShowDeptModal(true);
        }}
        onEditRoom={(room) => {
          setEditingRoom(room);
          setShowRoomModal(true);
        }}
        onEditDoctor={(doc) => {
          setEditingDoctor(doc);
          setShowDoctorModal(true);
        }}
      />

      {showDeptModal && (
        <DepartmentFormModal
          key={editingDept?.code ?? "new-dept"}
          editingDept={editingDept}
          onClose={() => setShowDeptModal(false)}
          onSaved={fetchData}
        />
      )}

      {showRoomModal && (
        <RoomFormModal
          key={editingRoom?.id ?? "new-room"}
          editingRoom={editingRoom}
          departments={departments ?? []}
          onClose={() => setShowRoomModal(false)}
          onSaved={fetchData}
        />
      )}

      {showDoctorModal && (
        <DoctorProfileFormModal
          key={editingDoctor?.id ?? "new-doctor"}
          editingDoctor={editingDoctor}
          departments={departments ?? []}
          rooms={rooms ?? []}
          defaultLicense={defaultLicense}
          onClose={() => setShowDoctorModal(false)}
          onSaved={fetchData}
        />
      )}
    </div>
  );
}
