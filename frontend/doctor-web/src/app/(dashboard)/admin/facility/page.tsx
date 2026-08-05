"use client";

import { useState } from "react";

interface DepartmentItem {
  code: string;
  name: string;
  rooms: string[];
}

interface ServicePointItem {
  id: string;
  name: string;
  category: string;
}

const INITIAL_DEPARTMENTS: DepartmentItem[] = [
  { code: "INTERNAL", name: "Khoa Nội tổng quát", rooms: ["Phòng khám Nội 01", "Phòng khám Nội 02"] },
  { code: "PEDIATRICS", name: "Khoa Nhi", rooms: ["Phòng khám Nhi 01"] },
  { code: "SURGERY", name: "Khoa Ngoại", rooms: ["Phòng khám Ngoại 01"] },
];

const INITIAL_SERVICE_POINTS: ServicePointItem[] = [
  { id: "LAB-HEMATOLOGY-01", name: "Phòng Xét nghiệm Huyết học 101", category: "HEMATOLOGY" },
  { id: "LAB-BIOCHEM-01", name: "Phòng Xét nghiệm Sinh hóa 102", category: "BIOCHEMISTRY" },
  { id: "US-ROOM-01", name: "Phòng Siêu âm 201", category: "ULTRASOUND" },
  { id: "XRAY-ROOM-01", name: "Phòng X-Quang 202", category: "XRAY" },
];

export default function AdminFacilityPage() {
  const [departments] = useState<DepartmentItem[]>(INITIAL_DEPARTMENTS);
  const [servicePoints] = useState<ServicePointItem[]>(INITIAL_SERVICE_POINTS);

  return (
    <div className="space-y-6 pb-12">
      {/* Header */}
      <div className="border border-card-border bg-card-bg p-5 rounded-xl shadow-sm">
        <div className="flex items-center gap-2">
          <span className="inline-block w-2.5 h-2.5 rounded-full bg-purple-500 animate-pulse" />
          <span className="text-xs font-bold uppercase tracking-wider text-purple-600">
            Quản trị Cấu hình Bệnh viện
          </span>
        </div>
        <h1 className="text-2xl font-extrabold text-[#2B1D30] mt-1">Cấu hình Cơ sở Y tế & Điểm phục vụ</h1>
        <p className="text-xs text-[#6A5C70] mt-0.5">
          Quản lý Khoa chuyên môn, Danh sách Phòng khám (ClinicRoom) và Các điểm phục vụ Cận lâm sàng (ServicePoint)
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Departments & Rooms */}
        <div className="border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-[#2B1D30]">Danh mục Khoa & Phòng khám</h2>
            <span className="text-xs text-purple-700 font-bold">{departments.length} khoa</span>
          </div>

          <div className="space-y-3 text-xs">
            {departments.map(dept => (
              <div key={dept.code} className="p-4 bg-purple-50/60 border border-purple-200 rounded-xl space-y-2">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-purple-950 text-sm">{dept.name}</span>
                  <span className="px-2 py-0.5 bg-purple-200 text-purple-800 font-bold text-[10px] rounded-md">
                    {dept.code}
                  </span>
                </div>
                <div>
                  <p className="text-[#6A5C70] font-semibold mb-1">Các phòng khám trực thuộc:</p>
                  <div className="flex flex-wrap gap-1.5">
                    {dept.rooms.map((r, idx) => (
                      <span key={idx} className="px-2.5 py-1 bg-white border border-purple-200 text-purple-900 rounded-lg text-xs font-semibold">
                        {r}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Service Points */}
        <div className="border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-[#2B1D30]">Các Điểm phục vụ Cận lâm sàng</h2>
            <span className="text-xs text-purple-700 font-bold">{servicePoints.length} điểm</span>
          </div>

          <div className="space-y-3 text-xs">
            {servicePoints.map(sp => (
              <div key={sp.id} className="p-4 bg-gray-50 border border-card-border rounded-xl flex items-center justify-between">
                <div>
                  <p className="font-bold text-[#2B1D30] text-sm">{sp.name}</p>
                  <p className="text-[#6A5C70] text-[11px] mt-0.5">Mã điểm phục vụ: {sp.id}</p>
                </div>
                <span className="px-2.5 py-1 bg-purple-100 text-purple-800 font-bold rounded-full text-[10px]">
                  {sp.category}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
