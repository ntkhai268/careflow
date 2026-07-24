"use client";

import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { consultationApi } from "@/lib/consultation-api";
import Link from "next/link";

// Mock data for dashboard statistics
const MOCK_STATS = {
  totalToday: 24,
  waiting: 8,
  inProgress: 1,
  completed: 15,
  avgWaitTime: 23,
  avgConsultTime: 12,
};

// Detailed mock data with Patient UUIDs to connect to the real API
const MOCK_QUEUE_PATIENTS = [
  { queueNo: "005", patientId: "f0000001-0000-0000-0000-000000000004", name: "Phạm Đức Anh", age: 7, department: "Nội tổng quát", status: "IN_PROGRESS", time: "10:20" },
  { queueNo: "006", patientId: "f0000001-0000-0000-0000-000000000005", name: "Võ Thị Lan", age: 55, department: "Nội tổng quát", status: "WAITING", time: "10:45" },
  { queueNo: "007", patientId: "f0000001-0000-0000-0000-000000000003", name: "Lê Hoàng Nam", age: 34, department: "Nội tổng quát", status: "WAITING", time: "11:00" },
  { queueNo: "008", patientId: "f0000001-0000-0000-0000-000000000001", name: "Nguyễn Thị Mai", age: 45, department: "Nội tổng quát", status: "WAITING", time: "11:15" },
  { queueNo: "009", patientId: "f0000001-0000-0000-0000-000000000002", name: "Trần Văn Hùng", age: 62, department: "Nội tổng quát", status: "WAITING", time: "11:30" },
];

const MOCK_RECENT_COMPLETED = [
  { id: 1, name: "Nguyễn Thị Mai", age: 45, department: "Nội tổng quát", status: "COMPLETED", time: "08:30" },
  { id: 2, name: "Trần Văn Hùng", age: 62, department: "Nội tổng quát", status: "COMPLETED", time: "09:15" },
  { id: 3, name: "Lê Thị Hoa", age: 28, department: "Nội tổng quát", status: "COMPLETED", time: "09:45" },
];

function StatCard({
  label,
  value,
  suffix,
}: {
  label: string;
  value: number;
  suffix?: string;
}) {
  return (
    <div className="border border-card-border bg-card-bg p-4 shadow-[0_1px_3px_rgba(110,37,130,0.06)]">
      <p className="text-xs font-semibold text-[#6A5C70] tracking-wide uppercase">{label}</p>
      <p className="mt-1 text-2xl font-bold text-[#2B1D30]">
        {value}
        {suffix && <span className="ml-1 text-sm font-medium text-[#6A5C70]">{suffix}</span>}
      </p>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    WAITING: "bg-warning-light text-warning border border-warning/20",
    IN_PROGRESS: "bg-[#E8F1FD] text-[#2F80ED] border border-[#2F80ED]/20",
    COMPLETED: "bg-success-light text-success border border-success/20",
  };
  const labels: Record<string, string> = {
    WAITING: "Chờ khám",
    IN_PROGRESS: "Đang khám",
    COMPLETED: "Hoàn tất",
  };

  return (
    <span className={`inline-flex items-center px-2 py-0.5 text-xs font-semibold ${styles[status] || ""}`}>
      {labels[status] || status}
    </span>
  );
}

export default function DashboardPage() {
  const { user } = useAuth();
  const router = useRouter();
  const [isCalling, setIsCalling] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [todayCompletedCount, setTodayCompletedCount] = useState<number>(MOCK_STATS.completed);
  const [todayTotalCount, setTodayTotalCount] = useState<number>(MOCK_STATS.totalToday);

  useEffect(() => {
    async function loadTodayStats() {
      if (!user?.id) return;
      try {
        const res = await consultationApi.getTodayByDoctor(user.id);
        const todayCons = res.data || [];
        if (todayCons.length > 0) {
          const completed = todayCons.filter(c => c.status === "COMPLETED").length;
          setTodayCompletedCount(completed);
          setTodayTotalCount(todayCons.length + MOCK_STATS.waiting);
        }
      } catch (err) {
        console.log("Could not load today stats from API, using fallback", err);
      }
    }
    loadTodayStats();
  }, [user]);

  const handleCallPatient = async (patientId: string) => {
    if (!user) return;
    setIsCalling(true);
    setErrorMessage("");
    try {
      // Call actual consultation-service to create a new session
      const res = await consultationApi.createConsultation({
        patientId,
        doctorId: user.id
      });
      router.push(`/consultation/${res.data.id}`);
    } catch (err) {
      console.error(err);
      setErrorMessage("Không thể kết nối API Consultation-Service. Vui lòng đảm bảo các microservices đã được khởi chạy.");
    } finally {
      setIsCalling(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-[#2B1D30] tracking-tight">
          Xin chào, {user?.title || user?.username || "Bác sĩ"}
        </h1>
        <p className="mt-1 text-sm text-[#6A5C70]">
          Tổng quan hoạt động khám bệnh hôm nay
        </p>
      </div>

      {errorMessage && (
        <div className="border border-danger/30 bg-danger-light p-3 text-sm text-danger">
          <span>{errorMessage}</span>
        </div>
      )}

      {/* Stats grid */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          label="Tổng bệnh nhân hôm nay"
          value={todayTotalCount}
        />
        <StatCard
          label="Đang chờ khám"
          value={MOCK_STATS.waiting}
        />
        <StatCard
          label="Đã khám xong"
          value={todayCompletedCount}
        />
        <StatCard
          label="Thời gian khám TB"
          value={MOCK_STATS.avgConsultTime}
          suffix="phút"
        />
      </div>

      {/* Two column layout to show Queue Details and Recent Completed Patients */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Left Column (2/3 width): Expanded Hàng đợi khám — actionable information */}
        <div className="lg:col-span-2 border border-card-border bg-card-bg flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between border-b border-card-border px-6 py-4">
              <h2 className="text-base font-bold text-[#2B1D30]">Hàng đợi khám cần xử lý</h2>
              <Link
                href="/queue"
                className="text-sm font-bold text-primary-600 hover:text-primary-800 transition-colors"
              >
                Vào phòng khám →
              </Link>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-card-border bg-[#F3E8F5]">
                    <th className="px-6 py-3 font-semibold text-[#6A5C70] w-20">STT</th>
                    <th className="px-6 py-3 font-semibold text-[#6A5C70]">Bệnh nhân</th>
                    <th className="px-6 py-3 font-semibold text-[#6A5C70] w-20">Tuổi</th>
                    <th className="px-6 py-3 font-semibold text-[#6A5C70] w-32">Giờ vào</th>
                    <th className="px-6 py-3 font-semibold text-[#6A5C70] w-32">Trạng thái</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-card-border">
                  {MOCK_QUEUE_PATIENTS.map((patient) => (
                    <tr 
                      key={patient.queueNo} 
                      className="hover:bg-[#F8F6F9] cursor-pointer transition-colors"
                      onClick={() => handleCallPatient(patient.patientId)}
                    >
                      <td className="px-6 py-3.5 font-mono font-bold text-[#2B1D30]">{patient.queueNo}</td>
                      <td className="px-6 py-3.5">
                        <div className="flex items-center gap-3">
                          {/* Square avatar */}
                          <div className="flex h-8 w-8 items-center justify-center bg-[#F3E8F5] text-xs font-bold text-primary-700">
                            {patient.name.charAt(0)}
                          </div>
                          <span className="font-semibold text-primary-600 hover:text-primary-800 transition-colors">{patient.name}</span>
                        </div>
                      </td>
                      <td className="px-6 py-3.5 text-[#6A5C70]">{patient.age}</td>
                      <td className="px-6 py-3.5 text-[#6A5C70] font-mono">{patient.time}</td>
                      <td className="px-6 py-3.5">
                        <StatusBadge status={patient.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <div className="p-4 border-t border-card-border bg-gray-50 flex justify-end">
            <button 
              onClick={() => setErrorMessage("Tính năng tự động gọi bệnh nhân tiếp theo yêu cầu Queue Service (sẽ được phát triển ở Giai đoạn 2). Vui lòng click trực tiếp vào một bệnh nhân trong danh sách hàng đợi ở trên để bắt đầu khám.")}
              className="bg-primary-600 hover:bg-primary-700 text-white px-4 py-2 text-xs font-bold transition-all"
            >
              GỌI BỆNH NHÂN TIẾP THEO
            </button>
          </div>
        </div>

        {/* Right Column (1/3 width): Bệnh nhân vừa hoàn tất */}
        <div className="border border-card-border bg-card-bg">
          <div className="flex items-center justify-between border-b border-card-border px-6 py-4">
            <h2 className="text-base font-bold text-[#2B1D30]">Vừa hoàn tất</h2>
          </div>
          <div className="p-4 space-y-4">
            {MOCK_RECENT_COMPLETED.map((patient) => (
              <div key={patient.id} className="border border-card-border p-3 flex justify-between items-center bg-[#F8F6F9]">
                <div className="flex items-center gap-3">
                  {/* Square avatar */}
                  <div className="flex h-8 w-8 items-center justify-center bg-success-light text-xs font-bold text-success">
                    {patient.name.charAt(0)}
                  </div>
                  <div>
                    <p className="font-semibold text-sm text-[#2B1D30]">{patient.name}</p>
                    <p className="text-xs text-[#6A5C70]">{patient.age} tuổi · {patient.time}</p>
                  </div>
                </div>
                <StatusBadge status={patient.status} />
              </div>
            ))}
          </div>
        </div>

      </div>
    </div>
  );
}
