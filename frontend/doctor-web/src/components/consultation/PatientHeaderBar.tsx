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
        <div className="border border-[#A86100]/30 bg-[#FFEBCB] p-3.5 rounded-xl text-xs text-[#A86100] space-y-2">
          <p className="font-medium">
            Ca bàn giao có toa nháp của bác sĩ trước. Bạn có thể xem để đối chiếu; toa mới sẽ được lập dưới tên của bạn, không ký thay tác giả cũ.
          </p>
          {inheritedDrafts.map((p) => (
            <button
              key={p.id}
              type="button"
              onClick={() => onSelectInheritedDraft(p)}
              className="text-[#00577E] font-bold underline mr-3 cursor-pointer"
            >
              Xem toa nháp {p.id.slice(0, 8)}
            </button>
          ))}
        </div>
      )}

      <div className="border border-[#D5E0E6] bg-white px-5 py-4 shadow-[0_2px_8px_rgba(23,36,45,0.03)] flex flex-wrap justify-between items-center gap-4 rounded-xl">
        <div>
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[#0277A8]" />
            <span className="text-[10px] font-bold text-[#0277A8] uppercase tracking-widest">
              Phiên khám hiện hành
            </span>
          </div>
          <h1 className="text-xl font-bold text-[#17242D] tracking-tight mt-0.5">{patientName}</h1>
          <p className="text-xs text-[#4D626E] flex flex-wrap items-center gap-1.5 mt-0.5">
            <span>{patientAge === null ? "Chưa có ngày sinh" : `${patientAge} tuổi`}</span>
            <span className="opacity-40">·</span>
            <span>{patientGender}</span>
            {patientIdCode && (
              <>
                <span className="opacity-40">·</span>
                <span className="font-mono text-[#667985] text-[11px]">
                  Mã BN: {patientIdCode.slice(0, 8).toUpperCase()}
                </span>
              </>
            )}
          </p>
          {consultation.doctorName && (
            <p className="text-xs text-[#4D626E] mt-1">
              Bác sĩ phụ trách: <strong className="text-[#17242D]">{consultation.doctorName}</strong>
              {consultation.authoredByDoctorName &&
              consultation.authoredByDoctorId !== consultation.doctorId
                ? ` · Khởi tạo: ${consultation.authoredByDoctorName}`
                : ""}
            </p>
          )}
          {!["COMPLETED", "CANCELLED", "TRANSFERRED"].includes(consultation.status) && (
            <Link
              href="/dashboard/handovers"
              className="inline-flex items-center gap-1 mt-2 text-xs font-semibold text-[#0277A8] hover:text-[#00577E] hover:underline"
            >
              <span>Bàn giao ca khám</span>
              <span>→</span>
            </Link>
          )}
        </div>

        <div className="flex gap-5">
          <div className="text-right">
            <span className="text-[10px] uppercase font-bold text-[#667985]">Trạng thái khám</span>
            <div className="mt-1">
              <span
                className={`px-2.5 py-1 text-xs font-bold rounded-full inline-block ${
                  consultation.status === "COMPLETED"
                    ? "bg-[#DDF4E7] text-[#1B7F4B]"
                    : "bg-[#DDF3FC] text-[#00577E]"
                }`}
              >
                {CONSULTATION_STATUS_LABELS[consultation.status || ""] || consultation.status}
              </span>
            </div>
          </div>

          {prescriptionStatus && (
            <div className="text-right">
              <span className="text-[10px] uppercase font-bold text-[#667985]">Trạng thái đơn</span>
              <div className="mt-1">
                <span
                  className={`px-2.5 py-1 text-xs font-bold rounded-full inline-block ${
                    prescriptionStatus === "DRAFT"
                      ? "bg-[#FFEBCB] text-[#A86100]"
                      : "bg-[#DDF4E7] text-[#1B7F4B]"
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
        <div
          role="alert"
          className="rounded-xl border border-[#A86100]/30 bg-[#FFEBCB] p-3.5 text-xs text-[#A86100]"
        >
          Không tải được thông tin hồ sơ / dị ứng: {patientDataError}. Chưa thể kết luận bệnh nhân không có tiền sử.
          <button
            type="button"
            className="ml-3 font-bold underline cursor-pointer"
            onClick={onRetryPatientDetails}
          >
            Thử lại thông tin hồ sơ
          </button>
        </div>
      )}

      {allergyLoaded &&
        (patientAllergies.length > 0 ? (
          <div className="space-y-1.5">
            {criticalAllergies.length > 0 && (
              <div className="min-h-[38px] px-3.5 py-2 border border-[#BA1A1A]/30 border-l-4 border-l-[#BA1A1A] bg-[#FFDAD6]/60 flex items-center justify-between rounded-xl text-xs text-[#17242D]">
                <div className="flex items-center gap-2 flex-wrap min-w-0">
                  <svg
                    className="w-4 h-4 text-[#BA1A1A] flex-shrink-0"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth={2}
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.538-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
                    />
                  </svg>
                  <span className="text-[10px] font-bold text-white bg-[#BA1A1A] px-2 py-0.5 rounded-full flex-shrink-0">
                    Dị ứng nguy hiểm
                  </span>
                  <div className="flex flex-wrap items-center gap-1.5 text-[#BA1A1A] font-bold">
                    {criticalAllergies.map((a, idx) => (
                      <span key={idx}>
                        {a.allergyName}
                        {a.reaction && (
                          <span className="font-medium text-[#BA1A1A]/85 text-[11px] ml-1">
                            ({a.reaction})
                          </span>
                        )}
                        {idx < criticalAllergies.length - 1 && <span className="mx-1">·</span>}
                      </span>
                    ))}
                  </div>
                </div>
                <span className="text-[11px] text-[#BA1A1A] font-bold whitespace-nowrap ml-2 flex-shrink-0">
                  Chặn kê đơn trùng nhóm
                </span>
              </div>
            )}

            {warningAllergies.length > 0 && (
              <div className="min-h-[38px] px-3.5 py-2 border border-[#A86100]/30 border-l-4 border-l-[#A86100] bg-[#FFEBCB]/70 flex items-center gap-2 rounded-xl text-xs text-[#17242D]">
                <svg
                  className="w-4 h-4 text-[#A86100] flex-shrink-0"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth={2}
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.538-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
                  />
                </svg>
                <span className="text-[10px] font-bold text-[#A86100] bg-white/80 px-2 py-0.5 rounded-full flex-shrink-0">
                  Cần lưu ý
                </span>
                <span className="text-[#A86100] font-medium flex-wrap">
                  {warningAllergies.map((a, idx) => (
                    <span key={idx}>
                      <span className="font-bold">{a.allergyName}</span>
                      {a.reaction && (
                        <span className="font-normal text-[#A86100] text-[11px] ml-1">
                          ({a.reaction})
                        </span>
                      )}
                      {idx < warningAllergies.length - 1 && <span className="mx-1">·</span>}
                    </span>
                  ))}
                </span>
              </div>
            )}
          </div>
        ) : allergyNotes ? (
          <div className="min-h-[38px] px-3.5 py-2 border border-[#A86100]/30 border-l-4 border-l-[#A86100] bg-[#FFEBCB]/70 flex items-center gap-2 rounded-xl text-xs text-[#A86100]">
            <span className="text-[10px] font-bold bg-white/80 px-2 py-0.5 rounded-full flex-shrink-0">
              Tiền sử dị ứng
            </span>
            <span className="font-medium">{allergyNotes}</span>
          </div>
        ) : (
          <div className="min-h-[34px] px-3.5 py-1.5 border border-[#D5E0E6] bg-white flex items-center gap-2 rounded-xl text-xs text-[#4D626E]">
            <span className="w-2 h-2 rounded-full bg-[#1B7F4B] inline-block flex-shrink-0" />
            <span>Không ghi nhận tiền sử dị ứng thuốc</span>
          </div>
        ))}

      {isConsultationLocked && (
        <div className="border border-[#A86100]/30 bg-[#FFEBCB] p-4 text-[#A86100] text-xs font-semibold flex items-center justify-between rounded-xl shadow-xs">
          <div className="flex items-center gap-2">
            <span className="font-bold uppercase tracking-wider text-[11px]">
              Đã khóa phiên khám
            </span>
            <span>
              · Phiên khám này đã HOÀN TẤT. Hồ sơ bệnh án đã được lưu trữ an toàn và ở chế độ chỉ xem (Read-Only).
            </span>
          </div>
          <button
            type="button"
            onClick={onNavigateDashboard}
            className="px-3.5 py-1.5 bg-[#A86100] text-white text-xs font-bold hover:opacity-90 rounded-lg transition-all cursor-pointer"
          >
            Về Bảng điều khiển
          </button>
        </div>
      )}

      {consultation.status === "AWAITING_CLS" && !isConsultationLocked && (
        <div className="border border-[#1D5FA7]/30 bg-[#DCEBFF] p-4 text-[#1D5FA7] text-xs font-semibold flex items-center justify-between rounded-xl shadow-xs">
          <div className="flex items-center gap-2">
            <span className="font-bold uppercase tracking-wider text-[11px]">
              Chờ Cận lâm sàng
            </span>
            <span>
              · Đã tạo chỉ định Cận lâm sàng. Đang chờ bệnh nhân tới phòng kỹ thuật và chờ trả kết quả.
            </span>
          </div>
        </div>
      )}

      {consultation.status === "AWAITING_REVIEW" && !isConsultationLocked && (
        <div className="border border-[#087F6A]/30 bg-[#D9F3EC] p-4 text-[#087F6A] text-xs font-semibold flex items-center justify-between rounded-xl shadow-xs">
          <div className="flex items-center gap-2">
            <span className="font-bold uppercase tracking-wider text-[11px]">
              Đã có kết quả CLS
            </span>
            <span>
              · Kết quả Cận lâm sàng đã sẵn sàng. Bác sĩ bấm &quot;Tiếp tục đọc kết quả&quot; để xem và hoàn tất ca khám.
            </span>
          </div>
          <button
            type="button"
            onClick={() => void onResumeReview()}
            className="px-3.5 py-1.5 bg-[#087F6A] text-white text-xs font-bold hover:bg-[#066352] rounded-lg transition-all cursor-pointer shadow-xs"
          >
            Tiếp tục đọc kết quả
          </button>
        </div>
      )}
    </>
  );
}
