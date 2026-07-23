"use client";

import React from "react";

interface ConfirmModalProps {
  isOpen: boolean;
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  variant?: "primary" | "warning" | "danger";
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmModal({
  isOpen,
  title,
  message,
  confirmText = "XÁC NHẬN",
  cancelText = "HỦY BỎ",
  variant = "primary",
  onConfirm,
  onCancel,
}: ConfirmModalProps) {
  if (!isOpen) return null;

  const confirmBtnBg =
    variant === "danger"
      ? "bg-rose-600 hover:bg-rose-700"
      : variant === "warning"
      ? "bg-amber-600 hover:bg-amber-700"
      : "bg-primary-600 hover:bg-primary-700";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 animate-in fade-in duration-150">
      <div className="w-full max-w-md bg-card-bg border border-card-border shadow-2xl p-6 rounded-none space-y-4">
        {/* Header */}
        <div className="border-b border-card-border pb-3">
          <h3 className="text-base font-bold text-[#2B1D30] uppercase tracking-wide">
            {title}
          </h3>
        </div>

        {/* Message body */}
        <div className="py-2">
          <p className="text-sm text-[#6A5C70] leading-relaxed">
            {message}
          </p>
        </div>

        {/* Footer Actions */}
        <div className="pt-3 border-t border-card-border flex justify-end gap-3">
          <button
            type="button"
            onClick={onCancel}
            className="border border-card-border hover:bg-gray-100 text-[#2B1D30] font-bold px-4 py-2 text-xs transition-all rounded-none"
          >
            {cancelText}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className={`${confirmBtnBg} text-white font-bold px-5 py-2 text-xs transition-all shadow-sm rounded-none`}
          >
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}
