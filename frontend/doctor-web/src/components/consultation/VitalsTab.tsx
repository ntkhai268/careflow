"use client";

import {
  getBpWarning,
  getHeartRateWarning,
  getSpo2Warning,
  getTempWarning,
} from "./consultation-constants";

interface VitalsTabProps {
  temperature: string;
  setTemperature: (val: string) => void;
  bloodPressure: string;
  setBloodPressure: (val: string) => void;
  heartRate: string;
  setHeartRate: (val: string) => void;
  spo2: string;
  setSpo2: (val: string) => void;
  height: string;
  setHeight: (val: string) => void;
  weight: string;
  setWeight: (val: string) => void;
  isConsultationLocked: boolean;
}

export default function VitalsTab({
  temperature,
  setTemperature,
  bloodPressure,
  setBloodPressure,
  heartRate,
  setHeartRate,
  spo2,
  setSpo2,
  height,
  setHeight,
  weight,
  setWeight,
  isConsultationLocked,
}: VitalsTabProps) {
  const tempWarn = getTempWarning(temperature);
  const bpWarn = getBpWarning(bloodPressure);
  const hrWarn = getHeartRateWarning(heartRate);
  const spo2Warn = getSpo2Warning(spo2);

  return (
    <div className="border border-card-border bg-card-bg p-5 shadow-[0_1px_3px_rgba(110,37,130,0.06)] rounded-b-lg space-y-4">
      <div className="flex justify-between items-center border-b border-card-border pb-2">
        <h2 className="text-xs font-bold text-[#2B1D30] uppercase tracking-wide">
          Chỉ số sinh hiệu bệnh nhân
        </h2>
        <span className="text-[10px] text-[#6A5C70]">Tự động phát hiện bất thường</span>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
        <div>
          <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Nhiệt độ (°C)</label>
          <input
            type="number"
            step="0.1"
            disabled={isConsultationLocked}
            value={temperature}
            onChange={(e) => setTemperature(e.target.value)}
            placeholder="36.5"
            className={`w-full border px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-none ${
              tempWarn
                ? tempWarn.level === "danger"
                  ? "border-rose-500 bg-rose-50 font-bold"
                  : "border-amber-500 bg-amber-50 font-bold"
                : "border-input-border bg-input-bg focus:border-input-focus"
            }`}
          />
          {tempWarn && (
            <span
              className={`text-[10px] font-bold mt-1 block ${
                tempWarn.level === "danger" ? "text-rose-600" : "text-amber-600"
              }`}
            >
              Cảnh báo: {tempWarn.text}
            </span>
          )}
        </div>

        <div>
          <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Huyết áp (mmHg)</label>
          <input
            type="text"
            disabled={isConsultationLocked}
            value={bloodPressure}
            onChange={(e) => setBloodPressure(e.target.value)}
            placeholder="120/80"
            className={`w-full border px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-none ${
              bpWarn
                ? bpWarn.level === "danger"
                  ? "border-rose-500 bg-rose-50 font-bold"
                  : "border-amber-500 bg-amber-50 font-bold"
                : "border-input-border bg-input-bg focus:border-input-focus"
            }`}
          />
          {bpWarn && (
            <span
              className={`text-[10px] font-bold mt-1 block ${
                bpWarn.level === "danger" ? "text-rose-600" : "text-amber-600"
              }`}
            >
              Cảnh báo: {bpWarn.text}
            </span>
          )}
        </div>

        <div>
          <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Mạch (lần/phút)</label>
          <input
            type="number"
            disabled={isConsultationLocked}
            value={heartRate}
            onChange={(e) => setHeartRate(e.target.value)}
            placeholder="80"
            className={`w-full border px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-none ${
              hrWarn ? "border-amber-500 bg-amber-50 font-bold" : "border-input-border bg-input-bg focus:border-input-focus"
            }`}
          />
          {hrWarn && (
            <span className="text-[10px] font-bold mt-1 block text-amber-600">
              Cảnh báo: {hrWarn.text}
            </span>
          )}
        </div>

        <div>
          <label className="block text-xs font-semibold text-[#6A5C70] mb-1">SpO2 (%)</label>
          <input
            type="number"
            disabled={isConsultationLocked}
            value={spo2}
            onChange={(e) => setSpo2(e.target.value)}
            placeholder="98"
            className={`w-full border px-3 py-2 text-sm text-[#2B1D30] focus:outline-none rounded-none ${
              spo2Warn ? "border-rose-500 bg-rose-50 font-bold" : "border-input-border bg-input-bg focus:border-input-focus"
            }`}
          />
          {spo2Warn && (
            <span className="text-[10px] font-bold mt-1 block text-rose-600">
              Cảnh báo: {spo2Warn.text}
            </span>
          )}
        </div>

        <div>
          <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Chiều cao (cm)</label>
          <input
            type="number"
            step="0.1"
            disabled={isConsultationLocked}
            value={height}
            onChange={(e) => setHeight(e.target.value)}
            placeholder="165"
            className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-none"
          />
        </div>

        <div>
          <label className="block text-xs font-semibold text-[#6A5C70] mb-1">Cân nặng (kg)</label>
          <input
            type="number"
            step="0.1"
            disabled={isConsultationLocked}
            value={weight}
            onChange={(e) => setWeight(e.target.value)}
            placeholder="60"
            className="w-full border border-input-border bg-input-bg px-3 py-2 text-sm text-[#2B1D30] focus:border-input-focus focus:outline-none rounded-none"
          />
        </div>
      </div>
    </div>
  );
}
