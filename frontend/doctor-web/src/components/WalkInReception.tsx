"use client";
import { useEffect, useRef, useState } from 'react';
import HandoverPicker from './ui/HandoverPicker';
import ProfileLinkReception from './ProfileLinkReception';
import { api } from '@/lib/api';
import { DepartmentItem, directoryApi } from '@/lib/directory-api';
import { receptionApi, ReceptionPatient } from '@/lib/reception-api';
export default function WalkInReception({ roomId, departmentCode, onIssued }: { roomId: string; departmentCode: string; onIssued: () => void }) {
  const [departments, setDepartments] = useState<DepartmentItem[]>([]), [services, setServices] = useState<{ code: string; name: string; active: boolean }[]>([]);
  const [query, setQuery] = useState(''), [results, setResults] = useState<ReceptionPatient[]>([]), [selected, setSelected] = useState<ReceptionPatient | null>(null), [service, setService] = useState('');
  const [creating, setCreating] = useState(false), [name, setName] = useState(''), [dob, setDob] = useState(''), [gender, setGender] = useState(''), [phone, setPhone] = useState(''), [card, setCard] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [message, setMessage] = useState('');
  const command = useRef<{ fingerprint: string; key: string } | null>(null);
  const keyFor = (body: unknown) => { const fingerprint = JSON.stringify(body); if (command.current?.fingerprint !== fingerprint) command.current = { fingerprint, key: crypto.randomUUID() }; return command.current!.key; };
  useEffect(() => { let active = true; void Promise.all([directoryApi.getDepartments(), api.get<{ code: string; name: string; active: boolean }[]>('/api/directory/services')]).then(([ds, ss]) => { if (active) { setDepartments(ds); setServices(ss.data.filter(s => s.active)); } }).catch(e => { if (active) setError(e.message); }); return () => { active = false; }; }, []);
  useEffect(() => { setResults([]); setSelected(null); setQuery(''); setError(''); setMessage(''); setCreating(false); }, [roomId]);
  const perform = async (action: () => Promise<void>) => { if (busy) return; setBusy(true); setError(''); setMessage(''); try { await action(); } catch (e) { setError(e instanceof Error ? e.message : 'Không thể tiếp nhận.'); } finally { setBusy(false); } };
  const department = departments.find(d => d.code === departmentCode && d.isActive !== false);
  return <section className="space-y-4 rounded-xl border bg-white p-5"><h2 className="font-bold">Tiếp nhận không hẹn trước</h2><p className="text-sm text-slate-600">{department?.name || 'Chọn phòng được phân công'} · {roomId || 'Chưa chọn phòng'}. Chỉ tra cứu thông tin hành chính tối thiểu.</p>
    {error && <p role="alert" className="text-red-700">{error}</p>}{message && <p role="status" className="text-green-800">{message}</p>}
    <form className="flex gap-2" onSubmit={e => { e.preventDefault(); void perform(async () => { const r = await receptionApi.search(roomId, query.trim()); setResults(r.data); setSelected(null); if (!r.data.length) setMessage('Không có hồ sơ phù hợp. Có thể tạo hồ sơ tiếp nhận mới.'); }); }}><input required minLength={3} maxLength={80} aria-label="Tìm hồ sơ tiếp nhận" placeholder="Họ tên, số điện thoại hoặc căn cước" value={query} onChange={e => setQuery(e.target.value)} className="w-full rounded-lg border p-2" /><button disabled={busy || !roomId} className="shrink-0 rounded-lg border p-2">Tìm hồ sơ</button></form>
    {!!results.length && <HandoverPicker label="Hồ sơ tìm thấy" options={results.map(p => ({ value: p.id, label: `${p.fullName} · ${p.dateOfBirth || 'Chưa có ngày sinh'} · ${p.maskedPhone || ''}${p.userId ? ' · Có tài khoản' : ''}` }))} value={selected?.id || ''} onChange={id => { setSelected(results.find(p => p.id === id) || null); setCreating(false); }} />}
    <button type="button" className="text-sm text-purple-800 underline" onClick={() => setCreating(!creating)}>Tạo hồ sơ mới không có tài khoản</button>
    {creating && <form className="grid gap-3 rounded-lg bg-slate-50 p-4 md:grid-cols-2" onSubmit={e => { e.preventDefault(); const body = { roomId, fullName: name.trim(), dateOfBirth: dob || undefined, gender: gender || undefined, phone: phone.trim() || undefined, idCardNumber: card.trim() || undefined }; void perform(async () => { const r = await receptionApi.create(body, keyFor(body)); setSelected(r.data); setCreating(false); setMessage('Đã tạo hồ sơ tiếp nhận. Chọn dịch vụ và cấp số để vào hàng đợi.'); }); }}>
      <label>Họ tên<input required maxLength={100} value={name} onChange={e => setName(e.target.value)} className="block w-full rounded-lg border p-2" /></label><label>Ngày sinh<input type="date" value={dob} onChange={e => setDob(e.target.value)} className="block w-full rounded-lg border p-2" /></label>
      <HandoverPicker label="Giới tính" options={[{ value: 'MALE', label: 'Nam' }, { value: 'FEMALE', label: 'Nữ' }, { value: 'OTHER', label: 'Khác' }]} value={gender} onChange={setGender} />
      <label>Điện thoại (nếu có)<input type="tel" maxLength={15} value={phone} onChange={e => setPhone(e.target.value)} className="block w-full rounded-lg border p-2" /></label><label>Căn cước (nếu có)<input inputMode="numeric" pattern="[0-9]{12}" maxLength={12} value={card} onChange={e => setCard(e.target.value)} className="block w-full rounded-lg border p-2" /></label>
      <button disabled={busy || !roomId} className="rounded-lg border p-2">Tạo hồ sơ</button>
    </form>}
    {selected && <p className="rounded-lg bg-purple-50 p-3 text-sm">Đã chọn: <strong>{selected.fullName}</strong> · {selected.dateOfBirth || 'Chưa có ngày sinh'}</p>}
    <HandoverPicker label="Dịch vụ khám" value={service} options={services.map(s => ({ value: s.code, label: s.name }))} onChange={setService} />
    <button type="button" disabled={busy || !selected || !department?.id || !service || !roomId} className="rounded-lg bg-purple-800 px-4 py-3 text-white disabled:opacity-50" onClick={() => { if (!selected || !department?.id) return; const body = { patientId: selected.id, departmentId: department.id, roomId, serviceCode: service, priorityLevel: 'WALK_IN' as const }; void perform(async () => { const r = await receptionApi.intake(body, keyFor(body)); setMessage(`Đã cấp số ${r.data.queueNumber} tại ${r.data.roomCode}.`); onIssued(); }); }}>Cấp số khám không hẹn</button>
    <ProfileLinkReception roomId={roomId} patientId={selected?.userId ? undefined : selected?.id} />
  </section>;
}
