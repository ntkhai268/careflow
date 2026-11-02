"use client";

import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { consultationApi, ConsultationResponse } from "@/lib/consultation-api";
import { queueApi } from "@/lib/queue-api";
import { patientApi } from "@/lib/patient-api";
import { appointmentApi } from "@/lib/appointment-api";
import { getErrorMessage } from "@/lib/error-utils";
import { patientAge, visitTime } from "@/lib/patient-display";
import { notifyAi } from "@/components/queue/useQueueOperations";
import GeneralStatCards from "@/components/dashboard/GeneralStatCards";
import GeneralQueuePanel, {
  DisplayQueuePatient,
} from "@/components/dashboard/GeneralQueuePanel";
import RecentlyCompletedPanel from "@/components/dashboard/RecentlyCompletedPanel";

export default function DashboardGeneralPage() {
  const { user } = useAuth();
  const router = useRouter();
  const [isCalling, setIsCalling] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [queuePatients, setQueuePatients] = useState<DisplayQueuePatient[] | null>(null);
  const [recentCompleted, setRecentCompleted] = useState<ConsultationResponse[] | null>(null);
  const [patientNames, setPatientNames] = useState<Record<string, string>>({});
  const [waitingCount, setWaitingCount] = useState<number | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [activeNotice, setActiveNotice] = useState<{
    show: boolean;
    message: string;
    consultationId?: string;
  }>({ show: false, message: "" });

  const isAdmin = Boolean(user?.role?.toUpperCase().includes("ADMIN"));
  const isLabTech = Boolean(user?.role?.toUpperCase().includes("LAB"));
  const isStaff = Boolean(user?.role?.toUpperCase().includes("STAFF"));

  useEffect(() => {
    if (isAdmin) {
      router.replace("/admin");
    }
  }, [isAdmin, router]);

  useEffect(() => {
    async function loadDashboardData() {
      if (!user?.id) return;
      setIsLoading(true);
      setErrorMessage("");
      try {
        if (isLabTech) {
          try {
            const labRes = await queueApi.getServicePointActive("LAB-HEMATOLOGY-01");
            const activeEntries = labRes.data?.entries || [];
            setWaitingCount(activeEntries.length);
            setQueuePatients(
              activeEntries.map((e) => ({
                entryId: e.entryId,
                queueNo: e.queueNumber,
                appointmentId: e.appointmentId,
                patientId: e.patientId,
                name: `Bệnh nhân (${e.patientId.slice(0, 6)})`,
                age: null,
                department: "Khu Xét nghiệm Cận lâm sàng",
                status: e.queueStatus,
                time: visitTime(e.scheduledStartAt),
              }))
            );
          } catch (error) {
            setWaitingCount(null);
            setQueuePatients(null);
            throw error;
          }
          setRecentCompleted([]);
        } else if (isStaff) {
          try {
            const staffRes = await queueApi.getServicePointActive("PHARMACY-MAIN-01");
            const activeEntries = staffRes.data?.entries || [];
            setWaitingCount(activeEntries.length);
            setQueuePatients(
              activeEntries.map((e) => ({
                entryId: e.entryId,
                queueNo: e.queueNumber,
                appointmentId: e.appointmentId,
                patientId: e.patientId,
                name: `Bệnh nhân (${e.patientId.slice(0, 6)})`,
                age: null,
                department: "Quầy Phát thuốc",
                status: e.queueStatus,
                time: visitTime(e.scheduledStartAt),
              }))
            );
          } catch (error) {
            setWaitingCount(null);
            setQueuePatients(null);
            throw error;
          }
          setRecentCompleted([]);
        } else {
          const consRes = await consultationApi.getTodayByDoctor(user.id);
          const todayCons = consRes.data || [];
          const completedList = todayCons.filter((c) => c.status === "COMPLETED");
          setRecentCompleted(completedList);

          const pNames: Record<string, string> = {};
          const pAges: Record<string, number | null> = {};

          try {
            const contextRes = await appointmentApi.getClinicalContext();
            const assignedRoomId = contextRes.data?.rooms?.[0]?.roomId;
            if (!assignedRoomId) throw new Error("Bác sĩ chưa được phân công phòng khám.");
            const queueRes = await queueApi.getRoomActive(assignedRoomId);
            const activeEntries = queueRes.data?.entries || [];
            setWaitingCount(activeEntries.length);

            for (const e of activeEntries) {
              if (!pNames[e.patientId]) {
                try {
                  const pRes = await patientApi.getPatientById(e.patientId);
                  if (pRes.data?.fullName) pNames[e.patientId] = pRes.data.fullName;
                  pAges[e.patientId] = patientAge(pRes.data?.dateOfBirth);
                } catch {
                  pNames[e.patientId] = `Bệnh nhân (${e.patientId.slice(0, 8)})`;
                }
              }
            }

            setQueuePatients(
              activeEntries.map((e) => ({
                entryId: e.entryId,
                queueNo: e.queueNumber,
                appointmentId: e.appointmentId,
                patientId: e.patientId,
                name: pNames[e.patientId] || `Bệnh nhân (${e.patientId.slice(0, 8)})`,
                age: pAges[e.patientId] ?? null,
                department: queueRes.data?.departmentName || "Chưa xác định khoa",
                status: e.queueStatus,
                time: visitTime(e.scheduledStartAt),
              }))
            );
          } catch (error) {
            setWaitingCount(null);
            setQueuePatients(null);
            throw error;
          }

          for (const c of completedList) {
            if (!pNames[c.patientId]) {
              try {
                const pRes = await patientApi.getPatientById(c.patientId);
                if (pRes.data?.fullName) pNames[c.patientId] = pRes.data.fullName;
              } catch {
                pNames[c.patientId] = `Bệnh nhân (${c.patientId.slice(0, 8)})`;
              }
            }
          }
          setPatientNames(pNames);
        }
      } catch (error) {
        setWaitingCount(null);
        setRecentCompleted(null);
        setErrorMessage(
          getErrorMessage(error, "Không thể tải dữ liệu khám bệnh. Vui lòng thử lại.")
        );
        setQueuePatients(null);
      } finally {
        setIsLoading(false);
      }
    }
    void loadDashboardData();
  }, [user, isLabTech, isStaff]);

  const handleCallPatient = async (patientId: string, appointmentId: string | null) => {
    if (!user) return;

    const queueEntry = (queuePatients ?? []).find(
      (entry) => entry.patientId === patientId && entry.appointmentId === appointmentId
    );
    const entryId = queueEntry?.entryId;
    const entryQuery = entryId ? `?entryId=${encodeURIComponent(entryId)}` : "";

    if (isLabTech) {
      router.push("/lab/queue");
      return;
    }

    if (isStaff) {
      router.push("/staff/pharmacy");
      return;
    }

    setIsCalling(true);
    setActiveNotice({ show: false, message: "" });

    try {
      if (!queueEntry || !entryId) {
        throw new Error("Không tìm thấy lượt hàng đợi của bệnh nhân.");
      }

      const consRes = await consultationApi.getByDoctor(user.id);
      const activeCons = (consRes.data || []).find((c) => c.status === "IN_PROGRESS");
      const isSameConsultation =
        activeCons &&
        (activeCons.queueEntryId === entryId ||
          (!!appointmentId && activeCons.appointmentId === appointmentId));

      if (activeCons && !isSameConsultation) {
        setActiveNotice({
          show: true,
          message:
            "Bạn đang có một ca khám chưa hoàn tất. Vào ca khám để xử lý trước khi gọi bệnh nhân khác.",
          consultationId: activeCons.id,
        });
        notifyAi(
          "Bác sĩ hiện tại đang có một ca khám chưa hoàn tất. Vui lòng hoàn thành lượt khám hiện tại trước khi gọi bệnh nhân khác.",
          activeCons.id
        );
        setIsCalling(false);
        return;
      }

      if (queueEntry.status === "CHECKED_IN" || queueEntry.status === "QUEUED") {
        await queueApi.callEntry(entryId);
        setQueuePatients(
          (current) =>
            current?.map((entry) =>
              entry.entryId === entryId ? { ...entry, status: "CALLED" } : entry
            ) ?? null
        );
        return;
      }

      if (queueEntry.status === "IN_PROGRESS" && isSameConsultation) {
        router.push(`/consultation/${activeCons.id}${entryQuery}`);
        return;
      }

      const created = await consultationApi.createConsultation({
        appointmentId,
        queueEntryId: entryId,
        patientId,
        doctorId: user.id,
      });
      const consultation = created.data;
      if (queueEntry.status === "CALLED") {
        await queueApi.startEntry(entryId);
      }
      router.push(`/consultation/${consultation.id}${entryQuery}`);
    } catch (err: unknown) {
      const msg = getErrorMessage(err, "Bác sĩ hiện tại đang có một ca khám chưa hoàn tất.");
      notifyAi(msg);
    } finally {
      setIsCalling(false);
    }
  };

  const safeQueuePatients = queuePatients ?? [];
  const filteredQueuePatients = safeQueuePatients.filter(
    (p) => !searchQuery.trim() || p.name.toLowerCase().includes(searchQuery.toLowerCase().trim())
  );
  const nextCallablePatient = filteredQueuePatients.find(
    (patient) => patient.status === "CHECKED_IN" || patient.status === "QUEUED"
  );

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900 tracking-tight">
            Xin chào,{" "}
            {user?.title ||
              user?.username ||
              (isLabTech ? "Kỹ thuật viên" : isStaff ? "Nhân viên Tiếp nhận & Dược" : "Bác sĩ")}
          </h1>
          <p className="mt-0.5 text-xs text-gray-400">
            {isLabTech
              ? "Tổng quan hoạt động thực hiện Cận lâm sàng hôm nay"
              : isStaff
              ? "Tổng quan hoạt động tiếp nhận & phát thuốc hôm nay"
              : "Tổng quan hoạt động khám bệnh hôm nay"}
          </p>
        </div>
        <p className="text-xs text-gray-400">
          {new Date().toLocaleDateString("vi-VN", {
            weekday: "long",
            day: "2-digit",
            month: "long",
            year: "numeric",
          })}
        </p>
      </div>

      {/* Informational Compact Notice Banner for Active Consultation */}
      {activeNotice.show && (
        <div className="bg-[#EEF2FF] border-l-4 border-[#6366F1] px-3.5 py-2.5 shadow-xs flex items-center justify-between gap-4 animate-in fade-in slide-in-from-top-1 duration-200 rounded-none text-slate-700">
          <div className="flex items-center gap-2.5">
            <svg
              className="w-4 h-4 text-[#6366F1] flex-shrink-0"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
            <p className="text-xs font-medium leading-normal">{activeNotice.message}</p>
          </div>
          {activeNotice.consultationId && (
            <button
              onClick={() => router.push(`/consultation/${activeNotice.consultationId}`)}
              className="px-2.5 py-1 bg-[#6366F1] hover:bg-[#4F46E5] text-white text-[11px] font-bold rounded-lg transition-colors whitespace-nowrap"
            >
              Vào ca khám →
            </button>
          )}
        </div>
      )}

      {/* Error */}
      {errorMessage && (
        <div className="flex items-center gap-2 px-4 py-2.5 bg-red-50 border border-red-100 rounded-xl text-xs font-semibold text-red-600">
          <svg
            className="w-4 h-4 flex-shrink-0"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
          {errorMessage}
        </div>
      )}

      <GeneralStatCards
        waitingCount={waitingCount}
        recentCompleted={recentCompleted}
        isLabTech={isLabTech}
        isStaff={isStaff}
      />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        <GeneralQueuePanel
          waitingCount={waitingCount}
          isLoading={isLoading}
          isCalling={isCalling}
          isLabTech={isLabTech}
          isStaff={isStaff}
          searchQuery={searchQuery}
          onSearchQueryChange={setSearchQuery}
          filteredQueuePatients={filteredQueuePatients}
          nextCallablePatient={nextCallablePatient}
          onCallPatient={handleCallPatient}
          onEmptyCallNext={() => setErrorMessage("Hàng đợi khám hiện tại rỗng.")}
        />

        <RecentlyCompletedPanel
          isLoading={isLoading}
          recentCompleted={recentCompleted}
          patientNames={patientNames}
        />
      </div>
    </div>
  );
}
