"use client";

import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { consultationApi, ConsultationResponse } from "@/lib/consultation-api";
import { queueApi } from "@/lib/queue-api";
import { patientApi } from "@/lib/patient-api";
import { appointmentApi } from "@/lib/appointment-api";
import { getErrorMessage } from "@/lib/error-utils";
import Link from "next/link";
import { patientAge, visitTime } from "@/lib/patient-display";
import { QueueStatusDot } from "@/components/queue/QueueBadges";

interface DisplayQueuePatient {
  entryId: string;
  queueNo: string;
  appointmentId: string | null;
  patientId: string;
  name: string;
  age: number | null;
  department: string;
  status: string;
  time: string;
}

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
  const isAdmin = user?.role?.toUpperCase().includes("ADMIN");
  const isLabTech = user?.role?.toUpperCase().includes("LAB");
  const isStaff = user?.role?.toUpperCase().includes("STAFF");

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
                name: e.patientName || `Bệnh nhân (${e.patientId.slice(0, 6)})`,
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
                name: e.patientName || `Bệnh nhân (${e.patientId.slice(0, 6)})`,
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
                  pNames[e.patientId] = e.patientName || `Bệnh nhân (${e.patientId.slice(0, 8)})`;
                }
              }
            }

            setQueuePatients(
              activeEntries.map((e) => ({
                entryId: e.entryId,
                queueNo: e.queueNumber,
                appointmentId: e.appointmentId,
                patientId: e.patientId,
                name: pNames[e.patientId] || e.patientName || `Bệnh nhân (${e.patientId.slice(0, 8)})`,
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
    loadDashboardData();
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
        if (typeof window !== "undefined") {
          window.dispatchEvent(
            new CustomEvent("careflow:ai-notify", {
              detail: {
                text: "Bác sĩ hiện tại đang có một ca khám chưa hoàn tất. Vui lòng hoàn thành lượt khám hiện tại trước khi gọi bệnh nhân khác.",
                consultationId: activeCons.id,
              },
            })
          );
        }
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
      if (typeof window !== "undefined") {
        window.dispatchEvent(
          new CustomEvent("careflow:ai-notify", {
            detail: { text: msg },
          })
        );
      }
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
  const actionLabel = (status: string) => {
    if (status === "CALLED") return "Bắt đầu khám";
    if (status === "IN_PROGRESS") return "Vào khám";
    return "Gọi số";
  };

  const displayDoctorTitle =
    user?.title?.split(" - ")[0] ||
    user?.fullName ||
    user?.username ||
    (isLabTech ? "Kỹ thuật viên" : isStaff ? "Nhân viên Tiếp nhận & Dược" : "Bác sĩ");
  const displayDept = user?.department || user?.title?.split(" - ")[1] || "";

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 bg-white border border-[#D5E0E6] rounded-xl px-5 py-4 shadow-[0_2px_8px_rgba(23,36,45,0.03)]">
        <div>
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-xl font-bold text-[#17242D] tracking-tight">
              Xin chào, {displayDoctorTitle}
            </h1>
            {displayDept && (
              <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-[#DDF3FC] text-[#00577E] border border-[#8BD2EE]/50">
                {displayDept}
              </span>
            )}
          </div>
          <p className="mt-1 text-xs text-[#4D626E]">
            {isLabTech
              ? "Tổng quan hoạt động thực hiện Cận lâm sàng hôm nay"
              : isStaff
                ? "Tổng quan hoạt động tiếp nhận & phát thuốc hôm nay"
                : "Tổng quan hoạt động khám chữa bệnh lâm sàng trong ca trực hôm nay"}
          </p>
        </div>
        <div className="flex items-center gap-2 text-xs font-medium text-[#667985] bg-[#F5F8FA] px-3 py-1.5 rounded-lg border border-[#D5E0E6]">
          <span>
            {new Date().toLocaleDateString("vi-VN", {
              weekday: "long",
              day: "2-digit",
              month: "long",
              year: "numeric",
            })}
          </span>
        </div>
      </div>

      {/* Informational Compact Notice Banner for Active Consultation */}
      {activeNotice.show && (
        <div className="bg-[#DDF3FC] border-l-4 border-[#0277A8] px-4 py-3 shadow-xs flex items-center justify-between gap-4 animate-in fade-in slide-in-from-top-1 duration-200 rounded-xl text-[#17242D]">
          <div className="flex items-center gap-2.5">
            <svg
              className="w-4 h-4 text-[#0277A8] flex-shrink-0"
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
              className="px-3 py-1.5 bg-[#0277A8] hover:bg-[#00577E] text-white text-[11px] font-bold rounded-lg transition-colors whitespace-nowrap cursor-pointer"
            >
              Vào ca khám →
            </button>
          )}
        </div>
      )}

      {/* Error */}
      {errorMessage && (
        <div className="flex items-center gap-2 px-4 py-3 bg-[#FFDAD6] border border-[#BA1A1A]/25 rounded-xl text-xs font-semibold text-[#BA1A1A]">
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

      {/* Stat Cards (No decorative icons inside stat cards per design.md, unified clean clinical surface) */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {/* Waiting */}
        <div className="bg-white rounded-xl border border-[#D5E0E6] px-5 py-4 shadow-[0_2px_8px_rgba(23,36,45,0.03)] flex items-center justify-between">
          <div>
            <p className="text-[11px] text-[#4D626E] font-semibold uppercase tracking-wider">
              Bệnh nhân đang chờ
            </p>
            <p className="text-2xl font-extrabold text-[#17242D] font-mono mt-1">
              {waitingCount ?? "—"}
            </p>
          </div>
          <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-[#DDF3FC] text-[#00577E]">
            Hàng đợi thực tế
          </span>
        </div>

        {/* Completed */}
        <div className="bg-white rounded-xl border border-[#D5E0E6] px-5 py-4 shadow-[0_2px_8px_rgba(23,36,45,0.03)] flex items-center justify-between">
          <div>
            <p className="text-[11px] text-[#4D626E] font-semibold uppercase tracking-wider">
              Đã hoàn tất hôm nay
            </p>
            <p className="text-2xl font-extrabold text-[#1B7F4B] font-mono mt-1">
              {isStaff || isLabTech || recentCompleted === null ? "—" : recentCompleted.length}
            </p>
          </div>
          <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-[#DDF4E7] text-[#1B7F4B]">
            Ca hoàn thành
          </span>
        </div>

        {/* Queue shortcut card depending on Role */}
        <Link
          href={isLabTech ? "/lab/queue" : isStaff ? "/staff/pharmacy" : "/dashboard/queue"}
          className="rounded-xl px-5 py-4 shadow-[0_4px_12px_rgba(2,119,168,0.16)] hover:shadow-[0_6px_16px_rgba(2,119,168,0.24)] transition-all flex items-center justify-between group"
          style={{ background: "linear-gradient(135deg, #0277A8 0%, #00577E 100%)" }}
        >
          <div>
            <p className="text-[11px] text-[#8BD2EE] font-semibold uppercase tracking-wider">
              {isLabTech
                ? "Điều phối Cận lâm sàng"
                : isStaff
                  ? "Điều phối Quầy Dược"
                  : "Điều phối Khám 3 Làn"}
            </p>
            <p className="text-sm font-bold text-white mt-1 group-hover:translate-x-0.5 transition-transform">
              {isLabTech
                ? "Đến hàng đợi CLS →"
                : isStaff
                  ? "Hàng đợi Phát thuốc →"
                  : "Mở bảng hàng đợi 3 làn →"}
            </p>
          </div>
          <div className="h-8 w-8 rounded-lg bg-white/15 flex items-center justify-center text-white font-bold">
            →
          </div>
        </Link>
      </div>

      {/* Two column layout */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Left: Queue Table */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-[#D5E0E6] shadow-[0_2px_8px_rgba(23,36,45,0.03)] flex flex-col h-[480px]">
          <div className="flex items-center justify-between px-5 py-3.5 border-b border-[#D5E0E6] flex-wrap gap-2 flex-shrink-0">
            <div className="flex items-center gap-2.5">
              <h2 className="text-sm font-bold text-[#17242D]">
                {isLabTech
                  ? "Hàng đợi Cận lâm sàng"
                  : isStaff
                    ? "Hàng đợi Phát thuốc"
                    : "Hàng đợi khám cần xử lý"}
              </h2>
              {(waitingCount ?? 0) > 0 && (
                <span className="inline-flex items-center justify-center min-w-5 h-5 px-1.5 rounded-full bg-[#DDF3FC] text-[#00577E] text-[10px] font-bold">
                  {waitingCount}
                </span>
              )}
            </div>

            {/* Search Input Filter for Queue */}
            <div className="flex items-center gap-3">
              <div className="relative">
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Tìm bệnh nhân theo tên..."
                  className="w-48 border border-[#D5E0E6] bg-[#F5F8FA] px-3 py-1.5 text-xs text-[#17242D] rounded-lg focus:bg-white focus:border-[#0277A8] focus:outline-none transition-all placeholder:text-[#667985]"
                />
                {searchQuery && (
                  <button
                    onClick={() => setSearchQuery("")}
                    className="absolute right-2.5 top-1/2 -translate-y-1/2 text-[#667985] hover:text-[#17242D] text-xs font-bold"
                  >
                    ✕
                  </button>
                )}
              </div>

              <Link
                href={isLabTech ? "/lab/queue" : isStaff ? "/staff/pharmacy" : "/dashboard/queue"}
                className="text-[11px] font-bold text-[#0277A8] hover:text-[#00577E] transition-colors flex items-center gap-0.5 whitespace-nowrap"
              >
                Xem tất cả
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M9 5l7 7-7 7"
                  />
                </svg>
              </Link>
            </div>
          </div>

          {/* Table Container */}
          <div className="overflow-x-auto overflow-y-auto flex-1 custom-scrollbar">
            <table className="w-full min-w-[600px] text-left text-xs">
              <thead className="sticky top-0 z-10 bg-[#EDF2F5] border-b border-[#D5E0E6]">
                <tr>
                  <th className="px-5 py-3 font-bold text-[#4D626E] uppercase tracking-wider text-[10px] w-16">
                    STT
                  </th>
                  <th className="px-5 py-3 font-bold text-[#4D626E] uppercase tracking-wider text-[10px]">
                    Bệnh nhân
                  </th>
                  <th className="px-5 py-3 font-bold text-[#4D626E] uppercase tracking-wider text-[10px] w-20">
                    Tuổi
                  </th>
                  <th className="px-5 py-3 font-bold text-[#4D626E] uppercase tracking-wider text-[10px] w-24">
                    Giờ vào
                  </th>
                  <th className="px-5 py-3 font-bold text-[#4D626E] uppercase tracking-wider text-[10px] w-32">
                    Trạng thái
                  </th>
                  <th className="px-5 py-3 font-bold text-[#4D626E] uppercase tracking-wider text-[10px] w-32 text-right">
                    Thao tác
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#EDF2F5]">
                {isLoading ? (
                  Array.from({ length: 4 }).map((_, i) => (
                    <tr key={i} className="border-b border-[#EDF2F5]">
                      {["w-10", "w-36", "w-10", "w-16", "w-20", "w-24"].map((w, j) => (
                        <td key={j} className="px-5 py-3.5">
                          <div className={`h-3 ${w} rounded bg-[#EDF2F5] animate-pulse`} />
                        </td>
                      ))}
                    </tr>
                  ))
                ) : filteredQueuePatients.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-5 py-12 text-center text-[#667985] text-xs">
                      {searchQuery
                        ? `Không tìm thấy bệnh nhân nào khớp với "${searchQuery}"`
                        : "Hiện chưa có bệnh nhân nào trong hàng đợi khám"}
                    </td>
                  </tr>
                ) : (
                  filteredQueuePatients.map((patient) => (
                    <tr
                      key={patient.queueNo}
                      className="hover:bg-[#DDF3FC]/35 cursor-pointer transition-colors"
                      onClick={() => handleCallPatient(patient.patientId, patient.appointmentId)}
                    >
                      <td className="px-5 py-3 font-mono font-bold text-[#00577E]">
                        {patient.queueNo}
                      </td>
                      <td className="px-5 py-3">
                        <div className="flex items-center gap-2.5">
                          <div
                            className="flex h-7 w-7 items-center justify-center rounded-full text-[10px] font-bold text-white flex-shrink-0"
                            style={{ background: "linear-gradient(135deg, #0277A8, #087F6A)" }}
                          >
                            {patient.name.charAt(0)}
                          </div>
                          <span className="font-semibold text-[#17242D]">{patient.name}</span>
                        </div>
                      </td>
                      <td className="px-5 py-3 text-[#4D626E] font-mono">{patient.age ?? "—"}</td>
                      <td className="px-5 py-3 text-[#4D626E] font-mono">{patient.time}</td>
                      <td className="px-5 py-3">
                        <QueueStatusDot status={patient.status} />
                      </td>
                      <td className="px-5 py-3 text-right">
                        <button
                          type="button"
                          onClick={(event) => {
                            event.stopPropagation();
                            handleCallPatient(patient.patientId, patient.appointmentId);
                          }}
                          disabled={isCalling}
                          className="w-28 inline-flex items-center justify-center whitespace-nowrap rounded-lg border border-[#D5E0E6] bg-white px-2.5 py-1.5 text-[11px] font-semibold text-[#17242D] hover:border-[#0277A8] hover:bg-[#DDF3FC]/60 hover:text-[#00577E] disabled:opacity-50 transition-colors cursor-pointer"
                        >
                          {actionLabel(patient.status)}
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {/* Footer inside Left Card: Call Next Patient Button */}
          <div className="px-5 py-3 border-t border-[#D5E0E6] bg-[#F5F8FA] rounded-b-xl flex justify-end flex-shrink-0">
            <button
              onClick={() => {
                if (nextCallablePatient)
                  handleCallPatient(
                    nextCallablePatient.patientId,
                    nextCallablePatient.appointmentId
                  );
                else setErrorMessage("Hàng đợi khám hiện tại rỗng.");
              }}
              disabled={isCalling || isLoading || !nextCallablePatient}
              className="flex items-center gap-2 text-white px-4 py-2 rounded-xl text-xs font-bold transition-all disabled:opacity-50 shadow-sm hover:bg-[#00577E] bg-[#0277A8] cursor-pointer"
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M11 5.882V19.24a1.76 1.76 0 01-3.417.592l-2.147-6.15M18 13a3 3 0 100-6M5.436 13.683A4.001 4.001 0 017 6h1.832c4.1 0 7.625-1.234 9.168-3v14c-1.543-1.766-5.067-3-9.168-3H7a3.988 3.988 0 01-1.564-.317z"
                />
              </svg>
              {isCalling ? "Đang gọi..." : "Gọi bệnh nhân tiếp theo"}
            </button>
          </div>
        </div>

        {/* Right: Recently Completed (Synced height with left panel) */}
        <div className="bg-white rounded-xl border border-[#D5E0E6] shadow-[0_2px_8px_rgba(23,36,45,0.03)] flex flex-col h-[480px]">
          <div className="px-5 py-3.5 border-b border-[#D5E0E6] flex-shrink-0">
            <h2 className="text-sm font-bold text-[#17242D]">Vừa hoàn tất</h2>
            <p className="text-[11px] text-[#667985] mt-0.5">Ca khám hoàn thành trong ngày hôm nay</p>
          </div>
          <div className="p-4 space-y-2.5 flex-1 overflow-y-auto custom-scrollbar">
            {isLoading ? (
              Array.from({ length: 4 }).map((_, i) => (
                <div
                  key={i}
                  className="p-3 rounded-xl border border-[#EDF2F5] bg-[#F5F8FA] space-y-2"
                >
                  <div className="h-3 w-28 rounded bg-[#D5E0E6] animate-pulse" />
                  <div className="h-2.5 w-40 rounded bg-[#EDF2F5] animate-pulse" />
                </div>
              ))
            ) : (recentCompleted ?? []).length === 0 ? (
              <div className="text-center py-12">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#EDF2F5] mx-auto mb-2.5">
                  <svg
                    className="w-5 h-5 text-[#667985]"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={1.6}
                      d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
                    />
                  </svg>
                </div>
                <p className="text-xs text-[#667985]">Chưa có ca khám nào hoàn tất hôm nay</p>
              </div>
            ) : (
              (recentCompleted ?? []).map((c) => (
                <div
                  key={c.id}
                  className="p-3.5 rounded-xl border border-[#D5E0E6] bg-[#F5F8FA] hover:bg-[#DDF3FC]/35 hover:border-[#8BD2EE] transition-colors"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0 flex-1">
                      <p className="font-bold text-[#17242D] text-xs truncate">
                        {patientNames[c.patientId] || "Bệnh nhân"}
                      </p>
                      <p className="text-[11px] text-[#4D626E] mt-0.5 truncate">
                        {c.diagnosis || "Chẩn đoán chưa cập nhật"}
                      </p>
                    </div>
                    <span className="flex-shrink-0 inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-[#DDF4E7] text-[#1B7F4B] text-[10px] font-bold">
                      <span className="w-1.5 h-1.5 rounded-full bg-[#1B7F4B] inline-block" />
                      Hoàn tất
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
