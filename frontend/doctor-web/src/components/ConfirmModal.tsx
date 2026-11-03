"use client";

import React from "react";

interface ConfirmModalProps {
  isOpen: boolean;
  title: string;
  message: string;
  children?: React.ReactNode;
  confirmText?: string;
  cancelText?: string;
  variant?: "primary" | "warning" | "danger" | "purple";
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmModal({
  isOpen,
  title,
  message,
  children,
  confirmText = "XÁC NHẬN",
  cancelText = "HỦY BỎ",
  variant = "primary",
  onConfirm,
  onCancel,
}: ConfirmModalProps) {
  if (!isOpen) return null;

  const confirmBtnBg =
    variant === "danger"
      ? "bg-[#BA1A1A] hover:bg-[#93000A]"
      : "bg-[#0277A8] hover:bg-[#00577E]";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 backdrop-blur-xs p-4 animate-in fade-in duration-150">
      <div className="w-full max-w-lg max-h-[85vh] bg-white border border-[#D5E0E6] shadow-2xl p-6 rounded-2xl flex flex-col space-y-4">
        {/* Header */}
        <div className="border-b border-[#D5E0E6] pb-3 flex-shrink-0">
          <h3 className="text-base font-bold text-[#17242D] uppercase tracking-wide">{title}</h3>
        </div>

        {/* Message body with internal scroll if overflowing */}
        <div className="flex-1 overflow-y-auto min-h-0 py-1 space-y-3 pr-1 custom-scrollbar">
          <p className="text-xs text-[#4D626E] leading-relaxed">{message}</p>
          {children}
        </div>

        {/* Footer Actions */}
        <div className="pt-3 border-t border-[#D5E0E6] flex justify-end gap-3 flex-shrink-0">
          <button
            type="button"
            onClick={onCancel}
            className="border border-[#D5E0E6] hover:bg-[#EDF2F5] text-[#17242D] font-bold px-4 py-2 text-xs transition-all rounded-xl cursor-pointer"
          >
            {cancelText}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className={`${confirmBtnBg} text-white font-bold px-5 py-2 text-xs transition-all shadow-sm rounded-xl cursor-pointer`}
          >
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}
