"use client";

import ClinicalCitations from "@/components/ClinicalCitations";
import { ClinicalSuggestionResponse } from "@/lib/ai-api";
import { FormattedAiText } from "@/lib/format-ai-text";
import { PrescriptionItemRequest } from "@/lib/prescription-api";

interface AiClinicalTabProps {
  aiLoading: boolean;
  aiResponse: ClinicalSuggestionResponse | null;
  aiQueryInput: string;
  setAiQueryInput: (val: string) => void;
  handleGetAiSuggestions: (promptQuestion?: string) => Promise<void>;
  prescriptionItems: PrescriptionItemRequest[];
  onApplyDiagnosisSuggestion: (text: string) => void;
}

export default function AiClinicalTab({
  aiLoading,
  aiResponse,
  aiQueryInput,
  setAiQueryInput,
  handleGetAiSuggestions,
  prescriptionItems,
  onApplyDiagnosisSuggestion,
}: AiClinicalTabProps) {
  return (
    <div className="border border-card-border bg-card-bg p-5 rounded-b-lg space-y-4">
      <div className="flex items-center justify-between pb-3 border-b border-card-border">
        <div>
          <h3 className="font-bold text-xs text-[#2B1D30] tracking-wide">
            Trợ lý Lâm sàng (Phác đồ điều trị Bộ Y Tế)
          </h3>
          <p className="text-[10px] text-[#6A5C70]">
            Truy hồi tri thức phác đồ BYT &amp; Dược thư Quốc gia (RAG Hybrid Search)
          </p>
        </div>
        <span className="text-[10px] bg-purple-50 text-[#6E2582] border border-purple-200 px-2 py-0.5 rounded font-mono font-medium">
          BYT Guidelines &amp; Gemini 2.5 Flash
        </span>
      </div>

      <div className="space-y-2.5 bg-slate-50 p-3 rounded-lg border border-card-border">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void handleGetAiSuggestions();
          }}
          className="flex gap-2"
        >
          <input
            type="text"
            value={aiQueryInput}
            onChange={(e) => setAiQueryInput(e.target.value)}
            placeholder="Nhập câu hỏi lâm sàng, biệt dược, liều dùng hoặc tra cứu phác đồ..."
            className="flex-1 px-3 py-2 border border-card-border rounded-md text-xs bg-white text-[#2B1D30] placeholder-[#6A5C70] focus:outline-none focus:border-[#6E2582]"
          />
          <button
            type="submit"
            disabled={aiLoading || !aiQueryInput.trim()}
            className="px-4 py-2 bg-[#6E2582] hover:bg-[#561A66] text-white text-xs font-semibold rounded-md transition-all disabled:opacity-40 cursor-pointer"
          >
            Tra cứu
          </button>
        </form>

        <div className="flex flex-wrap items-center gap-1.5 pt-1">
          <span className="text-[10px] font-semibold text-[#6A5C70] mr-1">Gợi ý nhanh:</span>
          <button
            type="button"
            onClick={() =>
              void handleGetAiSuggestions(
                "Gợi ý các khả năng chẩn đoán phân biệt cần cân nhắc theo phác đồ"
              )
            }
            disabled={aiLoading}
            className="px-2.5 py-1 bg-white hover:bg-purple-50 text-slate-700 hover:text-[#6E2582] border border-card-border text-[11px] font-medium rounded-md transition-all cursor-pointer"
          >
            Chẩn đoán phân biệt
          </button>
          <button
            type="button"
            onClick={() =>
              void handleGetAiSuggestions(
                "Kiểm tra tương tác thuốc DDI từ Dược thư Quốc gia và yếu tố dị ứng"
              )
            }
            disabled={aiLoading}
            className="px-2.5 py-1 bg-white hover:bg-rose-50 text-slate-700 hover:text-rose-700 border border-card-border text-[11px] font-medium rounded-md transition-all cursor-pointer"
          >
            Cảnh báo tương tác DDI
          </button>
          <button
            type="button"
            onClick={() =>
              void handleGetAiSuggestions("Kiểm tra dữ liệu lâm sàng và tiền sử còn thiếu")
            }
            disabled={aiLoading}
            className="px-2.5 py-1 bg-white hover:bg-amber-50 text-slate-700 hover:text-amber-800 border border-card-border text-[11px] font-medium rounded-md transition-all cursor-pointer"
          >
            Dữ liệu còn thiếu
          </button>
          <button
            type="button"
            onClick={() =>
              void handleGetAiSuggestions("Đề xuất các chỉ định Cận lâm sàng theo phác đồ")
            }
            disabled={aiLoading}
            className="px-2.5 py-1 bg-white hover:bg-blue-50 text-slate-700 hover:text-blue-800 border border-card-border text-[11px] font-medium rounded-md transition-all cursor-pointer"
          >
            Đề xuất Cận lâm sàng
          </button>
        </div>
      </div>

      {aiLoading && (
        <div className="p-6 text-center bg-slate-50 border border-card-border rounded-lg space-y-2">
          <div className="w-5 h-5 border-2 border-[#6E2582] border-t-transparent rounded-full animate-spin mx-auto" />
          <p className="text-xs text-slate-600 font-medium">
            Đang truy hồi phác đồ Bộ Y Tế và phân tích ca khám...
          </p>
        </div>
      )}

      {!aiLoading && aiResponse && (
        <div className="space-y-3.5">
          {prescriptionItems.length > 0 &&
            aiResponse.warnings &&
            aiResponse.warnings.some((w) => w.includes("DDI") || w.toLowerCase().includes("tương tác")) && (
              <div className="p-3 bg-amber-50/90 border-l-3 border-amber-500 rounded-r-md space-y-1">
                <div className="flex items-center gap-1.5 text-amber-900 font-bold text-xs">
                  <span>Cảnh báo tương tác thuốc (Dược Thư Quốc Gia):</span>
                </div>
                <ul className="list-disc list-inside text-xs text-amber-950 space-y-0.5 pl-1">
                  {aiResponse.warnings
                    .filter((w) => w.includes("DDI") || w.toLowerCase().includes("tương tác"))
                    .map((w, idx) => (
                      <li key={idx}>{w}</li>
                    ))}
                </ul>
              </div>
            )}

          <div className="p-4 bg-white border border-card-border rounded-lg space-y-2">
            <div className="flex items-center justify-between pb-2 border-b border-slate-100">
              <span className="text-[11px] font-bold text-[#2B1D30] uppercase tracking-wide">
                Kết quả phân tích lâm sàng:
              </span>
              {aiResponse.model && (
                <span className="text-[10px] text-slate-500 font-mono">{aiResponse.model.name}</span>
              )}
            </div>
            <FormattedAiText content={aiResponse.summary} />
            <ClinicalCitations response={aiResponse} />
          </div>

          {aiResponse.suggestions && aiResponse.suggestions.length > 0 && (
            <div className="space-y-2">
              <span className="text-xs font-bold text-[#2B1D30] uppercase tracking-wide block">
                Khuyến nghị từ Phác đồ Bộ Y Tế:
              </span>
              <div className="space-y-2">
                {aiResponse.suggestions.map((item, idx) => (
                  <div key={idx} className="p-3 bg-white border border-card-border rounded-lg space-y-2">
                    <div className="flex items-start justify-between gap-3">
                      <p className="text-xs text-slate-800 leading-relaxed font-medium">{item.text}</p>
                      <button
                        type="button"
                        onClick={() => onApplyDiagnosisSuggestion(item.text)}
                        className="px-2.5 py-1 text-[11px] font-medium text-[#6E2582] bg-purple-50 hover:bg-purple-100 border border-purple-200 rounded-md transition-all flex-shrink-0 cursor-pointer"
                      >
                        + Áp dụng
                      </button>
                    </div>
                    {item.evidenceRefs && item.evidenceRefs.length > 0 && (
                      <div className="flex flex-wrap items-center gap-1.5 pt-1.5 border-t border-slate-100 text-[10px]">
                        <span className="text-slate-500">Căn cứ:</span>
                        {item.evidenceRefs.map((ev, eIdx) => (
                          <span
                            key={eIdx}
                            className="bg-purple-50 text-purple-900 font-mono px-1.5 py-0.5 rounded border border-purple-200 text-[10px]"
                          >
                            <span className="font-semibold">{ev.sourceId}</span>
                            {ev.field && <span className="text-purple-700 ml-1">({ev.field})</span>}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {aiResponse.missingInformation && aiResponse.missingInformation.length > 0 && (
            <div className="p-3 bg-slate-50 border border-card-border rounded-lg space-y-1">
              <span className="text-xs font-semibold text-slate-800 block">
                Dữ liệu lâm sàng cần khai thác thêm:
              </span>
              <ul className="list-disc list-inside text-xs text-slate-700 space-y-0.5 pl-1">
                {aiResponse.missingInformation.map((info, idx) => (
                  <li key={idx}>{info}</li>
                ))}
              </ul>
            </div>
          )}

          <p className="text-[10px] text-slate-400 italic text-center pt-1">{aiResponse.disclaimer}</p>
        </div>
      )}
    </div>
  );
}
