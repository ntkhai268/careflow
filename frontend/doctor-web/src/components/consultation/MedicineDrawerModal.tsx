"use client";

import { useEffect, useRef } from "react";
import PrescriptionDetailsEditor from "@/components/PrescriptionDetailsEditor";
import { MedicineCatalogItem } from "@/lib/prescription-api";

interface MedicineDrawerModalProps {
  isOpen: boolean;
  onClose: () => void;
  editingMedicineIndex: number | null;
  medicines: MedicineCatalogItem[];
  medSearchQuery: string;
  setMedSearchQuery: (val: string) => void;
  showMedDropdown: boolean;
  setShowMedDropdown: (val: boolean) => void;
  selectedMedicine: MedicineCatalogItem | null;
  setSelectedMedicine: (med: MedicineCatalogItem | null) => void;
  medDosage: string;
  setMedDosage: (val: string) => void;
  medFrequency: string;
  setMedFrequency: (val: string) => void;
  medTiming: string;
  setMedTiming: (val: string) => void;
  medDuration: string;
  setMedDuration: (val: string) => void;
  medQuantity: string;
  setMedQuantity: (val: string) => void;
  medDetails: Record<string, string>;
  setMedDetails: (val: Record<string, string>) => void;
  showMedNotesInput: boolean;
  setShowMedNotesInput: (val: boolean) => void;
  medNotes: string;
  setMedNotes: (val: string) => void;
  saveMedicineToPrescription: () => void;
}

export default function MedicineDrawerModal({
  isOpen,
  onClose,
  editingMedicineIndex,
  medicines,
  medSearchQuery,
  setMedSearchQuery,
  showMedDropdown,
  setShowMedDropdown,
  selectedMedicine,
  setSelectedMedicine,
  medDosage,
  setMedDosage,
  medFrequency,
  setMedFrequency,
  medTiming,
  setMedTiming,
  medDuration,
  setMedDuration,
  medQuantity,
  setMedQuantity,
  medDetails,
  setMedDetails,
  showMedNotesInput,
  setShowMedNotesInput,
  medNotes,
  setMedNotes,
  saveMedicineToPrescription,
}: MedicineDrawerModalProps) {
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setShowMedDropdown(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [setShowMedDropdown]);

  if (!isOpen) return null;

  const filteredMedicines = medicines.filter(
    (m) =>
      m.name.toLowerCase().includes(medSearchQuery.toLowerCase()) ||
      m.code.toLowerCase().includes(medSearchQuery.toLowerCase())
  );

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="medicine-drawer-title"
      className="fixed inset-0 z-50 flex justify-end bg-black/50 backdrop-blur-xs"
    >
      <div className="w-full max-w-lg min-h-0 bg-card-bg border-l border-card-border shadow-2xl h-dvh flex flex-col p-6 animate-in slide-in-from-right duration-200 rounded-l-xl">
        <div className="shrink-0 flex justify-between items-center border-b border-card-border pb-3">
          <h3 id="medicine-drawer-title" className="text-base font-bold text-[#2B1D30] uppercase tracking-wide">
            {editingMedicineIndex !== null ? "Cập nhật thông tin thuốc" : "Thêm thuốc vào đơn kê"}
          </h3>
          <button
            type="button"
            onClick={onClose}
            className="text-gray-400 hover:text-black font-bold text-sm px-2"
          >
            X
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto py-4 pr-1 space-y-5">
          <div ref={dropdownRef} className="relative z-40">
            <label className="block text-xs font-bold text-[#2B1D30] mb-1">
              Tìm kiếm &amp; Chọn thuốc (*)
            </label>
            <input
              type="text"
              value={medSearchQuery}
              onChange={(e) => {
                setMedSearchQuery(e.target.value);
                setShowMedDropdown(true);
              }}
              onFocus={() => setShowMedDropdown(true)}
              placeholder="Nhập tên thuốc hoặc mã thuốc..."
              className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-md"
            />

            {showMedDropdown && filteredMedicines.length > 0 && (
              <div className="absolute left-0 right-0 top-full z-50 mt-1 border border-card-border bg-white shadow-2xl max-h-60 overflow-y-auto rounded-md divide-y divide-gray-100">
                {filteredMedicines.map((m) => (
                  <div
                    key={m.code}
                    onClick={() => {
                      setSelectedMedicine(m);
                      setMedSearchQuery(m.name);
                      setShowMedDropdown(false);
                    }}
                    className="px-4 py-2.5 hover:bg-[#F3E8F5] cursor-pointer text-sm flex justify-between items-center transition-colors"
                  >
                    <div>
                      <p className="font-bold text-[#2B1D30]">{m.name}</p>
                      <p className="text-xs text-[#6A5C70]">
                        Mã: <span className="font-mono">{m.code}</span> | Đơn vị: {m.unit}
                      </p>
                    </div>
                    <span className="text-[10px] bg-primary-100 text-primary-700 px-2 py-0.5 font-semibold rounded-sm">
                      Khả dụng
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>

          {selectedMedicine && (
            <div className="border border-primary-200 bg-[#F8F6F9] p-3 text-xs space-y-1 rounded-md">
              <p className="font-bold text-primary-700">{selectedMedicine.name}</p>
              <p className="text-[#6A5C70]">
                Mã thuốc chuẩn: <span className="font-mono font-bold">{selectedMedicine.code}</span> | Đơn vị tính:{" "}
                <strong>{selectedMedicine.unit}</strong>
              </p>
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Liều dùng (*)</label>
              <input
                type="text"
                value={medDosage}
                onChange={(e) => setMedDosage(e.target.value)}
                placeholder="1 viên / lần"
                className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-md"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Tần suất (*)</label>
              <input
                type="text"
                value={medFrequency}
                onChange={(e) => setMedFrequency(e.target.value)}
                placeholder="2 lần / ngày"
                className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-md"
              />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Thời điểm</label>
              <input
                type="text"
                value={medTiming}
                onChange={(e) => setMedTiming(e.target.value)}
                placeholder="Sau khi ăn"
                className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-md"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Số ngày</label>
              <input
                type="number"
                value={medDuration}
                onChange={(e) => setMedDuration(e.target.value)}
                className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-md"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Số lượng (*)</label>
              <input
                type="number"
                value={medQuantity}
                onChange={(e) => setMedQuantity(e.target.value)}
                className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-md"
              />
            </div>
          </div>

          <PrescriptionDetailsEditor value={medDetails} onChange={setMedDetails} />

          <div>
            {!showMedNotesInput && !medNotes ? (
              <button
                type="button"
                onClick={() => setShowMedNotesInput(true)}
                className="text-xs font-semibold text-primary-600 hover:underline"
              >
                + Thêm ghi chú/lưu ý dùng thuốc này
              </button>
            ) : (
              <div>
                <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Ghi chú uống thuốc</label>
                <input
                  type="text"
                  value={medNotes}
                  onChange={(e) => setMedNotes(e.target.value)}
                  placeholder="Ví dụ: Uống với nhiều nước, tránh uống cùng sữa..."
                  className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-md"
                />
              </div>
            )}
          </div>
        </div>

        <div className="shrink-0 pt-4 border-t border-card-border flex gap-3">
          <button
            type="button"
            onClick={onClose}
            className="flex-1 border border-card-border hover:bg-gray-100 text-[#2B1D30] font-bold py-2.5 text-xs transition-all rounded-md"
          >
            HỦY BỎ
          </button>
          <button
            type="button"
            onClick={saveMedicineToPrescription}
            className="flex-1 bg-primary-600 hover:bg-primary-700 text-white font-bold py-2.5 text-xs transition-all rounded-md shadow-sm"
          >
            {editingMedicineIndex !== null ? "LƯU CẬP NHẬT" : "XÁC NHẬN THÊM"}
          </button>
        </div>
      </div>
    </div>
  );
}
