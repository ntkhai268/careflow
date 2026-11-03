"use client";

import { useEffect, useRef, useState } from "react";
import HandoverPicker from "./ui/HandoverPicker";
import ProfileLinkReception from "./ProfileLinkReception";
import { api } from "@/lib/api";
import { DepartmentItem, directoryApi } from "@/lib/directory-api";
import { receptionApi, ReceptionPatient } from "@/lib/reception-api";

export default function WalkInReception({
  roomId,
  departmentCode,
  onIssued,
}: {
  roomId: string;
  departmentCode: string;
  onIssued: () => void;
}) {
  const [departments, setDepartments] = useState<DepartmentItem[]>([]);
  const [services, setServices] = useState<{ code: string; name: string; active: boolean }[]>([]);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<ReceptionPatient[]>([]);
  const [selected, setSelected] = useState<ReceptionPatient | null>(null);
  const [service, setService] = useState("");
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const [dob, setDob] = useState("");
  const [gender, setGender] = useState("");
  const [phone, setPhone] = useState("");
  const [card, setCard] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const command = useRef<{ fingerprint: string; key: string } | null>(null);
  const keyFor = (body: unknown) => {
    const fingerprint = JSON.stringify(body);
    if (command.current?.fingerprint !== fingerprint) {
      command.current = { fingerprint, key: crypto.randomUUID() };
    }
    return command.current!.key;
  };

  useEffect(() => {
    let active = true;
    void Promise.all([
      directoryApi.getDepartments(),
      api.get<{ code: string; name: string; active: boolean }[]>("/api/directory/services"),
    ])
      .then(([ds, ss]) => {
        if (active) {
          setDepartments(ds);
          setServices(ss.data.filter((s) => s.active));
        }
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    setResults([]);
    setSelected(null);
    setQuery("");
    setError("");
    setMessage("");
    setCreating(false);
  }, [roomId]);

  const perform = async (action: () => Promise<void>) => {
    if (busy) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await action();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Không thể tiếp nhận.");
    } finally {
      setBusy(false);
    }
  };

  const department = departments.find((d) => d.code === departmentCode && d.isActive !== false);

  return (
    <section className="space-y-4 rounded-xl border border-[#D5E0E6] bg-white p-5 shadow-xs">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 className="text-[15px] font-extrabold text-[#17242D]">Tiếp nhận không hẹn trước</h2>
          <p className="mt-0.5 text-xs text-[#667985]">
            <span className="font-semibold text-[#00577E]">
              {department?.name || "Chọn phòng được phân công"}
            </span>{" "}
            · <span className="font-mono font-bold text-[#17242D]">{roomId || "Chưa chọn phòng"}</span>
            . Chỉ tra cứu thông tin hành chính tối thiểu.
          </p>
        </div>
        <button
          type="button"
          className="rounded-lg border border-[#8BD2EE] bg-[#DDF3FC]/60 px-3 py-1.5 text-xs font-bold text-[#00577E] transition-colors hover:bg-[#DDF3FC]"
          onClick={() => setCreating(!creating)}
        >
          {creating ? "Đóng form tạo hồ sơ" : "+ Tạo hồ sơ mới không có tài khoản"}
        </button>
      </div>

      {error && (
        <p
          role="alert"
          className="rounded-lg border border-[#BA1A1A]/30 bg-[#BA1A1A]/10 px-3 py-2 text-xs font-semibold text-[#BA1A1A]"
        >
          {error}
        </p>
      )}
      {message && (
        <p
          role="status"
          className="rounded-lg border border-[#1B7F4B]/30 bg-[#1B7F4B]/10 px-3 py-2 text-xs font-semibold text-[#1B7F4B]"
        >
          {message}
        </p>
      )}

      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          void perform(async () => {
            const r = await receptionApi.search(roomId, query.trim());
            setResults(r.data);
            setSelected(null);
            if (!r.data.length) {
              setMessage("Không có hồ sơ phù hợp. Có thể tạo hồ sơ tiếp nhận mới.");
            }
          });
        }}
      >
        <input
          required
          minLength={3}
          maxLength={80}
          aria-label="Tìm hồ sơ tiếp nhận"
          placeholder="Nhập họ tên, số điện thoại hoặc số CCCD (tối thiểu 3 ký tự)..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="h-11 w-full rounded-xl border border-[#D5E0E6] bg-white px-3.5 text-xs text-[#17242D] outline-none placeholder:text-[#667985] focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15"
        />
        <button
          disabled={busy || !roomId}
          className="h-11 shrink-0 rounded-xl border border-[#D5E0E6] bg-[#F5F8FA] px-4 text-xs font-bold text-[#17242D] transition-colors hover:border-[#0277A8] hover:bg-[#DDF3FC] hover:text-[#00577E] disabled:opacity-50"
        >
          Tìm hồ sơ
        </button>
      </form>

      {!!results.length && (
        <HandoverPicker
          label="Hồ sơ tìm thấy"
          options={results.map((p) => ({
            value: p.id,
            label: `${p.fullName} · ${p.dateOfBirth || "Chưa có ngày sinh"} · ${
              p.maskedPhone || ""
            }${p.userId ? " · Có tài khoản" : ""}`,
          }))}
          value={selected?.id || ""}
          onChange={(id) => {
            setSelected(results.find((p) => p.id === id) || null);
            setCreating(false);
          }}
        />
      )}

      {creating && (
        <form
          className="grid gap-3 rounded-xl border border-[#D5E0E6] bg-[#F5F8FA] p-4 md:grid-cols-2"
          onSubmit={(e) => {
            e.preventDefault();
            const body = {
              roomId,
              fullName: name.trim(),
              dateOfBirth: dob || undefined,
              gender: gender || undefined,
              phone: phone.trim() || undefined,
              idCardNumber: card.trim() || undefined,
            };
            void perform(async () => {
              const r = await receptionApi.create(body, keyFor(body));
              setSelected(r.data);
              setCreating(false);
              setMessage("Đã tạo hồ sơ tiếp nhận. Chọn dịch vụ và cấp số để vào hàng đợi.");
            });
          }}
        >
          <label className="space-y-1 text-xs font-semibold text-[#4D626E]">
            Họ tên
            <input
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="block h-10 w-full rounded-xl border border-[#D5E0E6] bg-white px-3 text-xs text-[#17242D] outline-none focus:border-[#0277A8]"
            />
          </label>
          <label className="space-y-1 text-xs font-semibold text-[#4D626E]">
            Ngày sinh
            <input
              type="date"
              value={dob}
              onChange={(e) => setDob(e.target.value)}
              className="block h-10 w-full rounded-xl border border-[#D5E0E6] bg-white px-3 text-xs text-[#17242D] outline-none focus:border-[#0277A8]"
            />
          </label>
          <HandoverPicker
            label="Giới tính"
            options={[
              { value: "MALE", label: "Nam" },
              { value: "FEMALE", label: "Nữ" },
              { value: "OTHER", label: "Khác" },
            ]}
            value={gender}
            onChange={setGender}
          />
          <label className="space-y-1 text-xs font-semibold text-[#4D626E]">
            Điện thoại (nếu có)
            <input
              type="tel"
              maxLength={15}
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              className="block h-10 w-full rounded-xl border border-[#D5E0E6] bg-white px-3 text-xs text-[#17242D] outline-none focus:border-[#0277A8]"
            />
          </label>
          <label className="space-y-1 text-xs font-semibold text-[#4D626E]">
            Căn cước (nếu có)
            <input
              inputMode="numeric"
              pattern="[0-9]{12}"
              maxLength={12}
              value={card}
              onChange={(e) => setCard(e.target.value)}
              className="block h-10 w-full rounded-xl border border-[#D5E0E6] bg-white px-3 font-mono text-xs text-[#17242D] outline-none focus:border-[#0277A8]"
            />
          </label>
          <div className="flex items-end">
            <button
              disabled={busy || !roomId}
              className="h-10 w-full rounded-xl bg-[#087F6A] px-4 text-xs font-bold text-white transition-colors hover:bg-[#066554] disabled:opacity-50"
            >
              Tạo hồ sơ
            </button>
          </div>
        </form>
      )}

      {selected && (
        <p className="rounded-xl border border-[#8BD2EE] bg-[#DDF3FC]/60 px-3.5 py-2.5 text-xs text-[#00577E]">
          Đã chọn: <strong>{selected.fullName}</strong> ·{" "}
          {selected.dateOfBirth || "Chưa có ngày sinh"}
        </p>
      )}

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
        <HandoverPicker
          label="Dịch vụ khám"
          value={service}
          options={services.map((s) => ({ value: s.code, label: s.name }))}
          onChange={setService}
        />
        <button
          type="button"
          disabled={busy || !selected || !department?.id || !service || !roomId}
          className="h-11 rounded-xl bg-[#0277A8] px-5 text-xs font-bold text-white shadow-xs transition-colors hover:bg-[#00577E] disabled:cursor-not-allowed disabled:opacity-50"
          onClick={() => {
            if (!selected || !department?.id) return;
            const body = {
              patientId: selected.id,
              departmentId: department.id,
              roomId,
              serviceCode: service,
              priorityLevel: "WALK_IN" as const,
            };
            void perform(async () => {
              const r = await receptionApi.intake(body, keyFor(body));
              setMessage(`Đã cấp số ${r.data.queueNumber} tại ${r.data.roomCode}.`);
              onIssued();
            });
          }}
        >
          Cấp số khám không hẹn
        </button>
      </div>

      <ProfileLinkReception
        roomId={roomId}
        patientId={selected?.userId ? undefined : selected?.id}
      />
    </section>
  );
}
