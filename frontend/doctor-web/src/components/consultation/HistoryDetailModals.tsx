"use client";

import { ConsultationResponse } from "@/lib/consultation-api";
import { PrescriptionResponse } from "@/lib/prescription-api";

interface HistoryDetailModalsProps {
  selectedHistoryConsultation: ConsultationResponse | null;
  setSelectedHistoryConsultation: (cons: ConsultationResponse | null) => void;
  selectedHistoryPrescription: PrescriptionResponse | null;
  setSelectedHistoryPrescription: (presc: PrescriptionResponse | null) => void;
  consultationHistory: ConsultationResponse[] | null;
  prescriptionHistory: PrescriptionResponse[] | null;
}

export default function HistoryDetailModals({
  selectedHistoryConsultation,
  setSelectedHistoryConsultation,
  selectedHistoryPrescription,
  setSelectedHistoryPrescription,
  consultationHistory,
  prescriptionHistory,
}: HistoryDetailModalsProps) {
  return (
    <>
      {selectedHistoryConsultation && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="bg-white border border-card-border shadow-2xl rounded-xl w-full max-w-2xl max-h-[85vh] flex flex-col justify-between p-6">
            <div className="space-y-4 overflow-y-auto pr-1">
              <div className="flex justify-between items-center border-b border-card-border pb-3">
                <div>
                  <span className="text-[10px] font-bold text-primary-600 uppercase tracking-widest">
                    Chi tiết phiên khám cũ
                  </span>
                  <h3 className="text-base font-bold text-[#2B1D30]">
                    {selectedHistoryConsultation.icd10Name
                      ? `${selectedHistoryConsultation.icd10Code ? `${selectedHistoryConsultation.icd10Code} - ` : ""}${selectedHistoryConsultation.icd10Name}`
                      : selectedHistoryConsultation.diagnosis || "Ca khám trước"}
                  </h3>
                  <p className="text-xs text-[#6A5C70]">
                    Thời gian khám:{" "}
                    <strong className="font-mono text-[#2B1D30]">
                      {selectedHistoryConsultation.startedAt
                        ? new Date(selectedHistoryConsultation.startedAt).toLocaleString("vi-VN")
                        : "N/A"}
                    </strong>
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setSelectedHistoryConsultation(null)}
                  className="text-gray-400 hover:text-black font-bold text-sm px-2 py-1 rounded-md"
                >
                  ✕
                </button>
              </div>

              <div className="border border-slate-200 bg-slate-50/50 p-3.5 rounded-lg space-y-2">
                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">
                  Chỉ số sinh hiệu
                </span>
                <div className="grid grid-cols-4 gap-3 text-xs">
                  <div className="bg-white p-2 border border-slate-200 rounded-md">
                    <span className="text-[10px] text-slate-400 block">Huyết áp</span>
                    <strong className="font-mono text-slate-800">
                      {selectedHistoryConsultation.bloodPressure || "N/A"}
                    </strong>
                  </div>
                  <div className="bg-white p-2 border border-slate-200 rounded-md">
                    <span className="text-[10px] text-slate-400 block">Thân nhiệt</span>
                    <strong className="font-mono text-slate-800">
                      {selectedHistoryConsultation.temperature
                        ? `${selectedHistoryConsultation.temperature}°C`
                        : "N/A"}
                    </strong>
                  </div>
                  <div className="bg-white p-2 border border-slate-200 rounded-md">
                    <span className="text-[10px] text-slate-400 block">SpO2</span>
                    <strong className="font-mono text-slate-800">
                      {selectedHistoryConsultation.spo2 ? `${selectedHistoryConsultation.spo2}%` : "N/A"}
                    </strong>
                  </div>
                  <div className="bg-white p-2 border border-slate-200 rounded-md">
                    <span className="text-[10px] text-slate-400 block">Nhịp tim</span>
                    <strong className="font-mono text-slate-800">
                      {selectedHistoryConsultation.heartRate
                        ? `${selectedHistoryConsultation.heartRate} bpm`
                        : "N/A"}
                    </strong>
                  </div>
                </div>
              </div>

              <div className="space-y-3">
                <div className="border border-slate-200 p-3.5 bg-white rounded-lg space-y-1">
                  <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                    Triệu chứng cơ năng
                  </span>
                  <p className="text-xs text-slate-800 font-medium whitespace-pre-wrap">
                    {selectedHistoryConsultation.symptoms || "Không ghi nhận"}
                  </p>
                </div>
                <div className="border border-slate-200 p-3.5 bg-white rounded-lg space-y-1">
                  <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                    Nhận định &amp; Tóm tắt khám lâm sàng
                  </span>
                  <p className="text-xs text-slate-800 font-medium whitespace-pre-wrap">
                    {selectedHistoryConsultation.clinicalNotes || "Không ghi nhận"}
                  </p>
                </div>
                <div className="border border-slate-200 p-3.5 bg-white rounded-lg space-y-1">
                  <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                    Chẩn đoán lâm sàng
                  </span>
                  <p className="text-xs text-primary-700 font-bold">
                    {selectedHistoryConsultation.diagnosis || "Chẩn đoán chưa cập nhật"}
                  </p>
                </div>
              </div>

              {(() => {
                const linkedP = (prescriptionHistory ?? []).find(
                  (p) => p.consultationId === selectedHistoryConsultation.id
                );
                return linkedP ? (
                  <div className="border border-purple-200 bg-purple-50/40 p-4 rounded-lg space-y-2.5">
                    <div className="flex items-center justify-between border-b border-purple-200/60 pb-1.5">
                      <span className="text-xs font-bold text-purple-900 flex items-center gap-1.5">
                        <span>Đơn thuốc đính kèm ca khám này</span>
                      </span>
                      <span className="text-[10px] bg-purple-100 text-purple-700 px-2 py-0.5 font-bold uppercase rounded-sm border border-purple-200">
                        {linkedP.status === "CONFIRMED"
                          ? "Đã ký"
                          : linkedP.status === "DISPENSED"
                            ? "Đã cấp phát"
                            : "Nháp"}
                      </span>
                    </div>
                    <ul className="space-y-1.5">
                      {(linkedP.items || []).map((item, idx) => (
                        <li
                          key={item.id || idx}
                          className="text-xs text-slate-700 bg-white p-2 border border-purple-100 rounded-md flex justify-between items-center"
                        >
                          <div>
                            <strong className="text-slate-900">
                              {idx + 1}. {item.medicineName}
                            </strong>
                            <p className="text-[11px] text-slate-500 font-medium">
                              {item.dosage} · {item.frequency} · {item.timing}
                            </p>
                          </div>
                          <span className="font-mono text-xs font-bold text-purple-800">
                            x{item.quantity} {item.unit}
                          </span>
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : (
                  <div className="p-3 bg-slate-50 border border-slate-200 text-center text-xs text-slate-400 italic rounded-lg">
                    Phiên khám này không có đơn thuốc đính kèm
                  </div>
                );
              })()}
            </div>

            <div className="pt-4 border-t border-card-border flex justify-end">
              <button
                type="button"
                onClick={() => setSelectedHistoryConsultation(null)}
                className="bg-primary-600 hover:bg-primary-700 text-white font-bold px-5 py-2 text-xs transition-all rounded-md"
              >
                ĐÓNG CỬA SỔ
              </button>
            </div>
          </div>
        </div>
      )}

      {selectedHistoryPrescription && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="bg-white border border-card-border shadow-2xl rounded-xl w-full max-w-xl max-h-[85vh] flex flex-col justify-between p-6">
            <div className="space-y-4 overflow-y-auto pr-1">
              <div className="flex justify-between items-center border-b border-card-border pb-3">
                <div>
                  <span className="text-[10px] font-bold text-primary-600 uppercase tracking-widest">
                    Chi tiết đơn thuốc cũ
                  </span>
                  <h3 className="text-base font-bold text-[#2B1D30]">
                    Đơn thuốc · {new Date(selectedHistoryPrescription.createdAt).toLocaleDateString("vi-VN")}
                  </h3>
                  <p className="text-xs text-[#6A5C70]">
                    Mã đơn:{" "}
                    <span className="font-mono font-bold text-[#2B1D30]">{selectedHistoryPrescription.id}</span>
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setSelectedHistoryPrescription(null)}
                  className="text-gray-400 hover:text-black font-bold text-sm px-2 py-1 rounded-md"
                >
                  ✕
                </button>
              </div>

              {(() => {
                const linkedC = (consultationHistory ?? []).find(
                  (c) => c.id === selectedHistoryPrescription.consultationId
                );
                return linkedC ? (
                  <div className="border border-emerald-200 bg-emerald-50/50 p-3 rounded-lg flex items-center justify-between text-xs">
                    <div>
                      <span className="text-[10px] font-bold text-emerald-800 uppercase tracking-wider block">
                        Kê cho phiên khám
                      </span>
                      <strong className="text-emerald-950 font-bold">
                        {linkedC.icd10Name
                          ? `${linkedC.icd10Code} - ${linkedC.icd10Name}`
                          : linkedC.diagnosis || "Lần khám trước"}
                      </strong>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedHistoryPrescription(null);
                        setSelectedHistoryConsultation(linkedC);
                      }}
                      className="text-[11px] font-bold text-emerald-700 hover:underline bg-emerald-100 border border-emerald-300 px-2.5 py-1 rounded-md"
                    >
                      Xem phiên khám này →
                    </button>
                  </div>
                ) : null;
              })()}

              <div className="space-y-2">
                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">
                  Danh sách thuốc trong đơn ({(selectedHistoryPrescription.items || []).length} loại)
                </span>
                <div className="divide-y divide-slate-100 border border-slate-200 rounded-lg">
                  {(selectedHistoryPrescription.items || []).map((item, idx) => (
                    <div key={item.id || idx} className="p-3 bg-white space-y-1">
                      <div className="flex justify-between items-start">
                        <div>
                          <span className="text-xs font-bold text-slate-900">
                            {idx + 1}. {item.medicineName}
                          </span>
                          <span className="text-[10px] font-mono text-slate-400 block">
                            Mã: {item.medicineCode}
                          </span>
                        </div>
                        <span className="text-xs font-bold text-primary-700 font-mono bg-primary-50 px-2 py-0.5 border border-primary-200 rounded-sm">
                          x{item.quantity} {item.unit} ({item.duration || "N/A"} ngày)
                        </span>
                      </div>
                      <p className="text-xs text-slate-600">
                        <strong className="text-slate-700">Liều dùng:</strong> {item.dosage} · {item.frequency} ·{" "}
                        {item.timing}
                      </p>
                      {item.notes && (
                        <p className="text-[11px] text-amber-800 bg-amber-50 p-1.5 border border-amber-200 rounded-md">
                          Ghi chú: {item.notes}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              </div>

              {selectedHistoryPrescription.notes && (
                <div className="border border-slate-200 bg-slate-50 p-3 rounded-lg space-y-1">
                  <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                    Lời dặn chung của bác sĩ
                  </span>
                  <p className="text-xs text-slate-800 italic">{selectedHistoryPrescription.notes}</p>
                </div>
              )}
            </div>

            <div className="pt-4 border-t border-card-border flex justify-end">
              <button
                type="button"
                onClick={() => setSelectedHistoryPrescription(null)}
                className="bg-primary-600 hover:bg-primary-700 text-white font-bold px-5 py-2 text-xs transition-all rounded-md"
              >
                ĐÓNG CỬA SỔ
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
