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
    <div className="border border-[#D5E0E6] bg-white p-5 rounded-b-xl space-y-4 shadow-[0_2px_8px_rgba(23,36,45,0.04)]">
      <div className="flex items-center justify-between pb-3 border-b border-[#EDF2F5]">
        <div>
          <h3 className="font-bold text-xs text-[#17242D] tracking-wide uppercase">
            Trợ lý Lâm sàng (Phác đồ điều trị Bộ Y Tế)
          </h3>
          <p className="text-[11px] text-[#4D626E] mt-0.5">
            Truy hồi tri thức phác đồ BYT &amp; Dược thư Quốc gia (RAG Hybrid Search)
          </p>
        </div>
        <span className="text-[10px] bg-[#D9F3EC] text-[#087F6A] border border-[#087F6A]/25 px-2.5 py-0.5 rounded-full font-mono font-bold">
          BYT Guidelines &amp; Gemini 2.5 Flash
        </span>
      </div>

      <div className="space-y-2.5 bg-[#F5F8FA] p-3.5 rounded-xl border border-[#D5E0E6]">
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
            className="flex-1 px-3.5 py-2 border border-[#D5E0E6] rounded-xl text-xs bg-white text-[#17242D] placeholder-[#667985] focus:outline-none focus:border-[#0277A8] focus:ring-2 focus:ring-[#DDF3FC]"
          />
          <button
            type="submit"
            disabled={aiLoading || !aiQueryInput.trim()}
            className="px-4 py-2 bg-[#0277A8] hover:bg-[#00577E] text-white text-xs font-bold rounded-xl transition-all disabled:opacity-40 cursor-pointer"
          >
            Tra cứu
          </button>
        </form>

        <div className="flex flex-wrap items-center gap-1.5 pt-1">
          <span className="text-[11px] font-semibold text-[#4D626E] mr-1">Gợi ý nhanh:</span>
          <button
            type="button"
            onClick={() =>
              void handleGetAiSuggestions(
                "Gợi ý các khả năng chẩn đoán phân biệt cần cân nhắc theo phác đồ"
              )
            }
            disabled={aiLoading}
            className="px-2.5 py-1 bg-white hover:bg-[#DDF3FC] text-[#17242D] hover:text-[#00577E] border border-[#D5E0E6] text-[11px] font-medium rounded-lg transition-all cursor-pointer"
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
            className="px-2.5 py-1 bg-white hover:bg-[#FFDAD6]/50 text-[#17242D] hover:text-[#BA1A1A] border border-[#D5E0E6] text-[11px] font-medium rounded-lg transition-all cursor-pointer"
          >
            Cảnh báo tương tác DDI
          </button>
          <button
            type="button"
            onClick={() =>
              void handleGetAiSuggestions("Kiểm tra dữ liệu lâm sàng và tiền sử còn thiếu")
            }
            disabled={aiLoading}
            className="px-2.5 py-1 bg-white hover:bg-[#FFEBCB] text-[#17242D] hover:text-[#A86100] border border-[#D5E0E6] text-[11px] font-medium rounded-lg transition-all cursor-pointer"
          >
            Dữ liệu còn thiếu
          </button>
          <button
            type="button"
            onClick={() =>
              void handleGetAiSuggestions("Đề xuất các chỉ định Cận lâm sàng theo phác đồ")
            }
            disabled={aiLoading}
            className="px-2.5 py-1 bg-white hover:bg-[#D9F3EC] text-[#17242D] hover:text-[#087F6A] border border-[#D5E0E6] text-[11px] font-medium rounded-lg transition-all cursor-pointer"
          >
            Đề xuất Cận lâm sàng
          </button>
        </div>
      </div>

      {aiLoading && (
        <div className="p-6 text-center bg-[#F5F8FA] border border-[#D5E0E6] rounded-xl space-y-2">
          <div className="w-5 h-5 border-2 border-[#0277A8] border-t-transparent rounded-full animate-spin mx-auto" />
          <p className="text-xs text-[#4D626E] font-medium">
            Đang truy hồi phác đồ Bộ Y Tế và phân tích ca khám...
          </p>
        </div>
      )}

      {!aiLoading && aiResponse && (
        <div className="space-y-3.5">
          {prescriptionItems.length > 0 &&
            aiResponse.warnings &&
            aiResponse.warnings.some(
              (w) => w.includes("DDI") || w.toLowerCase().includes("tương tác")
            ) && (
              <div className="p-3.5 bg-[#FFEBCB] border-l-4 border-[#A86100] rounded-r-xl space-y-1">
                <div className="flex items-center gap-1.5 text-[#A86100] font-bold text-xs">
                  <span>Cảnh báo tương tác thuốc (Dược Thư Quốc Gia):</span>
                </div>
                <ul className="list-disc list-inside text-xs text-[#17242D] space-y-0.5 pl-1">
                  {aiResponse.warnings
                    .filter((w) => w.includes("DDI") || w.toLowerCase().includes("tương tác"))
                    .map((w, idx) => (
                      <li key={idx}>{w}</li>
                    ))}
                </ul>
              </div>
            )}

          <div className="p-4 bg-white border border-[#D5E0E6] rounded-xl space-y-2.5">
            <div className="flex items-center justify-between pb-2 border-b border-[#EDF2F5]">
              <span className="text-[11px] font-bold text-[#17242D] uppercase tracking-wide">
                Kết quả phân tích lâm sàng:
              </span>
              {aiResponse.model && (
                <span className="text-[10px] text-[#667985] font-mono">
                  {aiResponse.model.name}
                </span>
              )}
            </div>
            <FormattedAiText content={aiResponse.summary} />
            <ClinicalCitations response={aiResponse} />
          </div>

          {aiResponse.suggestions && aiResponse.suggestions.length > 0 && (
            <div className="space-y-2">
              <span className="text-xs font-bold text-[#17242D] uppercase tracking-wide block">
                Khuyến nghị từ Phác đồ Bộ Y Tế:
              </span>
              <div className="space-y-2">
                {aiResponse.suggestions.map((item, idx) => (
                  <div
                    key={idx}
                    className="p-3.5 bg-white border border-[#D5E0E6] rounded-xl space-y-2"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <p className="text-xs text-[#17242D] leading-relaxed font-medium">
                        {item.text}
                      </p>
                      <button
                        type="button"
                        onClick={() => onApplyDiagnosisSuggestion(item.text)}
                        className="px-3 py-1 text-[11px] font-bold text-[#00577E] bg-[#DDF3FC] hover:bg-[#8BD2EE]/50 border border-[#8BD2EE] rounded-lg transition-all flex-shrink-0 cursor-pointer"
                      >
                        + Áp dụng
                      </button>
                    </div>
                    {item.evidenceRefs && item.evidenceRefs.length > 0 && (
                      <div className="flex flex-wrap items-center gap-1.5 pt-2 border-t border-[#EDF2F5] text-[10px]">
                        <span className="text-[#667985]">Căn cứ:</span>
                        {item.evidenceRefs.map((ev, eIdx) => (
                          <span
                            key={eIdx}
                            className="bg-[#D9F3EC] text-[#087F6A] font-mono px-2 py-0.5 rounded-md border border-[#087F6A]/25 text-[10px]"
                          >
                            <span className="font-bold">{ev.sourceId}</span>
                            {ev.field && <span className="ml-1">({ev.field})</span>}
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
            <div className="p-3.5 bg-[#F5F8FA] border border-[#D5E0E6] rounded-xl space-y-1">
              <span className="text-xs font-bold text-[#17242D] block">
                Dữ liệu lâm sàng cần khai thác thêm:
              </span>
              <ul className="list-disc list-inside text-xs text-[#4D626E] space-y-0.5 pl-1">
                {aiResponse.missingInformation.map((info, idx) => (
                  <li key={idx}>{info}</li>
                ))}
              </ul>
            </div>
          )}

          <p className="text-[10px] text-[#667985] italic text-center pt-1">
            {aiResponse.disclaimer}
          </p>
        </div>
      )}
    </div>
  );
}
