"use client";

import { useState, useEffect } from "react";

interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function SettingsModal({ isOpen, onClose }: SettingsModalProps) {
  const [darkMode, setDarkMode] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(false);
  const [soundNotify, setSoundNotify] = useState(true);
  const [autoRefreshSecs, setAutoRefreshSecs] = useState("30");

  useEffect(() => {
    if (typeof window !== "undefined") {
      const savedTheme = localStorage.getItem("careflow_theme");
      if (savedTheme === "dark") setDarkMode(true);
      const savedMotion = localStorage.getItem("careflow_reduced_motion");
      if (savedMotion === "true") setReducedMotion(true);
      const savedSound = localStorage.getItem("careflow_sound_notify");
      if (savedSound === "false") setSoundNotify(false);
    }
  }, []);

  const handleSave = () => {
    if (typeof window !== "undefined") {
      localStorage.setItem("careflow_theme", darkMode ? "dark" : "light");
      localStorage.setItem("careflow_reduced_motion", String(reducedMotion));
      localStorage.setItem("careflow_sound_notify", String(soundNotify));
      localStorage.setItem("careflow_auto_refresh", autoRefreshSecs);
    }
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-lg overflow-hidden border border-gray-100 animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100" style={{ background: "linear-gradient(135deg, #0D0F1E, #161930)" }}>
          <div className="flex items-center gap-2.5">
            <div className="p-1.5 rounded-lg bg-indigo-500/20 text-indigo-300">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
            </div>
            <div>
              <h3 className="text-sm font-bold text-white">Cài đặt hệ thống</h3>
              <p className="text-[10px] text-gray-400">Tùy chỉnh giao diện và phương thức vận hành cho Bác sĩ</p>
            </div>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-white p-1 rounded-lg transition-colors">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Form Content */}
        <div className="p-5 space-y-5 max-h-[75vh] overflow-y-auto">
          {/* Section 1: Giao diện */}
          <div className="space-y-3">
            <h4 className="text-[11px] font-bold text-gray-500 uppercase tracking-wider">1. Giao diện & Trực quan</h4>
            
            <div className="flex items-center justify-between p-3 rounded-lg border border-gray-100 hover:bg-gray-50 transition-colors">
              <div>
                <p className="text-xs font-semibold text-gray-800">Chế độ tối (Dark Mode)</p>
                <p className="text-[10px] text-gray-500">Giảm mỏi mắt khi Bác sĩ làm việc ca trực đêm</p>
              </div>
              <label className="relative inline-flex items-center cursor-pointer">
                <input
                  type="checkbox"
                  checked={darkMode}
                  onChange={e => setDarkMode(e.target.checked)}
                  className="sr-only peer"
                />
                <div className="w-9 h-5 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-indigo-600"></div>
              </label>
            </div>

            <div className="flex items-center justify-between p-3 rounded-lg border border-gray-100 hover:bg-gray-50 transition-colors">
              <div>
                <p className="text-xs font-semibold text-gray-800">Giảm hiệu ứng chuyển động (Reduced Motion)</p>
                <p className="text-[10px] text-gray-500">Tối ưu tốc độ cho máy tính workstation bệnh viện public</p>
              </div>
              <label className="relative inline-flex items-center cursor-pointer">
                <input
                  type="checkbox"
                  checked={reducedMotion}
                  onChange={e => setReducedMotion(e.target.checked)}
                  className="sr-only peer"
                />
                <div className="w-9 h-5 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-indigo-600"></div>
              </label>
            </div>
          </div>

          {/* Section 2: Vận hành & Thông báo */}
          <div className="space-y-3">
            <h4 className="text-[11px] font-bold text-gray-500 uppercase tracking-wider">2. Vận hành & Thông báo Lâm sàng</h4>

            <div className="flex items-center justify-between p-3 rounded-lg border border-gray-100 hover:bg-gray-50 transition-colors">
              <div>
                <p className="text-xs font-semibold text-gray-800">Âm thanh chuông báo Hàng đợi</p>
                <p className="text-[10px] text-gray-500">Phát âm thanh nhẹ khi có bệnh nhân mới vào phòng khám</p>
              </div>
              <label className="relative inline-flex items-center cursor-pointer">
                <input
                  type="checkbox"
                  checked={soundNotify}
                  onChange={e => setSoundNotify(e.target.checked)}
                  className="sr-only peer"
                />
                <div className="w-9 h-5 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-indigo-600"></div>
              </label>
            </div>

            <div className="flex items-center justify-between p-3 rounded-lg border border-gray-100 hover:bg-gray-50 transition-colors">
              <div>
                <p className="text-xs font-semibold text-gray-800">Tần suất làm mới Hàng đợi</p>
                <p className="text-[10px] text-gray-500">Tự động đồng bộ danh sách bệnh nhân chờ</p>
              </div>
              <div className="relative shrink-0">
                <select
                  aria-label="Tần suất làm mới hàng đợi"
                  value={autoRefreshSecs}
                  onChange={e => setAutoRefreshSecs(e.target.value)}
                  className="h-9 w-28 appearance-none rounded-md border border-slate-200 bg-white px-3 pr-8 text-[11px] font-semibold text-[#2B1D30] outline-none transition-colors hover:border-[#BFA3C8] focus:border-[#7B4B94] focus:ring-2 focus:ring-[#F3E8F5]"
                >
                  <option value="15">15 giây</option>
                  <option value="30">30 giây</option>
                  <option value="60">1 phút</option>
                </select>
                <svg className="pointer-events-none absolute right-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path fillRule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.51a.75.75 0 01-1.08 0l-4.25-4.51a.75.75 0 01.02-1.06z" clipRule="evenodd" />
                </svg>
              </div>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="px-5 py-3 bg-gray-50 border-t border-gray-100 flex items-center justify-end gap-2">
          <button
            onClick={onClose}
            className="px-3.5 py-1.5 text-xs font-semibold text-gray-600 hover:bg-gray-200 rounded-md transition-colors"
          >
            Hủy
          </button>
          <button
            onClick={handleSave}
            className="px-4 py-1.5 text-xs font-semibold text-white rounded-md transition-all shadow-xs"
            style={{ background: "linear-gradient(135deg, #6366F1, #3B82F6)" }}
          >
            Lưu thay đổi
          </button>
        </div>
      </div>
    </div>
  );
}
