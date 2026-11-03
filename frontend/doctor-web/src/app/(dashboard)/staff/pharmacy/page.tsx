"use client";

import { useCallback, useEffect, useState } from "react";
import { queueApi, QueueEntry } from "@/lib/queue-api";
import { prescriptionApi, PrescriptionResponse } from "@/lib/prescription-api";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import { getErrorMessage } from "@/lib/error-utils";
import { pharmacyStatusLabel } from "@/components/queue/QueueBadges";
import PharmacyVerificationPanel from "@/components/queue/PharmacyVerificationPanel";
import { notifyAi, useVisibilityPolling } from "@/components/queue/useQueueOperations";

const PHARMACY_SERVICE_POINT = "PHARMACY-MAIN-01";

export default function StaffPharmacyPage() {
  const [entries, setEntries] = useState<QueueEntry[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [activeEntry, setActiveEntry] = useState<QueueEntry | null>(null);
  const [activePrescription, setActivePrescription] = useState<PrescriptionResponse | null>(null);
  const [isLoadingPrescription, setIsLoadingPrescription] = useState(false);
  const [prescriptionError, setPrescriptionError] = useState<string | null>(null);
  const [isStarting, setIsStarting] = useState(false);
  const [isDispensing, setIsDispensing] = useState(false);
  const [toast, setToast] = useState<{
    message: string;
    type: "success" | "danger" | "warning";
  } | null>(null);

  const showToast = useCallback(
    (message: string, type: "success" | "danger" | "warning" = "success") => {
      setToast({ message, type });
      window.setTimeout(() => setToast(null), 3000);
    },
    []
  );

  const replaceEntry = useCallback((updated: QueueEntry) => {
    setEntries(
      (current) =>
        current?.map((entry) => (entry.entryId === updated.entryId ? updated : entry)) ?? null
    );
    setActiveEntry((current) => (current?.entryId === updated.entryId ? updated : current));
  }, []);

  const fetchPharmacyQueue = useCallback(async (showLoading = true) => {
    if (showLoading) setIsLoading(true);
    setError(null);
    try {
      const response = await queueApi.getServicePointActive(PHARMACY_SERVICE_POINT);
      const nextEntries = response.data?.entries ?? [];
      setEntries(nextEntries);
      setActiveEntry((current) => {
        if (current) {
          return nextEntries.find((entry) => entry.entryId === current.entryId) ?? current;
        }
        return (
          nextEntries.find(
            (entry) => entry.queueStatus === "IN_PROGRESS" || entry.queueStatus === "CALLED"
          ) ?? null
        );
      });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Không thể tải danh sách hàng đợi phát thuốc."));
      setEntries([]);
    } finally {
      if (showLoading) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void fetchPharmacyQueue(), 0);
    return () => {
      window.clearTimeout(timer);
    };
  }, [fetchPharmacyQueue]);

  useVisibilityPolling(
    true,
    useCallback(() => void fetchPharmacyQueue(false), [fetchPharmacyQueue]),
    10_000
  );

  useEffect(() => {
    let cancelled = false;
    const timer = window.setTimeout(() => {
      if (!activeEntry?.prescriptionId) {
        setActivePrescription(null);
        setPrescriptionError(activeEntry ? "Lượt này chưa liên kết với toa thuốc." : null);
        setIsLoadingPrescription(false);
        return;
      }

      setActivePrescription(null);
      setPrescriptionError(null);
      setIsLoadingPrescription(true);

      void prescriptionApi
        .getPrescription(activeEntry.prescriptionId)
        .then((response) => {
          if (cancelled) return;
          if (!response.data) {
            setPrescriptionError("Không tìm thấy chi tiết toa thuốc.");
            return;
          }
          if (response.data.patientId !== activeEntry.patientId) {
            setPrescriptionError("Mã bệnh nhân trong queue không khớp với mã bệnh nhân trên toa.");
            return;
          }
          setActivePrescription(response.data);
        })
        .catch((err: unknown) => {
          if (!cancelled) {
            setPrescriptionError(getErrorMessage(err, "Không thể tải chi tiết toa thuốc."));
          }
        })
        .finally(() => {
          if (!cancelled) setIsLoadingPrescription(false);
        });
    }, 0);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [activeEntry]);

  const handleCallNext = async () => {
    try {
      const response = await queueApi.callNextAtServicePoint(PHARMACY_SERVICE_POINT);
      if (!response.data?.queueNumber) {
        notifyAi("Hiện tại chưa có bệnh nhân nào đâu ạ!");
        showToast("Hiện chưa có bệnh nhân nào trong hàng đợi.", "warning");
        return;
      }
      replaceEntry(response.data);
      notifyAi(`Mời số thứ tự ${response.data.queueNumber} tới quầy phát thuốc ạ!`);
      showToast(`Mời số thứ tự ${response.data.queueNumber} tới quầy phát thuốc!`);
      await fetchPharmacyQueue();
    } catch (err: unknown) {
      const message = getErrorMessage(err, "Không thể gọi lượt phát thuốc tiếp theo.");
      notifyAi(message);
      showToast(message, "danger");
    }
  };

  const handleStartVerification = async () => {
    if (!activeEntry || activeEntry.queueStatus !== "CALLED") return;
    setIsStarting(true);
    try {
      const response = await queueApi.startEntry(activeEntry.entryId);
      replaceEntry(response.data);
      showToast("Đã bắt đầu đối chiếu bệnh nhân và toa thuốc.");
    } catch (err: unknown) {
      showToast(getErrorMessage(err, "Không thể bắt đầu lượt phát thuốc."), "danger");
    } finally {
      setIsStarting(false);
    }
  };

  const handleDispenseMedicine = async () => {
    if (!activeEntry || !activePrescription) return;
    if (activeEntry.queueStatus !== "IN_PROGRESS") {
      showToast("Cần bắt đầu đối chiếu trước khi xác nhận phát thuốc.", "warning");
      return;
    }
    if (activePrescription.status !== "CONFIRMED") {
      showToast("Chỉ được phát toa đang ở trạng thái CONFIRMED.", "warning");
      return;
    }
    if (activePrescription.patientId !== activeEntry.patientId) {
      showToast("Mã bệnh nhân trên queue và toa không khớp.", "danger");
      return;
    }

    setIsDispensing(true);
    try {
      await prescriptionApi.dispensePrescription(activePrescription.id);
      notifyAi(`Đã hoàn tất cấp phát thuốc cho số thứ tự ${activeEntry.queueNumber} rồi ạ!`);
      showToast(`Đã hoàn tất phát thuốc cho số thứ tự ${activeEntry.queueNumber}!`);
      setActiveEntry(null);
      setActivePrescription(null);
      await fetchPharmacyQueue();
    } catch (err: unknown) {
      const message = getErrorMessage(err, "Đã xảy ra lỗi khi xác nhận phát thuốc.");
      notifyAi(message);
      showToast(message, "danger");
    } finally {
      setIsDispensing(false);
    }
  };

  const activePatientMatches = Boolean(
    activeEntry && activePrescription && activeEntry.patientId === activePrescription.patientId
  );
  const canDispense = Boolean(
    activeEntry?.queueStatus === "IN_PROGRESS" &&
      activePrescription?.status === "CONFIRMED" &&
      activePatientMatches &&
      !isLoadingPrescription &&
      !prescriptionError
  );
  const hasServingEntry =
    activeEntry?.queueStatus === "CALLED" || activeEntry?.queueStatus === "IN_PROGRESS";

  return (
    <div className="space-y-6 pb-12">
      {toast && (
        <div
          className={`fixed top-5 right-5 z-50 max-w-md rounded-xl border p-4 text-white shadow-lg ${
            toast.type === "success"
              ? "border-[#0277A8] bg-[#0B2534]"
              : toast.type === "warning"
                ? "border-[#A86100] bg-[#A86100]"
                : "border-[#BA1A1A] bg-[#BA1A1A]"
          }`}
        >
          <div className="flex items-center justify-between gap-4">
            <span className="text-xs font-semibold">{toast.message}</span>
            <button
              onClick={() => setToast(null)}
              className="text-xs font-bold text-white/70 hover:text-white"
              aria-label="Đóng thông báo"
            >
              X
            </button>
          </div>
        </div>
      )}

      <div className="flex flex-col justify-between gap-4 rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-xs md:flex-row md:items-center">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-block h-2.5 w-2.5 animate-pulse rounded-full bg-[#087F6A]" />
            <span className="text-xs font-bold uppercase tracking-wider text-[#087F6A]">
              Phân hệ Nhân viên Dược · Quầy {PHARMACY_SERVICE_POINT}
            </span>
          </div>
          <h1 className="mt-1 text-2xl font-extrabold text-[#17242D]">Hàng đợi Phát thuốc</h1>
          <p className="mt-0.5 text-xs text-[#667985]">
            Cấp phát theo thứ tự FIFO cho đơn thuốc đã được bác sĩ ký xác nhận
          </p>
        </div>
        <button
          onClick={handleCallNext}
          disabled={isLoading || Boolean(hasServingEntry)}
          className="cursor-pointer rounded-xl bg-[#0277A8] px-5 py-3 text-sm font-bold text-white shadow-xs transition-all hover:bg-[#00577E] disabled:cursor-not-allowed disabled:opacity-50"
        >
          Gọi số tiếp theo
        </button>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="space-y-4 rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-xs lg:col-span-2">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-extrabold text-[#17242D]">
              Danh sách bệnh nhân chờ nhận thuốc
            </h2>
            <span className="rounded-full bg-[#DDF3FC] px-3 py-1 text-xs font-bold text-[#00577E]">
              {(entries ?? []).length} lượt
            </span>
          </div>

          {isLoading ? (
            <div className="flex justify-center py-12">
              <LoadingSpinner size="md" />
            </div>
          ) : error ? (
            <div className="rounded-xl border border-[#BA1A1A]/30 bg-[#BA1A1A]/10 p-4 text-xs font-semibold text-[#BA1A1A]">
              {error}
            </div>
          ) : (entries ?? []).length === 0 ? (
            <div className="py-12 text-center text-xs text-[#667985]">
              Hiện chưa có lượt chờ phát thuốc nào.
            </div>
          ) : (
            <div className="divide-y divide-[#EDF2F5]">
              {(entries ?? []).map((entry) => {
                const selected = activeEntry?.entryId === entry.entryId;
                const isInProgress = entry.queueStatus === "IN_PROGRESS";
                const isCalled = entry.queueStatus === "CALLED";

                return (
                  <div
                    key={entry.entryId}
                    className={`flex items-center justify-between gap-4 py-4 transition-colors ${
                      selected
                        ? "-mx-2 rounded-xl border border-[#8BD2EE] bg-[#DDF3FC]/45 px-3.5"
                        : "px-1"
                    }`}
                  >
                    <div className="flex min-w-0 items-center gap-4">
                      <div
                        className={`flex min-w-[104px] shrink-0 flex-col items-center justify-center rounded-xl px-3.5 py-2 transition-colors ${
                          selected
                            ? "bg-[#0277A8] text-white shadow-xs"
                            : "border border-[#8BD2EE] bg-[#DDF3FC]/70 text-[#00577E]"
                        }`}
                      >
                        <span
                          className={`text-[10px] font-bold uppercase tracking-wider ${
                            selected ? "text-[#DDF3FC]" : "text-[#0277A8]"
                          }`}
                        >
                          Số thứ tự
                        </span>
                        <span className="mt-0.5 font-mono text-sm font-extrabold whitespace-nowrap">
                          {entry.queueNumber}
                        </span>
                      </div>

                      <div className="min-w-0 space-y-1.5">
                        <div className="flex flex-wrap items-center gap-2.5">
                          <p className="text-sm font-extrabold text-[#17242D] whitespace-nowrap">
                            {entry.patientName
                              ? entry.patientName
                              : `Hồ sơ BN: ${entry.patientId.slice(0, 8).toUpperCase()}`}
                          </p>
                          <span
                            className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-bold whitespace-nowrap ${
                              isInProgress
                                ? "border border-[#087F6A]/25 bg-[#D9F3EC] text-[#087F6A]"
                                : isCalled
                                  ? "border border-[#8BD2EE] bg-[#DDF3FC] text-[#00577E]"
                                  : "border border-[#D5E0E6] bg-[#F5F8FA] text-[#4D626E]"
                            }`}
                          >
                            <span
                              className={`h-1.5 w-1.5 rounded-full ${
                                isInProgress
                                  ? "bg-[#087F6A]"
                                  : isCalled
                                    ? "bg-[#0277A8]"
                                    : "bg-[#667985]"
                              }`}
                            />
                            {pharmacyStatusLabel(entry.queueStatus)}
                          </span>
                        </div>
                        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-xs text-[#667985]">
                          <span title={entry.patientId}>
                            Mã BN: {entry.patientId.slice(0, 8).toUpperCase()}
                          </span>
                          <span>·</span>
                          <span title={entry.prescriptionId ?? undefined}>
                            Mã toa:{" "}
                            {entry.prescriptionId
                              ? entry.prescriptionId.slice(0, 8).toUpperCase()
                              : "Chưa liên kết"}
                          </span>
                        </div>
                      </div>
                    </div>
                    <button
                      onClick={() => setActiveEntry(entry)}
                      className={`w-28 shrink-0 cursor-pointer rounded-xl py-2.5 text-center text-xs font-bold whitespace-nowrap transition-colors ${
                        selected
                          ? "bg-[#087F6A] text-white shadow-xs"
                          : "bg-[#0277A8] text-white hover:bg-[#00577E]"
                      }`}
                    >
                      {selected ? "Đang chọn" : "Đối chiếu toa"}
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        <PharmacyVerificationPanel
          activeEntry={activeEntry}
          activePrescription={activePrescription}
          isLoadingPrescription={isLoadingPrescription}
          prescriptionError={prescriptionError}
          isStarting={isStarting}
          isDispensing={isDispensing}
          activePatientMatches={activePatientMatches}
          canDispense={canDispense}
          onStartVerification={handleStartVerification}
          onDispenseMedicine={handleDispenseMedicine}
        />
      </div>
    </div>
  );
}
