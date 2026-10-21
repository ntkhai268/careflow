"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { api } from "@/lib/api";

type Price = { code: string; name: string; description: string | null; amount: number;
  currency: string; version: number; active: boolean; effectiveAt: string; estimatedMinutes: number };
const empty = { code: "", name: "", description: "", amount: "", estimatedMinutes: "15", active: true, effectiveAt: "", expectedVersion: 0 };

export default function ServicePricingPage() {
  const [prices, setPrices] = useState<Price[]>([]);
  const [form, setForm] = useState(empty);
  const [editing, setEditing] = useState(false), [busy, setBusy] = useState(false), [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    setLoading(true);
    try { setPrices((await api.get<Price[]>("/api/directory/services/admin")).data); setError(null); }
    catch (e) { setError(e instanceof Error ? e.message : "Không thể tải bảng giá."); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const save = async (event: React.FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null);
    try {
      await api.post("/api/directory/services", { ...form,
        amount: Number(form.amount), estimatedMinutes: Number(form.estimatedMinutes),
        effectiveAt: form.effectiveAt ? new Date(form.effectiveAt).toISOString() : null });
      setEditing(false); setForm(empty); await load();
    } catch (e) { setError(e instanceof Error ? e.message : "Không thể công bố bảng giá."); }
    finally { setBusy(false); }
  };
  const edit = (price: Price) => {
    setForm({ code: price.code, name: price.name, description: price.description || "", amount: String(price.amount),
      estimatedMinutes: String(price.estimatedMinutes), active: price.active, effectiveAt: "", expectedVersion: price.version });
    setEditing(true); setError(null);
  };
  const input = "w-full rounded-lg border border-slate-200 p-3 focus:outline-none focus:ring-2 focus:ring-purple-200";

  return <div className="space-y-5 p-4">
    <Link href="/admin/facility" className="text-purple-700">← Cấu hình bệnh viện</Link>
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div><h1 className="text-2xl font-bold">Dịch vụ và bảng giá</h1><p className="text-sm text-slate-600">Mỗi lần công bố tạo phiên bản mới. Báo giá của lịch đã đặt được giữ nguyên.</p></div>
      <button disabled={busy || loading} className="rounded-lg bg-purple-700 px-4 py-2 text-white disabled:opacity-50" onClick={() => { setForm(empty); setEditing(true); setError(null); }}>Thêm dịch vụ</button>
    </div>
    {error && <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-red-700">{error}<button onClick={() => void load()} className="ml-3 underline">Tải lại</button></div>}
    {loading ? <p>Đang tải bảng giá…</p> : <div className="overflow-x-auto rounded-xl border bg-white"><table className="w-full text-left text-sm">
      <thead><tr className="bg-purple-50"><th className="p-3">Dịch vụ</th><th>Giá</th><th>Phiên bản / Hiệu lực</th><th>Trạng thái</th><th /></tr></thead>
      <tbody>{prices.map(price => <tr key={price.code} className="border-t"><td className="p-3"><strong>{price.name}</strong><div className="text-xs text-slate-500">{price.code}</div></td>
        <td>{price.amount.toLocaleString("vi-VN")} {price.currency}</td>
        <td>v{price.version}<div>{new Date(price.effectiveAt).toLocaleString("vi-VN")}</div>{new Date(price.effectiveAt).getTime() > Date.now() && <span className="text-amber-700">Chưa tới thời điểm hiệu lực</span>}</td>
        <td>{price.active ? "Hoạt động" : "Ngừng nhận lịch"}</td><td><button onClick={() => edit(price)} className="rounded-lg p-3 text-purple-700 hover:bg-purple-50">Cập nhật</button></td></tr>)}</tbody>
    </table>{prices.length === 0 && <p className="p-4">Chưa có dịch vụ. Tạo dịch vụ trước khi cho phép đặt lịch.</p>}</div>}
    {editing && <form onSubmit={save} className="space-y-4 rounded-xl border bg-white p-5 shadow-sm">
      <h2 className="font-semibold">{form.expectedVersion ? `Công bố phiên bản mới cho ${form.code}` : "Tạo dịch vụ"}</h2>
      <div className="grid gap-4 md:grid-cols-2">
        <label>Mã dịch vụ<input className={input} required pattern="[A-Z][A-Z0-9_]{2,49}" maxLength={50} disabled={form.expectedVersion > 0 || busy} value={form.code} onChange={e => setForm({ ...form, code: e.target.value.toUpperCase() })} /></label>
        <label>Tên dịch vụ<input className={input} required maxLength={150} disabled={busy} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
        <label>Giá (VND)<input className={input} required type="number" min={0} max={1000000000000} step={1} disabled={busy} value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })} /></label>
        <label>Thời lượng dự kiến (phút)<input className={input} required type="number" min={1} max={1440} step={1} disabled={busy} value={form.estimatedMinutes} onChange={e => setForm({ ...form, estimatedMinutes: e.target.value })} /></label>
        <label>Hiệu lực từ (để trống: ngay khi lưu)<input className={input} type="datetime-local" disabled={busy} value={form.effectiveAt} onChange={e => setForm({ ...form, effectiveAt: e.target.value })} /></label>
        <label className="flex items-center gap-3"><input type="checkbox" disabled={busy} checked={form.active} onChange={e => setForm({ ...form, active: e.target.checked })} />Hoạt động / nhận lịch mới</label>
      </div>
      <label className="block">Mô tả<textarea className={input} maxLength={500} disabled={busy} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></label>
      <div className="flex gap-3"><button disabled={busy} type="submit" className="rounded-lg bg-purple-700 px-4 py-2 text-white disabled:opacity-50">{busy ? "Đang lưu…" : "Công bố bảng giá"}</button>
        <button disabled={busy} type="button" className="rounded-lg border px-4 py-2" onClick={() => setEditing(false)}>Hủy</button></div>
    </form>}
  </div>;
}
