"use client";
import { useCallback, useEffect, useState } from "react";
import { handoverApi, type CoordinatorGrant } from "@/lib/handover-api";
import { directoryApi, type DoctorProfileItem } from "@/lib/directory-api";
import HandoverPicker from "@/components/ui/HandoverPicker";
export default function HandoverPermissionsPage() {
  const [grants, setGrants] = useState<CoordinatorGrant[] | null>(null), [doctors, setDoctors] = useState<DoctorProfileItem[]>([]);
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false), [error, setError] = useState<string | null>(null);
  const [doctorId, setDoctorId] = useState(""), [reason, setReason] = useState("");
  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try { const [g,d] = await Promise.all([handoverApi.grants(), directoryApi.getDoctors()]); setGrants(g.data); setDoctors(d); }
    catch(e) { setError(e instanceof Error ? e.message : "Không thể tải quyền điều phối."); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  async function submit(event: React.FormEvent) {
    event.preventDefault(); if (!doctorId) { setError("Cần chọn bác sĩ từ danh mục."); return; } setBusy(true); setError(null);
    try { await handoverApi.grant(doctorId, reason); setDoctorId(""); setReason(""); await load(); }
    catch(e) { setError(e instanceof Error ? e.message : "Không thể cấp quyền."); }
    finally { setBusy(false); }
  }
  async function revoke(id: string) {
    if (!reason.trim()) { setError("Nhập lý do thu hồi vào ô lý do bên dưới trước khi xác nhận."); return; }
    setBusy(true); setError(null);
    try { await handoverApi.revoke(id, reason); setReason(""); await load(); }
    catch(e) { setError(e instanceof Error ? e.message : "Không thể thu hồi quyền."); }
    finally { setBusy(false); }
  }
  return <div className="max-w-4xl mx-auto space-y-5">
    <h1 className="text-2xl font-bold">Quyền điều phối bàn giao</h1>
    <p className="text-sm text-gray-600">Cấp cho bác sĩ được bệnh viện ủy quyền chuyên môn. Người này có thể khởi tạo yêu cầu tiếp quản ngoại lệ trong cùng khoa/phòng; bác sĩ nhận vẫn phải xác nhận. ADMIN không được ký nhận thay bác sĩ.</p>
    {error && <p role="alert" className="bg-red-50 border border-red-200 text-red-800 rounded-lg p-3">{error}</p>}
    {loading ? <div className="h-40 rounded-xl animate-pulse bg-purple-50" /> : <>
      <form onSubmit={submit} className="bg-white border rounded-xl p-5 space-y-4">
        <HandoverPicker label="Bác sĩ được ủy quyền" value={doctorId} onChange={setDoctorId} options={doctors.filter(d => d.isActive && d.assignedRoomId && d.departmentCode).map(d => ({ value: d.userId, label: `${d.fullName} · ${d.assignedRoomId}` }))} />
        <label className="block text-sm font-medium">Lý do cấp hoặc thu hồi quyền<textarea required maxLength={2000} value={reason} onChange={e => setReason(e.target.value)} className="mt-1 block w-full rounded-lg border p-2" /></label>
        <button disabled={busy} className="bg-purple-800 text-white px-4 py-2 rounded-lg">Cấp quyền điều phối</button>
      </form>
      <section className="space-y-3"><h2 className="font-semibold">Phân quyền đã ghi nhận</h2>{grants === null ? <p>Không tải được danh sách.</p> : grants.length === 0 ? <p>Chưa cấp quyền điều phối cho bác sĩ nào.</p> : grants.map(g => <article key={g.doctorId} className="bg-white border rounded-xl p-4 space-y-2">
        <p className="font-medium">{doctors.find(d => d.userId === g.doctorId)?.fullName || g.doctorId} — {g.active ? 'Đang có quyền' : 'Đã thu hồi'}</p>
        <p className="text-sm break-all">Mã bác sĩ: {g.doctorId} · {doctors.find(d => d.userId === g.doctorId)?.assignedRoomId}</p>
        <p className="text-sm">Cấp lúc {new Date(g.grantedAt).toLocaleString('vi-VN')} · {g.grantReason}</p>
        {g.revokedAt && <p className="text-sm">Thu hồi lúc {new Date(g.revokedAt).toLocaleString('vi-VN')} · {g.revokeReason}</p>}
        {g.active && <button disabled={busy} onClick={() => void revoke(g.doctorId)} className="border border-red-200 text-red-800 rounded-lg px-3 py-2">Thu hồi quyền</button>}
      </article>)}</section>
    </>}
  </div>;
}
