"use client";

import { useCallback, useRef, useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { queueApi, QueueEntry, QueueDashboardResponse } from "@/lib/queue-api";
import { consultationApi } from "@/lib/consultation-api";
import { patientApi } from "@/lib/patient-api";
import { appointmentApi } from "@/lib/appointment-api";
import { getErrorMessage } from "@/lib/error-utils";
import Link from "next/link";
import { QueuePriorityBadge, QueueStatusDot } from "@/components/queue/QueueBadges";
import { notifyAi, useVisibilityPolling } from "@/components/queue/useQueueOperations";

export default function DashboardQueuePage() {
  const { user } = useAuth();
  const router = useRouter();

  const [dashboardData, setDashboardData] = useState<QueueDashboardResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const [patientNames, setPatientNames] = useState<Record<string, string>>({});
  const patientNamesRef = useRef<Record<string, string>>({});
  const [isCallingNext, setIsCallingNext] = useState(false);
  const [callingEntryId, setCallingEntryId] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [activeConsultationId, setActiveConsultationId] = useState<string | null>(null);

  const [roomId, setRoomId] = useState<string | null>(null);

  const loadQueue = useCallback(async (targetRoomId?: string, background = false) => {
    if (!targetRoomId) return;
    if (!background) setIsLoading(true);
    setFetchError(null);
    try {
      const res = await queueApi.getRoomActive(targetRoomId);
      const data = res.data ?? null;
      setDashboardData(data);

      if (data?.entries && data.entries.length > 0) {
        const pMap: Record<string, string> = { ...patientNamesRef.current };
        for (const entry of data.entries) {
          if (entry.patientName) {
            pMap[entry.patientId] = entry.patientName;
            continue;
          }
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
    void loadClinicalContextAndQueue();
    return () => {
      active = false;
    };
  }, [user, loadQueue]);

  useVisibilityPolling(Boolean(roomId), () => {
    if (roomId) void loadQueue(roomId, true);
  });

  const handleCallNext = async () => {
    if (!user || !roomId) return;
    setIsCallingNext(true);
    setErrorMessage("");
    setActiveConsultationId(null);
    try {
      const consRes = await consultationApi.getByDoctor(user.id);
      const activeCons = (consRes.data ?? []).find((c) => c.status === "IN_PROGRESS");
      if (activeCons) {
        setActiveConsultationId(activeCons.id);
        setErrorMessage(
          "Bạn đang có một ca khám chưa hoàn tất. Vào ca khám để xử lý trước khi gọi bệnh nhân tiếp theo."
        );
        notifyAi(
          "Bác sĩ hiện tại đang có một ca khám chưa hoàn tất. Vui lòng hoàn thành lượt khám hiện tại trước khi gọi bệnh nhân tiếp theo.",
          activeCons.id
        );
        setIsCallingNext(false);
        return;
      }

      const callRes = await queueApi.callNextInRoom(roomId);
      const nextEntry = callRes.data;

      if (nextEntry && nextEntry.queueNumber) {
        await loadQueue(roomId);
      } else {
        notifyAi("Bác sĩ ơi, hiện tại chưa có bệnh nhân nào đâu ạ!");
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
    setActiveConsultationId(null);
    try {
      const consRes = await consultationApi.getByDoctor(user.id);
      const activeCons = (consRes.data ?? []).find((c) => c.status === "IN_PROGRESS");
      const isSameConsultation =
        activeCons &&
        (activeCons.queueEntryId === entry.entryId ||
          (!!entry.appointmentId && activeCons.appointmentId === entry.appointmentId));

      if (activeCons && !isSameConsultation) {
        setActiveConsultationId(activeCons.id);
        setErrorMessage(
          "Bạn đang có một ca khám chưa hoàn tất. Vào ca khám để xử lý trước khi gọi bệnh nhân khác."
        );
        notifyAi(
          "Bác sĩ hiện tại đang có một ca khám chưa hoàn tất. Vui lòng hoàn thành lượt khám hiện tại trước khi gọi bệnh nhân khác.",
          activeCons.id
        );
        setCallingEntryId(null);
        return;
      }

      if (entry.queueStatus === "CHECKED_IN" || entry.queueStatus === "QUEUED") {
        await queueApi.callEntry(entry.entryId);
        await loadQueue(roomId);
        return;
      }

      if (entry.consultationPhase === "RESULT_REVIEW") {
        if (!entry.consultationId) throw new Error("Lượt đọc kết quả chưa liên kết ca khám.");
        const resumed = await consultationApi.resume(entry.consultationId);
        const appointmentId = resumed.data.appointmentId;
        if (appointmentId) {
          const initial = await queueApi.getTicket(appointmentId);
          if (initial.data.status === "IN_PROGRESS" && initial.data.ticketId !== entry.entryId) {
            await queueApi.completeEntry(initial.data.ticketId);
          }
        }
        if (entry.queueStatus === "CALLED") await queueApi.startEntry(entry.entryId);
        router.push(`/consultation/${resumed.data.id}?entryId=${entry.entryId}`);
        return;
      }

      if (entry.queueStatus === "IN_PROGRESS" && isSameConsultation) {
        router.push(`/consultation/${activeCons.id}?entryId=${entry.entryId}`);
        return;
      }

      const res = await consultationApi.createConsultation({
        appointmentId: entry.appointmentId,
        queueEntryId: entry.entryId,
        patientId: entry.patientId,
        doctorId: user.id,
      });
      const consultation = res.data;

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

  const handleMissedAction = async (
    entry: QueueEntry,
    action: "recall" | "miss" | "requeue",
    position?: "FRONT" | "BACK"
  ) => {
    if (!roomId || callingEntryId) return;
    setCallingEntryId(entry.entryId);
    setErrorMessage("");
    try {
      if (action === "recall") await queueApi.recallEntry(entry.entryId);
      else if (action === "miss") await queueApi.missEntry(entry.entryId);
      else await queueApi.requeueEntry(entry.entryId, position);
    } catch (e) {
      setErrorMessage(getErrorMessage(e, "Không thể cập nhật lượt khám."));
    } finally {
      await loadQueue(roomId, true);
      setCallingEntryId(null);
    }
  };

  const safeEntries = dashboardData?.entries ?? [];
  const recommended = dashboardData?.recommendedNext;
  const hasCallableEntry =
    dashboardData?.receptionActive !== false &&
    safeEntries.some(
      (entry) => entry.queueStatus === "CHECKED_IN" || entry.queueStatus === "QUEUED"
    );
  const actionLabel = (status: QueueEntry["queueStatus"]) => {
    if (status === "CALLED") return "Bắt đầu khám";
    if (status === "IN_PROGRESS") return "Vào khám";
    return "Gọi số";
  };
  const laneOf = (entry: QueueEntry) =>
    entry.schedulingLane ??
    (entry.consultationPhase === "RESULT_REVIEW" || entry.priorityLevel === "RESULT_REVIEW"
      ? "RESULT_REVIEW"
      : entry.queueClass === "PRIORITY" ||
          entry.priorityLevel === "PRIORITY" ||
          entry.priorityLevel === "EMERGENCY"
        ? "PRIORITY"
        : "NORMAL");

  const laneViews = [
    {
      key: "PRIORITY" as const,
      title: "Làn ưu tiên",
      description: "Ca cần được xử lý trước",
      dotClass: "bg-[#BA1A1A]",
      headerClass: "bg-[#FFDAD6]/45 border-[#BA1A1A]/20",
      badgeClass: "bg-[#FFDAD6] text-[#BA1A1A]",
      entries:
        dashboardData?.priorityQueue ?? safeEntries.filter((entry) => laneOf(entry) === "PRIORITY"),
    },
    {
      key: "NORMAL" as const,
      title: "Làn thông thường",
      description: "FIFO theo thời điểm tiếp nhận",
      dotClass: "bg-[#0277A8]",
      headerClass: "bg-[#DDF3FC]/70 border-[#8BD2EE]/50",
      badgeClass: "bg-[#DDF3FC] text-[#00577E]",
      entries:
        dashboardData?.normalQueue ?? safeEntries.filter((entry) => laneOf(entry) === "NORMAL"),
    },
    {
      key: "RESULT_REVIEW" as const,
      title: "Làn đọc kết quả CLS",
      description: "Bệnh nhân quay lại đọc kết quả",
      dotClass: "bg-[#087F6A]",
      headerClass: "bg-[#D9F3EC]/75 border-[#087F6A]/25",
      badgeClass: "bg-[#D9F3EC] text-[#087F6A]",
      entries:
        dashboardData?.resultReviewQueue ??
        safeEntries.filter((entry) => laneOf(entry) === "RESULT_REVIEW"),
    },
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between bg-white border border-[#D5E0E6] rounded-xl px-5 py-4 shadow-[0_2px_8px_rgba(23,36,45,0.03)]">
        <div>
          <h1 className="text-xl font-bold text-[#17242D] tracking-tight">
            Hàng đợi khám (Phòng {dashboardData?.roomCode || roomId || "—"})
          </h1>
          <p className="mt-0.5 text-xs text-[#4D626E]">
            Danh sách bệnh nhân đã tiếp nhận sẵn sàng vào khám theo mô hình 3 làn
          </p>
        </div>
        <button
          onClick={handleCallNext}
          disabled={isCallingNext || isLoading || !hasCallableEntry}
          className="px-4 py-2.5 bg-[#0277A8] hover:bg-[#00577E] disabled:opacity-50 text-white rounded-xl text-xs font-bold shadow-xs flex items-center gap-2 transition-all cursor-pointer"
        >
          {isCallingNext ? (
            <>
              <svg className="animate-spin h-3.5 w-3.5 text-white" viewBox="0 0 24 24" fill="none">
                <circle
                  className="opacity-25"
                  cx="12"
                  cy="12"
                  r="10"
                  stroke="currentColor"
                  strokeWidth="4"
                />
                <path
                  className="opacity-75"
                  fill="currentColor"
                  d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                />
              </svg>
              <span>Đang gọi...</span>
            </>
          ) : (
            <span>Gọi số tiếp theo</span>
          )}
        </button>
      </div>

      {dashboardData?.receptionActive === false && (
        <p
          role="status"
          className="rounded-xl border border-[#A86100]/30 bg-[#FFEBCB] p-3.5 text-xs font-medium text-[#A86100]"
        >
          Phòng đang tạm ngừng tiếp nhận và gọi lượt mới. Bạn vẫn có thể xem và hoàn tất các ca đang phục vụ.
        </p>
      )}

      {recommended && (
        <div
          className="rounded-xl p-4 text-white border border-[#163A50] shadow-[0_4px_14px_rgba(2,119,168,0.18)] flex items-center justify-between gap-4"
          style={{
            background: "linear-gradient(135deg, #0B2534 0%, #00577E 60%, #0277A8 100%)",
          }}
        >
          <div className="flex items-center gap-3.5">
            <span className="text-lg font-extrabold bg-white/15 text-[#8BD2EE] border border-[#8BD2EE]/40 px-3 py-1 rounded-xl font-mono">
              #{recommended.queueNumber}
            </span>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-[10px] bg-[#FFEBCB] text-[#A86100] font-bold px-2 py-0.5 rounded-full">
                  Đề xuất gọi tiếp theo
                </span>
                <span className="text-sm font-bold text-white">
                  {recommended.patientName ||
                    patientNames[recommended.patientId] ||
                    "Đang tải tên..."}
                </span>
              </div>
              <p className="text-xs text-[#DDF3FC] mt-1">
                Dự kiến thời gian chờ:{" "}
                <span className="font-bold text-white font-mono">
                  ~{recommended.estimatedWaitMinutes ?? 0} phút
                </span>
              </p>
            </div>
          </div>
          <button
            onClick={() => void handleCallEntry(recommended)}
            disabled={!!callingEntryId || dashboardData?.receptionActive === false}
            className="w-28 py-2 bg-white hover:bg-[#DDF3FC] disabled:opacity-50 text-[#00577E] font-bold text-xs rounded-xl shadow-xs transition-colors cursor-pointer"
          >
            {callingEntryId === recommended.entryId
              ? "Đang xử lý..."
              : actionLabel(recommended.queueStatus)}
          </button>
        </div>
      )}

      {errorMessage && (
        <div
          role="alert"
          className="flex items-center justify-between gap-4 rounded-xl border border-[#A86100]/30 bg-[#FFEBCB] px-4 py-3 text-xs font-medium text-[#A86100]"
        >
          <p>{errorMessage}</p>
          {activeConsultationId && (
            <Link
              href={`/consultation/${activeConsultationId}`}
              className="shrink-0 rounded-xl bg-[#0277A8] px-3.5 py-2 font-bold text-white hover:bg-[#00577E]"
            >
              Vào ca khám
            </Link>
          )}
        </div>
      )}

      <div className="bg-white rounded-xl border border-[#D5E0E6] shadow-[0_2px_8px_rgba(23,36,45,0.03)] overflow-hidden">
        <div className="px-5 py-3.5 border-b border-[#D5E0E6] flex items-center justify-between flex-wrap gap-2 bg-[#F5F8FA]">
          <h2 className="text-sm font-bold text-[#17242D] tracking-tight">
            Hàng đợi ({isLoading ? "..." : safeEntries.length} bệnh nhân)
          </h2>
          <span className="text-[11px] text-[#667985]">
            Bác sĩ chủ động chọn bệnh nhân; hệ thống đề xuất thứ tự gọi ưu tiên
          </span>
        </div>

        {isLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 p-4">
            {Array.from({ length: 3 }).map((_, index) => (
              <div
                key={index}
                className="h-48 rounded-xl border border-[#EDF2F5] bg-[#F5F8FA] animate-pulse"
              />
            ))}
          </div>
        ) : fetchError ? (
          <p className="px-4 py-8 text-center text-xs font-semibold text-[#BA1A1A]">{fetchError}</p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 p-4">
            {laneViews.map((lane) => (
              <section
                key={lane.key}
                className="min-w-0 rounded-xl border border-[#D5E0E6] overflow-hidden bg-white flex flex-col"
              >
                <div className={`px-3.5 py-3 border-b ${lane.headerClass}`}>
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2 min-w-0">
                      <span className={`w-2 h-2 rounded-full ${lane.dotClass} flex-shrink-0`} />
                      <h3 className="text-xs font-bold text-[#17242D] truncate">{lane.title}</h3>
                    </div>
                    <span
                      className={`min-w-6 h-5 px-2 inline-flex items-center justify-center rounded-full text-[11px] font-bold ${lane.badgeClass}`}
                    >
                      {lane.entries.length}
                    </span>
                  </div>
                  <p className="mt-1 text-[11px] text-[#4D626E] truncate">{lane.description}</p>
                </div>

                <div className="divide-y divide-[#EDF2F5] flex-1">
                  {lane.entries.length === 0 ? (
                    <div className="px-3 py-10 text-center text-xs text-[#667985]">
                      Chưa có bệnh nhân
                    </div>
                  ) : (
                    lane.entries.map((entry) => (
                      <div
                        key={entry.entryId}
                        className="px-3.5 py-3.5 hover:bg-[#F5F8FA] transition-colors"
                      >
                        <div className="flex items-start justify-between gap-2.5">
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-2">
                              <span className="font-bold text-[#00577E] font-mono text-xs">
                                #{entry.queueNumber}
                              </span>
                              <QueueStatusDot status={entry.queueStatus} />
                            </div>
                            <p className="mt-1 text-xs font-bold text-[#17242D] truncate">
                              {entry.patientName ||
                                patientNames[entry.patientId] ||
                                "Đang tải..."}
                            </p>
                            <p
                              className="mt-0.5 text-[10px] text-[#667985] font-mono truncate"
                              title={entry.patientId}
                            >
                              Mã BN: {entry.patientId.slice(0, 8).toUpperCase()}
                            </p>
                          </div>
                          <button
                            onClick={() => void handleCallEntry(entry)}
                            disabled={
                              !!callingEntryId ||
                              (dashboardData?.receptionActive === false &&
                                (entry.queueStatus === "CHECKED_IN" ||
                                  entry.queueStatus === "QUEUED"))
                            }
                            className="flex-shrink-0 w-24 py-1.5 bg-white hover:bg-[#DDF3FC]/70 border border-[#D5E0E6] hover:border-[#0277A8] text-[#17242D] hover:text-[#00577E] disabled:opacity-50 rounded-lg text-[11px] font-semibold transition-all cursor-pointer"
                          >
                            {callingEntryId === entry.entryId
                              ? "Đang xử lý..."
                              : actionLabel(entry.queueStatus)}
                          </button>
                        </div>
                        <div className="mt-2.5 flex items-center justify-between text-[10px] text-[#667985]">
                          <span>{entry.roomCode || "Phòng khám"}</span>
                          <QueuePriorityBadge
                            level={
                              lane.key === "RESULT_REVIEW"
                                ? "RESULT_REVIEW"
                                : entry.priorityLevel
                            }
                          />
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

      <section className="space-y-3 rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-[0_2px_8px_rgba(23,36,45,0.03)]">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1 border-b border-[#EDF2F5] pb-3">
          <div>
            <h2 className="text-sm font-bold text-[#17242D]">Lượt đã gọi và lỡ lượt</h2>
            <p className="text-xs text-[#4D626E] mt-0.5">
              Gọi đủ 3 lần theo quy định trước khi đánh dấu lỡ lượt. Xếp lại theo chính sách phòng; lượt đọc kết quả luôn về cuối.
            </p>
          </div>
        </div>
        {safeEntries
          .filter((e) => e.queueStatus === "CALLED" || e.queueStatus === "MISSED")
          .map((entry) => (
            <div
              key={entry.entryId}
              className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#D5E0E6] bg-[#F5F8FA] px-4 py-3 text-xs"
            >
              <div className="flex flex-wrap items-center gap-2 font-medium text-[#17242D]">
                <span className="font-mono font-bold text-[#00577E]">#{entry.queueNumber}</span>
                <span>·</span>
                <span className="font-bold">
                  {entry.patientName || patientNames[entry.patientId] || "Bệnh nhân"}
                </span>
                <span>·</span>
                <QueueStatusDot status={entry.queueStatus} />
                <span>·</span>
                <span className="text-[#4D626E] font-mono">
                  Đã gọi {entry.callAttempts}/3 lần
                </span>
              </div>
              <div className="flex flex-wrap gap-2">
                {entry.queueStatus === "CALLED" ? (
                  <>
                    <button
                      disabled={!!callingEntryId || entry.callAttempts >= 3}
                      onClick={() => void handleMissedAction(entry, "recall")}
                      className="rounded-lg border border-[#D5E0E6] bg-white px-3 py-1.5 text-xs font-semibold text-[#0277A8] hover:bg-[#DDF3FC]/60 disabled:opacity-50 cursor-pointer"
                    >
                      Gọi lại
                    </button>
                    <button
                      disabled={!!callingEntryId || entry.callAttempts < 3}
                      onClick={() => void handleMissedAction(entry, "miss")}
                      className="rounded-lg border border-[#D5E0E6] bg-white px-3 py-1.5 text-xs font-semibold text-[#4D626E] hover:border-[#BA1A1A] hover:bg-[#FFDAD6]/40 hover:text-[#BA1A1A] disabled:opacity-50 cursor-pointer"
                    >
                      Đánh dấu lỡ lượt
                    </button>
                  </>
                ) : (
                  <>
                    {dashboardData?.missedPolicy === "REQUIRE_MANUAL" &&
                    entry.consultationPhase !== "RESULT_REVIEW" ? (
                      <>
                        <button
                          disabled={!!callingEntryId}
                          onClick={() => void handleMissedAction(entry, "requeue", "FRONT")}
                          className="rounded-lg border border-[#D5E0E6] bg-white px-3 py-1.5 text-xs font-semibold text-[#0277A8] hover:bg-[#DDF3FC]/60 disabled:opacity-50 cursor-pointer"
                        >
                          Xếp đầu
                        </button>
                        <button
                          disabled={!!callingEntryId}
                          onClick={() => void handleMissedAction(entry, "requeue", "BACK")}
                          className="rounded-lg border border-[#D5E0E6] bg-white px-3 py-1.5 text-xs font-semibold text-[#4D626E] hover:bg-[#EDF2F5] cursor-pointer"
                        >
                          Xếp cuối
                        </button>
                      </>
                    ) : (
                      <button
                        disabled={!!callingEntryId}
                        onClick={() => void handleMissedAction(entry, "requeue")}
                        className="rounded-lg border border-[#D5E0E6] bg-white px-3 py-1.5 text-xs font-semibold text-[#0277A8] hover:bg-[#DDF3FC]/60 cursor-pointer"
                      >
                        {entry.consultationPhase === "RESULT_REVIEW" ||
                        dashboardData?.missedPolicy === "REQUEUE_BACK"
                          ? "Xếp cuối"
                          : "Xếp đầu"}
                      </button>
                    )}
                  </>
                )}
              </div>
            </div>
          ))}
        {!safeEntries.some((e) => e.queueStatus === "CALLED" || e.queueStatus === "MISSED") && (
          <p className="text-xs text-[#667985] py-2">Không có lượt cần xử lý.</p>
        )}
      </section>
    </div>
  );
}
