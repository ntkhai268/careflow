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
  variant = "purple",
  onConfirm,
  onCancel,
}: ConfirmModalProps) {
  if (!isOpen) return null;

  // Primary confirmation buttons in all modals use SJD Purple (#6E2582 / bg-primary-600)
  const confirmBtnBg =
    variant === "danger"
      ? "bg-rose-600 hover:bg-rose-700"
      : "bg-primary-600 hover:bg-primary-700";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 animate-in fade-in duration-150">
      {/* Strict Fixed Height & Internal Flex Container */}
      <div className="w-full max-w-lg max-h-[85vh] bg-card-bg border border-card-border shadow-2xl p-6 rounded-none flex flex-col space-y-4">
        {/* Header */}
        <div className="border-b border-card-border pb-3 flex-shrink-0">
          <h3 className="text-base font-bold text-[#2B1D30] uppercase tracking-wide">
            {title}
          </h3>
        </div>

        {/* Message body with internal scroll if overflowing */}
        <div className="flex-1 overflow-y-auto min-h-0 py-1 space-y-3 pr-1 custom-scrollbar">
          <p className="text-xs text-[#6A5C70] leading-relaxed">
            {message}
          </p>

          {/* Additional Content (e.g., Medicine List preview) */}
          {children}
        </div>

        {/* Footer Actions */}
        <div className="pt-3 border-t border-card-border flex justify-end gap-3 flex-shrink-0">
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
