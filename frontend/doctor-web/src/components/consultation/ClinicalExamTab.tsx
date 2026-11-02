"use client";

interface ClinicalExamTabProps {
  symptoms: string;
  setSymptoms: (val: string) => void;
  clinicalNotes: string;
  setClinicalNotes: (val: string) => void;
  isConsultationLocked: boolean;
}

export default function ClinicalExamTab({
  symptoms,
  setSymptoms,
  clinicalNotes,
  setClinicalNotes,
  isConsultationLocked,
}: ClinicalExamTabProps) {
  return (
    <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] rounded-b-lg space-y-4">
      <h2 className="text-xs font-bold text-[#2B1D30] border-b border-card-border pb-2 uppercase tracking-wide">
        Ghi nhận bệnh sử & Thăm khám
      </h2>
      <div>
        <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Triệu chứng & Diễn biến bệnh</label>
        <textarea
          disabled={isConsultationLocked}
          value={symptoms}
          onChange={(e) => setSymptoms(e.target.value)}
          rows={4}
          placeholder="Ghi nhận triệu chứng cơ năng, thời gian khởi phát..."
          className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-md"
        />
      </div>
      <div>
        <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Nhận định & Tóm tắt khám lâm sàng</label>
        <textarea
          disabled={isConsultationLocked}
          value={clinicalNotes}
          onChange={(e) => setClinicalNotes(e.target.value)}
          rows={4}
          placeholder="Nhận định của bác sĩ qua thăm khám thể chất..."
          className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-md"
        />
      </div>
    </div>
  );
}
