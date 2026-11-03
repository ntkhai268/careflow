"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { queueApi, QueueEntry } from "@/lib/queue-api";
import { patientApi, PatientOperationalResponse } from "@/lib/patient-api";
import { directoryApi, RoomItem } from "@/lib/directory-api";
import { useAuth } from "@/contexts/AuthContext";
import HandoverPicker from "@/components/ui/HandoverPicker";
import WalkInReception from "@/components/WalkInReception";
import { receptionApi } from "@/lib/reception-api";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/error-utils";
import HospitalQrCheckInCard from "@/components/queue/HospitalQrCheckInCard";
import ReceptionQueueTable from "@/components/queue/ReceptionQueueTable";
import { notifyAi, useVisibilityPolling } from "@/components/queue/useQueueOperations";

function RoomPicker({
  rooms,
  value,
  onChange,
}: {
  rooms: RoomItem[];
  value: string;
  onChange: (roomId: string) => void;
}) {
  return (
    <HandoverPicker
      label="Phòng tiếp nhận"
      value={value}
      options={rooms.map((room) => ({
        value: room.id,
        label: `${room.id} · ${room.displayName}`,
      }))}
      onChange={onChange}
    />
  );
}

export default function StaffCheckinPage() {
  const { user } = useAuth();
  const [qrInput, setQrInput] = useState("");
  const [roomId, setRoomId] = useState("");
  const [queueError, setQueueError] = useState<string | null>(null);
  const [roomError, setRoomError] = useState<string | null>(null);
  const queueRequest = useRef(0);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [checkInResult, setCheckInResult] = useState<QueueEntry | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [roomEntries, setRoomEntries] = useState<QueueEntry[] | null>(null);
  const [patientDetails, setPatientDetails] = useState<Record<string, PatientOperationalResponse | null>>({});
  const [isLoadingQueue, setIsLoadingQueue] = useState(false);
  const [processingEntryId, setProcessingEntryId] = useState<string | null>(null);
  const [rooms, setRooms] = useState<RoomItem[]>([]);
  const [hospitalQr, setHospitalQr] = useState<{
    siteId: string;
    qrToken: string;
    sessionCode: string;
    sessionDate: string;
    expiresAt: string;
  } | null>(null);
  const [isLoadingQr, setIsLoadingQr] = useState(false);
  const [qrError, setQrError] = useState<string | null>(null);

  useEffect(() => {
    if (!user) return;
    let active = true;
    const loadRooms = window.setTimeout(() => {
      void directoryApi
        .getRooms("", "CONSULTATION")
        .then(async (items) => {
          const consultationRooms = items.filter(
            (room) => room.roomType === "CONSULTATION" && room.isActive !== false
          );
          const permittedRooms =
            user.role === "ADMIN"
              ? consultationRooms
              : (
                  await Promise.all(
                    consultationRooms.map(async (room) => {
                      const access = await api.get<boolean>(
                        `/api/directory/staff/${user.id}/room-access?roomId=${encodeURIComponent(room.id)}`
                      );
                      return access.data === true ? room : null;
                    })
                  )
                ).filter((room): room is RoomItem => room !== null);
          if (!active) return;
          setRooms(permittedRooms);
          setRoomError(
            permittedRooms.length
              ? null
              : "Bạn chưa được phân công phòng tiếp nhận. Liên hệ quản trị để kiểm tra phân công."
          );
          if (permittedRooms.length > 0) {
            setRoomId((currentRoomId) =>
              permittedRooms.some((room) => room.id === currentRoomId)
                ? currentRoomId
                : permittedRooms[0].id
            );
          } else {
            setRoomId("");
          }
        })
        .catch((err: unknown) => {
          if (!active) return;
          setRooms([]);
          setRoomId("");
          setRoomError(
            getErrorMessage(err, "Không thể tải phân công phòng tiếp nhận. Vui lòng tải lại trang.")
          );
        });
    }, 0);
    return () => {
      active = false;
      window.clearTimeout(loadRooms);
    };
  }, [user]);

  const fetchHospitalQr = useCallback(async () => {
    setIsLoadingQr(true);
    setQrError(null);
    try {
      const res = await queueApi.getHospitalCheckInQr();
      setHospitalQr(res.data ?? null);
    } catch (err: unknown) {
      setHospitalQr(null);
      setQrError(getErrorMessage(err, "Không thể tạo QR check-in bệnh viện."));
    } finally {
      setIsLoadingQr(false);
    }
  }, []);

  useEffect(() => {
    const loadQr = window.setTimeout(() => void fetchHospitalQr(), 0);
    return () => window.clearTimeout(loadQr);
  }, [fetchHospitalQr]);

  const fetchRoomQueue = useCallback(
    async (background = false) => {
      if (!roomId) return;
      const request = ++queueRequest.current;
      if (!background) setIsLoadingQueue(true);
      try {
        const res = await queueApi.getRoomReception(roomId);
        if (request !== queueRequest.current) return;
        const entries = res.data?.entries ?? [];
        setRoomEntries(entries);
        setQueueError(null);

        const uniquePatientIds = [
          ...new Set(entries.map((entry) => entry.patientId).filter(Boolean)),
        ];
        const patientResults = await Promise.all(
          uniquePatientIds.map(async (patientId) => {
            try {
              const entry = entries.find((item) => item.patientId === patientId);
              if (!entry?.roomCode) return [patientId, null] as const;
              if (!entry.appointmentId) {
                const minimum = await receptionApi.minimum(patientId, entry.roomCode);
                const profile = minimum.data;
                return [
                  patientId,
                  {
                    id: profile.id,
                    fullName: profile.fullName,
                    dateOfBirth: profile.dateOfBirth ?? undefined,
                    gender: profile.gender ?? undefined,
                  },
                ] as const;
              }
              const patientRes = await patientApi.getOperationalSummary(
                patientId,
                entry.appointmentId,
                entry.roomCode
              );
              return [patientId, patientRes.data ?? null] as const;
            } catch {
              return [patientId, null] as const;
            }
          })
        );
        if (request === queueRequest.current) {
          setPatientDetails(Object.fromEntries(patientResults));
        }
      } catch (err: unknown) {
        if (request !== queueRequest.current) return;
        setRoomEntries(null);
        setQueueError(
          getErrorMessage(err, "Không thể tải danh sách tiếp nhận. Vui lòng thử lại.")
        );
      } finally {
        if (request === queueRequest.current) setIsLoadingQueue(false);
      }
    },
    [roomId]
  );

  useEffect(() => {
    const loadQueue = window.setTimeout(() => void fetchRoomQueue(), 0);
    return () => {
      ++queueRequest.current;
      window.clearTimeout(loadQueue);
    };
  }, [fetchRoomQueue]);

  useVisibilityPolling(Boolean(roomId), () => void fetchRoomQueue(true));

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
        isQrToken ? { qrToken: identifier, roomId } : { ticketCode: identifier, roomId }
      );
      if (res.data) {
        setCheckInResult(res.data);
        setQrInput("");
        notifyAi(
          `Tiếp nhận thành công bệnh nhân. Số thứ tự ${res.data.queueNumber} tại ${res.data.roomCode}.`
        );
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
      if (entry.queueStatus === "CHECKED_IN" || entry.queueStatus === "QUEUED") {
        await queueApi.callEntry(entry.entryId);
        notifyAi(`Đã gọi số thứ tự ${entry.queueNumber}.`);
      } else if (entry.queueStatus === "CALLED") {
        await queueApi.recallEntry(entry.entryId);
        notifyAi(`Đã gọi lại số thứ tự ${entry.queueNumber}.`);
      } else {
        return;
      }
      await fetchRoomQueue();
    } catch (err: unknown) {
      notifyAi(getErrorMessage(err, "Không thể gọi lại lượt khám."));
      await fetchRoomQueue();
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
    ["WAITING", "TICKET_ISSUED", "CHECKED_IN"].includes(entry.queueStatus)
  ).length;

  return (
    <div className="mx-auto max-w-[1440px] space-y-5 pb-12">
      <header className="flex flex-col gap-4 rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-xs lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-[#087F6A]" />
            <p className="text-[11px] font-bold uppercase tracking-[0.14em] text-[#087F6A]">
              Quầy tiếp nhận & phân luồng bệnh nhân
            </p>
          </div>
          <h1 className="mt-1 text-2xl font-extrabold tracking-tight text-[#17242D]">
            Check-in & Tiếp đón Bệnh nhân
          </h1>
          <p className="mt-0.5 text-xs text-[#667985]">
            Hiển thị QR chung cho bệnh nhân tự check-in tại viện, cấp số khám vãng lai và điều phối
            danh sách chờ tại phòng.
          </p>
        </div>

        <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="w-full sm:w-72">
            <RoomPicker rooms={rooms} value={roomId} onChange={setRoomId} />
          </div>
          <div className="flex h-11 items-center gap-3 rounded-xl border border-[#D5E0E6] bg-[#F5F8FA] px-4 text-xs font-semibold text-[#4D626E]">
            <span className="flex items-center gap-1.5 text-[#1B7F4B]">
              <span className="h-2 w-2 rounded-full bg-[#1B7F4B]" />
              Sẵn sàng
            </span>
            <span className="text-[#D5E0E6]">|</span>
            <span className="font-bold text-[#00577E]">{waitingCount} lượt chờ</span>
          </div>
        </div>
      </header>

      <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-12">
        <div className="space-y-5 xl:col-span-7">
          <ReceptionQueueTable
            roomId={roomId}
            roomError={roomError}
            queueError={queueError}
            isLoadingQueue={isLoadingQueue}
            roomEntries={roomEntries}
            patientDetails={patientDetails}
            processingEntryId={processingEntryId}
            onRefreshQueue={() => void fetchRoomQueue()}
            onCheckInEntry={(entry) => void handleCheckInEntry(entry)}
            onRecallEntry={(entry) => void handleRecall(entry)}
            onMissEntry={(entry) => void handleMiss(entry)}
          />

          <WalkInReception
            key={roomId}
            roomId={roomId}
            departmentCode={rooms.find((r) => r.id === roomId)?.departmentCode || ""}
            onIssued={() => void fetchRoomQueue()}
          />
        </div>

        <div className="xl:col-span-5">
          <HospitalQrCheckInCard
            hospitalQr={hospitalQr}
            isLoadingQr={isLoadingQr}
            qrError={qrError}
            onRefreshQr={() => void fetchHospitalQr()}
            qrInput={qrInput}
            setQrInput={setQrInput}
            error={error}
            isSubmitting={isSubmitting}
            roomId={roomId}
            onSubmitCheckIn={handleCheckIn}
            checkInResult={checkInResult}
          />
        </div>
      </div>
    </div>
  );
}
