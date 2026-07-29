"use client";

import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { appointmentApi, AppointmentResponse } from "@/lib/appointment-api";
import { consultationApi } from "@/lib/consultation-api";

type TabFilter = "all" | "waiting" | "completed";

function StatusDot({ status }: { status: string }) {
  const config: Record<string, { color: string; label: string; bg: string; text: string }> = {
    WAITING:     { color: "#F59E0B", label: "Chờ khám",     bg: "#FEF3C7", text: "#92400E" },
    PENDING:     { color: "#F59E0B", label: "Chờ khám",     bg: "#FEF3C7", text: "#92400E" },
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

// Skeleton row for loading state
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
  const [appointments, setAppointments] = useState<AppointmentResponse[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [isCalling, setIsCalling] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [activeTab, setActiveTab] = useState<TabFilter>("all");
  const [activeNotice, setActiveNotice] = useState<{
    show: boolean;
    message: string;
    consultationId?: string;
  }>({ show: false, message: "" });

  useEffect(() => {
    async function fetchQueue() {
      setIsLoading(true);
      setFetchError(null);
      const now = new Date();
      const todayStr = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,"0")}-${String(now.getDate()).padStart(2,"0")}`;
      try {
        const res = await appointmentApi.getAppointmentsByDepartment("NOI_TONG_QUAT", todayStr);
        setAppointments(res.data ?? []);
      } catch {
        setFetchError("Không thể tải danh sách hàng đợi. Vui lòng thử lại.");
        setAppointments([]);
      } finally {
        setIsLoading(false);
      }
    }
    fetchQueue();
  }, []);

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

  const safeAppointments = appointments ?? [];

  const filtered = safeAppointments.filter(a => {
    if (activeTab === "waiting") return ["WAITING","PENDING","CONFIRMED"].includes(a.status);
    if (activeTab === "completed") return a.status === "COMPLETED";
    return true;
  });

  const waitingCount = safeAppointments.filter(a => ["WAITING","PENDING","CONFIRMED"].includes(a.status)).length;

  return (
    <div className="space-y-5">
      {/* Header */}
      <div>
        <h1 className="text-xl font-bold text-gray-900 tracking-tight">Hàng đợi khám bệnh</h1>
        <p className="mt-0.5 text-xs text-gray-400">
          Danh sách bệnh nhân đăng ký khám trong ngày theo thứ tự tiếp nhận
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

      {errorMessage && <p className="text-xs font-semibold text-red-500">{errorMessage}</p>}
      {fetchError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-xs font-medium px-4 py-3 rounded-lg">
          {fetchError}
        </div>
      )}

      {/* Queue Card */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm">
        {/* Header with Tabs */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-gray-100 flex-wrap gap-3">
          {/* Segmented pill tab bar */}
          <div className="flex bg-indigo-50 rounded-xl p-1 gap-0.5">
            {[
              { key: "all" as TabFilter,       label: "Tất cả",   count: safeAppointments.length },
              { key: "waiting" as TabFilter,   label: "Chờ khám", count: waitingCount },
              { key: "completed" as TabFilter, label: "Hoàn tất", count: safeAppointments.filter(a => a.status === "COMPLETED").length },
            ].map(tab => (
              <button key={tab.key} onClick={() => setActiveTab(tab.key)}
                className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-[11px] font-semibold transition-all duration-150"
                style={activeTab === tab.key
                  ? { backgroundColor: "#fff", color: "#6366F1", boxShadow: "0 1px 3px rgba(0,0,0,0.08)" }
                  : { color: "#64748B" }
                }
              >
                {tab.label}
                {tab.count > 0 && (
                  <span className="inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 rounded-full text-[10px] font-bold"
                    style={activeTab === tab.key
                      ? { backgroundColor: "#EEF2FF", color: "#6366F1" }
                      : { backgroundColor: "#E2E8F0", color: "#64748B" }
                    }>
                    {tab.count}
                  </span>
                )}
              </button>
            ))}
          </div>

          {/* Legend */}
          <div className="flex items-center gap-3 text-[10px] text-gray-400">
            <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-emerald-400 inline-block" /> Hoàn tất</span>
            <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-violet-400 inline-block" /> Đang khám</span>
            <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-red-400 inline-block" /> Khẩn cấp</span>
          </div>
        </div>

        {/* Table */}
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-gray-100 bg-indigo-50">
                <th className="px-5 py-3 font-semibold text-gray-600 w-14">STT</th>
                <th className="px-5 py-3 font-semibold text-gray-600">Bệnh nhân</th>
                <th className="px-5 py-3 font-semibold text-gray-600">Chuyên khoa</th>
                <th className="px-5 py-3 font-semibold text-gray-600">Lý do khám</th>
                <th className="px-5 py-3 font-semibold text-gray-600 w-24">Giờ hẹn</th>
                <th className="px-5 py-3 font-semibold text-gray-600 w-28">Trạng thái</th>
                <th className="px-5 py-3 font-semibold text-gray-600 w-24 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {isLoading ? (
                Array.from({ length: 4 }).map((_, i) => <SkeletonRow key={i} />)
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-5 py-12 text-center">
                    <div className="flex flex-col items-center gap-2">
                      <svg className="w-8 h-8 text-gray-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
                      </svg>
                      <p className="text-xs text-gray-400 italic">Hiện chưa có bệnh nhân nào trong hàng đợi khám hôm nay</p>
                    </div>
                  </td>
                </tr>
              ) : (
                filtered.map((a, idx) => (
                  <tr key={a.id} className="hover:bg-indigo-50 transition-colors group">
                    <td className="px-5 py-3 font-mono font-bold text-gray-600">
                      {a.queueNumber || String(idx + 1).padStart(3, "0")}
                    </td>
                    <td className="px-5 py-3">
                      <div className="flex items-center gap-2.5">
                        <div className="flex h-7 w-7 items-center justify-center rounded-full text-[10px] font-bold text-white group-hover:opacity-90"
                          style={{ background: "linear-gradient(135deg, #6366F1, #3B82F6)" }}>
                          {(a.patientName || "B").charAt(0)}
                        </div>
                        <span className="font-semibold text-gray-800">{a.patientName}</span>
                      </div>
                    </td>
                    <td className="px-5 py-3 text-gray-500">{a.departmentDisplayName || "Nội tổng quát"}</td>
                    <td className="px-5 py-3 text-gray-500">{a.reason || "Khám thông thường"}</td>
                    <td className="px-5 py-3 font-mono text-gray-500">{a.timeSlot}</td>
                    <td className="px-5 py-3"><StatusDot status={a.status} /></td>
                    <td className="px-5 py-3 text-right">
                      <button
                        onClick={() => handleCallPatient(a.patientId, a.id)}
                        disabled={isCalling}
                        className="text-white px-3 py-1.5 rounded-lg text-[11px] font-semibold transition-all disabled:opacity-50 shadow-sm hover:shadow-md"
                        style={{ background: "linear-gradient(135deg, #6366F1, #3B82F6)" }}
                      >
                        Vào khám
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
