"use client";

import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { queueApi, QueueEntry, QueueDashboardResponse } from "@/lib/queue-api";
import { consultationApi } from "@/lib/consultation-api";

function StatusDot({ status }: { status: string }) {
  const config: Record<string, { color: string; label: string; bg: string; text: string }> = {
    CHECKED_IN:  { color: "#3B82F6", label: "Đã tiếp nhận", bg: "#DBEAFE", text: "#1E40AF" },
    CALLED:      { color: "#F59E0B", label: "Đã gọi số",    bg: "#FEF3C7", text: "#92400E" },
    IN_PROGRESS: { color: "#8B5CF6", label: "Đang khám",    bg: "#EDE9FE", text: "#5B21B6" },
    COMPLETED:   { color: "#10B981", label: "Hoàn tất",     bg: "#D1FAE5", text: "#065F46" },
    MISSED:      { color: "#EF4444", label: "Lỡ lượt",      bg: "#FEE2E2", text: "#991B1B" },
  };
  const cfg = config[status] || { color: "#94A3B8", label: status, bg: "#F1F5F9", text: "#475569" };
  return (
    <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-semibold"
      style={{ backgroundColor: cfg.bg, color: cfg.text }}>
      <span className="inline-block w-1.5 h-1.5 rounded-full flex-shrink-0" style={{ backgroundColor: cfg.color }} />
      {cfg.label}
    </span>
  );
}

function SkeletonRow() {
  return (
    <tr className="border-b border-gray-50">
      {["w-10", "w-36", "w-24", "w-32", "w-16", "w-20", "w-16"].map((w, i) => (
        <td key={i} className="px-5 py-3.5">
          <div className={`h-3.5 ${w} rounded bg-gray-100 animate-pulse`} />
        </td>
      ))}
    </tr>
  );
}

export default function DashboardQueuePage() {
  const { user } = useAuth();
  const router = useRouter();
  const [dashboardData, setDashboardData] = useState<QueueDashboardResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [isCallingNext, setIsCallingNext] = useState(false);
  const [callingEntryId, setCallingEntryId] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState("");

  const roomId = "ROOM-01";

  const loadQueue = async () => {
    setIsLoading(true);
    setFetchError(null);
    try {
      const res = await queueApi.getRoomActive(roomId);
      setDashboardData(res.data);
    } catch {
      setFetchError("Không thể tải danh sách hàng đợi Active Queue. Vui lòng thử lại.");
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
      const activeCons = (consRes.data || []).find(c => c.status === "IN_PROGRESS");
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

      await queueApi.callNextInRoom(roomId);
      await loadQueue();
    } catch (err: any) {
      setErrorMessage(err?.message || "Không thể gọi bệnh nhân tiếp theo.");
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
      const activeCons = (consRes.data || []).find(c => c.status === "IN_PROGRESS");
      if (activeCons) {
        if (activeCons.patientId === entry.patientId || activeCons.appointmentId === entry.appointmentId) {
          router.push(`/consultation/${activeCons.id}`);
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
      router.push(`/consultation/${res.data.id}`);
    } catch (err: any) {
      setErrorMessage(err?.message || "Không thể gọi bệnh nhân.");
    } finally {
      setCallingEntryId(null);
    }
  };

  const safeEntries = dashboardData?.entries ?? [];
  const recommended = dashboardData?.recommendedNext;

  const priorityEntries = safeEntries.filter(e => e.priorityLevel === "PRIORITY" || e.priorityLevel === "EMERGENCY");
  const normalEntries = safeEntries.filter(e => e.priorityLevel === "APPOINTMENT" || e.priorityLevel === "WALK_IN");

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900 tracking-tight">Active Queue & Gọi bệnh nhân (Phòng {dashboardData?.roomCode || roomId})</h1>
          <p className="mt-0.5 text-xs text-gray-400">
            Danh sách bệnh nhân đã Check-in sẵn sàng vào khám theo điều phối Round-Robin 1:1:1
          </p>
        </div>
        <button
          onClick={handleCallNext}
          disabled={isCallingNext || isLoading || safeEntries.length === 0}
          className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-lg text-xs font-semibold shadow-xs flex items-center gap-2 transition-all cursor-pointer"
        >
          {isCallingNext ? (
            <>
              <svg className="animate-spin h-3.5 w-3.5 text-white" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              <span>Đang gọi số tiếp theo...</span>
            </>
          ) : (
            <>
              <span>🔊 Gọi số tiếp theo (Round-Robin)</span>
            </>
          )}
        </button>
      </div>

      {recommended && (
        <div className="bg-gradient-to-r from-indigo-500 to-purple-600 rounded-xl p-4 text-white shadow-sm flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-2xl font-extrabold bg-white/20 px-3 py-1.5 rounded-lg border border-white/30">
              #{recommended.queueNumber}
            </span>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-[10px] bg-amber-400 text-amber-950 font-bold px-2 py-0.5 rounded-full uppercase tracking-wider">Đề xuất gọi tiếp theo</span>
                <span className="text-xs text-indigo-100">Bệnh nhân ID: {recommended.patientId.slice(0, 8)}...</span>
              </div>
              <p className="text-sm font-semibold mt-0.5">Dự kiến thời gian chờ: ~{recommended.estimatedWaitMinutes ?? 0} phút</p>
            </div>
          </div>
          <button
            onClick={() => handleCallEntry(recommended)}
            disabled={callingEntryId === recommended.entryId}
            className="px-3.5 py-1.5 bg-white text-indigo-700 hover:bg-indigo-50 font-bold text-xs rounded-lg shadow-xs transition-colors cursor-pointer"
          >
            Gọi ngay bệnh nhân này
          </button>
        </div>
      )}

      {errorMessage && <p className="text-xs font-semibold text-red-500">{errorMessage}</p>}
      {fetchError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-xs font-medium px-4 py-3 rounded-lg">
          {fetchError}
        </div>
      )}

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="px-5 py-3.5 border-b border-gray-100 flex items-center justify-between">
          <h2 className="text-xs font-bold text-gray-700 uppercase tracking-wider">Hàng đợi Active ({safeEntries.length} bệnh nhân)</h2>
          <div className="flex items-center gap-3 text-[10px] text-gray-500 font-medium">
            <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-blue-500 inline-block" /> Đã tiếp nhận</span>
            <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-amber-500 inline-block" /> Đã gọi số</span>
            <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-purple-500 inline-block" /> Đang khám</span>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-gray-100 bg-indigo-50">
                <th className="px-5 py-3 font-semibold text-gray-600 w-20">Số STT</th>
                <th className="px-5 py-3 font-semibold text-gray-600">Mã Bệnh nhân</th>
                <th className="px-5 py-3 font-semibold text-gray-600">Phòng khám</th>
                <th className="px-5 py-3 font-semibold text-gray-600">Mức ưu tiên</th>
                <th className="px-5 py-3 font-semibold text-gray-600 w-28">Trạng thái</th>
                <th className="px-5 py-3 font-semibold text-gray-600 w-28 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {isLoading ? (
                Array.from({ length: 4 }).map((_, i) => <SkeletonRow key={i} />)
              ) : safeEntries.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-5 py-8 text-center text-gray-400">
                    Hiện chưa có bệnh nhân nào trong Active Queue
                  </td>
                </tr>
              ) : (
                safeEntries.map(entry => (
                  <tr key={entry.entryId} className="hover:bg-gray-50/80 transition-colors">
                    <td className="px-5 py-3.5 font-extrabold text-indigo-600">#{entry.queueNumber}</td>
                    <td className="px-5 py-3.5 font-medium text-gray-800">{entry.patientId}</td>
                    <td className="px-5 py-3.5 text-gray-600">{entry.roomCode}</td>
                    <td className="px-5 py-3.5">
                      <span className={`inline-flex px-2 py-0.5 text-[10px] font-bold rounded-full ${
                        entry.priorityLevel === "PRIORITY" || entry.priorityLevel === "EMERGENCY"
                          ? "bg-rose-100 text-rose-700"
                          : "bg-gray-100 text-gray-600"
                      }`}>
                        {entry.priorityLevel}
                      </span>
                    </td>
                    <td className="px-5 py-3.5">
                      <StatusDot status={entry.queueStatus} />
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      <button
                        onClick={() => handleCallEntry(entry)}
                        disabled={callingEntryId === entry.entryId}
                        className="px-3 py-1 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded text-[11px] font-semibold transition-colors cursor-pointer"
                      >
                        {callingEntryId === entry.entryId ? "Đang gọi..." : entry.queueStatus === "CALLED" ? "Vào khám" : "Gọi số"}
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
