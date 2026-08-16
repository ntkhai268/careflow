"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { queueApi, QueueEntry } from "@/lib/queue-api";
import { patientApi, PatientOperationalResponse } from "@/lib/patient-api";
import { directoryApi, RoomItem } from "@/lib/directory-api";
import LoadingSpinner from "@/components/ui/LoadingSpinner";

const statusLabels: Record<string, string> = {
  WAITING: "Đang chờ",
  TICKET_ISSUED: "Chờ tiếp nhận",
  CHECKED_IN: "Đã tiếp nhận",
  CALLED: "Đã gọi số",
  IN_PROGRESS: "Đang khám",
  COMPLETED: "Hoàn tất",
  MISSED: "Lỡ lượt",
  CANCELLED: "Đã hủy",
};

const statusColors: Record<string, string> = {
  WAITING: "text-amber-600",
  TICKET_ISSUED: "text-amber-600",
  CHECKED_IN: "text-blue-600",
  CALLED: "text-purple-600",
  IN_PROGRESS: "text-purple-600",
  COMPLETED: "text-emerald-600",
  MISSED: "text-rose-600",
  CANCELLED: "text-slate-500",
};

const priorityLabels: Record<string, string> = {
  APPOINTMENT: "Đặt lịch",
  PRIORITY: "Ưu tiên",
  EMERGENCY: "Khẩn cấp",
  WALK_IN: "Vãng lai",
  RESULT_REVIEW: "Đọc kết quả CLS",
};

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

function StatusText({ status }: { status: string }) {
  return (
    <span className={`inline-flex items-center gap-1.5 text-[10px] font-medium ${statusColors[status] || "text-slate-500"}`}>
      <span className="h-1.5 w-1.5 rounded-full bg-current" />
      {statusLabels[status] || status}
    </span>
  );
}

function notifyAi(text: string) {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent("careflow:ai-notify", { detail: { text } }));
  }
}

function RoomPicker({
  rooms,
  value,
  onChange,
}: {
  rooms: RoomItem[];
  value: string;
  onChange: (roomId: string) => void;
}) {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const selected = rooms.find((room) => room.id === value);

  useEffect(() => {
    const handleOutsideClick = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setIsOpen(false);
    };
    document.addEventListener("mousedown", handleOutsideClick);
    return () => document.removeEventListener("mousedown", handleOutsideClick);
  }, []);

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setIsOpen((open) => !open)}
        className="flex h-11 w-full items-center justify-between rounded-lg border border-slate-200 bg-slate-50 px-3 text-left text-[11px] text-slate-800 shadow-sm outline-none hover:bg-[#F3E8F5] focus:border-[#7B4B94] focus:ring-2 focus:ring-[#F3E8F5]"
      >
        <span>{selected ? `${selected.id} · ${selected.displayName}` : value}</span>
        <span className="text-slate-400">⌄</span>
      </button>
      {isOpen && (
        <div className="absolute z-20 mt-2 max-h-56 w-full overflow-y-auto rounded-lg border border-slate-200 bg-white p-1 shadow-lg">
          {rooms.length === 0 ? (
            <p className="px-3 py-2 text-[10px] text-slate-500">Chưa tải được phòng khám.</p>
          ) : rooms.map((room) => (
            <button
              key={room.id}
              type="button"
              onClick={() => { onChange(room.id); setIsOpen(false); }}
              className={`block w-full rounded-md px-3 py-2 text-left text-[11px] hover:bg-[#F3E8F5] ${room.id === value ? "bg-[#F3E8F5] font-semibold text-[#6E2582]" : "text-slate-700"}`}
            >
              {room.id} · {room.displayName}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export default function StaffCheckinPage() {
  const [qrInput, setQrInput] = useState("");
  const [roomId, setRoomId] = useState("ROOM-01");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [checkInResult, setCheckInResult] = useState<QueueEntry | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [roomEntries, setRoomEntries] = useState<QueueEntry[] | null>(null);
  const [patientDetails, setPatientDetails] = useState<Record<string, PatientOperationalResponse | null>>({});
  const [isLoadingQueue, setIsLoadingQueue] = useState(false);
  const [processingEntryId, setProcessingEntryId] = useState<string | null>(null);
  const [rooms, setRooms] = useState<RoomItem[]>([]);
  const [hospitalQr, setHospitalQr] = useState<{ qrToken: string; sessionCode: string; sessionDate: string; expiresAt: string } | null>(null);
  const [isLoadingQr, setIsLoadingQr] = useState(false);
  const [qrError, setQrError] = useState<string | null>(null);

  useEffect(() => {
    const loadRooms = window.setTimeout(() => {
      void directoryApi.getRooms("", "CONSULTATION")
        .then((items) => {
          const consultationRooms = items.filter(
            (room) => room.roomType === "CONSULTATION" && room.isActive !== false,
          );
          setRooms(consultationRooms);
          if (consultationRooms.length > 0) {
            setRoomId((currentRoomId) => consultationRooms.some((room) => room.id === currentRoomId)
              ? currentRoomId
              : consultationRooms[0].id);
          }
        })
        .catch(() => setRooms([]));
    }, 0);
    return () => window.clearTimeout(loadRooms);
  }, []);

  const fetchHospitalQr = useCallback(async () => {
    if (!roomId) return;
    setIsLoadingQr(true);
    setQrError(null);
    try {
      const res = await queueApi.getHospitalCheckInQr(roomId);
      setHospitalQr(res.data ?? null);
    } catch (err: unknown) {
      setHospitalQr(null);
      setQrError(getErrorMessage(err, "Không thể tạo QR check-in cho phòng này."));
    } finally {
      setIsLoadingQr(false);
    }
  }, [roomId]);

  useEffect(() => {
    const loadQr = window.setTimeout(() => void fetchHospitalQr(), 0);
    return () => window.clearTimeout(loadQr);
  }, [fetchHospitalQr]);

  const fetchRoomQueue = useCallback(async () => {
    setIsLoadingQueue(true);
    try {
      const res = await queueApi.getRoomActive(roomId);
      const entries = res.data?.entries ?? [];
      setRoomEntries(entries);

      const uniquePatientIds = [...new Set(entries.map((entry) => entry.patientId).filter(Boolean))];
      const patientResults = await Promise.all(
        uniquePatientIds.map(async (patientId) => {
          try {
            const entry = entries.find((item) => item.patientId === patientId);
            if (!entry?.appointmentId || !entry.roomCode) {
              return [patientId, null] as const;
            }
            const patientRes = await patientApi.getOperationalSummary(
              patientId, entry.appointmentId, entry.roomCode);
            return [patientId, patientRes.data ?? null] as const;
          } catch {
            return [patientId, null] as const;
          }
        }),
      );
      setPatientDetails(Object.fromEntries(patientResults));
    } catch {
      setRoomEntries([]);
      notifyAi("Không thể tải hàng đợi phòng khám. Vui lòng thử lại.");
    } finally {
      setIsLoadingQueue(false);
    }
  }, [roomId]);

  useEffect(() => {
    const loadQueue = window.setTimeout(() => void fetchRoomQueue(), 0);
    return () => window.clearTimeout(loadQueue);
  }, [fetchRoomQueue]);

  const handleCheckIn = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!qrInput.trim()) {
      setError("Vui lòng nhập mã phiếu khám khi hỗ trợ bệnh nhân.");
      notifyAi("Nhân viên vui lòng nhập mã phiếu khám để hỗ trợ check-in tại quầy.");
      return;
    }

    setIsSubmitting(true);
    setError(null);
    setCheckInResult(null);

    try {
      const identifier = qrInput.trim();
      const isQrToken = identifier.split(".").length === 3;
      const res = await queueApi.checkIn(
        isQrToken ? { qrToken: identifier, roomId } : { ticketCode: identifier, roomId },
      );
      if (res.data) {
        setCheckInResult(res.data);
        setQrInput("");
        notifyAi(`Tiếp nhận thành công bệnh nhân. Số thứ tự ${res.data.queueNumber} tại ${res.data.roomCode}.`);
        await fetchRoomQueue();
      }
    } catch (err: unknown) {
      const errorMessage = getErrorMessage(err, "Mã QR không hợp lệ hoặc đã quá hạn.");
      setError(errorMessage);
      notifyAi(`Rất tiếc. ${errorMessage}`);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCheckInEntry = async (entry: QueueEntry) => {
    setProcessingEntryId(entry.entryId);
    try {
      const res = await queueApi.checkIn({ ticketCode: entry.queueNumber, roomId });
      if (res.data) {
        setCheckInResult(res.data);
        notifyAi(`Tiếp nhận thành công bệnh nhân. Số thứ tự ${res.data.queueNumber}.`);
        await fetchRoomQueue();
      }
    } catch (err: unknown) {
      notifyAi(getErrorMessage(err, "Không thể duyệt tiếp nhận lượt khám."));
    } finally {
      setProcessingEntryId(null);
    }
  };

  const handleRecall = async (entry: QueueEntry) => {
    setProcessingEntryId(entry.entryId);
    try {
      await queueApi.recallEntry(entry.entryId);
      notifyAi(`Đã gọi lại số thứ tự ${entry.queueNumber}.`);
      await fetchRoomQueue();
    } catch (err: unknown) {
      notifyAi(getErrorMessage(err, "Không thể gọi lại lượt khám."));
    } finally {
      setProcessingEntryId(null);
    }
  };

  const handleMiss = async (entry: QueueEntry) => {
    setProcessingEntryId(entry.entryId);
    try {
      await queueApi.missEntry(entry.entryId);
      notifyAi(`Đã đánh dấu vắng mặt cho số thứ tự ${entry.queueNumber}.`);
      await fetchRoomQueue();
    } catch (err: unknown) {
      notifyAi(getErrorMessage(err, "Không thể đánh dấu vắng mặt."));
    } finally {
      setProcessingEntryId(null);
    }
  };

  const waitingCount = (roomEntries ?? []).filter((entry) =>
    ["WAITING", "TICKET_ISSUED", "CHECKED_IN"].includes(entry.queueStatus),
  ).length;

  return (
    <div className="mx-auto max-w-[1440px] space-y-5 pb-12">
      <header className="flex flex-col gap-3 border-b border-slate-200 pb-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-[#7B4B94]">Quầy tiếp nhận</p>
          <h1 className="mt-1 text-xl font-bold tracking-tight text-[#2B1D30]">Check-in bệnh nhân</h1>
          <p className="mt-1 text-[11px] text-slate-500">Hiển thị QR theo phòng/phiên để bệnh nhân tự quét và xác minh geofence.</p>
        </div>
        <div className="flex items-center gap-4 text-[10px] text-slate-500">
          <span className="flex items-center gap-1.5"><span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />Hệ thống sẵn sàng</span>
          <span>{waitingCount} lượt đang chờ</span>
        </div>
      </header>

      <section className="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)]">
          <div className="mb-5 flex items-start justify-between gap-4">
            <div>
              <h2 className="text-[15px] font-semibold text-[#2B1D30]">QR check-in bệnh viện</h2>
              <p className="mt-1 text-[10px] text-slate-500">Bệnh nhân dùng Patient Mobile quét mã này tại bệnh viện.</p>
            </div>
            <button
              type="button"
              onClick={() => void fetchHospitalQr()}
              className="text-[10px] font-semibold text-[#7B4B94] hover:underline"
            >
              Tạo mã mới
            </button>
          </div>

          <div className="mb-5">
            <div>
              <label className="mb-1.5 block text-[10px] font-semibold text-slate-700">Phòng khám</label>
              <RoomPicker rooms={rooms} value={roomId} onChange={setRoomId} />
            </div>
          </div>

          <div className="rounded-lg border border-dashed border-[#C7A6D1] bg-[#FCF8FD] p-4 text-center">
            {isLoadingQr ? (
              <div className="flex min-h-[230px] items-center justify-center"><LoadingSpinner size="md" /></div>
            ) : hospitalQr ? (
              <>
                <div className="mx-auto w-fit rounded-xl bg-white p-3 shadow-sm">
                  <QRCodeSVG value={hospitalQr.qrToken} size={210} level="M" includeMargin />
                </div>
                <p className="mt-3 text-[11px] font-semibold text-[#2B1D30]">{roomId} · {hospitalQr.sessionCode}</p>
                <p className="mt-1 text-[10px] text-slate-500">Có hiệu lực đến {new Date(hospitalQr.expiresAt).toLocaleTimeString("vi-VN")}</p>
              </>
            ) : (
              <p className="min-h-[230px] content-center text-[11px] text-slate-500">{qrError || "Chưa có QR check-in cho phòng này."}</p>
            )}
          </div>

          <form onSubmit={handleCheckIn} className="mt-5 space-y-4 border-t border-slate-100 pt-5">
            <div>
              <h3 className="text-[12px] font-semibold text-[#2B1D30]">Hỗ trợ tại quầy</h3>
              <p className="mt-1 text-[10px] text-slate-500">Dùng mã phiếu khám khi bệnh nhân không thể dùng Mobile.</p>
            </div>

            <div>
              <label htmlFor="qr-input" className="mb-1.5 block text-[10px] font-semibold text-slate-700">Mã phiếu khám</label>
              <input
                id="qr-input"
                type="text"
                value={qrInput}
                onChange={(e) => setQrInput(e.target.value)}
                placeholder="Nhập mã phiếu khám"
                autoComplete="off"
                className="h-12 w-full rounded-md border border-slate-200 bg-white px-3 font-mono text-xs text-slate-800 outline-none placeholder:font-sans placeholder:text-slate-400 focus:border-[#7B4B94] focus:ring-2 focus:ring-[#F3E8F5]"
              />
              {error && <p className="mt-2 text-[10px] text-[#D9381E]">{error}</p>}
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="h-11 w-full rounded-md bg-[#6E2582] px-4 text-[11px] font-semibold text-white transition-colors hover:bg-[#561A66] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {isSubmitting ? "Đang tiếp nhận..." : "Hỗ trợ check-in"}
            </button>
          </form>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)]">
          <div className="mb-5 flex items-start justify-between gap-4">
            <div>
              <h2 className="text-[15px] font-semibold text-[#2B1D30]">Kết quả gần nhất</h2>
              <p className="mt-1 text-[10px] text-slate-500">Thông tin được cập nhật sau mỗi lượt tiếp nhận.</p>
            </div>
            {checkInResult && <span className="text-[10px] font-medium text-emerald-600">Đã cập nhật</span>}
          </div>

          {checkInResult ? (
            <div className="border-l-3 border-[#7B4B94] bg-[#F8FAFC] px-5 py-4">
              <div className="flex items-center justify-between gap-4">
                <p className="text-[10px] font-semibold uppercase tracking-wider text-emerald-600">Tiếp nhận thành công</p>
                <StatusText status={checkInResult.queueStatus} />
              </div>
              <div className="mt-5 flex items-end gap-3 border-b border-slate-200 pb-5">
                <span className="font-mono text-5xl font-bold leading-none text-[#2B1D30]">{checkInResult.queueNumber}</span>
                <div className="pb-1 text-[10px] text-slate-500">
                  <p>Phòng {checkInResult.roomCode}</p>
                  <p className="mt-1">{checkInResult.departmentName}</p>
                </div>
              </div>
              <dl className="mt-4 grid grid-cols-2 gap-4 text-[10px]">
                <div><dt className="text-slate-400">Thời gian</dt><dd className="mt-1 font-medium text-slate-700">{checkInResult.checkedInAt ? new Date(checkInResult.checkedInAt).toLocaleTimeString("vi-VN") : "Vừa xong"}</dd></div>
                <div><dt className="text-slate-400">Loại lượt</dt><dd className="mt-1 font-medium text-slate-700">{priorityLabels[checkInResult.priorityLevel] || checkInResult.priorityLevel}</dd></div>
              </dl>
            </div>
          ) : (
            <div className="flex min-h-[220px] items-center justify-center border-l-3 border-slate-200 bg-slate-50 px-8 text-center text-[11px] text-slate-500">
              Kết quả check-in sẽ hiển thị tại đây sau khi quét mã.
            </div>
          )}
        </div>
      </section>

      <section className="rounded-lg border border-slate-200 bg-white shadow-[0_1px_3px_rgba(110,37,130,0.06)]">
        <div className="flex flex-col gap-3 border-b border-slate-200 p-5 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h2 className="text-[15px] font-semibold text-[#2B1D30]">Hàng đợi phòng khám</h2>
            <p className="mt-1 text-[10px] text-slate-500">Phòng đang chọn: <span className="font-medium text-slate-700">{roomId}</span></p>
          </div>
          <button onClick={() => void fetchRoomQueue()} className="h-9 rounded-md border border-slate-200 px-3 text-[10px] font-semibold text-[#7B4B94] hover:bg-[#F3E8F5]">Tải lại</button>
        </div>

        {isLoadingQueue ? (
          <div className="flex min-h-[180px] items-center justify-center"><LoadingSpinner size="md" /></div>
        ) : (roomEntries ?? []).length === 0 ? (
          <div className="flex min-h-[180px] items-center justify-center px-5 text-center text-[11px] text-slate-500">Chưa có lượt khám trong hàng đợi của phòng này.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] border-collapse text-left">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 text-[9px] font-semibold uppercase tracking-wider text-slate-400">
                  <th className="px-5 py-3">Số thứ tự</th>
                  <th className="px-4 py-3">Bệnh nhân</th>
                  <th className="px-4 py-3">Loại lượt</th>
                  <th className="px-4 py-3">Trạng thái</th>
                  <th className="px-4 py-3">Thời gian tiếp nhận</th>
                  <th className="px-5 py-3 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {(roomEntries ?? []).map((entry) => {
                  const patient = patientDetails[entry.patientId];
                  const isProcessing = processingEntryId === entry.entryId;
                  const canCheckIn = entry.queueStatus === "WAITING" || entry.queueStatus === "TICKET_ISSUED";
                  return (
                    <tr key={entry.entryId} className="min-h-[44px] border-b border-slate-100 last:border-0 hover:bg-slate-50">
                      <td className="px-5 py-3.5"><span className="font-mono text-sm font-bold text-[#2B1D30]">{entry.queueNumber}</span></td>
                      <td className="px-4 py-3.5">
                        {patient ? (
                          <div>
                            <p className="text-[11px] font-semibold text-[#2B1D30]">{patient.fullName}</p>
                            <p className="mt-1 text-[9px] text-slate-500">
                              Mã BN: {patient.id.slice(0, 8)} · {patient.gender || "Chưa rõ giới tính"}
                              {patient.dateOfBirth ? ` · ${new Date(patient.dateOfBirth).getFullYear()}` : ""}
                            </p>
                          </div>
                        ) : (
                          <div>
                            <p className="text-[11px] font-medium text-slate-400">Đang tải thông tin</p>
                            <p className="mt-1 text-[9px] text-slate-400">Mã BN: {entry.patientId.slice(0, 8)}</p>
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3.5 text-[10px] text-slate-600">{priorityLabels[entry.priorityLevel] || entry.priorityLevel}</td>
                      <td className="px-4 py-3.5"><StatusText status={entry.queueStatus} /></td>
                      <td className="px-4 py-3.5 text-[10px] text-slate-500">{entry.checkedInAt ? new Date(entry.checkedInAt).toLocaleTimeString("vi-VN") : "Chưa tiếp nhận"}</td>
                      <td className="px-5 py-3.5">
                        <div className="flex justify-end gap-2">
                          {canCheckIn && <button onClick={() => void handleCheckInEntry(entry)} disabled={isProcessing} className="h-8 w-24 rounded-md text-[10px] font-semibold text-[#7B4B94] hover:bg-[#F3E8F5] disabled:opacity-50">{isProcessing ? "Đang xử lý" : "Duyệt vào"}</button>}
                          <button onClick={() => void handleRecall(entry)} disabled={isProcessing} className="h-8 w-20 rounded-md border border-slate-200 text-[10px] font-semibold text-slate-600 hover:border-[#7B4B94] hover:text-[#7B4B94] disabled:opacity-50">Gọi lại</button>
                          <button onClick={() => void handleMiss(entry)} disabled={isProcessing} className="h-8 w-20 rounded-md text-[10px] font-medium text-slate-500 hover:bg-rose-50 hover:text-rose-600 disabled:opacity-50">Vắng mặt</button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
