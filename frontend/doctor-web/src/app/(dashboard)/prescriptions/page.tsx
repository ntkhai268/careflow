"use client";

import { useEffect, useState } from "react";
import { prescriptionApi, PrescriptionResponse } from "@/lib/prescription-api";

// Simple mock dictionary to map patient UUIDs to names in UI
const MOCK_PATIENTS: Record<string, { name: string; age: number }> = {
  "f0000001-0000-0000-0000-000000000001": { name: "Nguyễn Thị Mai", age: 45 },
  "f0000001-0000-0000-0000-000000000002": { name: "Trần Văn Hùng", age: 62 },
  "f0000001-0000-0000-0000-000000000003": { name: "Lê Thị Hoa", age: 28 },
  "f0000001-0000-0000-0000-000000000004": { name: "Phạm Đức Anh", age: 7 },
  "f0000001-0000-0000-0000-000000000005": { name: "Võ Thị Lan", age: 55 },
};

export default function PrescriptionsPage() {
  const [prescriptions, setPrescriptions] = useState<PrescriptionResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedPrescription, setSelectedPrescription] = useState<PrescriptionResponse | null>(null);

  useEffect(() => {
    async function loadPrescriptions() {
      try {
        setIsLoading(true);
        // We can query prescriptions by fetching for patient 1 or iterate through mock patients
        // Let's aggregate prescriptions from patient 1 and 2 to show a list
        const patient1Res = await prescriptionApi.getByPatient("f0000001-0000-0000-0000-000000000001");
        const patient2Res = await prescriptionApi.getByPatient("f0000001-0000-0000-0000-000000000002");
        const patient5Res = await prescriptionApi.getByPatient("f0000001-0000-0000-0000-000000000005");

        const combined = [...(patient1Res.data || []), ...(patient2Res.data || []), ...(patient5Res.data || [])];
        // Sort by date desc
        combined.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
        setPrescriptions(combined);
      } catch (err) {
        console.error(err);
        setError("Không thể kết nối API đơn thuốc từ Prescription-Service.");
      } finally {
        setIsLoading(false);
      }
    }
    loadPrescriptions();
  }, []);

  if (isLoading) {
    return (
      <div className="flex h-[calc(100vh-200px)] items-center justify-center">
        <div className="h-8 w-8 animate-spin border-4 border-primary-200 border-t-primary-600" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-[#2B1D30]">Lịch sử đơn thuốc đã kê</h1>
        <p className="mt-1 text-sm text-[#6A5C70]">
          Danh sách đơn thuốc điện tử được ghi nhận trên hệ thống liên thông y tế
        </p>
      </div>

      {error && (
        <div className="border border-danger/30 bg-danger-light p-3 text-sm text-danger">
          <span>{error}</span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Left Column (2/3 width): Prescriptions Table List */}
        <div className="lg:col-span-2 border border-card-border bg-card-bg">
          <div className="px-6 py-4 border-b border-card-border">
            <h2 className="text-sm font-bold text-[#2B1D30] uppercase tracking-wide">Danh sách đơn thuốc</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-card-border bg-[#F3E8F5]">
                  <th className="px-6 py-3 font-semibold text-[#6A5C70]">Bệnh nhân</th>
                  <th className="px-6 py-3 font-semibold text-[#6A5C70]">Chẩn đoán</th>
                  <th className="px-6 py-3 font-semibold text-[#6A5C70] w-28">Trạng thái</th>
                  <th className="px-6 py-3 font-semibold text-[#6A5C70] w-20"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-card-border">
                {prescriptions.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-6 py-12 text-center text-gray-400 italic">
                      Chưa có đơn thuốc nào được kê gần đây
                    </td>
                  </tr>
                ) : (
                  prescriptions.map((presc) => {
                    const pInfo = MOCK_PATIENTS[presc.patientId] || { name: "Bệnh nhân khác", age: 30 };
                    return (
                      <tr 
                        key={presc.id} 
                        className={`hover:bg-[#F8F6F9] cursor-pointer transition-colors ${selectedPrescription?.id === presc.id ? "bg-[#F3E8F5]" : ""}`}
                        onClick={() => setSelectedPrescription(presc)}
                      >
                        <td className="px-6 py-3.5">
                          <div className="font-semibold text-[#2B1D30]">{pInfo.name}</div>
                          <div className="text-xs text-[#6A5C70]">{pInfo.age} tuổi</div>
                        </td>
                        <td className="px-6 py-3.5 text-[#6A5C70] max-w-xs truncate">
                          {presc.diagnosis || "Chưa ghi nhận"}
                        </td>
                        <td className="px-6 py-3.5">
                          <span className={`inline-flex items-center px-2 py-0.5 text-xs font-semibold ${
                            presc.status === "DRAFT"
                              ? "bg-warning-light text-warning border border-warning/20"
                              : "bg-success-light text-success border border-success/20"
                          }`}>
                            {presc.status === "DRAFT" ? "Đơn nháp" : "Đã ký"}
                          </span>
                        </td>
                        <td className="px-6 py-3.5 text-right">
                          <span className="text-xs font-bold text-primary-600">Xem →</span>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Right Column (1/3 width): Prescription Detail View */}
        <div className="border border-card-border bg-card-bg">
          <div className="px-6 py-4 border-b border-card-border bg-[#F3E8F5]">
            <h2 className="text-sm font-bold text-[#2B1D30] uppercase tracking-wide">Chi tiết đơn thuốc</h2>
          </div>
          <div className="p-5">
            {selectedPrescription ? (
              <div className="space-y-4">
                <div>
                  <span className="text-[10px] text-gray-400 font-bold uppercase">Mã đơn thuốc</span>
                  <p className="font-mono font-bold text-sm text-[#2B1D30] truncate">{selectedPrescription.id}</p>
                </div>
                
                <div>
                  <span className="text-[10px] text-gray-400 font-bold uppercase">Bệnh nhân</span>
                  <p className="font-bold text-[#2B1D30] text-base">
                    {MOCK_PATIENTS[selectedPrescription.patientId]?.name || "Bệnh nhân khác"}
                  </p>
                </div>

                <div>
                  <span className="text-[10px] text-gray-400 font-bold uppercase">Chẩn đoán</span>
                  <p className="text-sm text-[#2B1D30]">{selectedPrescription.diagnosis || "Không có chẩn đoán"}</p>
                </div>

                <div>
                  <span className="text-[10px] text-gray-400 font-bold uppercase">Ngày kê đơn</span>
                  <p className="text-sm text-[#2B1D30] font-mono">
                    {new Date(selectedPrescription.createdAt).toLocaleDateString("vi-VN", {
                      year: "numeric",
                      month: "long",
                      day: "numeric",
                      hour: "2-digit",
                      minute: "2-digit"
                    })}
                  </p>
                </div>

                {selectedPrescription.followUpDate && (
                  <div>
                    <span className="text-[10px] text-primary-600 font-bold uppercase">📅 Hẹn tái khám</span>
                    <p className="text-sm text-primary-600 font-bold font-mono">
                      {new Date(selectedPrescription.followUpDate).toLocaleDateString("vi-VN")}
                    </p>
                  </div>
                )}

                <div className="border-t border-card-border pt-3">
                  <span className="text-[10px] text-gray-400 font-bold uppercase block mb-2">Danh sách thuốc kê</span>
                  <div className="space-y-2 max-h-60 overflow-y-auto">
                    {selectedPrescription.items.map((item, idx) => (
                      <div key={item.id} className="border border-card-border p-2 bg-[#F8F6F9] text-xs">
                        <p className="font-bold text-[#2B1D30]">{idx + 1}. {item.medicineName}</p>
                        <p className="text-[#6A5C70]">Mã: {item.medicineCode} · SL: {item.quantity} ({item.duration} ngày)</p>
                        <p className="text-[#6A5C70] italic">Cách dùng: {item.dosage} · {item.frequency} · {item.timing}</p>
                      </div>
                    ))}
                  </div>
                </div>

                {selectedPrescription.notes && (
                  <div className="border-t border-card-border pt-3">
                    <span className="text-[10px] text-gray-400 font-bold uppercase">Ghi chú</span>
                    <p className="text-xs text-[#6A5C70] italic mt-1">{selectedPrescription.notes}</p>
                  </div>
                )}
              </div>
            ) : (
              <p className="text-sm text-gray-400 italic text-center py-12">Chọn một đơn thuốc ở bảng bên trái để xem chi tiết</p>
            )}
          </div>
        </div>

      </div>
    </div>
  );
}
