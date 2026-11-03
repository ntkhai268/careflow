"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";

interface LinkRequest {
  id: string;
  patientId: string;
  patientName: string;
  dateOfBirth: string | null;
  roomId: string;
  status: string;
}

export default function ProfileLinkReception({
  patientId,
  roomId,
}: {
  patientId?: string;
  roomId: string;
}) {
  const [token, setToken] = useState("");
  const [reference, setReference] = useState("");
  const [checked, setChecked] = useState(false);
  const [items, setItems] = useState<LinkRequest[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const command = useRef<{ fingerprint: string; id: string } | null>(null);
  const generation = useRef(0);

  const load = useCallback(async () => {
    const gen = ++generation.current;
    if (!roomId) return;
    try {
      const r = await api.get<LinkRequest[]>(
        `/api/patients/profile-links/reception?roomId=${encodeURIComponent(roomId)}`
      );
      if (gen === generation.current) setItems(r.data);
    } catch (e) {
      if (gen === generation.current) {
        setError(e instanceof Error ? e.message : "Không thể tải yêu cầu.");
      }
    }
  }, [roomId]);

  useEffect(() => {
    setItems([]);
    setToken("");
    setReference("");
    setChecked(false);
    setMessage("");
    setError("");
    void load();
    return () => {
      ++generation.current;
    };
  }, [load]);

  const act = async (id: string, action: "reject" | "revoke") => {
    if (
      !window.confirm(
        action === "revoke"
          ? "Thu hồi quyền truy cập của tài khoản vào hồ sơ này? Dữ liệu khám vẫn được lưu."
          : "Từ chối yêu cầu này? Hồ sơ chưa được liên kết sẽ tiếp tục chưa có chủ tài khoản."
      )
    )
      return;
    setBusy(true);
    setError("");
    try {
      await api.post(`/api/patients/profile-links/${id}/${action}`, {});
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Không thể cập nhật.");
    } finally {
      setBusy(false);
    }
  };

  const labels: Record<string, string> = {
    PENDING_OWNER: "Chờ bệnh nhân xác nhận trên điện thoại",
    LINKED: "Đã liên kết",
    DUPLICATE_REVIEW: "Hồ sơ trùng — chưa cấp quyền",
    REJECTED: "Đã từ chối",
    EXPIRED: "Đã hết hạn",
    REVOKED: "Đã thu hồi",
  };

  return (
    <div className="space-y-3 rounded-xl border border-[#D5E0E6] bg-[#F5F8FA] p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-xs font-extrabold text-[#17242D]">
          Liên kết tài khoản sau đối chiếu trực tiếp
        </h3>
        <button
          type="button"
          disabled={busy}
          onClick={() => void load()}
          className="text-xs font-bold text-[#0277A8] hover:underline"
        >
          Tải lại yêu cầu tại phòng
        </button>
      </div>
      <p className="text-[11px] leading-relaxed text-[#4D626E]">
        Bệnh nhân mở Tài khoản → Liên kết hồ sơ tại viện, đưa mã dùng một lần và xác nhận yêu cầu
        trên điện thoại. Thông tin hồ sơ phải khớp kết quả đối chiếu giấy tờ đã được xác nhận;
        không liên kết cho người đại diện.
      </p>

      {error && (
        <p role="alert" className="text-xs font-semibold text-[#BA1A1A]">
          {error}
        </p>
      )}
      {message && (
        <p role="status" className="text-xs font-semibold text-[#1B7F4B]">
          {message}
        </p>
      )}

      {patientId && (
        <form
          className="space-y-3 rounded-xl border border-[#D5E0E6] bg-white p-3.5"
          onSubmit={async (e) => {
            e.preventDefault();
            setBusy(true);
            setError("");
            setMessage("");
            const body = {
              patientId,
              roomId,
              token: token.trim(),
              reference: reference.trim(),
              originalSeen: checked,
            };
            const fingerprint = JSON.stringify(body);
            if (command.current?.fingerprint !== fingerprint) {
              command.current = { fingerprint, id: crypto.randomUUID() };
            }
            try {
              await api.post("/api/patients/profile-links", {
                requestId: command.current!.id,
                ...body,
              });
              setToken("");
              setChecked(false);
              setMessage(
                "Đã gửi yêu cầu. Bệnh nhân cần xác nhận trên điện thoại trước khi nhận quyền hồ sơ."
              );
              await load();
            } catch (e) {
              setError(e instanceof Error ? e.message : "Không thể lập yêu cầu.");
            } finally {
              setBusy(false);
            }
          }}
        >
          <label className="block space-y-1 text-xs font-semibold text-[#4D626E]">
            Mã từ điện thoại bệnh nhân
            <input
              required
              autoComplete="off"
              spellCheck={false}
              pattern="[A-Za-z0-9_-]{43}"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              className="block h-10 w-full rounded-xl border border-[#D5E0E6] bg-white px-3 font-mono text-xs text-[#17242D] outline-none focus:border-[#0277A8]"
            />
          </label>
          <label className="block space-y-1 text-xs font-semibold text-[#4D626E]">
            Mã quầy / biên bản (không nhập thông tin cá nhân)
            <input
              required
              minLength={3}
              maxLength={80}
              value={reference}
              onChange={(e) => setReference(e.target.value)}
              className="block h-10 w-full rounded-xl border border-[#D5E0E6] bg-white px-3 text-xs text-[#17242D] outline-none focus:border-[#0277A8]"
            />
          </label>
          <label className="flex items-center gap-2 text-xs text-[#17242D]">
            <input
              required
              type="checkbox"
              checked={checked}
              onChange={(e) => setChecked(e.target.checked)}
              className="h-4 w-4 accent-[#0277A8]"
            />
            Đã kiểm tra hồ sơ tiếp nhận gốc và người yêu cầu tại quầy.
          </label>
          <button
            disabled={busy || !roomId}
            className="rounded-xl bg-[#0277A8] px-4 py-2 text-xs font-bold text-white transition-colors hover:bg-[#00577E] disabled:opacity-50"
          >
            Gửi yêu cầu liên kết hồ sơ đang chọn
          </button>
        </form>
      )}

      <details className="text-xs text-[#4D626E]">
        <summary className="cursor-pointer font-bold text-[#17242D]">
          Yêu cầu liên kết tại phòng ({items.length})
        </summary>
        <div className="mt-2 space-y-2">
          {items.map((item) => (
            <div
              key={item.id}
              className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#D5E0E6] bg-white p-3 text-xs"
            >
              <div>
                <p className="font-bold text-[#17242D]">
                  {item.patientName} · {item.dateOfBirth || "Chưa có ngày sinh"}
                </p>
                <p className="mt-0.5 text-[#4D626E]">{labels[item.status] || item.status}</p>
                {item.status === "DUPLICATE_REVIEW" && (
                  <p className="mt-1 text-[#A86100]">
                    Không gộp tự động. Kiểm tra hồ sơ hiện có; từ chối yêu cầu trùng nếu không đủ
                    điều kiện.
                  </p>
                )}
              </div>
              {["PENDING_OWNER", "DUPLICATE_REVIEW"].includes(item.status) && (
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => void act(item.id, "reject")}
                  className="rounded-lg border border-[#D5E0E6] px-3 py-1.5 text-xs font-bold text-[#4D626E] hover:border-[#BA1A1A] hover:text-[#BA1A1A]"
                >
                  Từ chối yêu cầu
                </button>
              )}
              {item.status === "LINKED" && (
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => void act(item.id, "revoke")}
                  className="rounded-lg border border-[#D5E0E6] px-3 py-1.5 text-xs font-bold text-[#4D626E] hover:border-[#BA1A1A] hover:text-[#BA1A1A]"
                >
                  Thu hồi liên kết
                </button>
              )}
            </div>
          ))}
        </div>
      </details>
    </div>
  );
}
