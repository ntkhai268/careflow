"use client";

import { useState } from "react";
import { DepartmentItem, directoryApi } from "@/lib/directory-api";

interface DepartmentFormModalProps {
  editingDept: DepartmentItem | null;
  onClose: () => void;
  onSaved: () => Promise<void>;
}

export default function DepartmentFormModal({
  editingDept,
  onClose,
  onSaved,
}: DepartmentFormModalProps) {
  const [deptCode, setDeptCode] = useState(editingDept?.code ?? "");
  const [deptName, setDeptName] = useState(editingDept?.name ?? "");
  const [deptDesc, setDeptDesc] = useState(editingDept?.description ?? "");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const handleSaveDept = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!deptName.trim()) return;
    setIsSubmitting(true);
    setFormError(null);
    try {
      if (editingDept) {
        await directoryApi.updateDepartment(editingDept.code, {
          name: deptName.trim(),
          description: deptDesc.trim(),
        });
      } else {
        await directoryApi.createDepartment({
          code: deptCode.trim().toUpperCase(),
          name: deptName.trim(),
          description: deptDesc.trim(),
        });
      }
      onClose();
      await onSaved();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Thao tác lưu khoa thất bại.";
      setFormError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl p-6 w-full max-w-md shadow-2xl space-y-4">
        <h3 className="text-lg font-bold text-[#2B1D30]">
          {editingDept ? "Chỉnh sửa Khoa chuyên môn" : "Thêm Khoa chuyên môn mới"}
        </h3>

        {formError && (
          <div className="p-3 bg-rose-50 border border-rose-200 text-rose-700 rounded-lg text-xs font-semibold">
            {formError}
          </div>
        )}

        <form onSubmit={handleSaveDept} className="space-y-3 text-xs">
          <div>
            <label className="block font-bold text-[#2B1D30] mb-1">Mã Khoa</label>
            <input
              type="text"
              disabled={!!editingDept}
              value={deptCode}
              onChange={(e) => setDeptCode(e.target.value)}
              placeholder="VD: INTERNAL, PEDIATRICS"
              className="w-full p-2.5 border border-card-border rounded-lg bg-gray-50 focus:ring-2 focus:ring-purple-500 uppercase font-mono disabled:opacity-60"
              required
            />
          </div>
          <div>
            <label className="block font-bold text-[#2B1D30] mb-1">Tên Khoa chuyên môn</label>
            <input
              type="text"
              value={deptName}
              onChange={(e) => setDeptName(e.target.value)}
              placeholder="VD: Khoa Nội tổng quát"
              className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500"
              required
            />
          </div>
          <div>
            <label className="block font-bold text-[#2B1D30] mb-1">Mô tả / Ghi chú</label>
            <textarea
              value={deptDesc}
              onChange={(e) => setDeptDesc(e.target.value)}
              placeholder="Mô tả nhiệm vụ khoa..."
              rows={2}
              className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg focus:ring-2 focus:ring-purple-500"
            />
          </div>

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
