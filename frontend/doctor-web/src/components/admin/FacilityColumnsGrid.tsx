"use client";

import {
  DepartmentItem,
  DoctorProfileItem,
  RoomItem,
} from "@/lib/directory-api";

interface FacilityColumnsGridProps {
  departments: DepartmentItem[] | null;
  rooms: RoomItem[] | null;
  doctors: DoctorProfileItem[] | null;
  isLoading: boolean;
  onEditDept: (dept: DepartmentItem) => void;
  onEditRoom: (room: RoomItem) => void;
  onEditDoctor: (doc: DoctorProfileItem) => void;
}

export default function FacilityColumnsGrid({
  departments,
  rooms,
  doctors,
  isLoading,
  onEditDept,
  onEditRoom,
  onEditDoctor,
}: FacilityColumnsGridProps) {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      {/* Departments Column */}
      <div className="border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-bold text-[#2B1D30]">Khoa chuyên môn</h2>
          <span className="text-xs text-purple-700 font-bold">
            {(departments ?? []).length} khoa
          </span>
        </div>

        {isLoading ? (
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-20 bg-purple-50/50 rounded-xl animate-pulse" />
            ))}
          </div>
        ) : (departments ?? []).length === 0 ? (
          <p className="text-xs text-[#6A5C70] py-6 text-center">Chưa có khoa nào được khởi tạo</p>
        ) : (
          <div className="space-y-3 text-xs">
            {(departments ?? []).map((dept) => (
              <div key={dept.code} className="p-4 bg-purple-50/60 border border-purple-200 rounded-xl space-y-2">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-purple-950 text-sm">{dept.name}</span>
                  <span className="px-2 py-0.5 bg-purple-200 text-purple-800 font-bold text-[10px] rounded-md">
                    {dept.code}
                  </span>
                </div>
                {dept.description && (
                  <p className="text-[#6A5C70] text-[11px]">{dept.description}</p>
                )}
                <div className="pt-1 flex justify-end">
                  <button
                    onClick={() => onEditDept(dept)}
                    className="text-xs font-bold text-[#6E2582] hover:underline cursor-pointer"
                  >
                    Chỉnh sửa
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Rooms Column */}
      <div className="border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-bold text-[#2B1D30]">Danh sách Phòng khám & CLS</h2>
          <span className="text-xs text-purple-700 font-bold">
            {(rooms ?? []).length} phòng
          </span>
        </div>

        {isLoading ? (
          <div className="space-y-3">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="h-16 bg-gray-100 rounded-xl animate-pulse" />
            ))}
          </div>
        ) : (rooms ?? []).length === 0 ? (
          <p className="text-xs text-[#6A5C70] py-6 text-center">Chưa có phòng nào được tạo</p>
        ) : (
          <div className="space-y-3 text-xs">
            {(rooms ?? []).map((room) => (
              <div key={room.id} className="p-3.5 bg-gray-50 border border-card-border rounded-xl flex items-center justify-between">
                <div>
                  <p className="font-bold text-[#2B1D30] text-sm">{room.displayName}</p>
                  <p className="text-[#6A5C70] text-[11px] mt-0.5">
                    Mã: <span className="font-mono font-bold text-gray-800">{room.id}</span> | Khoa: {room.departmentCode}
                  </p>
                </div>
                <div className="flex flex-col items-end gap-1">
                  <span className="px-2 py-0.5 bg-purple-100 text-purple-800 font-bold rounded-md text-[10px]">
                    {room.roomType === "CONSULTATION"
                      ? "Phòng khám"
                      : room.roomType === "LAB"
                      ? "Phòng XN"
                      : room.roomType === "IMAGING"
                      ? "CĐHA"
                      : "Nhà thuốc"}
                  </span>
                  <button
                    onClick={() => onEditRoom(room)}
                    className="text-[11px] font-bold text-[#6E2582] hover:underline cursor-pointer"
                  >
                    Chỉnh sửa
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Doctors Column */}
      <div className="border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-bold text-[#2B1D30]">Danh bạ Hồ sơ Bác sĩ</h2>
          <span className="text-xs text-purple-700 font-bold">
            {(doctors ?? []).length} bác sĩ
          </span>
        </div>

        {isLoading ? (
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-20 bg-emerald-50/50 rounded-xl animate-pulse" />
            ))}
          </div>
        ) : (doctors ?? []).length === 0 ? (
          <p className="text-xs text-[#6A5C70] py-6 text-center">Chưa có hồ sơ bác sĩ nào</p>
        ) : (
          <div className="space-y-3 text-xs">
            {(doctors ?? []).map((doc) => (
              <div key={doc.id} className="p-3.5 bg-emerald-50/50 border border-emerald-200 rounded-xl space-y-1">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-emerald-950 text-sm">
                    {doc.title ? `${doc.title} ${doc.fullName}` : doc.fullName}
                  </span>
                  <button
                    onClick={() => onEditDoctor(doc)}
                    className="text-[11px] font-bold text-emerald-700 hover:underline cursor-pointer"
                  >
                    Sửa hồ sơ
                  </button>
                </div>
                <p className="text-[#6A5C70] text-[11px]">
                  Chuyên khoa: <span className="font-semibold text-gray-800">{doc.specialization || "Nội tổng quát"}</span>
                </p>
                <p className="text-[#6A5C70] text-[11px]">
                  Khoa: <span className="font-semibold text-gray-800">{doc.departmentName || doc.departmentCode || "Chưa gán"}</span> | Phòng: <span className="font-mono font-bold">{doc.assignedRoomId || "Chưa gán"}</span>
                </p>
                {doc.licenseNumber && (
                  <p className="text-[10px] text-emerald-800 font-mono">
                    Số CCHN: {doc.licenseNumber}
                  </p>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
