"use client";

import { useState } from "react";
import {
  DepartmentItem,
  directoryApi,
  DoctorProfileItem,
  RoomItem,
} from "@/lib/directory-api";
import FacilitySelectBox from "./FacilitySelectBox";

interface DoctorProfileFormModalProps {
  editingDoctor: DoctorProfileItem | null;
  departments: DepartmentItem[];
  rooms: RoomItem[];
  defaultLicense: string;
  onClose: () => void;
  onSaved: () => Promise<void>;
}

export default function DoctorProfileFormModal({
  editingDoctor,
  departments,
  rooms,
  defaultLicense,
  onClose,
  onSaved,
}: DoctorProfileFormModalProps) {
  const [docUserId, setDocUserId] = useState(editingDoctor?.userId ?? "");
  const [docFullName, setDocFullName] = useState(editingDoctor?.fullName ?? "");
  const [docTitle, setDocTitle] = useState(editingDoctor?.title || (editingDoctor ? "BS" : "BS. CKI"));
  const [docDeptCode, setDocDeptCode] = useState(
    editingDoctor?.departmentCode ?? (departments[0]?.code || "")
  );
  const [docRoomId, setDocRoomId] = useState(editingDoctor?.assignedRoomId ?? "");
  const [docSpec, setDocSpec] = useState(
    editingDoctor ? editingDoctor.specialization || "" : "Nội tổng quát"
  );
  const [docLicense, setDocLicense] = useState(
    editingDoctor ? editingDoctor.licenseNumber || "" : defaultLicense
  );
  const [docPrimary, setDocPrimary] = useState(editingDoctor?.primaryForBooking === true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const handleSaveDoctor = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!docFullName.trim() || (!editingDoctor && !docUserId.trim())) return;
    setIsSubmitting(true);
    setFormError(null);
    try {
      if (editingDoctor) {
        await directoryApi.updateDoctorProfile(editingDoctor.id, {
          fullName: docFullName.trim(),
          title: docTitle.trim(),
          departmentCode: docDeptCode,
          assignedRoomId: docRoomId.trim(),
          specialization: docSpec.trim(),
          licenseNumber: docLicense.trim(),
          primaryForBooking: docPrimary,
        });
      } else {
        await directoryApi.createDoctorProfile({
          userId: docUserId.trim(),
          fullName: docFullName.trim(),
          title: docTitle.trim(),
          departmentCode: docDeptCode,
          assignedRoomId: docRoomId.trim(),
          specialization: docSpec.trim(),
          licenseNumber: docLicense.trim(),
          primaryForBooking: docPrimary,
        });
      }
      onClose();
      await onSaved();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Thao tác lưu hồ sơ bác sĩ thất bại.";
      setFormError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl p-6 w-full max-w-md shadow-2xl space-y-4">
        <h3 className="text-lg font-bold text-[#2B1D30]">
          {editingDoctor ? "Chỉnh sửa Hồ sơ Bác sĩ" : "Gán Hồ sơ Bác sĩ mới (Sau khi tạo Account)"}
        </h3>

        {formError && (
          <div className="p-3 bg-rose-50 border border-rose-200 text-rose-700 rounded-lg text-xs font-semibold">
            {formError}
          </div>
        )}

        <form onSubmit={handleSaveDoctor} className="space-y-3 text-xs">
          <div>
            <label className="block font-bold text-[#2B1D30] mb-1">Mã Tài khoản (User ID từ Identity)</label>
            <input
              type="text"
              disabled={!!editingDoctor}
              value={docUserId}
              onChange={(e) => setDocUserId(e.target.value)}
              placeholder="VD: d0000001-0000-0000-0000-000000000001"
              className="w-full p-2.5 border border-card-border rounded-lg bg-gray-50 focus:ring-2 focus:ring-purple-500 font-mono text-[11px] disabled:opacity-60"
              required
            />
          </div>
          <div>
            <label className="block font-bold text-[#2B1D30] mb-1">Họ và Tên Bác sĩ</label>
            <input
              type="text"
              value={docFullName}
              onChange={(e) => setDocFullName(e.target.value)}
              placeholder="VD: Nguyễn Văn An"
              className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500"
              required
            />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="block font-bold text-[#2B1D30] mb-1">Học hàm / Học vị</label>
              <input
                type="text"
                value={docTitle}
                onChange={(e) => setDocTitle(e.target.value)}
                placeholder="VD: BS. CKI"
                className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500"
              />
            </div>
            <div>
              <label className="block font-bold text-[#2B1D30] mb-1">Số CCHN</label>
              <input
                type="text"
                value={docLicense}
                onChange={(e) => setDocLicense(e.target.value)}
                placeholder="VD: CCHN-012345"
                className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500 font-mono"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <FacilitySelectBox
              label="Khoa làm việc"
              value={docDeptCode}
              onChange={setDocDeptCode}
              placeholder="-- Chọn khoa --"
              options={[
                { value: "", label: "-- Chưa gán khoa --" },
                ...departments.map((d) => ({
                  value: d.code,
                  label: d.name,
                  subLabel: d.code,
                })),
              ]}
            />
            <FacilitySelectBox
              label="Phòng khám gán"
              value={docRoomId}
              onChange={setDocRoomId}
              placeholder="-- Chọn phòng --"
              options={[
                { value: "", label: "-- Chưa gán phòng --" },
                ...rooms.map((r) => ({
                  value: r.id,
                  label: r.displayName,
                  subLabel: r.id,
                })),
              ]}
            />
          </div>
          <div>
            <label className="block font-bold text-[#2B1D30] mb-1">Chuyên khoa sâu</label>
            <input
              type="text"
              value={docSpec}
              onChange={(e) => setDocSpec(e.target.value)}
              placeholder="VD: Tim mạch, Nội tiết..."
              className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500"
            />
          </div>

          <label className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              checked={docPrimary}
              onChange={(e) => setDocPrimary(e.target.checked)}
            />
            Bác sĩ nhận lịch mặc định của phòng. Khi có nhiều bác sĩ cùng phòng, chỉ chọn một người; bỏ chọn người cũ trước khi đổi.
          </label>
          <div className="flex items-center justify-end gap-2 pt-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-xl text-xs font-bold text-gray-600 hover:bg-gray-100 cursor-pointer"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-4 py-2 rounded-xl text-xs font-bold text-white bg-emerald-700 hover:bg-emerald-800 shadow-md cursor-pointer disabled:opacity-50"
            >
              {isSubmitting ? "Đang lưu..." : "Lưu hồ sơ"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
