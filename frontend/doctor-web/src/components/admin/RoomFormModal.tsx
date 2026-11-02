"use client";

import { useState } from "react";
import { DepartmentItem, directoryApi, RoomItem } from "@/lib/directory-api";
import FacilitySelectBox from "./FacilitySelectBox";

const ROOM_TYPE_OPTIONS = [
  { value: "CONSULTATION", label: "Phòng khám Lâm sàng" },
  { value: "LAB", label: "Phòng Xét nghiệm" },
  { value: "IMAGING", label: "Chẩn đoán Hình ảnh" },
  { value: "PHARMACY", label: "Quầy Phát thuốc" },
];

interface RoomFormModalProps {
  editingRoom: RoomItem | null;
  departments: DepartmentItem[];
  onClose: () => void;
  onSaved: () => Promise<void>;
}

export default function RoomFormModal({
  editingRoom,
  departments,
  onClose,
  onSaved,
}: RoomFormModalProps) {
  const [roomId, setRoomId] = useState(editingRoom?.id ?? "");
  const [roomDeptCode, setRoomDeptCode] = useState(
    editingRoom?.departmentCode ?? (departments[0]?.code || "")
  );
  const [roomDisplayName, setRoomDisplayName] = useState(editingRoom?.displayName ?? "");
  const [roomType, setRoomType] = useState(editingRoom?.roomType ?? "CONSULTATION");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const handleSaveRoom = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!roomDisplayName.trim() || !roomDeptCode) return;
    setIsSubmitting(true);
    setFormError(null);
    try {
      if (editingRoom) {
        await directoryApi.updateRoom(editingRoom.id, {
          departmentCode: roomDeptCode,
          displayName: roomDisplayName.trim(),
          roomType: roomType,
        });
      } else {
        await directoryApi.createRoom({
          id: roomId.trim().toUpperCase(),
          departmentCode: roomDeptCode,
          displayName: roomDisplayName.trim(),
          roomType: roomType,
        });
      }
      onClose();
      await onSaved();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Thao tác lưu phòng thất bại.";
      setFormError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl p-6 w-full max-w-md shadow-2xl space-y-4">
        <h3 className="text-lg font-bold text-[#2B1D30]">
          {editingRoom ? "Chỉnh sửa Phòng" : "Thêm Phòng mới"}
        </h3>

        {formError && (
          <div className="p-3 bg-rose-50 border border-rose-200 text-rose-700 rounded-lg text-xs font-semibold">
            {formError}
          </div>
        )}

        <form onSubmit={handleSaveRoom} className="space-y-3 text-xs">
          <div>
            <label className="block font-bold text-[#2B1D30] mb-1">Mã Phòng</label>
            <input
              type="text"
              disabled={!!editingRoom}
              value={roomId}
              onChange={(e) => setRoomId(e.target.value)}
              placeholder="VD: ROOM-01, LAB-01"
              className="w-full p-2.5 border border-card-border rounded-lg bg-gray-50 focus:ring-2 focus:ring-purple-500 uppercase font-mono disabled:opacity-60"
              required
            />
          </div>

          <FacilitySelectBox
            label="Khoa trực thuộc"
            value={roomDeptCode}
            onChange={setRoomDeptCode}
            placeholder="-- Chọn khoa trực thuộc --"
            options={departments.map((d) => ({
              value: d.code,
              label: d.name,
              subLabel: d.code,
            }))}
          />

          <div>
            <label className="block font-bold text-[#2B1D30] mb-1">Tên phòng hiển thị</label>
            <input
              type="text"
              value={roomDisplayName}
              onChange={(e) => setRoomDisplayName(e.target.value)}
              placeholder="VD: Phòng khám Nội 01"
              className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500"
              required
            />
          </div>

          <FacilitySelectBox
            label="Loại điểm phục vụ"
            value={roomType}
            onChange={setRoomType}
            options={ROOM_TYPE_OPTIONS}
            searchable={false}
          />

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
              className="px-4 py-2 rounded-xl text-xs font-bold text-white bg-[#6E2582] hover:bg-[#561A66] shadow-md cursor-pointer disabled:opacity-50"
            >
              {isSubmitting ? "Đang lưu..." : "Lưu lại"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
