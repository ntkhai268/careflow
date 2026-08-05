"use client";

interface HelpModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function HelpModal({ isOpen, onClose }: HelpModalProps) {
  if (!isOpen) return null;

  const shortcuts = [
    { key: "Alt + 1 / Ctrl + 1", desc: "Chuyển sang tab Tổng quan" },
    { key: "Alt + 2 / Ctrl + 2", desc: "Chuyển sang tab Hàng đợi khám" },
    { key: "Alt + 3 / Ctrl + 3", desc: "Chuyển sang tab Đơn thuốc" },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md overflow-hidden border border-gray-100 animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100" style={{ background: "linear-gradient(135deg, #0D0F1E, #161930)" }}>
          <div className="flex items-center gap-2.5">
            <div className="p-1.5 rounded-lg bg-indigo-500/20 text-indigo-300">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <div>
              <h3 className="text-sm font-bold text-white">Trợ giúp & Phím tắt</h3>
              <p className="text-[10px] text-gray-400">Danh sách phím tắt điều hướng nhanh trong hệ thống</p>
            </div>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-white p-1 rounded-lg transition-colors">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Content */}
        <div className="p-5 space-y-4">
          <div className="space-y-2">
            <h4 className="text-[11px] font-bold text-gray-500 uppercase tracking-wider">Phím tắt điều hướng Tab Dashboard</h4>
            <div className="divide-y divide-gray-100 border border-gray-100 rounded-lg overflow-hidden">
              {shortcuts.map((item, idx) => (
                <div key={idx} className="flex items-center justify-between px-3.5 py-2.5 bg-gray-50/50 hover:bg-indigo-50/50 transition-colors">
                  <span className="text-xs text-gray-700 font-medium">{item.desc}</span>
                  <kbd className="px-2 py-1 text-[10px] font-semibold text-indigo-600 bg-white border border-indigo-200 rounded shadow-xs font-mono">
                    {item.key}
                  </kbd>
                </div>
              ))}
            </div>
          </div>

        </div>

        {/* Footer */}
        <div className="px-5 py-3 bg-gray-50 border-t border-gray-100 flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-1.5 text-xs font-semibold text-white rounded-md transition-all shadow-xs"
            style={{ background: "linear-gradient(135deg, #6366F1, #3B82F6)" }}
          >
            Đã hiểu
          </button>
        </div>
      </div>
    </div>
  );
}
