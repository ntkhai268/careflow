"use client";
import { useEffect, useState } from "react";
import { HospitalCheckInConfigRequest, queueApi } from "@/lib/queue-api";
export default function HospitalGeofenceSettings() {
  const [data, setData] = useState<HospitalCheckInConfigRequest | null>(null);
  const [error, setError] = useState(""), [message, setMessage] = useState(""), [busy, setBusy] = useState(false);
  useEffect(() => { let active = true; void queueApi.getHospitalCheckInConfig().then(r => { if (active) setData(r.data); }).catch(e => { if (active) setError(e.message); }); return () => { active = false; }; }, []);
  return <section className="space-y-4 rounded-xl border bg-white p-5"><h2 className="text-lg font-bold">Khu vực check-in bệnh viện</h2>
    <p className="text-sm text-slate-600">Check-in trên điện thoại cần QR đúng ngày và vị trí trong bán kính cho phép. Thay đổi áp dụng cho lần check-in tiếp theo.</p>
    {error && <p role="alert" className="text-red-700">{error}</p>}{message && <p role="status">{message}</p>}
    {!data ? <p>Chưa tải được cấu hình.</p> : <form className="grid gap-4 md:grid-cols-2" onSubmit={async e => { e.preventDefault(); setBusy(true); setError(""); setMessage(""); try { const r = await queueApi.updateHospitalCheckInConfig(data); setData(r.data); setMessage("Đã lưu khu vực check-in."); } catch (e) { setError(e instanceof Error ? e.message : "Không thể lưu."); } finally { setBusy(false); } }}>
      <label>Mã cơ sở<input required value={data.siteId} onChange={e => setData({ ...data, siteId: e.target.value })} className="block w-full rounded-lg border p-2" /></label>
      <label>Tên cơ sở<input required value={data.facilityName} onChange={e => setData({ ...data, facilityName: e.target.value })} className="block w-full rounded-lg border p-2" /></label>
      {([['latitude', 'Vĩ độ', -90, 90], ['longitude', 'Kinh độ', -180, 180], ['radiusMeters', 'Bán kính (m)', 1, 5000], ['maxAccuracyMeters', 'Sai số GPS tối đa (m)', 1, 1000]] as const).map(([key, label, min, max]) => <label key={key}>{label}<input required type="number" step="any" min={min} max={max} value={data[key]} onChange={e => setData({ ...data, [key]: Number(e.target.value) })} className="block w-full rounded-lg border p-2" /></label>)}
      <button disabled={busy} className="rounded-lg bg-purple-800 p-3 text-white disabled:opacity-50">{busy ? "Đang lưu…" : "Lưu khu vực check-in"}</button>
    </form>}
  </section>;
}
