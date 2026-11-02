"use client";

import { useEffect, useRef, useState } from "react";
import { ICD10_CATALOG } from "./consultation-constants";

interface DiagnosisTabProps {
  icd10Code: string;
  setIcd10Code: (val: string) => void;
  icd10Name: string;
  setIcd10Name: (val: string) => void;
  diagnosis: string;
  setDiagnosis: (val: string) => void;
  isConsultationLocked: boolean;
}

export default function DiagnosisTab({
  icd10Code,
  setIcd10Code,
  icd10Name,
  setIcd10Name,
  diagnosis,
  setDiagnosis,
  isConsultationLocked,
}: DiagnosisTabProps) {
  const [icdSearchQuery, setIcdSearchQuery] = useState("");
  const [showIcdDropdown, setShowIcdDropdown] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setShowIcdDropdown(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const filteredIcd10 = ICD10_CATALOG.filter(
    (item) =>
      item.code.toLowerCase().includes(icdSearchQuery.toLowerCase()) ||
      item.name.toLowerCase().includes(icdSearchQuery.toLowerCase())
  );

  const selectIcd10 = (code: string, name: string) => {
    setIcd10Code(code);
    setIcd10Name(name);
    setDiagnosis(name);
    setIcdSearchQuery("");
    setShowIcdDropdown(false);
  };

  return (
    <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] rounded-b-lg space-y-4">
      <h2 className="text-xs font-bold text-[#2B1D30] border-b border-card-border pb-2 uppercase tracking-wide">
        Chẩn đoán bệnh theo chuẩn y tế
      </h2>

      {!isConsultationLocked && (
        <div ref={dropdownRef} className="relative">
          <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Tra cứu Mã ICD-10</label>
          <div className="flex gap-2">
            <input
              type="text"
              value={icdSearchQuery}
              onChange={(e) => {
                setIcdSearchQuery(e.target.value);
                setShowIcdDropdown(true);
              }}
              onFocus={() => setShowIcdDropdown(true)}
              placeholder="Nhập mã hoặc tên bệnh (ví dụ: K21, Tăng huyết áp...)"
              className="flex-1 border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-md"
            />
            {icdSearchQuery && (
              <button
                type="button"
                onClick={() => {
                  setIcdSearchQuery("");
                  setShowIcdDropdown(false);
                }}
                className="border border-card-border px-3 text-xs hover:bg-[#F8F6F9] rounded-md"
              >
                Xóa
              </button>
            )}
          </div>

          {showIcdDropdown && filteredIcd10.length > 0 && (
            <div className="absolute left-0 right-0 z-20 mt-1 border border-card-border bg-card-bg shadow-md max-h-48 overflow-y-auto rounded-md">
              {filteredIcd10.map((item) => (
                <div
                  key={item.code}
                  onClick={() => selectIcd10(item.code, item.name)}
                  className="px-4 py-2 text-sm hover:bg-[#F3E8F5] cursor-pointer text-[#2B1D30] flex justify-between"
                >
                  <span className="font-mono font-bold text-primary-600">{item.code}</span>
                  <span className="text-[#6A5C70] truncate max-w-xs">{item.name}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="sm:col-span-1">
          <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Mã ICD-10 đã chọn</label>
          <input
            type="text"
            readOnly
            value={icd10Code}
            placeholder="Chưa chọn"
            className="w-full border border-card-border bg-gray-50 px-3 py-2 text-sm text-[#2B1D30] font-mono font-bold focus:outline-none rounded-md"
          />
        </div>
        <div className="sm:col-span-2">
          <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Tên bệnh theo ICD</label>
          <input
            type="text"
            readOnly
            value={icd10Name}
            placeholder="Chưa chọn"
            className="w-full border border-card-border bg-gray-50 px-3 py-2 text-sm text-[#6A5C70] focus:outline-none rounded-md"
          />
        </div>
      </div>

      <div>
        <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Chẩn đoán chi tiết của bác sĩ</label>
        <textarea
          disabled={isConsultationLocked}
          value={diagnosis}
          onChange={(e) => setDiagnosis(e.target.value)}
          rows={3}
          placeholder="Nhập chẩn đoán lâm sàng chi tiết..."
          className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-md"
        />
      </div>
    </div>
  );
}
