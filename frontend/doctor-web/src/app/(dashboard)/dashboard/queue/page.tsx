"use client";

import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { queueApi, QueueEntry, QueueDashboardResponse } from "@/lib/queue-api";
import { consultationApi } from "@/lib/consultation-api";
import { patientApi } from "@/lib/patient-api";

function StatusDot({ status }: { status: string }) {
  const config: Record<string, { color: string; label: string; textClass: string }> = {
    CHECKED_IN:  { color: "#3B82F6", label: "Đã tiếp nhận", textClass: "text-blue-600 font-medium" },
    CALLED:      { color: "#F59E0B", label: "Đã gọi số",    textClass: "text-amber-600 font-semibold" },
    IN_PROGRESS: { color: "#8B5CF6", label: "Đang khám",    textClass: "text-purple-600 font-semibold" },
    COMPLETED:   { color: "#10B981", label: "Hoàn tất",     textClass: "text-emerald-600 font-medium" },
    MISSED:      { color: "#EF4444", label: "Lỡ lượt",      textClass: "text-rose-600 font-medium" },
  };
  const cfg = config[status] || { color: "#94A3B8", label: status, textClass: "text-gray-600 font-medium" };
  return (
    <span className={`inline-flex items-center gap-1.5 text-[11px] leading-none ${cfg.textClass}`}>
      <span className="w-1.5 h-1.5 rounded-full flex-shrink-0 inline-block align-middle" style={{ backgroundColor: cfg.color }} />
      <span className="leading-none">{cfg.label}</span>
    </span>
  );
}

function PriorityBadge({ level }: { level: string }) {
  if (level === "PRIORITY" || level === "EMERGENCY") {
    return (
      <span className="inline-flex items-center gap-1.5 text-[11px] leading-none text-rose-600 font-semibold">
        <span className="w-1.5 h-1.5 rounded-full bg-rose-600 flex-shrink-0 inline-block align-middle" />
        <span className="leading-none">Ưu tiên</span>
      </span>
    );
  }
  if (level === "RESULT_REVIEW") {
    return (
      <span className="inline-flex items-center gap-1.5 text-[11px] leading-none text-purple-600 font-medium">
        <span className="w-1.5 h-1.5 rounded-full bg-purple-600 flex-shrink-0 inline-block align-middle" />
        <span className="leading-none">Đọc kết quả CLS</span>
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1.5 text-[11px] leading-none text-blue-600 font-medium">
      <span className="w-1.5 h-1.5 rounded-full bg-blue-600 flex-shrink-0 inline-block align-middle" />
      <span className="leading-none">Khám thông thường</span>
    </span>
  );
}

function SkeletonRow() {
  return (
    <tr className="border-b border-gray-100">
      <td className="px-4 py-3"><div className="h-3 w-10 bg-gray-100 rounded animate-pulse" /></td>
      <td className="px-4 py-3"><div className="h-3 w-28 bg-gray-100 rounded animate-pulse" /></td>
      <td className="px-4 py-3"><div className="h-3 w-20 bg-gray-100 rounded animate-pulse" /></td>
      <td className="px-4 py-3"><div className="h-3 w-24 bg-gray-100 rounded animate-pulse" /></td>
      <td className="px-4 py-3"><div className="h-3 w-20 bg-gray-100 rounded animate-pulse" /></td>
      <td className="px-4 py-3 text-right"><div className="h-6 w-16 bg-gray-100 rounded ml-auto animate-pulse" /></td>
    </tr>
  );
}

export default function DashboardQueuePage() {
  const { user } = useAuth();
  const router = useRouter();

  // 3-State Pattern as required by project rules
  const [dashboardData, setDashboardData] = useState<QueueDashboardResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const [patientNames, setPatientNames] = useState<Record<string, string>>({});
  const [isCallingNext, setIsCallingNext] = useState(false);
  const [callingEntryId, setCallingEntryId] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState("");

  const roomId = "ROOM-01";

  const loadQueue = async () => {
    setIsLoading(true);
    setFetchError(null);
    try {
      const res = await queueApi.getRoomActive(roomId);
      const data = res.data ?? null;
      setDashboardData(data);

      // Fetch patient names asynchronously for entries
      if (data?.entries && data.entries.length > 0) {
        const pMap: Record<string, string> = { ...patientNames };
        for (const entry of data.entries) {
          if (entry.patientId && !pMap[entry.patientId]) {
            try {
              const pRes = await patientApi.getPatientById(entry.patientId);
              if (pRes.data?.fullName) {
                pMap[entry.patientId] = pRes.data.fullName;
              }
            } catch {
              pMap[entry.patientId] = `Bệnh nhân (${entry.patientId.slice(0, 8)})`;
            }
          }
        }
        setPatientNames(pMap);
      }
    } catch {
      setFetchError("Không thể tải dữ liệu hàng đợi khám. Vui lòng thử lại.");
      setDashboardData(null);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (user) {
      loadQueue();
    }
  }, [user]);

  const handleCallNext = async () => {
    if (!user) return;
    setIsCallingNext(true);
    setErrorMessage("");
    try {
      const consRes = await consultationApi.getTodayByDoctor(user.id);
      const activeCons = (consRes.data ?? []).find(c => c.status === "IN_PROGRESS");
      if (activeCons) {
        if (typeof window !== "undefined") {
          window.dispatchEvent(new CustomEvent("careflow:ai-notify", {
            detail: {
              text: "Bác sĩ hiện tại đang có một ca khám chưa hoàn tất. Vui lòng hoàn thành lượt khám hiện tại trước khi gọi bệnh nhân tiếp theo.",
              consultationId: activeCons.id
            }
          }));
        }
        setIsCallingNext(false);
        return;
      }

      // 1. Trigger callNextInRoom on queue-service
      const callRes = await queueApi.callNextInRoom(roomId);
      const nextEntry = callRes.data;

      if (nextEntry && nextEntry.queueNumber) {
        // 2. Automatically create consultation and navigate to consultation page
        const res = await consultationApi.createConsultation({
          appointmentId: nextEntry.appointmentId,
          patientId: nextEntry.patientId,
          doctorId: user.id
        });
        router.push(`/consultation/${res.data.id}?entryId=${nextEntry.entryId}`);
      } else {
        if (typeof window !== "undefined") {
          window.dispatchEvent(new CustomEvent("careflow:ai-notify", {
            detail: { text: "Bác sĩ ơi, hiện tại chưa có bệnh nhân nào đâu ạ!" }
          }));
        }
        await loadQueue();
      }
    } catch {
      if (typeof window !== "undefined") {
        window.dispatchEvent(new CustomEvent("careflow:ai-notify", {
          detail: { text: "Bác sĩ ơi, hiện tại chưa có bệnh nhân nào đâu ạ!" }
        }));
      }
    } finally {
      setIsCallingNext(false);
    }
  };

  const handleCallEntry = async (entry: QueueEntry) => {
    if (!user) return;
    setCallingEntryId(entry.entryId);
    setErrorMessage("");
    try {
      const consRes = await consultationApi.getTodayByDoctor(user.id);
      const activeCons = (consRes.data ?? []).find(c => c.status === "IN_PROGRESS");
      if (activeCons) {
        if (activeCons.patientId === entry.patientId || activeCons.appointmentId === entry.appointmentId) {
          router.push(`/consultation/${activeCons.id}?entryId=${entry.entryId}`);
          return;
        }
        if (typeof window !== "undefined") {
          window.dispatchEvent(new CustomEvent("careflow:ai-notify", {
            detail: {
              text: "Bác sĩ hiện tại đang có một ca khám chưa hoàn tất. Vui lòng hoàn thành lượt khám hiện tại trước khi gọi bệnh nhân khác.",
              consultationId: activeCons.id
            }
          }));
        }
        setCallingEntryId(null);
        return;
      }

      if (entry.queueStatus === "CHECKED_IN") {
        await queueApi.callEntry(entry.entryId);
        await loadQueue();
      }

      const res = await consultationApi.createConsultation({
        appointmentId: entry.appointmentId,
        patientId: entry.patientId,
        doctorId: user.id
      });
      router.push(`/consultation/${res.data.id}?entryId=${entry.entryId}`);
    } catch (err: any) {
      setErrorMessage(err?.message || "Không thể khởi tạo ca khám cho bệnh nhân.");
    } finally {
      setCallingEntryId(null);
    }
  };

  const safeEntries = dashboardData?.entries ?? [];
  const recommended = dashboardData?.recommendedNext;

  return (
    <div className="space-y-4">
      {/* Header Bar */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-[20px] font-bold text-gray-900 tracking-tight">
            Hàng đợi khám (Phòng {dashboardData?.roomCode || roomId})
          </h1>
          <p className="mt-0.5 text-[11px] text-gray-500">
            Danh sách bệnh nhân đã tiếp nhận sẵn sàng vào khám
          </p>
        </div>
        <button
          onClick={handleCallNext}
          disabled={isCallingNext || isLoading || (safeEntries).length === 0}
          className="px-4 py-2 bg-[#6E2582] hover:bg-[#581c69] disabled:opacity-50 text-white rounded-md text-[11px] font-bold shadow-xs flex items-center gap-1.5 transition-all cursor-pointer"
        >
          {isCallingNext ? (
            <>
              <svg className="animate-spin h-3.5 w-3.5 text-white" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              <span>Đang gọi...</span>
            </>
          ) : (
            <span>Gọi số</span>
          )}
        </button>
      </div>

      {/* Recommended Next Patient Card (Indigo-Navy Dark Gradient Accent) */}
      {recommended && (
        <div className="bg-gradient-to-r from-[#0D0F1E] via-[#161930] to-[#1E2340] rounded-lg p-3.5 text-white border border-[#2E3462] shadow-xs flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-lg font-black bg-indigo-500/20 text-[#818CF8] border border-indigo-500/30 px-2.5 py-0.5 rounded-md font-mono">
              #{recommended.queueNumber}
            </span>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-[9px] bg-amber-500/20 text-amber-300 border border-amber-500/30 font-semibold px-1.5 py-0.5 rounded">
                  Đề xuất gọi tiếp theo
                </span>
                <span className="text-xs font-semibold text-gray-200">
                  {patientNames[recommended.patientId] || "Đang tải tên..."}
                </span>
              </div>
              <p className="text-[10px] text-gray-300 mt-0.5">
                Dự kiến thời gian chờ: <span className="font-semibold text-indigo-300">~{recommended.estimatedWaitMinutes ?? 0} phút</span>
              </p>
            </div>
          </div>
          <button
            onClick={() => handleCallEntry(recommended)}
            disabled={callingEntryId === recommended.entryId}
            className="px-3.5 py-1.5 bg-[#6E2582] hover:bg-[#581c69] disabled:opacity-50 text-white font-semibold text-[11px] rounded-md shadow-xs transition-colors cursor-pointer"
          >
            {callingEntryId === recommended.entryId ? "Đang gọi..." : "Gọi số"}
          </button>
        </div>
      )}

      {/* Clean Inline Errors as specified in design.md Rule 39-40 */}
      {errorMessage && (
        <p className="text-[11px] text-[#D9381E] font-medium px-1">
          {errorMessage}
        </p>
      )}

      {/* Active Queue Table Card */}
      <div className="bg-white rounded-lg border border-gray-200 shadow-xs overflow-hidden">
        <div className="px-4 py-3 border-b border-gray-200 flex items-center justify-between flex-wrap gap-2 bg-gray-50/50">
          <h2 className="text-[13px] font-bold text-[#2B1D30] tracking-tight">
            Hàng đợi ({isLoading ? "..." : (safeEntries).length} bệnh nhân)
          </h2>
          <div className="flex items-center gap-3 text-[10px] font-medium flex-wrap">
            <span className="inline-flex items-center gap-1 text-rose-600">
              <span className="w-1.5 h-1.5 rounded-full bg-rose-600" />
              <span>Ưu tiên</span>
            </span>
            <span className="inline-flex items-center gap-1 text-blue-600">
              <span className="w-1.5 h-1.5 rounded-full bg-blue-600" />
              <span>Khám thông thường</span>
            </span>
            <span className="inline-flex items-center gap-1 text-purple-600">
              <span className="w-1.5 h-1.5 rounded-full bg-purple-600" />
              <span>Đọc kết quả CLS</span>
            </span>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-[11px]">
            <thead>
              <tr className="border-b border-gray-200 bg-[#F8FAFC] text-gray-700">
                <th className="px-4 py-2.5 font-semibold w-20 font-mono">Số STT</th>
                <th className="px-4 py-2.5 font-semibold">Họ tên & Mã bệnh nhân</th>
                <th className="px-4 py-2.5 font-semibold">Phòng khám</th>
                <th className="px-4 py-2.5 font-semibold">Mức ưu tiên</th>
                <th className="px-4 py-2.5 font-semibold w-32">Trạng thái</th>
                <th className="px-4 py-2.5 font-semibold w-24 text-center">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 text-gray-800">
              {isLoading ? (
                Array.from({ length: 4 }).map((_, i) => <SkeletonRow key={i} />)
              ) : fetchError ? (
                <tr>
                  <td colSpan={6} className="px-4 py-6 text-center text-[11px] text-[#D9381E]">
                    {fetchError}
                  </td>
                </tr>
              ) : (safeEntries).length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-[11px] text-gray-400">
                    Hiện chưa có bệnh nhân nào trong hàng đợi
                  </td>
                </tr>
              ) : (
                safeEntries.map(entry => (
                  <tr key={entry.entryId} className="hover:bg-gray-50/80 transition-colors">
                    <td className="px-4 py-3 font-bold text-[#6E2582] font-mono">
                      #{entry.queueNumber}
                    </td>
                    <td className="px-4 py-3">
                      <div className="font-medium text-gray-900">
                        {patientNames[entry.patientId] || "Đang tải..."}
                      </div>
                      <div className="text-[10px] text-gray-400 font-mono">
                        {entry.patientId}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-gray-600">
                      {entry.roomCode}
                    </td>
                    <td className="px-4 py-3">
                      <PriorityBadge level={entry.priorityLevel} />
                    </td>
                    <td className="px-4 py-3">
                      <StatusDot status={entry.queueStatus} />
                    </td>
                    <td className="px-4 py-3 text-center">
                      <button
                        onClick={() => handleCallEntry(entry)}
                        disabled={callingEntryId === entry.entryId}
                        className="w-20 py-1 bg-white hover:bg-purple-50 border border-gray-200 text-[#2B1D30] hover:text-[#6E2582] disabled:opacity-50 rounded text-[11px] font-medium transition-all cursor-pointer text-center inline-block shadow-2xs"
                      >
                        {callingEntryId === entry.entryId
                          ? "Đang gọi..."
                          : entry.queueStatus === "CALLED"
                          ? "Vào khám"
                          : "Gọi số"}
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

