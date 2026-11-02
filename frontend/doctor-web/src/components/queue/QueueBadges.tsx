"use client";

import { QueueEntry } from "@/lib/queue-api";

export const RECEPTION_STATUS_LABELS: Record<string, string> = {
  WAITING: "Đang chờ",
  TICKET_ISSUED: "Chờ tiếp nhận",
  CHECKED_IN: "Đã tiếp nhận",
  CALLED: "Đã gọi số",
  IN_PROGRESS: "Đang khám",
  COMPLETED: "Hoàn tất",
  MISSED: "Lỡ lượt",
  CANCELLED: "Đã hủy",
};

export const RECEPTION_STATUS_COLORS: Record<string, string> = {
  WAITING: "text-amber-600",
  TICKET_ISSUED: "text-amber-600",
  CHECKED_IN: "text-blue-600",
  CALLED: "text-purple-600",
  IN_PROGRESS: "text-purple-600",
  COMPLETED: "text-emerald-600",
  MISSED: "text-rose-600",
  CANCELLED: "text-slate-500",
};

export const PRIORITY_LABELS: Record<string, string> = {
  APPOINTMENT: "Đặt lịch",
  PRIORITY: "Ưu tiên",
  EMERGENCY: "Khẩn cấp",
  WALK_IN: "Vãng lai",
  RESULT_REVIEW: "Đọc kết quả CLS",
};

export function ReceptionStatusText({ status }: { status: string }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 text-[10px] font-medium ${
        RECEPTION_STATUS_COLORS[status] || "text-slate-500"
      }`}
    >
      <span className="h-1.5 w-1.5 rounded-full bg-current" />
      {RECEPTION_STATUS_LABELS[status] || status}
    </span>
  );
}

export function QueueStatusDot({ status }: { status: string }) {
  const config: Record<string, { color: string; label: string; textClass: string }> = {
    WAITING: { color: "#F59E0B", label: "Chờ khám", textClass: "text-amber-600 font-medium" },
    PENDING: { color: "#F59E0B", label: "Chờ khám", textClass: "text-amber-600 font-medium" },
    CHECKED_IN: { color: "#3B82F6", label: "Đã check-in", textClass: "text-blue-600 font-medium" },
    QUEUED: { color: "#3B82F6", label: "Đang chờ gọi", textClass: "text-blue-600 font-medium" },
    CALLED: { color: "#F59E0B", label: "Đã gọi số", textClass: "text-amber-600 font-semibold" },
    IN_PROGRESS: { color: "#8B5CF6", label: "Đang khám", textClass: "text-purple-600 font-semibold" },
    CONFIRMED: { color: "#3B82F6", label: "Đã tiếp nhận", textClass: "text-blue-600 font-medium" },
    COMPLETED: { color: "#10B981", label: "Hoàn tất", textClass: "text-emerald-600 font-medium" },
    MISSED: { color: "#EF4444", label: "Lỡ lượt", textClass: "text-rose-600 font-medium" },
    CANCELLED: { color: "#EF4444", label: "Đã huỷ", textClass: "text-rose-600 font-medium" },
  };
  const cfg = config[status] || {
    color: "#94A3B8",
    label: status,
    textClass: "text-gray-600 font-medium",
  };
  return (
    <span className={`inline-flex items-center gap-1.5 text-[11px] leading-none ${cfg.textClass}`}>
      <span
        className="w-1.5 h-1.5 rounded-full flex-shrink-0 inline-block align-middle"
        style={{ backgroundColor: cfg.color }}
      />
      <span className="leading-none">{cfg.label}</span>
    </span>
  );
}

export function QueuePriorityBadge({ level }: { level: string }) {
  if (level === "PRIORITY" || level === "EMERGENCY") {
    return (
      <span className="inline-flex items-center gap-1.5 text-[11px] leading-none text-rose-600 font-semibold">
        <span className="w-1.5 h-1.5 rounded-full bg-rose-600 flex-shrink-0 inline-block align-middle" />
        <span className="leading-none">Ưu tiên</span>
      </span>
    );
  }
  if (level === "RESULT_REVIEW") {
    return (
      <span className="inline-flex items-center gap-1.5 text-[11px] leading-none text-purple-600 font-medium">
        <span className="w-1.5 h-1.5 rounded-full bg-purple-600 flex-shrink-0 inline-block align-middle" />
        <span className="leading-none">Đọc kết quả CLS</span>
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1.5 text-[11px] leading-none text-blue-600 font-medium">
      <span className="w-1.5 h-1.5 rounded-full bg-blue-600 flex-shrink-0 inline-block align-middle" />
      <span className="leading-none">Khám thông thường</span>
    </span>
  );
}

export function pharmacyStatusLabel(status: QueueEntry["queueStatus"]) {
  switch (status) {
    case "QUEUED":
      return "Đang chờ gọi";
    case "CALLED":
      return "Đã gọi số";
    case "IN_PROGRESS":
      return "Đang đối chiếu / phát thuốc";
    case "COMPLETED":
      return "Hoàn tất";
    case "CANCELLED":
      return "Đã hủy";
    default:
      return status;
  }
}
