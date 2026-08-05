"use client";

import { useState } from "react";

interface TimeSlotItem {
  time: string;
  maxCapacity: number;
  active: boolean;
}

const DEFAULT_SLOTS: TimeSlotItem[] = [
  { time: "07:30-08:00", maxCapacity: 5, active: true },
  { time: "08:00-08:30", maxCapacity: 5, active: true },
  { time: "08:30-09:00", maxCapacity: 5, active: true },
  { time: "09:00-09:30", maxCapacity: 5, active: true },
  { time: "09:30-10:00", maxCapacity: 5, active: true },
  { time: "10:00-10:30", maxCapacity: 5, active: true },
  { time: "10:30-11:00", maxCapacity: 5, active: true },
  { time: "11:00-11:30", maxCapacity: 5, active: true },
  { time: "13:30-14:00", maxCapacity: 5, active: true },
  { time: "14:00-14:30", maxCapacity: 5, active: true },
  { time: "14:30-15:00", maxCapacity: 5, active: true },
  { time: "15:00-15:30", maxCapacity: 5, active: true },
  { time: "15:30-16:00", maxCapacity: 5, active: true },
  { time: "16:00-16:30", maxCapacity: 5, active: true },
];

export default function AdminSchedulePage() {
  const [checkInWindowMinutes, setCheckInWindowMinutes] = useState(30);
  const [timeSlots, setTimeSlots] = useState<TimeSlotItem[]>(DEFAULT_SLOTS);
  const [toast, setToast] = useState<string | null>(null);

  const handleToggleSlot = (index: number) => {
    setTimeSlots(prev =>
      prev.map((s, i) => (i === index ? { ...s, active: !s.active } : s))
    );
  };

  const handleSaveConfig = () => {
    setToast("Đã lưu cấu hình khung giờ & chính sách check-in thành công!");
    setTimeout(() => setToast(null), 3000);
  };

  return (
    <div className="space-y-6 pb-12">
      {/* Toast */}
      {toast && (
        <div className="fixed top-5 right-5 z-50 p-4 bg-[#2B1D30] border border-purple-500 text-white shadow-lg rounded-lg text-xs font-semibold">
          {toast}
        </div>
      )}

      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border border-card-border bg-card-bg p-5 rounded-xl shadow-sm">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-block w-2.5 h-2.5 rounded-full bg-purple-500 animate-pulse" />
            <span className="text-xs font-bold uppercase tracking-wider text-purple-600">
              Quản trị Cấu hình Lịch & Capacity
            </span>
          </div>
          <h1 className="text-2xl font-extrabold text-[#2B1D30] mt-1">Cấu hình Khung giờ & Lịch làm việc</h1>
          <p className="text-xs text-[#6A5C70] mt-0.5">
            Cấu hình thời gian check-in sớm/muộn (checkInWindowMinutes), khung giờ khám (TimeSlot) và Capacity tối đa
          </p>
        </div>

        <button
          onClick={handleSaveConfig}
          className="px-5 py-3 rounded-xl font-bold text-xs text-white bg-[#6E2582] hover:bg-[#561A66] shadow-md transition-all cursor-pointer"
        >
          Lưu cấu hình
        </button>
      </div>

      {/* Policy Settings */}
      <div className="border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
        <h2 className="text-base font-bold text-[#2B1D30]">Chính sách Check-in & Gọi lượt</h2>

        <div className="max-w-md space-y-2 text-xs">
          <label className="block font-bold text-[#2B1D30]">
            Khoảng thời gian cho phép check-in trước/sau khung giờ (phút):
          </label>
          <input
            type="number"
            value={checkInWindowMinutes}
            onChange={e => setCheckInWindowMinutes(Number(e.target.value))}
            className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg text-xs font-bold focus:ring-2 focus:ring-purple-500"
          />
          <p className="text-[#6A5C70] text-[11px]">
            Bệnh nhân có thể check-in tối đa trước {checkInWindowMinutes} phút so với thời gian slot bắt đầu.
          </p>
        </div>
      </div>

      {/* Time Slots & Capacity Config */}
      <div className="border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
        <h2 className="text-base font-bold text-[#2B1D30]">Danh sách Khung giờ & Capacity tối đa (Slot Capacity)</h2>

        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {timeSlots.map((slot, idx) => (
            <div
              key={idx}
              className={`p-4 border rounded-xl space-y-2 transition-all ${
                slot.active
                  ? "bg-purple-50/70 border-purple-200"
                  : "bg-gray-50 border-gray-200 opacity-60"
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="font-extrabold text-[#2B1D30] text-sm">{slot.time}</span>
                <input
                  type="checkbox"
                  checked={slot.active}
                  onChange={() => handleToggleSlot(idx)}
                  className="w-4 h-4 text-purple-600 rounded cursor-pointer"
                />
              </div>

              <div className="text-xs text-[#6A5C70]">
                Capacity tối đa: <span className="font-bold text-purple-900">{slot.maxCapacity} bệnh nhân</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
