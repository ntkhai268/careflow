"use client";

import Link from "next/link";
import { ConsultationResponse } from "@/lib/consultation-api";
import { PatientAllergyResponse } from "@/lib/patient-api";
import { PrescriptionResponse } from "@/lib/prescription-api";
import { CONSULTATION_STATUS_LABELS } from "./consultation-constants";

interface PatientHeaderBarProps {
  consultation: ConsultationResponse;
  patientName: string;
  patientAge: number | null;
  patientGender: string;
  patientIdCode: string;
  prescriptionStatus: "DRAFT" | "CONFIRMED" | "DISPENSED" | "CANCELLED" | "CANCELLED_BY_AMENDMENT" | null;
  inheritedDrafts: PrescriptionResponse[];
  onSelectInheritedDraft: (draft: PrescriptionResponse) => void;
  patientDataError: string | null;
  onRetryPatientDetails: () => void;
  allergyLoaded: boolean;
  patientAllergies: PatientAllergyResponse[];
  allergyNotes: string | null;
  isConsultationLocked: boolean;
  onNavigateDashboard: () => void;
  onResumeReview: () => Promise<void>;
}

export default function PatientHeaderBar({
  consultation,
  patientName,
  patientAge,
  patientGender,
  patientIdCode,
  prescriptionStatus,
  inheritedDrafts,
  onSelectInheritedDraft,
  patientDataError,
  onRetryPatientDetails,
  allergyLoaded,
  patientAllergies,
  allergyNotes,
  isConsultationLocked,
  onNavigateDashboard,
  onResumeReview,
}: PatientHeaderBarProps) {
  const criticalAllergies = patientAllergies.filter((a) => a.severity === "CRITICAL");
  const warningAllergies = patientAllergies.filter((a) => a.severity === "WARNING");

  return (
    <>
      {inheritedDrafts.length > 0 && (
        <div className="border border-amber-200 bg-amber-50 p-3 rounded-lg text-sm space-y-2">
          <p>
            Ca bàn giao có toa nháp của bác sĩ trước. Bạn có thể xem để đối chiếu; toa mới sẽ được lập dưới tên của bạn, không ký thay tác giả cũ.
          </p>
          {inheritedDrafts.map((p) => (
            <button
              key={p.id}
              type="button"
              onClick={() => onSelectInheritedDraft(p)}
              className="text-purple-800 underline mr-3"
            >
              Xem toa nháp {p.id.slice(0, 8)}
            </button>
          ))}
        </div>
      )}

      <div className="border border-card-border bg-card-bg p-4 shadow-[0_1px_3px_rgba(110,37,130,0.06)] flex flex-wrap justify-between items-center rounded-lg">
        <div>
          <span className="text-[10px] font-bold text-primary-600 uppercase tracking-widest">Phiên khám hiện hành</span>
          <h1 className="text-xl font-bold text-[#2B1D30] tracking-tight">{patientName}</h1>
          <p className="text-xs text-[#6A5C70] flex items-center gap-1.5 mt-0.5">
            <span>{patientAge === null ? "Chưa có ngày sinh" : `${patientAge} tuổi`}</span>
            <span className="opacity-40">·</span>
            <span>{patientGender}</span>
            {patientIdCode && (
              <>
                <span className="opacity-40">·</span>
                <span className="font-mono text-slate-400 text-[11px]">ID: {patientIdCode}</span>
              </>
            )}
          </p>
          {consultation.doctorName && (
            <p className="text-xs text-[#6A5C70] mt-1">
              Bác sĩ phụ trách: {consultation.doctorName}
              {consultation.authoredByDoctorName && consultation.authoredByDoctorId !== consultation.doctorId
                ? ` · Khởi tạo: ${consultation.authoredByDoctorName}`
                : ""}
            </p>
          )}
          {!["COMPLETED", "CANCELLED", "TRANSFERRED"].includes(consultation.status) && (
            <Link href="/dashboard/handovers" className="inline-block mt-2 text-sm text-purple-800 underline">
              Bàn giao ca khám
            </Link>
          )}
        </div>

        <div className="flex gap-4">
          <div className="text-right">
            <span className="text-[10px] uppercase font-bold text-gray-400">Trạng thái khám</span>
            <div className="mt-1">
              <span
                className={`px-2 py-0.5 text-xs font-semibold rounded-md ${
                  consultation.status === "COMPLETED"
                    ? "bg-success-light text-success border border-success/20"
                    : "bg-[#E8F1FD] text-[#2F80ED] border border-[#2F80ED]/20"
                }`}
              >
                {CONSULTATION_STATUS_LABELS[consultation.status || ""] || consultation.status}
              </span>
            </div>
          </div>

          {prescriptionStatus && (
            <div className="text-right">
              <span className="text-[10px] uppercase font-bold text-gray-400">Trạng thái đơn</span>
              <div className="mt-1">
                <span
                  className={`px-2 py-0.5 text-xs font-semibold rounded-md ${
                    prescriptionStatus === "DRAFT"
                      ? "bg-warning-light text-warning border border-warning/20"
                      : "bg-success-light text-success border border-success/20"
                  }`}
                >
                  {prescriptionStatus === "DRAFT" ? "Đơn nháp" : "Đã ký xác nhận"}
                </span>
              </div>
            </div>
          )}
        </div>
      </div>

      {patientDataError && (
        <div role="alert" className="rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900">
          Không tải được thông tin hồ sơ / dị ứng: {patientDataError}. Chưa thể kết luận bệnh nhân không có tiền sử.
          <button type="button" className="ml-3 font-semibold underline" onClick={onRetryPatientDetails}>
            Thử lại thông tin hồ sơ
          </button>
        </div>
      )}

      {allergyLoaded && (
        patientAllergies.length > 0 ? (
          <div className="space-y-1.5">
            {criticalAllergies.length > 0 && (
              <div className="min-h-[36px] px-3 py-1.5 border border-red-200/80 border-l-[3px] border-l-red-500 bg-red-50/70 flex items-center justify-between rounded-md text-[11px] text-red-950">
                <div className="flex items-center gap-2 flex-wrap min-w-0">
                  <svg className="w-3.5 h-3.5 text-red-600 flex-shrink-0" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.538-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                  </svg>
                  <span className="text-[10px] font-semibold text-red-700 bg-red-100/80 px-1.5 py-0.5 rounded flex-shrink-0">
                    Dị ứng nguy hiểm
                  </span>
                  <div className="flex flex-wrap items-center gap-1.5 text-red-900 font-semibold">
                    {criticalAllergies.map((a, idx) => (
                      <span key={idx}>
                        {a.allergyName}
                        {a.reaction && <span className="font-normal text-red-700 text-[10px] ml-1">({a.reaction})</span>}
                        {idx < criticalAllergies.length - 1 && <span className="mx-1 text-red-300">·</span>}
                      </span>
                    ))}
                  </div>
                </div>
                <span className="text-[10px] text-red-600 font-medium whitespace-nowrap ml-2 flex-shrink-0">
                  Chặn kê đơn trùng nhóm
                </span>
              </div>
            )}

            {warningAllergies.length > 0 && (
              <div className="min-h-[36px] px-3 py-1.5 border border-amber-200/80 border-l-[3px] border-l-amber-500 bg-amber-50/70 flex items-center gap-2 rounded-md text-[11px] text-amber-950">
                <svg className="w-3.5 h-3.5 text-amber-600 flex-shrink-0" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.538-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
                <span className="text-[10px] font-semibold text-amber-800 bg-amber-100/80 px-1.5 py-0.5 rounded flex-shrink-0">
                  Cần lưu ý
                </span>
                <span className="text-amber-900 font-medium flex-wrap">
                  {warningAllergies.map((a, idx) => (
                    <span key={idx}>
                      <span className="font-semibold">{a.allergyName}</span>
                      {a.reaction && <span className="font-normal text-amber-700 text-[10px] ml-1">({a.reaction})</span>}
                      {idx < warningAllergies.length - 1 && <span className="mx-1 text-amber-300">·</span>}
                    </span>
                  ))}
                </span>
              </div>
            )}
          </div>
        ) : allergyNotes ? (
          <div className="min-h-[36px] px-3 py-1.5 border border-amber-200/80 border-l-[3px] border-l-amber-500 bg-amber-50/70 flex items-center gap-2 rounded-md text-[11px] text-amber-950">
            <svg className="w-3.5 h-3.5 text-amber-600 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.538-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <span className="text-[10px] font-semibold text-amber-800 bg-amber-100/80 px-1.5 py-0.5 rounded flex-shrink-0">
              Tiền sử dị ứng
            </span>
            <span className="text-amber-900 font-medium">{allergyNotes}</span>
          </div>
        ) : (
          <div className="min-h-[32px] px-3 py-1 border border-slate-200/80 bg-slate-50/60 flex items-center gap-2 rounded-md text-[11px] text-slate-500">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 inline-block flex-shrink-0" />
            <span>Không ghi nhận tiền sử dị ứng thuốc</span>
          </div>
        )
      )}

      {isConsultationLocked && (
        <div className="border border-amber-300 bg-amber-50 p-4 text-amber-900 text-xs font-semibold flex items-center justify-between rounded-lg shadow-sm">
          <div className="flex items-center gap-2">
            <span className="font-bold text-amber-700 uppercase tracking-wider text-[11px]">Đã khóa phiên khám</span>
            <span>· Phiên khám này đã HOÀN TẤT. Hồ sơ bệnh án đã được lưu trữ an toàn và ở chế độ chỉ xem (Read-Only).</span>
          </div>
          <button
            type="button"
            onClick={onNavigateDashboard}
            className="px-3 py-1 bg-amber-800 text-white text-xs font-bold hover:bg-amber-900 rounded-md transition-all cursor-pointer"
          >
            Về Bảng điều khiển
          </button>
        </div>
      )}

      {consultation.status === "AWAITING_CLS" && !isConsultationLocked && (
        <div className="border border-blue-300 bg-blue-50 p-4 text-blue-900 text-xs font-semibold flex items-center justify-between rounded-lg shadow-sm">
          <div className="flex items-center gap-2">
            <span className="font-bold text-blue-700 uppercase tracking-wider text-[11px]">Chờ Cận lâm sàng</span>
            <span>· Đã tạo chỉ định Cận lâm sàng. Đang chờ bệnh nhân tới phòng kỹ thuật và chờ trả kết quả.</span>
          </div>
        </div>
      )}

      {consultation.status === "AWAITING_REVIEW" && !isConsultationLocked && (
        <div className="border border-purple-300 bg-purple-50 p-4 text-purple-900 text-xs font-semibold flex items-center justify-between rounded-lg shadow-sm">
          <div className="flex items-center gap-2">
            <span className="font-bold text-purple-700 uppercase tracking-wider text-[11px]">Đã có kết quả CLS</span>
            <span>· Kết quả Cận lâm sàng đã sẵn sàng. Bác sĩ bấm &quot;Tiếp tục đọc kết quả&quot; để xem và hoàn tất ca khám.</span>
          </div>
          <button
            type="button"
            onClick={() => void onResumeReview()}
            className="px-3.5 py-1.5 bg-[#6E2582] text-white text-xs font-bold hover:bg-[#561A66] rounded-md transition-all cursor-pointer shadow-xs"
          >
            Tiếp tục đọc kết quả
          </button>
        </div>
      )}
    </>
  );
}
