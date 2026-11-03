"use client";

import { QueueEntry } from "@/lib/queue-api";

export const RECEPTION_STATUS_LABELS: Record<string, string> = {
  WAITING: "Đang chờ",
  TICKET_ISSUED: "Chờ tiếp nhận",
  CHECKED_IN: "Đã tiếp nhận",
  QUEUED: "Đang chờ gọi",
  CALLED: "Đã gọi số",
  IN_PROGRESS: "Đang khám",
  COMPLETED: "Hoàn tất",
  MISSED: "Lỡ lượt",
  CANCELLED: "Đã hủy",
};

export const RECEPTION_STATUS_COLORS: Record<string, string> = {
  WAITING: "text-[#A86100]",
  TICKET_ISSUED: "text-[#A86100]",
  CHECKED_IN: "text-[#1D5FA7]",
  QUEUED: "text-[#1D5FA7]",
  CALLED: "text-[#0277A8]",
  IN_PROGRESS: "text-[#087F6A]",
  COMPLETED: "text-[#1B7F4B]",
  MISSED: "text-[#BA1A1A]",
  CANCELLED: "text-[#667985]",
};

export const PRIORITY_LABELS: Record<string, string> = {
  APPOINTMENT: "Đặt lịch",
  NORMAL: "Khám thông thường",
  PRIORITY: "Ưu tiên",
  EMERGENCY: "Khẩn cấp",
  WALK_IN: "Vãng lai",
  RESULT_REVIEW: "Đọc kết quả CLS",
};

export function ReceptionStatusText({ status }: { status: string }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap text-[11px] font-semibold ${
        RECEPTION_STATUS_COLORS[status] || "text-[#667985]"
      }`}
    >
      <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-current" />
      {RECEPTION_STATUS_LABELS[status] || status}
    </span>
  );
}

export function QueueStatusDot({ status }: { status: string }) {
  const config: Record<string, { color: string; label: string; textClass: string }> = {
    WAITING: { color: "#A86100", label: "Chờ khám", textClass: "text-[#A86100] font-medium" },
    PENDING: { color: "#A86100", label: "Chờ khám", textClass: "text-[#A86100] font-medium" },
    CHECKED_IN: { color: "#1D5FA7", label: "Đã check-in", textClass: "text-[#1D5FA7] font-medium" },
    QUEUED: { color: "#0277A8", label: "Đang chờ gọi", textClass: "text-[#0277A8] font-medium" },
    CALLED: { color: "#A86100", label: "Đã gọi số", textClass: "text-[#A86100] font-semibold" },
    IN_PROGRESS: { color: "#087F6A", label: "Đang khám", textClass: "text-[#087F6A] font-semibold" },
    CONFIRMED: { color: "#1D5FA7", label: "Đã tiếp nhận", textClass: "text-[#1D5FA7] font-medium" },
    COMPLETED: { color: "#1B7F4B", label: "Hoàn tất", textClass: "text-[#1B7F4B] font-medium" },
    MISSED: { color: "#BA1A1A", label: "Lỡ lượt", textClass: "text-[#BA1A1A] font-medium" },
    CANCELLED: { color: "#BA1A1A", label: "Đã huỷ", textClass: "text-[#BA1A1A] font-medium" },
  };
  const cfg = config[status] || {
    color: "#667985",
    label: status,
    textClass: "text-[#4D626E] font-medium",
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
      <span className="inline-flex items-center gap-1.5 text-[10px] leading-none text-[#BA1A1A] font-semibold bg-[#FFDAD6]/60 px-2 py-0.5 rounded-full">
        <span className="w-1.5 h-1.5 rounded-full bg-[#BA1A1A] flex-shrink-0 inline-block align-middle" />
        <span className="leading-none">Ưu tiên</span>
      </span>
    );
  }
  if (level === "RESULT_REVIEW") {
    return (
      <span className="inline-flex items-center gap-1.5 text-[10px] leading-none text-[#087F6A] font-semibold bg-[#D9F3EC] px-2 py-0.5 rounded-full">
        <span className="w-1.5 h-1.5 rounded-full bg-[#087F6A] flex-shrink-0 inline-block align-middle" />
        <span className="leading-none">Đọc kết quả CLS</span>
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1.5 text-[10px] leading-none text-[#00577E] font-semibold bg-[#DDF3FC] px-2 py-0.5 rounded-full">
      <span className="w-1.5 h-1.5 rounded-full bg-[#0277A8] flex-shrink-0 inline-block align-middle" />
      <span className="leading-none">Khám thông thường</span>
    </span>
  );
}

export function pharmacyStatusLabel(status: QueueEntry["queueStatus"] | string) {
  switch (status) {
    case "WAITING":
    case "TICKET_ISSUED":
      return "Chờ tiếp nhận đơn";
    case "CHECKED_IN":
      return "Chờ gọi nhận thuốc";
    case "QUEUED":
      return "Đang chờ gọi";
    case "CALLED":
      return "Đã gọi tới quầy";
    case "IN_PROGRESS":
      return "Đang đối chiếu & cấp phát";
    case "COMPLETED":
      return "Đã phát thuốc";
    case "MISSED":
      return "Lỡ lượt nhận thuốc";
    case "CANCELLED":
      return "Đã hủy";
    default:
      return status;
  }
}
