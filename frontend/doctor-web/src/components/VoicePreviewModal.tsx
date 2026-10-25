"use client";

import { useState } from "react";
import { VoiceExtractedFields } from "@/lib/ai-api";

interface VoicePreviewModalProps {
  transcript: string;
  fields: VoiceExtractedFields;
  onApply: (selectedFields: Partial<VoiceExtractedFields>) => void;
  onClose: () => void;
}

interface FieldDefinition {
  key: keyof VoiceExtractedFields;
  label: string;
  tabName: string;
  badgeColor: string;
}

const FIELD_DEFINITIONS: FieldDefinition[] = [
  { key: "heartRate", label: "Mạch", tabName: "Sinh hiệu", badgeColor: "bg-blue-50 text-blue-700 border-blue-200" },
  { key: "bloodPressure", label: "Huyết áp", tabName: "Sinh hiệu", badgeColor: "bg-blue-50 text-blue-700 border-blue-200" },
  { key: "temperature", label: "Thân nhiệt", tabName: "Sinh hiệu", badgeColor: "bg-blue-50 text-blue-700 border-blue-200" },
  { key: "spo2", label: "SpO2", tabName: "Sinh hiệu", badgeColor: "bg-blue-50 text-blue-700 border-blue-200" },
  { key: "height", label: "Chiều cao", tabName: "Sinh hiệu", badgeColor: "bg-blue-50 text-blue-700 border-blue-200" },
  { key: "weight", label: "Cân nặng", tabName: "Sinh hiệu", badgeColor: "bg-blue-50 text-blue-700 border-blue-200" },
  { key: "symptoms", label: "Triệu chứng", tabName: "Lâm sàng", badgeColor: "bg-emerald-50 text-emerald-700 border-emerald-200" },
  { key: "clinicalNotes", label: "Diễn biến lâm sàng", tabName: "Lâm sàng", badgeColor: "bg-emerald-50 text-emerald-700 border-emerald-200" },
  { key: "diagnosis", label: "Chẩn đoán", tabName: "Chẩn đoán", badgeColor: "bg-purple-50 text-purple-700 border-purple-200" },
  { key: "icd10Code", label: "Mã ICD-10", tabName: "Chẩn đoán", badgeColor: "bg-purple-50 text-purple-700 border-purple-200" },
  { key: "icd10Name", label: "Tên bệnh ICD-10", tabName: "Chẩn đoán", badgeColor: "bg-purple-50 text-purple-700 border-purple-200" },
];

export default function VoicePreviewModal({
  transcript,
  fields,
  onApply,
  onClose,
}: VoicePreviewModalProps) {
  // Only present fields that actually contain a non-empty value
  const availableFields = FIELD_DEFINITIONS.filter(
    (def) => fields[def.key] !== undefined && fields[def.key] !== null && String(fields[def.key]).trim() !== ""
  );

  const [selectedKeys, setSelectedKeys] = useState<Set<keyof VoiceExtractedFields>>(
    new Set(availableFields.map((f) => f.key))
  );

  const toggleField = (key: keyof VoiceExtractedFields) => {
    setSelectedKeys((prev) => {
      const next = new Set(prev);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  };

  const selectAll = () => {
    setSelectedKeys(new Set(availableFields.map((f) => f.key)));
  };

  const deselectAll = () => {
    setSelectedKeys(new Set());
  };

  const handleApply = () => {
    const payload: Partial<VoiceExtractedFields> = {};
    selectedKeys.forEach((key) => {
      payload[key] = fields[key];
    });
    onApply(payload);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4 animate-in fade-in duration-150">
      <div className="bg-white rounded-xl shadow-2xl border border-purple-100 w-full max-w-xl flex flex-col max-h-[90vh] overflow-hidden">
        {/* Modal Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100 bg-gradient-to-r from-purple-50/50 to-white">
          <div className="flex items-center gap-2.5">
            <span className="text-xl">🎙️</span>
            <div>
              <h2 className="font-bold text-sm text-[#2B1D30]">Kết quả nhận dạng & Bóc tách bệnh án</h2>
              <p className="text-[11px] text-[#6A5C70]">Rà soát thông tin trước khi áp dụng vào ca khám</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-7 h-7 flex items-center justify-center rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
          >
            ✕
          </button>
        </div>

        {/* Modal Body */}
        <div className="overflow-y-auto flex-1 px-5 py-4 space-y-4">
          {/* Transcript box */}
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-[11px] font-bold text-[#6A5C70] uppercase tracking-wider">
                Văn bản phiên âm gốc
              </span>
              <span className="text-[10px] text-purple-600 font-medium">ElevenLabs STT (vie)</span>
            </div>
            <div className="bg-slate-50 border border-slate-200 rounded-lg p-3 text-xs text-slate-700 leading-relaxed italic max-h-24 overflow-y-auto">
              {transcript || "(Không có nội dung phiên âm)"}
            </div>
          </div>

          {/* Extracted fields list */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <span className="text-[11px] font-bold text-[#6A5C70] uppercase tracking-wider">
                Dữ liệu AI đã bóc tách ({selectedKeys.size}/{availableFields.length} trường đã chọn)
              </span>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={selectAll}
                  className="text-[11px] text-purple-700 hover:underline font-medium"
                >
                  Chọn tất cả
                </button>
                <span className="text-gray-300">|</span>
                <button
                  type="button"
                  onClick={deselectAll}
                  className="text-[11px] text-gray-500 hover:underline font-medium"
                >
                  Bỏ chọn
                </button>
              </div>
            </div>

            {availableFields.length === 0 ? (
              <div className="text-center py-6 border border-dashed border-gray-200 rounded-lg text-xs text-gray-500">
                AI không nhận diện được trường thông tin lâm sàng nào từ đoạn thoại.
              </div>
            ) : (
              <div className="space-y-2 max-h-64 overflow-y-auto pr-1">
                {availableFields.map((def) => {
                  const isChecked = selectedKeys.has(def.key);
                  const val = String(fields[def.key]);
                  return (
                    <label
                      key={def.key}
                      onClick={() => toggleField(def.key)}
                      className={`flex items-start gap-3 p-2.5 rounded-lg border transition-all cursor-pointer select-none ${
                        isChecked
                          ? "bg-purple-50/40 border-purple-200 shadow-sm"
                          : "bg-white border-gray-100 hover:bg-gray-50 opacity-70"
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={isChecked}
                        onChange={() => {}} // Controlled via container click
                        className="mt-0.5 h-4 w-4 rounded accent-[#6E2582] text-white cursor-pointer"
                      />
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-0.5">
                          <span
                            className={`text-[9px] font-bold px-1.5 py-0.5 rounded border uppercase tracking-wider ${def.badgeColor}`}
                          >
                            {def.tabName}
                          </span>
                          <span className="text-xs font-semibold text-[#2B1D30]">{def.label}</span>
                        </div>
                        <p className="text-xs text-slate-800 font-medium break-words">{val}</p>
                      </div>
                    </label>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* Modal Footer */}
        <div className="flex items-center justify-between gap-3 px-5 py-3.5 border-t border-gray-100 bg-gray-50/60">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-xs font-semibold text-[#6A5C70] hover:text-rose-700 hover:bg-rose-50 rounded-lg border border-transparent hover:border-rose-200 transition-colors cursor-pointer"
          >
            Hủy
          </button>
          <button
            type="button"
            onClick={handleApply}
            disabled={selectedKeys.size === 0}
            className="px-5 py-2 text-xs font-bold text-white bg-[#6E2582] hover:bg-[#5a1e6e] rounded-lg shadow-sm disabled:opacity-40 disabled:cursor-not-allowed transition-all cursor-pointer"
          >
            Áp dụng
          </button>
        </div>
      </div>
    </div>
  );
}
