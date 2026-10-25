"use client";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { handoverApi, type ClinicalHandover, type HandoverWorkspace } from "@/lib/handover-api";
import HandoverPicker from "@/components/ui/HandoverPicker";
const labels = { PENDING: "Chờ tiếp nhận", ACCEPTED: "Đã tiếp nhận", REJECTED: "Đã từ chối", CANCELLED: "Đã hủy" };
const empty = { reason: "", situation: "", background: "", assessment: "", recommendation: "" };
export default function HandoversPage() {
  const { user } = useAuth();
  const [data, setData] = useState<HandoverWorkspace | null>(null), [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null), [notice, setNotice] = useState<string | null>(null);
  const [caseId, setCaseId] = useState(""), [receiver, setReceiver] = useState("");
  const [form, setForm] = useState(empty), [busy, setBusy] = useState(false);
  const [decision, setDecision] = useState<{ handover: ClinicalHandover; action: "accept" | "reject" | "cancel" } | null>(null), [note, setNote] = useState("");
  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try { const result = await handoverApi.workspace(); setData(result.data); }
    catch (e) { setError(e instanceof Error ? e.message : "Không thể tải danh sách bàn giao."); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  const selected = data?.consultations.find(c => c.id === caseId);
  async function submit(event: React.FormEvent) {
    event.preventDefault(); if (!caseId || !receiver) { setError("Cần chọn ca khám và bác sĩ nhận từ danh sách."); return; }
    setBusy(true); setError(null); setNotice(null);
    try { await handoverApi.request({ consultationId: caseId, toDoctorId: receiver, ...form }); setForm(empty); setReceiver(""); await load(); setNotice("Đã gửi bàn giao. Trách nhiệm vẫn thuộc bác sĩ hiện tại cho tới khi bên nhận xác nhận."); }
    catch (e) { setError(e instanceof Error ? e.message : "Không thể gửi bàn giao."); }
    finally { setBusy(false); }
  }
  async function confirmDecision(event: React.FormEvent) {
    event.preventDefault(); if (!decision) return; setBusy(true); setError(null);
    try { await handoverApi.decide(decision.handover.id, decision.action, note); setDecision(null); setNote(""); await load(); setNotice("Đã lưu quyết định bàn giao."); }
    catch (e) { setError(e instanceof Error ? e.message : "Không thể xử lý bàn giao."); }
    finally { setBusy(false); }
  }
  return <div className="space-y-6 max-w-6xl mx-auto pb-10">
    <div className="flex justify-between items-start gap-4"><div><h1 className="text-2xl font-bold">Bàn giao ca khám</h1><p className="text-sm text-gray-600 mt-2">Bác sĩ nhận xác nhận trách nhiệm trên cùng ca khám. Giữ nguyên phiếu, số thứ tự và tác giả hồ sơ trước đó.</p></div><button onClick={() => void load()} disabled={busy || loading} className="border rounded-lg p-2">Tải lại</button></div>
    {error && <p role="alert" className="border border-red-200 bg-red-50 p-3 rounded-lg text-red-800">{error}</p>}
    {notice && <p role="status" className="bg-green-50 border border-green-200 p-3 rounded-lg">{notice}</p>}
    {loading ? <div aria-label="Đang tải bàn giao" className="h-44 bg-purple-50 rounded-xl animate-pulse" /> : !data ? <p>Không thể tải dữ liệu. Vui lòng thử lại.</p> : <>
      <form onSubmit={submit} className="bg-white border rounded-xl p-5 space-y-4">
        <h2 className="text-lg font-semibold">Yêu cầu bàn giao</h2>
        {data.coordinator && <p className="text-sm text-purple-800 bg-purple-50 p-3 rounded-lg">Bạn có quyền điều phối chuyên môn cùng phòng. Khi gửi thay bác sĩ phụ trách, hệ thống ghi rõ đây là yêu cầu tiếp quản ngoại lệ.</p>}
        <HandoverPicker label="Ca khám cần bàn giao" value={caseId} onChange={value => { setCaseId(value); setReceiver(""); }} options={data.consultations.map(c => ({ value: c.id, label: `${c.doctorName || c.doctorId} · ${c.patientName || c.patientId} · ${c.status}` }))} />
        {selected && <p className="text-sm break-all">Mã ca: {selected.id} · Mã bệnh nhân: {selected.patientId}{selected.doctorId === user?.id && <> · <Link className="text-purple-700 underline" href={`/consultation/${selected.id}`}>Mở ca khám</Link></>}</p>}
        <HandoverPicker label="Bác sĩ nhận bàn giao" value={receiver} onChange={setReceiver} options={data.doctors.filter(d => d.userId !== selected?.doctorId).map(d => ({ value: d.userId, label: `${d.fullName} · ${d.assignedRoomId}` }))} />
        {([['reason', 'Lý do bàn giao'], ['situation', 'S — Tình trạng hiện tại'], ['background', 'B — Tiền sử, dị ứng, xử trí đã làm'], ['assessment', 'A — Đánh giá và kết quả còn chờ'], ['recommendation', 'R — Việc cần tiếp tục, nguy cơ và kế hoạch']] as const).map(([key, label]) => <label key={key} className="block text-sm font-medium">{label}<textarea required maxLength={key === 'reason' ? 2000 : 4000} value={form[key]} onChange={e => setForm({ ...form, [key]: e.target.value })} rows={2} className="block w-full border rounded-lg p-2 mt-1" /></label>)}
        <button disabled={busy || data.consultations.length === 0} className="bg-purple-800 text-white rounded-lg px-4 py-2 disabled:opacity-50">Gửi yêu cầu bàn giao</button>
      </form>
      <section className="space-y-3"><h2 className="text-lg font-semibold">Yêu cầu và lịch sử của bạn</h2>
        {data.handovers.length === 0 ? <p className="bg-white border rounded-xl p-5 text-gray-600">Chưa có yêu cầu bàn giao.</p> : data.handovers.map(h => <article key={h.id} className="bg-white border rounded-xl p-5 space-y-3">
          <div className="flex flex-wrap justify-between gap-2"><h3 className="font-semibold">{h.fromDoctorName} → {h.toDoctorName}</h3><span className="text-sm bg-purple-50 px-3 py-1 rounded-lg">{labels[h.status]}</span></div>
          <p className="font-medium">{h.patientName || "Đối chiếu mã người bệnh bên dưới"}</p>
          <p className="text-sm break-all">Ca {h.consultationId} · Bệnh nhân {h.patientId} · {h.roomId}</p>
          {h.exceptional && <p className="text-sm text-amber-800">Tiếp quản ngoại lệ do điều phối chuyên môn khởi tạo.</p>}
          <dl className="text-sm space-y-2">{([['reason','Lý do'],['situation','S — Tình trạng'],['background','B — Bối cảnh'],['assessment','A — Đánh giá'],['recommendation','R — Kế hoạch']] as const).map(([key,label]) => <div key={key}><dt className="font-semibold">{label}</dt><dd className="whitespace-pre-wrap">{h[key]}</dd></div>)}</dl>
          <p className="text-xs text-gray-500">Gửi lúc {new Date(h.createdAt).toLocaleString('vi-VN')}{h.decidedAt && ` · Xử lý lúc ${new Date(h.decidedAt).toLocaleString('vi-VN')}`}</p>
          {h.decisionNote && <p className="text-sm whitespace-pre-wrap">Ghi nhận: {h.decisionNote}</p>}
          {h.status === 'PENDING' && <div className="flex gap-2 flex-wrap">
            {h.toDoctorId === user?.id && <><button disabled={busy} onClick={() => { setDecision({handover:h,action:'accept'});setNote(''); }} className="bg-purple-800 text-white px-3 py-2 rounded-lg">Tiếp nhận bàn giao</button><button disabled={busy} onClick={() => {setDecision({handover:h,action:'reject'});setNote('');}} className="border px-3 py-2 rounded-lg">Từ chối</button></>}
            {(h.fromDoctorId === user?.id || h.initiatedBy === user?.id || data.coordinator) && <button disabled={busy} onClick={() => {setDecision({handover:h,action:'cancel'});setNote('');}} className="border px-3 py-2 rounded-lg">Hủy yêu cầu</button>}
          </div>}
          {h.status === 'ACCEPTED' && data.consultations.some(c => c.id === h.consultationId && c.doctorId === user?.id) && <Link className="inline-block text-purple-800 underline" href={`/consultation/${h.consultationId}`}>Tiếp tục ca đã nhận</Link>}
        </article>)}
      </section>
    </>}
    {decision && <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4"><form role="dialog" aria-modal="true" aria-label="Xác nhận bàn giao" onSubmit={confirmDecision} className="bg-white rounded-xl p-6 max-w-lg w-full space-y-4">
      <h2 className="font-semibold text-lg">{decision.action === 'accept' ? 'Xác nhận tiếp nhận trách nhiệm' : decision.action === 'reject' ? 'Từ chối bàn giao' : 'Hủy yêu cầu bàn giao'}</h2>
      <p className="text-sm">{decision.action === 'accept' ? 'Xác nhận bạn đã đọc nội dung, trao đổi các vấn đề chưa rõ và đồng ý tiếp tục chăm sóc ca này. Bác sĩ trước sẽ không còn quyền sửa ca sau khi tiếp nhận thành công.' : 'Nhập lý do. Bác sĩ hiện tại tiếp tục phụ trách ca khám.'}</p>
      <label className="block text-sm">Ghi nhận quyết định<textarea required maxLength={2000} value={note} onChange={e => setNote(e.target.value)} className="block w-full border rounded-lg p-2 mt-1" /></label>
      <div className="flex gap-2 justify-end"><button type="button" disabled={busy} onClick={() => setDecision(null)} className="border rounded-lg px-3 py-2">Quay lại</button><button disabled={busy} className="bg-purple-800 text-white rounded-lg px-3 py-2">Xác nhận</button></div>
    </form></div>}
  </div>;
}
