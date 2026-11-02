"use client";

import HandoverPicker from "@/components/ui/HandoverPicker";
import { ConsultationResponse } from "@/lib/consultation-api";
import { getErrorMessage } from "@/lib/error-utils";
import { labApi, LabCatalogItem, LabOrderResponse } from "@/lib/lab-api";

interface LabOrdersTabProps {
  consultationId: string;
  consultation: ConsultationResponse;
  userId?: string;
  symptoms: string;
  isConsultationLocked: boolean;
  isSaving: boolean;
  setIsSaving: (val: boolean) => void;
  labCatalog: LabCatalogItem[];
  selectedLabServices: LabCatalogItem[];
  setSelectedLabServices: React.Dispatch<React.SetStateAction<LabCatalogItem[]>>;
  labClinicalNote: string;
  setLabClinicalNote: (val: string) => void;
  existingLabOrders: LabOrderResponse[];
  setExistingLabOrders: (orders: LabOrderResponse[]) => void;
  isSubmittingLabOrder: boolean;
  setIsSubmittingLabOrder: (val: boolean) => void;
  executeWaitForResults: () => Promise<void>;
  showToast: (message: string, type?: "success" | "danger" | "warning") => void;
}

export default function LabOrdersTab({
  consultationId,
  consultation,
  userId,
  symptoms,
  isConsultationLocked,
  isSaving,
  setIsSaving,
  labCatalog,
  selectedLabServices,
  setSelectedLabServices,
  labClinicalNote,
  setLabClinicalNote,
  existingLabOrders,
  setExistingLabOrders,
  isSubmittingLabOrder,
  setIsSubmittingLabOrder,
  executeWaitForResults,
  showToast,
}: LabOrdersTabProps) {
  return (
    <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] rounded-b-lg space-y-5">
      <div className="flex items-center justify-between border-b border-card-border pb-3">
        <div>
          <h2 className="text-xs font-bold text-[#2B1D30] uppercase tracking-wide">
            Chỉ định Cận lâm sàng (Xét nghiệm &amp; Cẩn lâm sàng)
          </h2>
          <p className="text-[11px] text-[#6A5C70] mt-0.5">
            Chọn các chỉ định cận lâm sàng cần thiết cho ca khám. Hệ thống sẽ tự động tạo lượt xếp hàng tại khu tương ứng.
          </p>
        </div>
        {selectedLabServices.length > 0 && !isConsultationLocked && (
          <button
            type="button"
            disabled={isSubmittingLabOrder}
            onClick={async () => {
              if (!userId || !consultation) return;
              setIsSubmittingLabOrder(true);
              try {
                const items = selectedLabServices.map((s) => ({
                  serviceCode: s.serviceCode,
                  serviceName: s.serviceName,
                  servicePointId: s.servicePointId,
                  required: true,
                  preparationInstruction: s.preparationInstruction,
                }));
                await labApi.createOrder({
                  consultationId,
                  patientId: consultation.patientId,
                  items,
                  clinicalNote: labClinicalNote || symptoms || "Chỉ định cận lâm sàng",
                  paymentRequired: false,
                });
                let transitioned = true;
                try {
                  await executeWaitForResults();
                } catch (failure) {
                  transitioned = false;
                  showToast(
                    getErrorMessage(
                      failure,
                      "Đã tạo chỉ định; bước chuyển chờ CLS chưa xong. Dùng Tiếp tục chờ CLS để thử lại."
                    ),
                    "warning"
                  );
                }
                if (transitioned) {
                  showToast(
                    "Đã tạo chỉ định và chuyển ca sang chờ CLS. Lượt xét nghiệm đang được đồng bộ.",
                    "success"
                  );
                }
                setSelectedLabServices([]);
                setLabClinicalNote("");
                const updatedOrders = await labApi.getByConsultation(consultationId);
                setExistingLabOrders(updatedOrders.data);
              } catch (err) {
                showToast(
                  err instanceof Error ? err.message : "Không thể tạo chỉ định Cận lâm sàng.",
                  "danger"
                );
              } finally {
                setIsSubmittingLabOrder(false);
              }
            }}
            className="px-3.5 py-1.5 bg-[#3B82F6] hover:bg-[#2563EB] text-white text-xs font-semibold rounded-md shadow-xs transition-all flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
          >
            {isSubmittingLabOrder ? "Đang gửi chỉ định..." : `Gửi ${selectedLabServices.length} chỉ định CLS`}
          </button>
        )}
      </div>

      {existingLabOrders.some((order) => !["REVIEWED", "CANCELLED"].includes(order.status)) &&
        (consultation.status === "IN_PROGRESS" || consultation.status === "AWAITING_CLS") && (
          <button
            disabled={isSaving || isSubmittingLabOrder}
            className="rounded-lg border border-indigo-200 px-3 py-2 text-sm"
            onClick={async () => {
              setIsSaving(true);
              try {
                await executeWaitForResults();
                showToast("Đã chuyển sang chờ xét nghiệm và đóng lượt khám ban đầu.");
              } catch (failure) {
                showToast(getErrorMessage(failure, "Chưa chuyển xong giai đoạn chờ CLS."), "warning");
              } finally {
                setIsSaving(false);
              }
            }}
          >
            Tiếp tục chờ CLS
          </button>
        )}

      {existingLabOrders.length > 0 && (
        <div className="space-y-3">
          <h3 className="text-xs font-bold text-[#2B1D30] uppercase tracking-wider flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-indigo-500 inline-block" />
            <span>Các Chỉ định CLS đã phát hành ({existingLabOrders.length})</span>
          </h3>
          <div className="space-y-2">
            {existingLabOrders.map((order) => (
              <div key={order.id} className="border border-indigo-100 bg-indigo-50/50 p-3 rounded-md space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-mono font-bold text-indigo-900">Order ID: {order.id.slice(0, 8)}...</span>
                  <span
                    className={`px-2 py-0.5 text-[10px] font-bold uppercase rounded ${
                      order.status === "RESULT_AVAILABLE"
                        ? "bg-emerald-100 text-emerald-800"
                        : order.status === "IN_PROGRESS"
                          ? "bg-purple-100 text-purple-800"
                          : "bg-amber-100 text-amber-800"
                    }`}
                  >
                    {order.status === "RESULT_AVAILABLE"
                      ? "Đã có kết quả"
                      : order.status === "IN_PROGRESS"
                        ? "Đang thực hiện"
                        : order.status === "REVIEWED"
                          ? "Bác sĩ đã đọc"
                          : order.status === "CANCELLED"
                            ? "Đã hủy"
                            : "Đã vào hàng chờ CLS"}
                  </span>
                </div>
                {!isConsultationLocked && (
                  <button
                    disabled={isSaving}
                    className="rounded-lg border border-indigo-200 px-3 py-2 text-xs text-indigo-800"
                    onClick={async () => {
                      setIsSaving(true);
                      try {
                        await labApi.synchronizeReview(order.id);
                        showToast("Đã đối chiếu lượt CLS. Nếu đủ kết quả, hệ thống sẽ đồng bộ lượt đọc kết quả.");
                      } catch (failure) {
                        showToast(getErrorMessage(failure, "Chưa đồng bộ được lượt CLS."), "warning");
                      } finally {
                        setIsSaving(false);
                      }
                    }}
                  >
                    Đồng bộ lượt CLS
                  </button>
                )}
                {order.status === "RESULT_AVAILABLE" && !isConsultationLocked && (
                  <button
                    disabled={isSaving}
                    className="rounded-lg border border-emerald-300 px-3 py-2 text-xs text-emerald-800 disabled:opacity-50"
                    onClick={async () => {
                      setIsSaving(true);
                      try {
                        await labApi.markReviewed(order.id);
                        setExistingLabOrders((await labApi.getByConsultation(consultationId)).data);
                        showToast("Đã ghi nhận bác sĩ đọc kết quả.");
                      } catch (failure) {
                        showToast(getErrorMessage(failure, "Chưa ghi nhận đọc kết quả."), "danger");
                      } finally {
                        setIsSaving(false);
                      }
                    }}
                  >
                    Tôi đã đọc kết quả chỉ định này
                  </button>
                )}
                <div className="divide-y divide-indigo-100 border border-indigo-100 bg-white rounded-md text-xs">
                  {order.items.map((item) => (
                    <div key={item.id} className="p-2.5 flex items-center justify-between">
                      <div>
                        <span className="font-semibold text-gray-900">{item.serviceName}</span>
                        <span className="text-[11px] text-gray-500 block">Điểm thực hiện: {item.servicePointId}</span>
                      </div>
                      {item.resultValue ? (
                        <div className="text-right font-mono text-xs">
                          <span className="font-bold text-emerald-700">
                            {item.resultValue} {item.unit || ""}
                          </span>
                          {item.referenceRange && (
                            <span className="text-[10px] text-gray-400 block">Chuẩn: {item.referenceRange}</span>
                          )}
                        </div>
                      ) : (
                        <span className="text-[11px] text-amber-600 italic">Chờ KTV nhập kết quả</span>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {!isConsultationLocked && (
        <div className="space-y-3">
          <h3 className="text-xs font-bold text-[#2B1D30] uppercase tracking-wider">
            Chọn dịch vụ Cận lâm sàng từ Danh mục
          </h3>
          <HandoverPicker
            label="Dịch vụ cận lâm sàng"
            value=""
            options={labCatalog
              .filter((service) => !selectedLabServices.some((selected) => selected.serviceCode === service.serviceCode))
              .map((service) => ({
                value: service.serviceCode,
                label: `${service.serviceName} · ${service.servicePointId}`,
              }))}
            onChange={(code) => {
              const service = labCatalog.find((item) => item.serviceCode === code);
              if (service) setSelectedLabServices((current) => [...current, service]);
            }}
          />
          <div className="flex flex-wrap gap-2">
            {selectedLabServices.map((service) => (
              <button
                type="button"
                key={service.serviceCode}
                onClick={() =>
                  setSelectedLabServices((current) =>
                    current.filter((item) => item.serviceCode !== service.serviceCode)
                  )
                }
                className="rounded-lg border border-purple-200 bg-purple-50 px-3 py-2 text-xs text-purple-800"
                aria-label={`Bỏ ${service.serviceName}`}
              >
                {service.serviceName} ×
              </button>
            ))}
          </div>

          <div>
            <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Ghi chú chỉ định cận lâm sàng cho KTV</label>
            <input
              type="text"
              value={labClinicalNote}
              onChange={(e) => setLabClinicalNote(e.target.value)}
              placeholder="Ví dụ: Kiểm tra nghi ngờ viêm dạ dày, loại trừ thiếu máu..."
              className="w-full border border-input-border bg-input-bg px-3 py-2 text-xs text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-md"
            />
          </div>
        </div>
      )}
    </div>
  );
}
