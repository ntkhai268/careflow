"use client";

import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";

interface ProfileModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function ProfileModal({ isOpen, onClose }: ProfileModalProps) {
  const { user } = useAuth();
  const [fullName, setFullName] = useState("");
  const [phone, setPhone] = useState("0912 345 678");
  const [bio, setBio] = useState("Bác sĩ Chuyên khoa I với 8 năm kinh nghiệm khám và điều trị các bệnh lý lâm sàng.");
  const [saveSuccess, setSaveSuccess] = useState(false);

  useEffect(() => {
    if (!user) return;
    const nextFullName = user.fullName || "BS. Nguyễn Văn An";
    queueMicrotask(() => setFullName(nextFullName));
  }, [user]);

  if (!isOpen) return null;

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    setSaveSuccess(true);
    setTimeout(() => {
      setSaveSuccess(false);
      onClose();
    }, 1000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-lg overflow-hidden border border-gray-100 animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100" style={{ background: "linear-gradient(135deg, #0D0F1E, #161930)" }}>
          <div className="flex items-center gap-2.5">
            <div className="p-1.5 rounded-lg bg-indigo-500/20 text-indigo-300">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
              </svg>
            </div>
            <div>
              <h3 className="text-sm font-bold text-white">Hồ sơ Bác sĩ</h3>
              <p className="text-[10px] text-gray-400">Xem và cập nhật thông tin cá nhân & chuyên môn</p>
            </div>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-white p-1 rounded-lg transition-colors">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Content */}
        <form onSubmit={handleSave} className="p-5 space-y-4 max-h-[75vh] overflow-y-auto">
          {saveSuccess && (
            <div className="p-2.5 rounded-lg bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs font-semibold flex items-center gap-2">
              <svg className="w-4 h-4 text-emerald-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
              Cập nhật thông tin hồ sơ thành công!
            </div>
          )}

          {/* Section 1: Thông tin cá nhân (Cho phép sửa) */}
          <div className="space-y-3">
            <h4 className="text-[11px] font-bold text-gray-500 uppercase tracking-wider">1. Thông tin cá nhân (Có thể chỉnh sửa)</h4>
            
            <div>
              <label className="block text-[11px] font-semibold text-gray-700 mb-1">Họ và tên Bác sĩ</label>
              <input
                type="text"
                value={fullName}
                onChange={e => setFullName(e.target.value)}
                className="w-full bg-gray-50 border border-gray-200 rounded-lg px-3 py-1.5 text-xs text-gray-800 outline-none focus:border-indigo-500 focus:bg-white transition-colors"
                required
              />
            </div>

            <div>
              <label className="block text-[11px] font-semibold text-gray-700 mb-1">Số điện thoại liên hệ</label>
              <input
                type="text"
                value={phone}
                onChange={e => setPhone(e.target.value)}
                className="w-full bg-gray-50 border border-gray-200 rounded-lg px-3 py-1.5 text-xs text-gray-800 outline-none focus:border-indigo-500 focus:bg-white transition-colors"
              />
            </div>

            <div>
              <label className="block text-[11px] font-semibold text-gray-700 mb-1">Giới thiệu ngắn / Tóm tắt chuyên môn</label>
              <textarea
                rows={2}
                value={bio}
                onChange={e => setBio(e.target.value)}
                className="w-full bg-gray-50 border border-gray-200 rounded-lg px-3 py-1.5 text-xs text-gray-800 outline-none focus:border-indigo-500 focus:bg-white transition-colors"
              />
            </div>
          </div>

          {/* Section 2: Thông tin do Bệnh viện cấp (Cố định - Readonly) */}
          <div className="space-y-3 pt-2 border-t border-gray-100">
            <h4 className="text-[11px] font-bold text-gray-500 uppercase tracking-wider flex items-center justify-between">
              <span>2. Thông tin Bệnh viện cấp (Cố định)</span>
              <span className="text-[9px] text-indigo-600 bg-indigo-50 px-2 py-0.5 rounded font-semibold">Chỉ đọc</span>
            </h4>

            <div className="grid grid-cols-2 gap-3 bg-gray-50/80 p-3 rounded-lg border border-gray-100">
              <div>
                <span className="text-[10px] text-gray-400 block">Mã định danh Bác sĩ (UUID):</span>
                <span className="text-[11px] font-mono font-semibold text-gray-800 truncate block" title={user?.id}>
                  {user?.id ? user.id.substring(0, 18) + "..." : "d0000001-0000..."}
                </span>
              </div>

              <div>
                <span className="text-[10px] text-gray-400 block">Số CCHN Y tế:</span>
                <span className="text-[11px] font-semibold text-gray-800 block">CCHN-001234/HCM</span>
              </div>

              <div>
                <span className="text-[10px] text-gray-400 block">Chuyên khoa làm việc:</span>
                <span className="text-[11px] font-semibold text-indigo-700 block">
                  {user?.department || "Nội tổng quát"} (NOI_TONG_QUAT)
                </span>
              </div>

              <div>
                <span className="text-[10px] text-gray-400 block">Phòng khám được phân công:</span>
                <span className="text-[11px] font-semibold text-indigo-700 block">Phòng 101 - Khu A (ROOM-01)</span>
              </div>
            </div>
          </div>

          {/* Footer buttons */}
          <div className="pt-3 border-t border-gray-100 flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-1.5 text-xs font-semibold text-gray-600 hover:bg-gray-200 rounded-md transition-colors"
            >
              Hủy
            </button>
            <button
              type="submit"
              className="px-4 py-1.5 text-xs font-semibold text-white rounded-md transition-all shadow-xs"
              style={{ background: "linear-gradient(135deg, #6366F1, #3B82F6)" }}
            >
              Lưu thay đổi
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
