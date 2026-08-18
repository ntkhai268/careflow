"use client";

import { useEffect, useState } from "react";
import { queueApi } from "@/lib/queue-api";

interface TimeSlotItem {
  time: string;
  maxCapacity: number;
  active: boolean;
}

interface GeofenceFormState {
  siteId: string;
  facilityName: string;
  latitude: string;
  longitude: string;
  radiusMeters: string;
  maxAccuracyMeters: string;
}

const DEFAULT_GEOFENCE: GeofenceFormState = {
  siteId: "HOSPITAL-MAIN",
  facilityName: "Bệnh viện CareFlow",
  latitude: "10.7769",
  longitude: "106.7009",
  radiusMeters: "150",
  maxAccuracyMeters: "50",
};

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
  const [geofence, setGeofence] = useState<GeofenceFormState>(DEFAULT_GEOFENCE);
  const [isLoadingGeofence, setIsLoadingGeofence] = useState(true);
  const [isSavingGeofence, setIsSavingGeofence] = useState(false);
  const [geofenceError, setGeofenceError] = useState<string | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void queueApi.getHospitalCheckInConfig()
        .then((response) => {
          if (!response.data) return;
          setGeofence({
            siteId: response.data.siteId,
            facilityName: response.data.facilityName,
            latitude: String(response.data.latitude),
            longitude: String(response.data.longitude),
            radiusMeters: String(response.data.radiusMeters),
            maxAccuracyMeters: String(response.data.maxAccuracyMeters),
          });
        })
        .catch((error: unknown) => {
          setGeofenceError(error instanceof Error
            ? error.message
            : "Không thể tải cấu hình geofence bệnh viện.");
        })
        .finally(() => setIsLoadingGeofence(false));
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  const handleToggleSlot = (index: number) => {
    setTimeSlots(prev =>
      prev.map((s, i) => (i === index ? { ...s, active: !s.active } : s))
    );
  };

  const handleSaveConfig = () => {
    setToast("Đã lưu cấu hình khung giờ & chính sách check-in thành công!");
    setTimeout(() => setToast(null), 3000);
  };

  const handleSaveGeofence = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const latitude = Number(geofence.latitude);
    const longitude = Number(geofence.longitude);
    const radiusMeters = Number(geofence.radiusMeters);
    const maxAccuracyMeters = Number(geofence.maxAccuracyMeters);

    if (!geofence.siteId.trim() || !geofence.facilityName.trim()
      || !Number.isFinite(latitude) || latitude < -90 || latitude > 90
      || !Number.isFinite(longitude) || longitude < -180 || longitude > 180
      || !Number.isFinite(radiusMeters) || radiusMeters <= 0
      || !Number.isFinite(maxAccuracyMeters) || maxAccuracyMeters <= 0) {
      setGeofenceError("Vui lòng nhập mã cơ sở, tọa độ hợp lệ, bán kính và sai số GPS lớn hơn 0.");
      return;
    }

    setIsSavingGeofence(true);
    setGeofenceError(null);
    try {
      const response = await queueApi.updateHospitalCheckInConfig({
        siteId: geofence.siteId.trim(),
        facilityName: geofence.facilityName.trim(),
        latitude,
        longitude,
        radiusMeters,
        maxAccuracyMeters,
      });
      if (response.data) {
        setGeofence({
          siteId: response.data.siteId,
          facilityName: response.data.facilityName,
          latitude: String(response.data.latitude),
          longitude: String(response.data.longitude),
          radiusMeters: String(response.data.radiusMeters),
          maxAccuracyMeters: String(response.data.maxAccuracyMeters),
        });
      }
      setToast("Đã cập nhật cấu hình geofence bệnh viện.");
      window.setTimeout(() => setToast(null), 3000);
    } catch (error: unknown) {
      setGeofenceError(error instanceof Error
        ? error.message
        : "Không thể lưu cấu hình geofence bệnh viện.");
    } finally {
      setIsSavingGeofence(false);
    }
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
            Cấu hình thời gian check-in sớm/muộn, khung giờ khám và Capacity tối đa
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

      {/* Hospital Geofence Config */}
      <section className="border border-[#C7A6D1] bg-[#FCF8FD] rounded-xl p-5 shadow-sm space-y-5">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-[#7B4B94]">Hospital Geofence</p>
            <h2 className="mt-1 text-base font-bold text-[#2B1D30]">Khu vực check-in bệnh viện</h2>
            <p className="mt-1 max-w-2xl text-[11px] leading-5 text-[#6A5C70]">
              Bệnh nhân chỉ được chuyển sang CHECKED_IN khi QR hợp lệ và vị trí thiết bị nằm trong bán kính này.
              Cấu hình được áp dụng ngay cho các lượt check-in tiếp theo.
            </p>
          </div>
          <span className="w-fit rounded-full bg-emerald-100 px-3 py-1 text-[10px] font-bold text-emerald-800">
            Đang áp dụng cho toàn bệnh viện
          </span>
        </div>

        {isLoadingGeofence ? (
          <div className="h-40 animate-pulse rounded-xl bg-white/70" aria-label="Đang tải cấu hình geofence" />
        ) : (
          <form onSubmit={handleSaveGeofence} className="space-y-5">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label htmlFor="geofence-site-id" className="mb-1.5 block text-[11px] font-bold text-[#2B1D30]">
                  Mã cơ sở
                </label>
                <input
                  id="geofence-site-id"
                  value={geofence.siteId}
                  onChange={(event) => setGeofence((current) => ({ ...current, siteId: event.target.value }))}
                  className="w-full rounded-lg border border-card-border bg-white px-3 py-2.5 text-xs font-semibold text-[#2B1D30] outline-none transition focus:border-[#7B4B94] focus:ring-2 focus:ring-[#E9D8EE]"
                  maxLength={50}
                  required
                />
              </div>
              <div>
                <label htmlFor="geofence-facility-name" className="mb-1.5 block text-[11px] font-bold text-[#2B1D30]">
                  Tên cơ sở
                </label>
                <input
                  id="geofence-facility-name"
                  value={geofence.facilityName}
                  onChange={(event) => setGeofence((current) => ({ ...current, facilityName: event.target.value }))}
                  className="w-full rounded-lg border border-card-border bg-white px-3 py-2.5 text-xs font-semibold text-[#2B1D30] outline-none transition focus:border-[#7B4B94] focus:ring-2 focus:ring-[#E9D8EE]"
                  maxLength={160}
                  required
                />
              </div>
              <div>
                <label htmlFor="geofence-latitude" className="mb-1.5 block text-[11px] font-bold text-[#2B1D30]">
                  Vĩ độ tâm geofence
                </label>
                <input
                  id="geofence-latitude"
                  type="number"
                  step="0.000001"
                  min="-90"
                  max="90"
                  value={geofence.latitude}
                  onChange={(event) => setGeofence((current) => ({ ...current, latitude: event.target.value }))}
                  className="w-full rounded-lg border border-card-border bg-white px-3 py-2.5 text-xs font-semibold text-[#2B1D30] outline-none transition focus:border-[#7B4B94] focus:ring-2 focus:ring-[#E9D8EE]"
                  required
                />
              </div>
              <div>
                <label htmlFor="geofence-longitude" className="mb-1.5 block text-[11px] font-bold text-[#2B1D30]">
                  Kinh độ tâm geofence
                </label>
                <input
                  id="geofence-longitude"
                  type="number"
                  step="0.000001"
                  min="-180"
                  max="180"
                  value={geofence.longitude}
                  onChange={(event) => setGeofence((current) => ({ ...current, longitude: event.target.value }))}
                  className="w-full rounded-lg border border-card-border bg-white px-3 py-2.5 text-xs font-semibold text-[#2B1D30] outline-none transition focus:border-[#7B4B94] focus:ring-2 focus:ring-[#E9D8EE]"
                  required
                />
              </div>
              <div>
                <label htmlFor="geofence-radius" className="mb-1.5 block text-[11px] font-bold text-[#2B1D30]">
                  Bán kính cho phép (mét)
                </label>
                <input
                  id="geofence-radius"
                  type="number"
                  step="1"
                  min="1"
                  max="10000"
                  value={geofence.radiusMeters}
                  onChange={(event) => setGeofence((current) => ({ ...current, radiusMeters: event.target.value }))}
                  className="w-full rounded-lg border border-[#A878B4] bg-white px-3 py-2.5 text-xs font-bold text-[#6E2582] outline-none transition focus:border-[#7B4B94] focus:ring-2 focus:ring-[#E9D8EE]"
                  required
                />
                <p className="mt-1.5 text-[10px] text-[#6A5C70]">Ví dụ: 150m bao quanh tâm bệnh viện.</p>
              </div>
              <div>
                <label htmlFor="geofence-accuracy" className="mb-1.5 block text-[11px] font-bold text-[#2B1D30]">
                  Sai số GPS tối đa (mét)
                </label>
                <input
                  id="geofence-accuracy"
                  type="number"
                  step="1"
                  min="1"
                  max="1000"
                  value={geofence.maxAccuracyMeters}
                  onChange={(event) => setGeofence((current) => ({ ...current, maxAccuracyMeters: event.target.value }))}
                  className="w-full rounded-lg border border-card-border bg-white px-3 py-2.5 text-xs font-semibold text-[#2B1D30] outline-none transition focus:border-[#7B4B94] focus:ring-2 focus:ring-[#E9D8EE]"
                  required
                />
                <p className="mt-1.5 text-[10px] text-[#6A5C70]">Từ chối vị trí có độ chính xác kém hơn ngưỡng này.</p>
              </div>
            </div>

            {geofenceError && (
              <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2.5 text-[11px] font-semibold text-rose-700">
                {geofenceError}
              </p>
            )}

            <div className="flex flex-col gap-3 border-t border-[#E9D8EE] pt-4 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-[10px] text-[#6A5C70]">
                Thay đổi này ảnh hưởng đến tất cả lượt bệnh nhân tự check-in sau khi lưu.
              </p>
              <button
                type="submit"
                disabled={isSavingGeofence}
                className="rounded-xl bg-[#6E2582] px-5 py-3 text-xs font-bold text-white shadow-md transition hover:bg-[#561A66] disabled:cursor-not-allowed disabled:opacity-60"
              >
                {isSavingGeofence ? "Đang lưu..." : "Lưu cấu hình geofence"}
              </button>
            </div>
          </form>
        )}
      </section>

      {/* Time Slots & Capacity Config */}
      <div className="border border-card-border bg-card-bg rounded-xl p-5 shadow-sm space-y-4">
        <h2 className="text-base font-bold text-[#2B1D30]">Danh sách Khung giờ & Capacity tối đa</h2>

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
