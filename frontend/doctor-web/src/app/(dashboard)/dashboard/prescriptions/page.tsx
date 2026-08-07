"use client";

import { useEffect, useState } from "react";
import { prescriptionApi, PrescriptionResponse } from "@/lib/prescription-api";
import { patientApi } from "@/lib/patient-api";
import { useAuth } from "@/contexts/AuthContext";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import { getErrorMessage } from "@/lib/error-utils";

function PrescriptionStatusBadge({ status }: { status: string }) {
  const isDraft = status === "DRAFT";
  const isCancelled = status === "CANCELLED" || status === "CANCELLED_BY_AMENDMENT";
  return (
    <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-semibold ${
      isCancelled ? "bg-rose-50 text-rose-700" : isDraft ? "bg-blue-50 text-blue-700" : "bg-emerald-50 text-emerald-700"
    }`}>
      <span className={`w-1.5 h-1.5 rounded-full inline-block ${isCancelled ? "bg-rose-400" : isDraft ? "bg-blue-400" : "bg-emerald-400"}`} />
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
  const [expandedPatientId, setExpandedPatientId] = useState<string | null>(null);

  useEffect(() => {
    async function loadPrescriptions() {
      try {
        setIsLoading(true);
        setError("");
        
        // Fetch prescriptions with individual try-catch to prevent uncaught promise rejection
        const fetchPatientPrescriptions = async (patientId: string) => {
          try {
            const res = await prescriptionApi.getByPatient(patientId);
            return res.data || [];
          } catch (err: unknown) {
            console.warn(`[Prescription API] Unable to fetch for patient ${patientId}:`, getErrorMessage(err));
            return [];
          }
        };

        const [p1List, p2List, p5List] = await Promise.all([
          fetchPatientPrescriptions("f0000001-0000-0000-0000-000000000001"),
          fetchPatientPrescriptions("f0000001-0000-0000-0000-000000000002"),
          fetchPatientPrescriptions("f0000001-0000-0000-0000-000000000005"),
        ]);

        const combined = [...p1List, ...p2List, ...p5List];
        combined.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
        setPrescriptions(combined);

        if (combined.length === 0) {
          // If no prescriptions returned or service is down
          setError("Không thể tải danh sách đơn thuốc từ Prescription Service. Vui lòng kiểm tra lại dịch vụ.");
        }

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
      } catch (err: unknown) {
        console.error("[Prescriptions Page] Error loading data:", getErrorMessage(err));
        setError("Không thể kết nối API đơn thuốc từ Prescription-Service.");
        setPrescriptions([]);
      } finally {
        setIsLoading(false);
      }
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

        {/* Left: Patient Grouped Prescriptions List */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 shadow-sm flex flex-col h-[560px]">
          <div className="flex items-center justify-between px-5 py-3.5 border-b border-gray-100 flex-shrink-0">
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-bold text-gray-800">Bệnh nhân & Đơn thuốc đã kê</h2>
              {prescriptions.length > 0 && (
                <span className="inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded-full bg-indigo-50 text-indigo-600 text-[10px] font-bold">
                  {prescriptions.length} đơn
                </span>
              )}
            </div>
            <div className="flex items-center gap-3 text-[10px] text-gray-400">
              <span className="flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-blue-400 inline-block" /> Đơn nháp</span>
              <span className="flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-emerald-400 inline-block" /> Đã ký</span>
            </div>
          </div>

          {/* Scrollable Patient Groups List */}
          <div className="overflow-y-auto flex-1 p-3 space-y-2.5 custom-scrollbar">
            {prescriptions.length === 0 ? (
              <div className="text-center py-16">
                <div className="flex flex-col items-center gap-2">
                  <svg className="w-8 h-8 text-gray-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                  </svg>
                  <p className="text-xs text-gray-400 italic">Chưa có đơn thuốc nào được kê trên hệ thống</p>
                </div>
              </div>
            ) : (
              (() => {
                // Group prescriptions by patientId
                const grouped = prescriptions.reduce((acc, p) => {
                  if (!acc[p.patientId]) acc[p.patientId] = [];
                  acc[p.patientId].push(p);
                  return acc;
                }, {} as Record<string, PrescriptionResponse[]>);

                return Object.entries(grouped).map(([pId, pList]) => {
                  const pInfo = patientMap[pId] || { name: `Bệnh nhân (${pId.slice(0, 6)})`, age: 30 };
                  const isExpanded = expandedPatientId === pId;

                  return (
                    <div key={pId} className="border border-gray-200/80 rounded-xl overflow-hidden bg-white shadow-xs transition-all">
                      {/* Patient Group Header (Accordion Toggle) */}
                      <div
                        onClick={() => setExpandedPatientId(isExpanded ? null : pId)}
                        className={`flex items-center justify-between px-4 py-3 cursor-pointer transition-colors ${
                          isExpanded ? "bg-indigo-50/70 border-b border-indigo-100" : "hover:bg-gray-50"
                        }`}
                      >
                        <div className="flex items-center gap-3">
                          <div className="flex h-9 w-9 items-center justify-center rounded-full text-xs font-bold text-white flex-shrink-0"
                            style={{ background: "linear-gradient(135deg, #6366F1, #3B82F6)" }}>
                            {pInfo.name.charAt(0)}
                          </div>
                          <div>
                            <div className="flex items-center gap-2">
                              <h3 className="font-bold text-gray-800 text-xs">{pInfo.name}</h3>
                              <span className="text-[10px] bg-gray-100 text-gray-500 px-2 py-0.5 rounded-full font-medium">
                                {pInfo.age} tuổi
                              </span>
                            </div>
                            <p className="text-[10px] text-gray-400 mt-0.5">
                              Tổng cộng <strong className="text-indigo-600 font-bold">{pList.length}</strong> đơn thuốc trong lịch sử
                            </p>
                          </div>
                        </div>

                        <div className="flex items-center gap-2">
                          <span className="text-[10px] font-semibold text-indigo-500 bg-indigo-50 px-2 py-1 rounded-md">
                            {isExpanded ? "Thu gọn ▲" : "Xem lịch sử đơn ▼"}
                          </span>
                        </div>
                      </div>

                      {/* Nested Prescription History List */}
                      {isExpanded && (
                        <div className="bg-gray-50/50 p-2 space-y-2 max-h-64 overflow-y-auto custom-scrollbar border-t border-gray-100">
                          {pList.map((presc) => {
                            const isSelected = selectedPrescription?.id === presc.id;
                            return (
                              <div
                                key={presc.id}
                                onClick={() => setSelectedPrescription(presc)}
                                className={`p-3 rounded-lg border transition-all cursor-pointer flex items-center justify-between gap-3 ${
                                  isSelected
                                    ? "bg-white border-indigo-400 shadow-sm ring-1 ring-indigo-300"
                                    : "bg-white border-gray-200/80 hover:border-indigo-200 hover:bg-indigo-50/30"
                                }`}
                              >
                                <div className="min-w-0 flex-1">
                                  <div className="flex items-center gap-2 mb-1">
                                    <span className="font-mono text-[10px] font-bold text-gray-600 bg-gray-100 px-1.5 py-0.5 rounded">
                                      {presc.id.slice(0, 8)}...
                                    </span>
                                    <PrescriptionStatusBadge status={presc.status} />
                                  </div>
                                  <p className="text-xs font-semibold text-gray-800 truncate">
                                    {presc.diagnosis || "Chẩn đoán chưa ghi nhận"}
                                  </p>
                                  <p className="text-[10px] text-gray-400 font-mono mt-0.5">
                                    Ngày kê: {new Date(presc.createdAt).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" })} · {presc.items?.length || 0} thuốc
                                  </p>
                                </div>

                                <div className="text-right flex-shrink-0">
                                  <span className={`text-[11px] font-bold ${isSelected ? "text-indigo-600" : "text-gray-400"}`}>
                                    {isSelected ? "Đang chọn ✓" : "Chi tiết →"}
                                  </span>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  );
                });
              })()
            )}
          </div>
        </div>

        {/* Right: Detail panel */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm flex flex-col h-[560px]">
          <div className="px-5 py-3.5 border-b border-gray-100 rounded-t-xl flex-shrink-0" style={{ background: "linear-gradient(135deg, #EEF2FF, #E0E7FF)" }}>
            <h2 className="text-sm font-bold text-gray-800">Chi tiết đơn thuốc</h2>
            <p className="text-[10px] text-indigo-500 mt-0.5">Chọn đơn thuốc ở bảng bên trái để xem</p>
          </div>

          <div className="p-4 flex-1 overflow-y-auto custom-scrollbar">
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
                    <div className="flex h-8 w-8 items-center justify-center rounded-full text-[11px] font-bold text-white flex-shrink-0"
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
                  <div className="space-y-2 max-h-52 overflow-y-auto custom-scrollbar">
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
              <div className="text-center py-16 flex flex-col items-center gap-3">
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
