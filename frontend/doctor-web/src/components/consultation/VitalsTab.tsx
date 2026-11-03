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

  const heightMeters = parseFloat(height) / 100;
  const weightKg = parseFloat(weight);
  const bmi =
    heightMeters > 0.5 && weightKg > 5
      ? (weightKg / (heightMeters * heightMeters)).toFixed(1)
      : null;

  return (
    <div className="border border-[#D5E0E6] bg-white p-5 shadow-[0_2px_8px_rgba(23,36,45,0.04)] rounded-b-xl space-y-4">
      <div className="flex flex-wrap justify-between items-center gap-2 border-b border-[#EDF2F5] pb-3">
        <div>
          <h2 className="text-xs font-bold text-[#17242D] uppercase tracking-wide">
            Chỉ số sinh hiệu bệnh nhân
          </h2>
          <span className="text-[11px] text-[#667985]">
            Tự động đối chiếu ngưỡng cảnh báo lâm sàng theo thời gian thực
          </span>
        </div>
        {bmi && (
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#DDF3FC] border border-[#8BD2EE] text-xs">
            <span className="font-semibold text-[#00577E]">BMI:</span>
            <span className="font-mono font-bold text-[#17242D]">{bmi} kg/m²</span>
          </div>
        )}
      </div>

      <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
        <div>
          <label className="block text-xs font-semibold text-[#4D626E] mb-1.5">
            Nhiệt độ cơ thể
          </label>
          <div className="relative">
            <input
              type="number"
              step="0.1"
              disabled={isConsultationLocked}
              value={temperature}
              onChange={(e) => setTemperature(e.target.value)}
              placeholder="36.5"
              className={`w-full border pl-3.5 pr-12 py-2.5 text-sm font-mono text-[#17242D] focus:outline-none rounded-xl transition-all ${
                tempWarn
                  ? tempWarn.level === "danger"
                    ? "border-[#BA1A1A] bg-[#FFDAD6]/35 font-bold"
                    : "border-[#A86100] bg-[#FFEBCB]/50 font-bold"
                  : "border-[#D5E0E6] bg-white focus:border-[#0277A8] focus:ring-2 focus:ring-[#DDF3FC]"
              }`}
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-mono font-semibold text-[#667985] pointer-events-none">
              °C
            </span>
          </div>
          {tempWarn && (
            <span
              className={`text-[11px] font-bold mt-1.5 block ${
                tempWarn.level === "danger" ? "text-[#BA1A1A]" : "text-[#A86100]"
              }`}
            >
              Cảnh báo: {tempWarn.text}
            </span>
          )}
        </div>

        <div>
          <label className="block text-xs font-semibold text-[#4D626E] mb-1.5">
            Huyết áp (Tâm thu / Tâm trương)
          </label>
          <div className="relative">
            <input
              type="text"
              disabled={isConsultationLocked}
              value={bloodPressure}
              onChange={(e) => setBloodPressure(e.target.value)}
              placeholder="120/80"
              className={`w-full border pl-3.5 pr-16 py-2.5 text-sm font-mono text-[#17242D] focus:outline-none rounded-xl transition-all ${
                bpWarn
                  ? bpWarn.level === "danger"
                    ? "border-[#BA1A1A] bg-[#FFDAD6]/35 font-bold"
                    : "border-[#A86100] bg-[#FFEBCB]/50 font-bold"
                  : "border-[#D5E0E6] bg-white focus:border-[#0277A8] focus:ring-2 focus:ring-[#DDF3FC]"
              }`}
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-mono font-semibold text-[#667985] pointer-events-none">
              mmHg
            </span>
          </div>
          {bpWarn && (
            <span
              className={`text-[11px] font-bold mt-1.5 block ${
                bpWarn.level === "danger" ? "text-[#BA1A1A]" : "text-[#A86100]"
              }`}
            >
              Cảnh báo: {bpWarn.text}
            </span>
          )}
        </div>

        <div>
          <label className="block text-xs font-semibold text-[#4D626E] mb-1.5">
            Nhịp tim / Mạch
          </label>
          <div className="relative">
            <input
              type="number"
              disabled={isConsultationLocked}
              value={heartRate}
              onChange={(e) => setHeartRate(e.target.value)}
              placeholder="80"
              className={`w-full border pl-3.5 pr-14 py-2.5 text-sm font-mono text-[#17242D] focus:outline-none rounded-xl transition-all ${
                hrWarn
                  ? "border-[#A86100] bg-[#FFEBCB]/50 font-bold"
                  : "border-[#D5E0E6] bg-white focus:border-[#0277A8] focus:ring-2 focus:ring-[#DDF3FC]"
              }`}
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-mono font-semibold text-[#667985] pointer-events-none">
              bpm
            </span>
          </div>
          {hrWarn && (
            <span className="text-[11px] font-bold mt-1.5 block text-[#A86100]">
              Cảnh báo: {hrWarn.text}
            </span>
          )}
        </div>

        <div>
          <label className="block text-xs font-semibold text-[#4D626E] mb-1.5">
            Độ bão hòa Oxy (SpO2)
          </label>
          <div className="relative">
            <input
              type="number"
              disabled={isConsultationLocked}
              value={spo2}
              onChange={(e) => setSpo2(e.target.value)}
              placeholder="98"
              className={`w-full border pl-3.5 pr-10 py-2.5 text-sm font-mono text-[#17242D] focus:outline-none rounded-xl transition-all ${
                spo2Warn
                  ? "border-[#BA1A1A] bg-[#FFDAD6]/35 font-bold"
                  : "border-[#D5E0E6] bg-white focus:border-[#0277A8] focus:ring-2 focus:ring-[#DDF3FC]"
              }`}
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-mono font-semibold text-[#667985] pointer-events-none">
              %
            </span>
          </div>
          {spo2Warn && (
            <span className="text-[11px] font-bold mt-1.5 block text-[#BA1A1A]">
              Cảnh báo: {spo2Warn.text}
            </span>
          )}
        </div>

        <div>
          <label className="block text-xs font-semibold text-[#4D626E] mb-1.5">
            Chiều cao
          </label>
          <div className="relative">
            <input
              type="number"
              step="0.1"
              disabled={isConsultationLocked}
              value={height}
              onChange={(e) => setHeight(e.target.value)}
              placeholder="165"
              className="w-full border border-[#D5E0E6] bg-white pl-3.5 pr-12 py-2.5 text-sm font-mono text-[#17242D] focus:border-[#0277A8] focus:ring-2 focus:ring-[#DDF3FC] focus:outline-none rounded-xl transition-all"
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-mono font-semibold text-[#667985] pointer-events-none">
              cm
            </span>
          </div>
        </div>

        <div>
          <label className="block text-xs font-semibold text-[#4D626E] mb-1.5">
            Cân nặng
          </label>
          <div className="relative">
            <input
              type="number"
              step="0.1"
              disabled={isConsultationLocked}
              value={weight}
              onChange={(e) => setWeight(e.target.value)}
              placeholder="60"
              className="w-full border border-[#D5E0E6] bg-white pl-3.5 pr-12 py-2.5 text-sm font-mono text-[#17242D] focus:border-[#0277A8] focus:ring-2 focus:ring-[#DDF3FC] focus:outline-none rounded-xl transition-all"
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-mono font-semibold text-[#667985] pointer-events-none">
              kg
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
