"use client";

import FollowUpAppointmentPanel from "@/components/FollowUpAppointmentPanel";
import UploadedHealthRecords from "@/components/UploadedHealthRecords";
import { ConsultationResponse } from "@/lib/consultation-api";
import { PrescriptionResponse } from "@/lib/prescription-api";

interface EmrSummaryTabProps {
  consultationId: string;
  consultation: ConsultationResponse;
  userId?: string;
  summaryLoading: boolean;
  summaryError: string | null;
  onRetrySummary: () => void;
  emrRecord: { recordNumber: string; bloodType: string; medicalHistory: string } | null;
  patientMedicalHistory: string | null;
  consultationHistory: ConsultationResponse[] | null;
  prescriptionHistory: PrescriptionResponse[] | null;
  onSelectHistoryConsultation: (cons: ConsultationResponse) => void;
  onSelectHistoryPrescription: (presc: PrescriptionResponse) => void;
}

export default function EmrSummaryTab({
  consultationId,
  consultation,
  userId,
  summaryLoading,
  summaryError,
  onRetrySummary,
  emrRecord,
  patientMedicalHistory,
  consultationHistory,
  prescriptionHistory,
  onSelectHistoryConsultation,
  onSelectHistoryPrescription,
}: EmrSummaryTabProps) {
  return (
    <>
      {userId && (
        <div className="space-y-4 mb-4">
          <UploadedHealthRecords key={`uploads-${consultation.patientId}-${userId}`} patientId={consultation.patientId} />
          <FollowUpAppointmentPanel
            key={`follow-up-${consultationId}-${userId}`}
            consultationId={consultationId}
            patientId={consultation.patientId}
            doctorId={userId}
          />
        </div>
      )}

      <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] rounded-b-lg space-y-5">
        <h2 className="text-xs font-bold text-[#2B1D30] border-b border-card-border pb-2 uppercase tracking-wide">
          Tóm tắt Lâm sàng &amp; Lịch sử Y tế Bệnh nhân
        </h2>

        {summaryLoading ? (
          <div className="space-y-4 py-2">
            <div className="h-16 bg-gray-100 animate-pulse rounded-md" />
            <div className="h-28 bg-gray-100 animate-pulse rounded-md" />
            <div className="h-24 bg-gray-100 animate-pulse rounded-md" />
          </div>
        ) : summaryError ? (
          <div role="alert" className="rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900">
            Không tải được bệnh sử: {summaryError}. Chưa thể kết luận không có lịch sử khám hoặc đơn thuốc.
            <button type="button" className="ml-3 font-semibold underline" onClick={onRetrySummary}>
              Thử lại bệnh sử
            </button>
          </div>
        ) : (
          <div className="space-y-5">
            <div className="border border-purple-200 bg-purple-50/40 p-4 space-y-3 rounded-md">
              <div className="flex items-center justify-between border-b border-purple-200/60 pb-2">
                <div className="flex items-center gap-2">
                  <span className="text-[10px] font-bold text-purple-700 uppercase tracking-widest bg-purple-100 px-2 py-0.5 border border-purple-200 rounded-sm">
                    Hồ sơ EMR Master
                  </span>
                  <span className="text-xs font-mono font-bold text-purple-900">
                    {emrRecord?.recordNumber || "Chưa có mã EMR"}
                  </span>
                </div>
                {emrRecord?.bloodType && (
                  <span className="text-xs font-bold text-rose-700 bg-rose-50 px-2 py-0.5 border border-rose-200 flex items-center gap-1 rounded-sm">
                    <span>Nhóm máu:</span>
                    <span>{emrRecord.bloodType}</span>
                  </span>
                )}
              </div>

              <div className="space-y-1">
                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">
                  Bệnh nền &amp; Tiền sử y tế
                </span>
                <p className="text-xs font-medium text-slate-800">
                  {emrRecord?.medicalHistory || patientMedicalHistory || "Chưa ghi nhận thông tin bệnh nền mạn tính"}
                </p>
              </div>
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between border-b border-slate-200 pb-1.5">
                <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                  Lịch sử khám bệnh gần đây
                </span>
                <span className="text-[10px] text-slate-400 font-mono">
                  {(consultationHistory ?? []).length} ca khám trước (Cuộn để xem thêm)
                </span>
              </div>

              {(consultationHistory ?? []).length === 0 ? (
                <div className="py-6 text-center text-xs text-slate-400 italic bg-slate-50 border border-dashed border-slate-200 rounded-md">
                  Chưa có lịch sử khám được trả về trong phạm vi tra cứu
                </div>
              ) : (
                <div className="divide-y divide-slate-100 border border-slate-200 rounded-md max-h-[260px] overflow-y-auto pr-1">
                  {(consultationHistory ?? []).map((c) => {
                    const linkedPrescription = (prescriptionHistory ?? []).find((p) => p.consultationId === c.id);
                    return (
                      <div
                        key={c.id}
                        onClick={() => onSelectHistoryConsultation(c)}
                        className="p-3 hover:bg-purple-50/50 transition-colors space-y-1.5 cursor-pointer group"
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 inline-block" />
                            <span className="text-xs font-bold text-slate-800 group-hover:text-primary-700">
                              {c.icd10Name
                                ? `${c.icd10Code ? `${c.icd10Code} - ` : ""}${c.icd10Name}`
                                : c.diagnosis || "Chẩn đoán chưa cập nhật"}
                            </span>
                          </div>
                          <div className="flex items-center gap-2">
                            <span className="text-[11px] font-mono text-slate-400">
                              {c.startedAt ? new Date(c.startedAt).toLocaleDateString("vi-VN") : "N/A"}
                            </span>
                            <span className="text-[10px] text-primary-600 font-semibold opacity-0 group-hover:opacity-100 transition-opacity">
                              Xem chi tiết →
                            </span>
                          </div>
                        </div>

                        <div className="text-[11px] text-slate-500 flex flex-wrap gap-x-3 gap-y-0.5 items-center justify-between">
                          <div className="flex flex-wrap gap-x-3 gap-y-0.5">
                            {c.bloodPressure && (
                              <span>
                                Huyết áp: <strong className="font-mono text-slate-700">{c.bloodPressure}</strong>
                              </span>
                            )}
                            {c.temperature && (
                              <span>
                                Thân nhiệt: <strong className="font-mono text-slate-700">{c.temperature}°C</strong>
                              </span>
                            )}
                            {c.spo2 && (
                              <span>
                                SpO2: <strong className="font-mono text-slate-700">{c.spo2}%</strong>
                              </span>
                            )}
                            {c.heartRate && (
                              <span>
                                Mạch: <strong className="font-mono text-slate-700">{c.heartRate} bpm</strong>
                              </span>
                            )}
                          </div>
                          {linkedPrescription ? (
                            <span className="text-[10px] font-semibold text-purple-700 bg-purple-50 px-1.5 py-0.5 border border-purple-200 rounded-sm">
                              Đã kê đơn thuốc
                            </span>
                          ) : (
                            <span className="text-[10px] text-slate-400 italic">Chưa kê đơn</span>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between border-b border-slate-200 pb-1.5">
                <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                  Đơn thuốc đã dùng gần đây
                </span>
                <span className="text-[10px] text-slate-400 font-mono">
                  {(prescriptionHistory ?? []).length} đơn (Cuộn để xem thêm)
                </span>
              </div>

              {(prescriptionHistory ?? []).length === 0 ? (
                <div className="py-6 text-center text-xs text-slate-400 italic bg-slate-50 border border-dashed border-slate-200 rounded-md">
                  Chưa ghi nhận lịch sử đơn thuốc cũ
                </div>
              ) : (
                <div className="space-y-2.5 max-h-[260px] overflow-y-auto pr-1">
                  {(prescriptionHistory ?? []).map((p) => {
                    const linkedConsultation = (consultationHistory ?? []).find((c) => c.id === p.consultationId);
                    return (
                      <div
                        key={p.id}
                        onClick={() => onSelectHistoryPrescription(p)}
                        className="border border-slate-200 p-3 bg-white space-y-2 rounded-md hover:border-primary-400 transition-colors cursor-pointer group"
                      >
                        <div className="flex items-center justify-between text-[11px] border-b border-slate-100 pb-1.5">
                          <div className="flex items-center gap-2">
                            <span className="font-bold text-slate-800 group-hover:text-primary-700">
                              Đơn thuốc · {new Date(p.createdAt).toLocaleDateString("vi-VN")}
                            </span>
                            {linkedConsultation && (
                              <span className="text-[10px] font-mono text-slate-500 truncate max-w-[200px]">
                                ({linkedConsultation.icd10Code || linkedConsultation.diagnosis || "Lần khám"})
                              </span>
                            )}
                          </div>
                          <div className="flex items-center gap-2">
                            <span
                              className={`px-1.5 py-0.5 font-bold uppercase text-[9px] rounded-sm ${
                                p.status === "CONFIRMED" || p.status === "DISPENSED"
                                  ? "bg-emerald-50 text-emerald-700 border border-emerald-200"
                                  : "bg-amber-50 text-amber-700 border border-amber-200"
                              }`}
                            >
                              {p.status === "CONFIRMED" ? "Đã ký" : p.status === "DISPENSED" ? "Đã cấp phát" : "Nháp"}
                            </span>
                            <span className="text-[10px] text-primary-600 font-semibold opacity-0 group-hover:opacity-100 transition-opacity">
                              Chi tiết →
                            </span>
                          </div>
                        </div>
                        <ul className="space-y-1">
                          {(p.items || []).slice(0, 3).map((item, idx) => (
                            <li key={item.id || idx} className="text-xs text-slate-600 flex justify-between">
                              <span>
                                <strong className="text-slate-800">
                                  {idx + 1}. {item.medicineName}
                                </strong>
                                {item.dosage && <span className="text-slate-500 font-normal"> · {item.dosage}</span>}
                                {item.frequency && <span className="text-slate-500 font-normal"> · {item.frequency}</span>}
                              </span>
                              <span className="font-mono text-slate-500 text-[11px]">
                                x{item.quantity} {item.unit}
                              </span>
                            </li>
                          ))}
                          {(p.items || []).length > 3 && (
                            <li className="text-[11px] text-primary-600 font-semibold italic">
                              + {p.items.length - 3} loại thuốc khác... (Bấm để xem đầy đủ)
                            </li>
                          )}
                        </ul>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </>
  );
}
