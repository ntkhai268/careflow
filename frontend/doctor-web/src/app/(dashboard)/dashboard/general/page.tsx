"use client";

import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { consultationApi, ConsultationResponse } from "@/lib/consultation-api";
import { queueApi } from "@/lib/queue-api";
import { patientApi } from "@/lib/patient-api";
import Link from "next/link";

interface DisplayQueuePatient {
  queueNo: string;
  appointmentId: string;
  patientId: string;
  name: string;
  age: number;
  department: string;
  status: string;
  time: string;
}

function StatusDot({ status }: { status: string }) {
  const config: Record<string, { color: string; label: string; bg: string; text: string }> = {
    WAITING:     { color: "#F59E0B", label: "Chờ khám",     bg: "#FEF3C7", text: "#92400E" },
    PENDING:     { color: "#F59E0B", label: "Chờ khám",     bg: "#FEF3C7", text: "#92400E" },
    CHECKED_IN:  { color: "#3B82F6", label: "Đã tiếp nhận", bg: "#DBEAFE", text: "#1E40AF" },
    IN_PROGRESS: { color: "#8B5CF6", label: "Đang khám",    bg: "#EDE9FE", text: "#5B21B6" },
    CONFIRMED:   { color: "#3B82F6", label: "Đã tiếp nhận", bg: "#DBEAFE", text: "#1E40AF" },
    COMPLETED:   { color: "#10B981", label: "Hoàn tất",     bg: "#D1FAE5", text: "#065F46" },
    CANCELLED:   { color: "#EF4444", label: "Đã huỷ",       bg: "#FEE2E2", text: "#991B1B" },
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

export default function DashboardGeneralPage() {
  const { user } = useAuth();
  const router = useRouter();
  const [isCalling, setIsCalling] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [queuePatients, setQueuePatients] = useState<DisplayQueuePatient[] | null>(null);
  const [recentCompleted, setRecentCompleted] = useState<ConsultationResponse[] | null>(null);
  const [patientNames, setPatientNames] = useState<Record<string, string>>({});
  const [waitingCount, setWaitingCount] = useState<number>(0);
  const [searchQuery, setSearchQuery] = useState("");
  const [activeNotice, setActiveNotice] = useState<{
    show: boolean;
    message: string;
    consultationId?: string;
  }>({ show: false, message: "" });

  useEffect(() => {
    async function loadDashboardData() {
      if (!user?.id) return;
      setIsLoading(true);
      try {
        const consRes = await consultationApi.getTodayByDoctor(user.id);
        const todayCons = consRes.data || [];
        const completedList = todayCons.filter(c => c.status === "COMPLETED");
        setRecentCompleted(completedList);

        const now = new Date();
        const todayStr = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,"0")}-${String(now.getDate()).padStart(2,"0")}`;
        try {
          const queueRes = await queueApi.getRoomActive("ROOM-01");
          const activeEntries = queueRes.data?.entries || [];
          setWaitingCount(activeEntries.length);
          setQueuePatients(activeEntries.map((e) => ({
            queueNo: e.queueNumber,
            appointmentId: e.appointmentId,
            patientId: e.patientId,
            name: `Bệnh nhân (${e.patientId.slice(0, 6)})`,
            age: 35,
            department: queueRes.data?.departmentName || "Nội tổng quát",
            status: e.queueStatus,
            time: e.scheduledStartAt ? e.scheduledStartAt.slice(11, 16) : "09:00"
          })));
        } catch {
          setWaitingCount(0);
          setQueuePatients([]);
        }

        const pNames: Record<string, string> = {};
        for (const c of completedList) {
          if (!pNames[c.patientId]) {
            try {
              const pRes = await patientApi.getPatientById(c.patientId);
              if (pRes.data?.fullName) pNames[c.patientId] = pRes.data.fullName;
            } catch { pNames[c.patientId] = `Bệnh nhân (${c.patientId.slice(0, 6)})`; }
          }
        }
        setPatientNames(pNames);
      } catch { /* outer silent */ } finally {
        setIsLoading(false);
      }
    }
    loadDashboardData();
  }, [user]);

  const handleCallPatient = async (patientId: string, appointmentId: string) => {
    if (!user) return;
    setIsCalling(true);
    setActiveNotice({ show: false, message: "" });

    try {
      // 1. Check if doctor already has an in-progress consultation
      const consRes = await consultationApi.getTodayByDoctor(user.id);
      const activeCons = (consRes.data || []).find(c => c.status === "IN_PROGRESS");
      if (activeCons) {
        // If clicking on the SAME patient/appointment -> Go straight into their consultation room!
        if (activeCons.patientId === patientId || activeCons.appointmentId === appointmentId) {
          router.push(`/consultation/${activeCons.id}`);
          return;
        }

        // If clicking a DIFFERENT patient -> Notify via AI Ferret Assistant
        if (typeof window !== "undefined") {
          window.dispatchEvent(new CustomEvent("careflow:ai-notify", {
            detail: {
              text: "Bác sĩ hiện tại đang có một ca khám chưa hoàn tất. Vui lòng hoàn thành lượt khám hiện tại trước khi gọi bệnh nhân khác.",
              consultationId: activeCons.id
            }
          }));
        }
        setIsCalling(false);
        return;
      }

      const res = await consultationApi.createConsultation({ appointmentId, patientId, doctorId: user.id });
      router.push(`/consultation/${res.data.id}`);
    } catch (err: any) {
      const msg = err?.message || "Bác sĩ hiện tại đang có một ca khám chưa hoàn tất.";
      if (typeof window !== "undefined") {
        window.dispatchEvent(new CustomEvent("careflow:ai-notify", {
          detail: { text: msg }
        }));
      }
    } finally { setIsCalling(false); }
  };

  const safeQueuePatients = queuePatients ?? [];
  const filteredQueuePatients = safeQueuePatients.filter(p => 
    !searchQuery.trim() || p.name.toLowerCase().includes(searchQuery.toLowerCase().trim())
  );

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900 tracking-tight">
            Xin chào, {user?.title || user?.username || "Bác sĩ"}
          </h1>
          <p className="mt-0.5 text-xs text-gray-400">Tổng quan hoạt động khám bệnh hôm nay</p>
        </div>
        <p className="text-xs text-gray-400">
          {new Date().toLocaleDateString("vi-VN", { weekday: "long", day: "2-digit", month: "long", year: "numeric" })}
        </p>
      </div>

      {/* Informational Compact Notice Banner for Active Consultation */}
      {activeNotice.show && (
        <div className="bg-[#EEF2FF] border-l-4 border-[#6366F1] px-3.5 py-2.5 shadow-xs flex items-center justify-between gap-4 animate-in fade-in slide-in-from-top-1 duration-200 rounded-none text-slate-700">
          <div className="flex items-center gap-2.5">
            <svg className="w-4 h-4 text-[#6366F1] flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <p className="text-xs font-medium leading-normal">
              {activeNotice.message}
            </p>
          </div>
          <div className="flex items-center gap-3 flex-shrink-0">
            {activeNotice.consultationId && (
              <button
                onClick={() => router.push(`/consultation/${activeNotice.consultationId}`)}
                className="text-[#6366F1] hover:text-indigo-700 font-semibold text-xs transition-colors underline flex items-center gap-1"
              >
                <span>Đến ca khám hiện tại</span>
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                </svg>
              </button>
            )}
            <button
              onClick={() => setActiveNotice({ show: false, message: "" })}
              className="text-slate-400 hover:text-slate-600 text-xs px-1 py-0.5 transition-colors"
              title="Đóng thông báo"
            >
              ✕
            </button>
          </div>
        </div>
      )}

      {/* Error */}
      {errorMessage && (
        <div className="flex items-center gap-2 px-4 py-2.5 bg-red-50 border border-red-100 rounded-xl text-xs font-semibold text-red-600">
          <svg className="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          {errorMessage}
        </div>
      )}

      {/* Stat Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
        {/* Waiting */}
        <div className="bg-white rounded-xl border border-gray-200 px-4 py-3 shadow-sm flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-indigo-50">
            <svg className="w-5 h-5 text-indigo-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <div>
            <p className="text-[10px] text-gray-400 font-medium">Đang chờ</p>
            <p className="text-lg font-bold text-gray-900">{waitingCount}</p>
          </div>
        </div>

        {/* Completed */}
        <div className="bg-white rounded-xl border border-gray-200 px-4 py-3 shadow-sm flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-50">
            <svg className="w-5 h-5 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <div>
            <p className="text-[10px] text-gray-400 font-medium">Hoàn tất</p>
            <p className="text-lg font-bold text-gray-900">{(recentCompleted ?? []).length}</p>
          </div>
        </div>

        {/* Queue shortcut */}
        <div className="rounded-xl px-4 py-3 shadow-sm col-span-2 sm:col-span-1 hover:shadow-md transition-shadow"
          style={{ background: "linear-gradient(135deg, #6366F1 0%, #3B82F6 100%)" }}>
          <Link href="/dashboard/queue" className="flex items-center gap-3 w-full">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/20">
              <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
            </div>
            <div>
              <p className="text-[10px] text-white/70 font-medium">Phòng khám</p>
              <p className="text-xs font-bold text-white">Vào hàng đợi →</p>
            </div>
          </Link>
        </div>
      </div>

      {/* Two column layout */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">

        {/* Left: Queue Table */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 shadow-sm flex flex-col h-[480px]">
          <div className="flex items-center justify-between px-5 py-3 border-b border-gray-100 flex-wrap gap-2 flex-shrink-0">
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-bold text-gray-800">Hàng đợi khám cần xử lý</h2>
              {waitingCount > 0 && (
                <span className="inline-flex items-center justify-center w-5 h-5 rounded-full bg-red-100 text-red-600 text-[10px] font-bold">
                  {waitingCount}
                </span>
              )}
            </div>
            
            {/* Search Input Filter for Queue */}
            <div className="flex items-center gap-2">
              <div className="relative">
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Tìm bệnh nhân theo tên..."
                  className="w-44 border border-gray-200 bg-gray-50 px-2.5 py-1 text-xs text-gray-800 rounded-lg focus:bg-white focus:border-indigo-400 focus:outline-none transition-all placeholder:text-gray-400"
                />
                {searchQuery && (
                  <button
                    onClick={() => setSearchQuery("")}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 text-xs font-bold"
                  >
                    ✕
                  </button>
                )}
              </div>

              <Link href="/dashboard/queue"
                className="text-[11px] font-semibold text-indigo-500 hover:text-indigo-700 transition-colors flex items-center gap-0.5 whitespace-nowrap">
                Xem tất cả
                <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </Link>
            </div>
          </div>

          {/* Table Container with Mouse Scroll Wheel */}
          <div className="overflow-y-auto flex-1 custom-scrollbar">
            <table className="w-full text-left text-xs">
              <thead className="sticky top-0 z-10 bg-indigo-50 border-b border-gray-100 shadow-xs">
                <tr>
                  <th className="px-5 py-3 font-semibold text-gray-600 w-14">STT</th>
                  <th className="px-5 py-3 font-semibold text-gray-600">Bệnh nhân</th>
                  <th className="px-5 py-3 font-semibold text-gray-600 w-20">Tuổi</th>
                  <th className="px-5 py-3 font-semibold text-gray-600 w-24">Giờ vào</th>
                  <th className="px-5 py-3 font-semibold text-gray-600 w-32">Trạng thái</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {isLoading ? (
                  Array.from({ length: 4 }).map((_, i) => (
                    <tr key={i} className="border-b border-gray-50">
                      {["w-10", "w-36", "w-10", "w-16", "w-20"].map((w, j) => (
                        <td key={j} className="px-5 py-3.5">
                          <div className={`h-3 ${w} rounded bg-gray-100 animate-pulse`} />
                        </td>
                      ))}
                    </tr>
                  ))
                ) : filteredQueuePatients.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-5 py-12 text-center text-gray-400 text-xs italic">
                      {searchQuery ? `Không tìm thấy bệnh nhân nào khớp với "${searchQuery}"` : "Hiện chưa có bệnh nhân nào trong hàng đợi khám"}
                    </td>
                  </tr>
                ) : (
                  filteredQueuePatients.map((patient) => (
                    <tr key={patient.queueNo}
                      className="hover:bg-indigo-50 cursor-pointer transition-colors"
                      onClick={() => handleCallPatient(patient.patientId, patient.appointmentId)}>
                      <td className="px-5 py-3 font-mono font-bold text-gray-700">{patient.queueNo}</td>
                      <td className="px-5 py-3">
                        <div className="flex items-center gap-2.5">
                          <div className="flex h-7 w-7 items-center justify-center rounded-full text-[10px] font-bold text-white flex-shrink-0"
                            style={{ background: "linear-gradient(135deg, #6366F1, #3B82F6)" }}>
                            {patient.name.charAt(0)}
                          </div>
                          <span className="font-semibold text-gray-800">{patient.name}</span>
                        </div>
                      </td>
                      <td className="px-5 py-3 text-gray-500">{patient.age}</td>
                      <td className="px-5 py-3 text-gray-500 font-mono">{patient.time}</td>
                      <td className="px-5 py-3"><StatusDot status={patient.status} /></td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {/* Footer inside Left Card: Call Next Patient Button */}
          <div className="px-5 py-3 border-t border-gray-100 bg-gray-50/80 rounded-b-xl flex justify-end flex-shrink-0">
            <button
              onClick={() => {
                if (filteredQueuePatients.length > 0) handleCallPatient(filteredQueuePatients[0].patientId, filteredQueuePatients[0].appointmentId);
                else setErrorMessage("Hàng đợi khám hiện tại rỗng.");
              }}
              disabled={isCalling || isLoading || filteredQueuePatients.length === 0}
              className="flex items-center gap-2 text-white px-4 py-2 rounded-lg text-xs font-bold transition-all disabled:opacity-50 shadow-sm hover:shadow-md cursor-pointer"
              style={{ background: "linear-gradient(135deg, #6366F1 0%, #3B82F6 100%)" }}
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.828 14.828a4 4 0 01-5.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              {isCalling ? "Đang gọi..." : "Gọi bệnh nhân tiếp theo"}
            </button>
          </div>
        </div>

        {/* Right: Recently Completed (Synced height with left panel) */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm flex flex-col h-[480px]">
          <div className="px-5 py-3.5 border-b border-gray-100 flex-shrink-0">
            <h2 className="text-sm font-bold text-gray-800">Vừa hoàn tất</h2>
            <p className="text-[10px] text-gray-400 mt-0.5">Ca khám hoàn thành hôm nay</p>
          </div>
          <div className="p-4 space-y-2 flex-1 overflow-y-auto custom-scrollbar">
            {isLoading ? (
              Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="p-3 rounded-xl border border-gray-100 bg-gray-50 space-y-2">
                  <div className="h-3 w-28 rounded bg-gray-200 animate-pulse" />
                  <div className="h-2.5 w-40 rounded bg-gray-100 animate-pulse" />
                </div>
              ))
            ) : (recentCompleted ?? []).length === 0 ? (
              <div className="text-center py-12">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gray-100 mx-auto mb-2">
                  <svg className="w-5 h-5 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                </div>
                <p className="text-[11px] text-gray-400 italic">Chưa có ca khám nào hoàn tất hôm nay</p>
              </div>
            ) : (
              (recentCompleted ?? []).map((c) => (
                <div key={c.id}
                  className="p-3 rounded-xl border border-gray-100 bg-gray-50 hover:bg-indigo-50 hover:border-indigo-100 transition-colors">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0 flex-1">
                      <p className="font-semibold text-gray-800 text-xs truncate">
                        {patientNames[c.patientId] || "Bệnh nhân"}
                      </p>
                      <p className="text-[10px] text-gray-400 mt-0.5 truncate">
                        {c.diagnosis || "Chẩn đoán chưa cập nhật"}
                      </p>
                    </div>
                    <span className="flex-shrink-0 inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 text-[10px] font-semibold">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 inline-block" />
                      Xong
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
