"use client";

import { useCallback, useRef, useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { queueApi, QueueEntry, QueueDashboardResponse } from "@/lib/queue-api";
import { consultationApi } from "@/lib/consultation-api";
import { patientApi } from "@/lib/patient-api";
import { appointmentApi } from "@/lib/appointment-api";
import { getErrorMessage } from "@/lib/error-utils";

function StatusDot({ status }: { status: string }) {
  const config: Record<string, { color: string; label: string; textClass: string }> = {
    CHECKED_IN:  { color: "#3B82F6", label: "Đã check-in",  textClass: "text-blue-600 font-medium" },
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

export default function DashboardQueuePage() {
  const { user } = useAuth();
  const router = useRouter();

  // 3-State Pattern as required by project rules
  const [dashboardData, setDashboardData] = useState<QueueDashboardResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const [patientNames, setPatientNames] = useState<Record<string, string>>({});
  const patientNamesRef = useRef<Record<string, string>>({});
  const [isCallingNext, setIsCallingNext] = useState(false);
  const [callingEntryId, setCallingEntryId] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState("");

  const [roomId, setRoomId] = useState<string | null>(null);

  const loadQueue = useCallback(async (targetRoomId?: string) => {
    if (!targetRoomId) return;
    setIsLoading(true);
    setFetchError(null);
    try {
      const res = await queueApi.getRoomActive(targetRoomId);
      const data = res.data ?? null;
      setDashboardData(data);

      // Fetch patient names asynchronously for entries
      if (data?.entries && data.entries.length > 0) {
        const pMap: Record<string, string> = { ...patientNamesRef.current };
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
        patientNamesRef.current = pMap;
        setPatientNames(pMap);
      }
    } catch {
      setFetchError("Không thể tải dữ liệu hàng đợi khám. Vui lòng thử lại.");
      setDashboardData(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!user) return;
    let active = true;
    const loadClinicalContextAndQueue = async () => {
      setIsLoading(true);
      setFetchError(null);
      try {
        const contextRes = await appointmentApi.getClinicalContext();
        const assignedRoom = contextRes.data?.rooms?.[0];
        if (!assignedRoom) {
          throw new Error("Bác sĩ chưa được phân công phòng khám.");
        }
        if (!active) return;
        setRoomId(assignedRoom.roomId);
        await loadQueue(assignedRoom.roomId);
      } catch (err: unknown) {
        if (!active) return;
        setFetchError(getErrorMessage(err, "Không thể tải thông tin phân công phòng khám."));
        setDashboardData(null);
        setIsLoading(false);
      }
    };
    loadClinicalContextAndQueue();
    return () => { active = false; };
  }, [user, loadQueue]);

  const handleCallNext = async () => {
    if (!user || !roomId) return;
    setIsCallingNext(true);
    setErrorMessage("");
    try {
      const consRes = await consultationApi.getByDoctor(user.id);
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

      const callRes = await queueApi.callNextInRoom(roomId);
      const nextEntry = callRes.data;

      if (nextEntry && nextEntry.queueNumber) {
        await loadQueue(roomId);
      } else {
        if (typeof window !== "undefined") {
          window.dispatchEvent(new CustomEvent("careflow:ai-notify", {
            detail: { text: "Bác sĩ ơi, hiện tại chưa có bệnh nhân nào đâu ạ!" }
          }));
        }
        await loadQueue(roomId);
      }
    } catch (err: unknown) {
      setErrorMessage(getErrorMessage(err, "Không thể gọi bệnh nhân tiếp theo."));
    } finally {
      setIsCallingNext(false);
    }
  };

  const handleCallEntry = async (entry: QueueEntry) => {
    if (!user || !roomId) return;
    setCallingEntryId(entry.entryId);
    setErrorMessage("");
    try {
      const consRes = await consultationApi.getByDoctor(user.id);
      const activeCons = (consRes.data ?? []).find(c => c.status === "IN_PROGRESS");
      const isSameConsultation = activeCons && (
        activeCons.patientId === entry.patientId || activeCons.appointmentId === entry.appointmentId
      );

      if (activeCons && !isSameConsultation) {
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

      if (entry.queueStatus === "CHECKED_IN" || entry.queueStatus === "QUEUED") {
        await queueApi.callEntry(entry.entryId);
        await loadQueue(roomId);
        return;
      }

      if (entry.queueStatus === "IN_PROGRESS" && isSameConsultation) {
        router.push(`/consultation/${activeCons.id}?entryId=${entry.entryId}`);
        return;
      }

      const appointmentConsultations = await consultationApi.getByAppointment(entry.appointmentId);
      let consultation = (appointmentConsultations.data ?? []).find(c => c.status === "IN_PROGRESS");
      if (!consultation) {
        const res = await consultationApi.createConsultation({
          appointmentId: entry.appointmentId,
          patientId: entry.patientId,
          doctorId: user.id
        });
        consultation = res.data;
      }

      if (entry.queueStatus === "CALLED") {
        await queueApi.startEntry(entry.entryId);
      }
      router.push(`/consultation/${consultation.id}?entryId=${entry.entryId}`);
    } catch (err: unknown) {
      setErrorMessage(getErrorMessage(err, "Không thể khởi tạo ca khám cho bệnh nhân."));
    } finally {
      setCallingEntryId(null);
    }
  };

  const safeEntries = dashboardData?.entries ?? [];
  const recommended = dashboardData?.recommendedNext;
  const hasCallableEntry = safeEntries.some(entry =>
    entry.queueStatus === "CHECKED_IN" || entry.queueStatus === "QUEUED"
  );
  const actionLabel = (status: QueueEntry["queueStatus"]) => {
    if (status === "CALLED") return "Bắt đầu khám";
    if (status === "IN_PROGRESS") return "Vào khám";
    return "Gọi số";
  };
  const laneOf = (entry: QueueEntry) => entry.schedulingLane ?? (
    entry.consultationPhase === "RESULT_REVIEW" || entry.priorityLevel === "RESULT_REVIEW"
      ? "RESULT_REVIEW"
      : entry.queueClass === "PRIORITY" || entry.priorityLevel === "PRIORITY" || entry.priorityLevel === "EMERGENCY"
        ? "PRIORITY"
        : "NORMAL"
  );
  const laneViews = [
    {
      key: "PRIORITY" as const,
      title: "Làn ưu tiên",
      description: "Ca cần được xử lý trước",
      dotClass: "bg-rose-500",
      headerClass: "bg-rose-50 border-rose-100",
      entries: dashboardData?.priorityQueue ?? safeEntries.filter(entry => laneOf(entry) === "PRIORITY"),
    },
    {
      key: "NORMAL" as const,
      title: "Làn thông thường",
      description: "FIFO theo thời điểm tiếp nhận",
      dotClass: "bg-blue-500",
      headerClass: "bg-blue-50 border-blue-100",
      entries: dashboardData?.normalQueue ?? safeEntries.filter(entry => laneOf(entry) === "NORMAL"),
    },
    {
      key: "RESULT_REVIEW" as const,
      title: "Làn đọc kết quả CLS",
      description: "Bệnh nhân quay lại đọc kết quả",
      dotClass: "bg-purple-500",
      headerClass: "bg-purple-50 border-purple-100",
      entries: dashboardData?.resultReviewQueue ?? safeEntries.filter(entry => laneOf(entry) === "RESULT_REVIEW"),
    },
  ];

  return (
    <div className="space-y-4">
      {/* Header Bar */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900 tracking-tight">
            Hàng đợi khám (Phòng {dashboardData?.roomCode || roomId || "—"})
          </h1>
          <p className="mt-0.5 text-[11px] text-gray-500">
            Danh sách bệnh nhân đã tiếp nhận sẵn sàng vào khám
          </p>
        </div>
        <button
          onClick={handleCallNext}
          disabled={isCallingNext || isLoading || !hasCallableEntry}
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
            {callingEntryId === recommended.entryId ? "Đang xử lý..." : actionLabel(recommended.queueStatus)}
          </button>
        </div>
      )}

      {/* Clean Inline Errors as specified in design.md Rule 39-40 */}
      {errorMessage && (
        <p className="text-[11px] text-[#D9381E] font-medium px-1">
          {errorMessage}
        </p>
      )}

      {/* Three scheduling lanes */}
      <div className="bg-white rounded-lg border border-gray-200 shadow-xs overflow-hidden">
        <div className="px-4 py-3 border-b border-gray-200 flex items-center justify-between flex-wrap gap-2 bg-gray-50/50">
          <h2 className="text-[13px] font-bold text-[#2B1D30] tracking-tight">
            Hàng đợi ({isLoading ? "..." : safeEntries.length} bệnh nhân)
          </h2>
          <span className="text-[10px] text-gray-500">
            Bác sĩ chọn bệnh nhân; hệ thống chỉ đề xuất thứ tự gọi
          </span>
        </div>

        {isLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3 p-3">
            {Array.from({ length: 3 }).map((_, index) => (
              <div key={index} className="h-48 rounded-lg border border-gray-100 bg-gray-50 animate-pulse" />
            ))}
          </div>
        ) : fetchError ? (
          <p className="px-4 py-8 text-center text-[11px] text-[#D9381E]">{fetchError}</p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3 p-3">
            {laneViews.map(lane => (
              <section key={lane.key} className="min-w-0 rounded-lg border border-gray-200 overflow-hidden bg-white">
                <div className={`px-3 py-2.5 border-b ${lane.headerClass}`}>
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2 min-w-0">
                      <span className={`w-2 h-2 rounded-full ${lane.dotClass} flex-shrink-0`} />
                      <h3 className="text-[12px] font-bold text-[#2B1D30] truncate">{lane.title}</h3>
                    </div>
                    <span className="min-w-6 h-5 px-1.5 inline-flex items-center justify-center rounded-full bg-white/80 text-[10px] font-bold text-gray-700">
                      {lane.entries.length}
                    </span>
                  </div>
                  <p className="mt-1 text-[10px] text-gray-500 truncate">{lane.description}</p>
                </div>

                <div className="divide-y divide-gray-100">
                  {lane.entries.length === 0 ? (
                    <div className="px-3 py-10 text-center text-[11px] text-gray-400">
                      Chưa có bệnh nhân
                    </div>
                  ) : (
                    lane.entries.map(entry => (
                      <div key={entry.entryId} className="px-3 py-3 hover:bg-gray-50/80 transition-colors">
                        <div className="flex items-start justify-between gap-2">
                          <div className="min-w-0">
                            <div className="flex items-center gap-2">
                              <span className="font-bold text-[#6E2582] font-mono text-[12px]">#{entry.queueNumber}</span>
                              <StatusDot status={entry.queueStatus} />
                            </div>
                            <p className="mt-1 text-[11px] font-semibold text-gray-900 truncate">
                              {patientNames[entry.patientId] || "Đang tải..."}
                            </p>
                            <p className="mt-0.5 text-[9px] text-gray-400 font-mono truncate">{entry.patientId}</p>
                          </div>
                          <button
                            onClick={() => handleCallEntry(entry)}
                            disabled={callingEntryId === entry.entryId}
                            className="flex-shrink-0 px-2.5 py-1.5 bg-white hover:bg-purple-50 border border-gray-200 text-[#2B1D30] hover:text-[#6E2582] disabled:opacity-50 rounded text-[10px] font-semibold transition-all cursor-pointer"
                          >
                            {callingEntryId === entry.entryId
                              ? "Đang xử lý..."
                              : actionLabel(entry.queueStatus)}
                          </button>
                        </div>
                        <div className="mt-2 flex items-center justify-between text-[9px] text-gray-400">
                          <span>{entry.roomCode || "Phòng khám"}</span>
                          <PriorityBadge level={entry.priorityLevel} />
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </section>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
