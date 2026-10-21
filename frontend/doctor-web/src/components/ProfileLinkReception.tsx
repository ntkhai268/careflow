"use client";
import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '@/lib/api';
interface LinkRequest { id: string; patientId: string; patientName: string; dateOfBirth: string | null; roomId: string; status: string }
export default function ProfileLinkReception({ patientId, roomId }: { patientId?: string; roomId: string }) {
  const [token, setToken] = useState(''), [reference, setReference] = useState(''), [checked, setChecked] = useState(false);
  const [items, setItems] = useState<LinkRequest[]>([]), [busy, setBusy] = useState(false), [error, setError] = useState(''), [message, setMessage] = useState('');
  const command = useRef<{ fingerprint: string; id: string } | null>(null), generation = useRef(0);
  const load = useCallback(async () => { const gen = ++generation.current; if (!roomId) return; try { const r = await api.get<LinkRequest[]>(`/api/patients/profile-links/reception?roomId=${encodeURIComponent(roomId)}`); if (gen === generation.current) setItems(r.data); } catch (e) { if (gen === generation.current) setError(e instanceof Error ? e.message : 'Không thể tải yêu cầu.'); } }, [roomId]);
  useEffect(() => { setItems([]); setToken(''); setReference(''); setChecked(false); setMessage(''); setError(''); void load(); return () => { ++generation.current; }; }, [load]);
  const act = async (id: string, action: 'reject' | 'revoke') => {
    if (!window.confirm(action === 'revoke' ? 'Thu hồi quyền truy cập của tài khoản vào hồ sơ này? Dữ liệu khám vẫn được lưu.' : 'Từ chối yêu cầu này? Hồ sơ chưa được liên kết sẽ tiếp tục chưa có chủ tài khoản.')) return;
    setBusy(true);setError('');try { await api.post(`/api/patients/profile-links/${id}/${action}`, {}); await load(); } catch (e) { setError(e instanceof Error ? e.message : 'Không thể cập nhật.'); } finally { setBusy(false); }
  };
  const labels: Record<string,string> = { PENDING_OWNER: 'Chờ bệnh nhân xác nhận trên điện thoại', LINKED: 'Đã liên kết', DUPLICATE_REVIEW: 'Hồ sơ trùng — chưa cấp quyền', REJECTED: 'Đã từ chối', EXPIRED: 'Đã hết hạn', REVOKED: 'Đã thu hồi' };
  return <div className="space-y-3 rounded-xl border bg-slate-50 p-4"><h3 className="font-semibold">Liên kết tài khoản sau đối chiếu trực tiếp</h3><p className="text-sm text-slate-600">Bệnh nhân mở Tài khoản → Liên kết hồ sơ tại viện, đưa mã dùng một lần và xác nhận yêu cầu trên điện thoại. Thông tin hồ sơ phải khớp kết quả đối chiếu giấy tờ đã được xác nhận; không liên kết cho người đại diện.</p>
    {error && <p role="alert" className="text-red-700">{error}</p>}{message && <p role="status">{message}</p>}
    {patientId && <form className="space-y-3" onSubmit={async e => { e.preventDefault(); setBusy(true);setError('');setMessage(''); const body = { patientId, roomId, token: token.trim(), reference: reference.trim(), originalSeen: checked }; const fingerprint = JSON.stringify(body); if (command.current?.fingerprint !== fingerprint) command.current = { fingerprint, id: crypto.randomUUID() }; try { await api.post('/api/patients/profile-links', { requestId: command.current!.id, ...body }); setToken('');setChecked(false);setMessage('Đã gửi yêu cầu. Bệnh nhân cần xác nhận trên điện thoại trước khi nhận quyền hồ sơ.'); await load(); } catch (e) { setError(e instanceof Error ? e.message : 'Không thể lập yêu cầu.'); } finally { setBusy(false); } }}>
      <label className="block text-sm">Mã từ điện thoại bệnh nhân<input required autoComplete="off" spellCheck={false} pattern="[A-Za-z0-9_-]{43}" value={token} onChange={e => setToken(e.target.value)} className="block w-full rounded-lg border p-2" /></label>
      <label className="block text-sm">Mã quầy / biên bản (không nhập thông tin cá nhân)<input required minLength={3} maxLength={80} value={reference} onChange={e => setReference(e.target.value)} className="block w-full rounded-lg border p-2" /></label>
      <label className="block text-sm"><input required type="checkbox" checked={checked} onChange={e => setChecked(e.target.checked)} /> Đã kiểm tra hồ sơ tiếp nhận gốc và người yêu cầu tại quầy.</label>
      <button disabled={busy || !roomId} className="rounded-lg bg-purple-800 p-2 text-white disabled:opacity-50">Gửi yêu cầu liên kết hồ sơ đang chọn</button>
    </form>}
    <button type="button" disabled={busy} onClick={() => void load()} className="text-sm text-purple-800 underline">Tải lại yêu cầu tại phòng</button>
    <details><summary className="cursor-pointer text-sm font-medium">Yêu cầu liên kết tại phòng ({items.length})</summary>
    {items.map(item => <div key={item.id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-white p-3 text-sm"><div>{item.patientName} · {item.dateOfBirth || 'Chưa có ngày sinh'}<p>{labels[item.status] || item.status}</p>{item.status === 'DUPLICATE_REVIEW' && <p>Không gộp tự động. Kiểm tra hồ sơ hiện có; từ chối yêu cầu trùng nếu không đủ điều kiện.</p>}</div>{['PENDING_OWNER','DUPLICATE_REVIEW'].includes(item.status) && <button type="button" disabled={busy} onClick={() => void act(item.id,'reject')} className="rounded-lg border p-2">Từ chối yêu cầu</button>}{item.status === 'LINKED' && <button type="button" disabled={busy} onClick={() => void act(item.id,'revoke')} className="rounded-lg border p-2">Thu hồi liên kết</button>}</div>)}
    </details>
  </div>;
}
