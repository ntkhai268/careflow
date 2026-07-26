"use client";

import { useEffect, useState } from "react";
import { prescriptionApi, PrescriptionResponse } from "@/lib/prescription-api";
import { patientApi } from "@/lib/patient-api";
import { useAuth } from "@/contexts/AuthContext";
import LoadingSpinner from "@/components/ui/LoadingSpinner";

function PrescriptionStatusBadge({ status }: { status: string }) {
  const isDraft = status === "DRAFT";
  return (
    <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-semibold ${
      isDraft ? "bg-blue-50 text-blue-700" : "bg-emerald-50 text-emerald-700"
    }`}>
      <span className={`w-1.5 h-1.5 rounded-full inline-block ${isDraft ? "bg-blue-400" : "bg-emerald-400"}`} />
      {isDraft ? "Đơn nháp" : "Đã ký"}
    </span>
  );
}

export default function DashboardPrescriptionsPage() {
  const { user } = useAuth();
  const [prescriptions, setPrescriptions] = useState<PrescriptionResponse[]>([]);
  const [patientMap, setPatientMap] = useState<Record<string, { name: string; age: number }>>({});
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedPrescription, setSelectedPrescription] = useState<PrescriptionResponse | null>(null);

  useEffect(() => {
    async function loadPrescriptions() {
      try {
        setIsLoading(true);
        const p1Res = await prescriptionApi.getByPatient("f0000001-0000-0000-0000-000000000001");
        const p2Res = await prescriptionApi.getByPatient("f0000001-0000-0000-0000-000000000002");
        const p5Res = await prescriptionApi.getByPatient("f0000001-0000-0000-0000-000000000005");

        const combined = [...(p1Res.data || []), ...(p2Res.data || []), ...(p5Res.data || [])];
        combined.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
        setPrescriptions(combined);

        const map: Record<string, { name: string; age: number }> = {};
        for (const presc of combined) {
          if (!map[presc.patientId]) {
            try {
              const pRes = await patientApi.getPatientById(presc.patientId);
              if (pRes.data) {
                const birthYear = pRes.data.dateOfBirth ? new Date(pRes.data.dateOfBirth).getFullYear() : 1990;
                map[presc.patientId] = {
                  name: pRes.data.fullName || `Bệnh nhân (${presc.patientId.slice(0, 6)})`,
                  age: new Date().getFullYear() - birthYear
                };
              }
            } catch {
              map[presc.patientId] = { name: `Bệnh nhân (${presc.patientId.slice(0, 8)})`, age: 35 };
            }
          }
        }
        setPatientMap(map);
      } catch (err) {
        console.error(err);
        setError("Không thể kết nối API đơn thuốc từ Prescription-Service.");
      } finally { setIsLoading(false); }
    }
    loadPrescriptions();
  }, [user]);

  if (isLoading) {
    return <div className="flex h-[calc(100vh-200px)] items-center justify-center"><LoadingSpinner size="md" /></div>;
  }

  return (
    <div className="space-y-5">
      {/* Header */}
      <div>
        <h1 className="text-xl font-bold text-gray-900 tracking-tight">Lịch sử đơn thuốc đã kê</h1>
        <p className="mt-0.5 text-xs text-gray-400">
          Danh sách đơn thuốc điện tử được ghi nhận trên hệ thống liên thông y tế
        </p>
      </div>

      {error && <p className="text-xs font-semibold text-red-500">{error}</p>}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">

        {/* Left: Table */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 shadow-sm">
          <div className="flex items-center justify-between px-5 py-3.5 border-b border-gray-100">
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-bold text-gray-800">Danh sách đơn thuốc</h2>
              {prescriptions.length > 0 && (
                <span className="inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded-full bg-indigo-50 text-indigo-600 text-[10px] font-bold">
                  {prescriptions.length}
                </span>
              )}
            </div>
            <div className="flex items-center gap-2 text-[10px] text-gray-400">
              <span className="flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-blue-400 inline-block" /> Đơn nháp</span>
              <span className="flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-emerald-400 inline-block" /> Đã ký</span>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-gray-100 bg-indigo-50">
                  <th className="px-5 py-3 font-semibold text-gray-600">Bệnh nhân</th>
                  <th className="px-5 py-3 font-semibold text-gray-600">Chẩn đoán</th>
                  <th className="px-5 py-3 font-semibold text-gray-600 w-20">Số thuốc</th>
                  <th className="px-5 py-3 font-semibold text-gray-600 w-24">Trạng thái</th>
                  <th className="px-5 py-3 w-12"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {prescriptions.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-5 py-12 text-center">
                      <div className="flex flex-col items-center gap-2">
                        <svg className="w-8 h-8 text-gray-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                        </svg>
                        <p className="text-xs text-gray-400 italic">Chưa có đơn thuốc nào được kê gần đây</p>
                      </div>
                    </td>
                  </tr>
                ) : (
                  prescriptions.map((presc) => {
                    const pInfo = patientMap[presc.patientId] || { name: `Bệnh nhân (${presc.patientId.slice(0, 6)})`, age: 30 };
                    const isSelected = selectedPrescription?.id === presc.id;
                    return (
                      <tr key={presc.id}
                        className={`cursor-pointer transition-colors ${isSelected ? "bg-indigo-50" : "hover:bg-indigo-50"}`}
                        style={isSelected ? { borderLeft: "2px solid #6366F1" } : {}}
                        onClick={() => setSelectedPrescription(presc)}>
                        <td className="px-5 py-3">
                          <div className="flex items-center gap-2.5">
                            <div className="flex h-7 w-7 items-center justify-center rounded-full text-[10px] font-bold text-white"
                              style={{ background: "linear-gradient(135deg, #6366F1, #3B82F6)" }}>
                              {pInfo.name.charAt(0)}
                            </div>
                            <div>
                              <div className="font-semibold text-gray-800">{pInfo.name}</div>
                              <div className="text-[10px] text-gray-400">{pInfo.age} tuổi</div>
                            </div>
                          </div>
                        </td>
                        <td className="px-5 py-3 text-gray-500 max-w-xs truncate">{presc.diagnosis || "Chưa ghi nhận"}</td>
                        <td className="px-5 py-3">
                          <span className="font-semibold text-gray-700">{presc.items?.length || 0}</span>
                          <span className="text-gray-400 ml-1">loại</span>
                        </td>
                        <td className="px-5 py-3"><PrescriptionStatusBadge status={presc.status} /></td>
                        <td className="px-5 py-3 text-right">
                          <span className="text-[10px] font-bold text-indigo-500">{isSelected ? "▼" : "→"}</span>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Right: Detail panel */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm">
          <div className="px-5 py-3.5 border-b border-gray-100 rounded-t-xl" style={{ background: "linear-gradient(135deg, #EEF2FF, #E0E7FF)" }}>
            <h2 className="text-sm font-bold text-gray-800">Chi tiết đơn thuốc</h2>
            <p className="text-[10px] text-indigo-400 mt-0.5">Chọn đơn thuốc ở bảng trái để xem</p>
          </div>

          <div className="p-4">
            {selectedPrescription ? (
              <div className="space-y-3.5 text-xs">
                {/* Prescription ID */}
                <div className="bg-gray-50 rounded-lg px-3 py-2">
                  <span className="text-[10px] text-gray-400 font-semibold uppercase tracking-wide block mb-0.5">Mã đơn thuốc</span>
                  <p className="font-mono font-bold text-xs text-gray-700 truncate">{selectedPrescription.id}</p>
                </div>

                {/* Patient */}
                <div>
                  <span className="text-[10px] text-gray-400 font-semibold uppercase tracking-wide block mb-1">Bệnh nhân</span>
                  <div className="flex items-center gap-2">
                    <div className="flex h-8 w-8 items-center justify-center rounded-full text-[11px] font-bold text-white"
                      style={{ background: "linear-gradient(135deg, #6366F1, #3B82F6)" }}>
                      {(patientMap[selectedPrescription.patientId]?.name || "B").charAt(0)}
                    </div>
                    <p className="font-bold text-gray-800 text-sm">
                      {patientMap[selectedPrescription.patientId]?.name || `Bệnh nhân (${selectedPrescription.patientId.slice(0, 6)})`}
                    </p>
                  </div>
                </div>

                {/* Status */}
                <div>
                  <span className="text-[10px] text-gray-400 font-semibold uppercase tracking-wide block mb-1">Trạng thái</span>
                  <PrescriptionStatusBadge status={selectedPrescription.status} />
                </div>

                {/* Diagnosis */}
                <div>
                  <span className="text-[10px] text-gray-400 font-semibold uppercase tracking-wide block mb-0.5">Chẩn đoán</span>
                  <p className="text-xs text-gray-700">{selectedPrescription.diagnosis || "Không có chẩn đoán"}</p>
                </div>

                {/* Date */}
                <div>
                  <span className="text-[10px] text-gray-400 font-semibold uppercase tracking-wide block mb-0.5">Ngày kê đơn</span>
                  <p className="text-xs text-gray-500 font-mono">
                    {new Date(selectedPrescription.createdAt).toLocaleDateString("vi-VN", {
                      year: "numeric", month: "long", day: "numeric", hour: "2-digit", minute: "2-digit"
                    })}
                  </p>
                </div>

                {/* Follow-up */}
                {selectedPrescription.followUpDate && (
                  <div className="rounded-lg px-3 py-2 border border-indigo-100" style={{ background: "#EEF2FF" }}>
                    <span className="text-[10px] text-indigo-500 font-semibold uppercase tracking-wide block mb-0.5">Hẹn tái khám</span>
                    <p className="text-xs text-indigo-700 font-bold font-mono">
                      {new Date(selectedPrescription.followUpDate).toLocaleDateString("vi-VN")}
                    </p>
                  </div>
                )}

                {/* Medication list */}
                <div className="border-t border-gray-100 pt-3">
                  <span className="text-[10px] text-gray-400 font-semibold uppercase tracking-wide block mb-2">
                    Danh sách thuốc ({selectedPrescription.items.length} loại)
                  </span>
                  <div className="space-y-2 max-h-52 overflow-y-auto">
                    {selectedPrescription.items.map((item, idx) => (
                      <div key={item.id} className="rounded-lg border border-gray-100 bg-gray-50 p-2.5">
                        <div className="flex items-start justify-between gap-2">
                          <p className="font-bold text-gray-800 text-[11px]">{idx + 1}. {item.medicineName}</p>
                          <span className="text-[10px] text-gray-400 font-mono flex-shrink-0">×{item.quantity}</span>
                        </div>
                        <p className="text-[10px] text-gray-400 mt-0.5">Mã: {item.medicineCode} · {item.duration} ngày</p>
                        <p className="text-[10px] text-gray-500 italic mt-0.5">{item.dosage} · {item.frequency} · {item.timing}</p>
                      </div>
                    ))}
                  </div>
                </div>

                {selectedPrescription.notes && (
                  <div className="border-t border-gray-100 pt-3">
                    <span className="text-[10px] text-gray-400 font-semibold uppercase tracking-wide block mb-0.5">Ghi chú</span>
                    <p className="text-[11px] text-gray-500 italic">{selectedPrescription.notes}</p>
                  </div>
                )}
              </div>
            ) : (
              <div className="text-center py-12 flex flex-col items-center gap-3">
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-indigo-50">
                  <svg className="w-6 h-6 text-indigo-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                  </svg>
                </div>
                <p className="text-xs text-gray-400 italic">Chọn một đơn thuốc ở bảng bên trái để xem chi tiết</p>
              </div>
            )}
          </div>
        </div>

      </div>
    </div>
  );
}
