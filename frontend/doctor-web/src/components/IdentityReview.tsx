"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/AuthContext";
import AccessDenied from "@/components/AccessDenied";
import { getErrorMessage } from "@/lib/error-utils";

type Summary = { id: string; status: string; maskedDocumentNumber: string; createdAt: string };
type QueueItem = { check: Summary; username: string };
type Detail = { check: Summary; fields: { documentNumber: string; fullName: string; birthDate: string } | null };
const root = "/api/auth/identity-document";
const button = "rounded-xl border px-4 py-2 disabled:opacity-50 hover:bg-purple-50";
export default function IdentityReview() {
  const { user } = useAuth();
  const allowed = user?.role === "STAFF" || user?.role === "ADMIN";
  const [items, setItems] = useState<QueueItem[]>([]), [detail, setDetail] = useState<Detail | null>(null);
  const [page, setPage] = useState(0), [busy, setBusy] = useState(false), [error, setError] = useState("");
  const [checks, setChecks] = useState([false, false, false]), [reference, setReference] = useState("");
  const selection = useRef(0);
  const load = useCallback(async () => {
    if (!allowed) return;
    try { setItems((await api.get<QueueItem[]>(`${root}/pending?page=${page}`, { cache: "no-store" })).data); }
    catch (e) { setError(getErrorMessage(e)); }
  }, [allowed, page]);
  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    const clear = () => { if (document.hidden) { selection.current++; setDetail(null); setChecks([false,false,false]); setReference(""); } };
    document.addEventListener("visibilitychange", clear);
    return () => { selection.current++; document.removeEventListener("visibilitychange", clear); };
  }, []);
  async function open(id: string) {
    const selected = ++selection.current;
    setDetail(null); setChecks([false,false,false]); setReference(""); setBusy(true); setError("");
    try { const data = (await api.get<Detail>(`${root}/requests/${id}`, { cache: "no-store" })).data;
      if (selection.current === selected && !document.hidden) setDetail(data);
    } catch (e) { setError(getErrorMessage(e)); }
    finally { setBusy(false); }
  }
  async function review(accepted: boolean) {
    if (!detail || busy) return;
    setBusy(true); setError("");
    try {
      await api.post(`${root}/requests/${detail.check.id}/review`, { accepted,
        originalSeen: checks[0], holderMatched: checks[1], informationMatched: checks[2], reference: reference.trim() });
      selection.current++; setDetail(null); setReference(""); setChecks([false,false,false]); await load();
    } catch (e) { setError(getErrorMessage(e)); }
    finally { setBusy(false); }
  }
  if (!allowed) return <AccessDenied requiredRoleLabel="Nhân viên tiếp nhận hoặc quản trị viên" />;
  return <div className="mx-auto max-w-5xl space-y-5">
    <h1 className="text-2xl font-semibold">Đối chiếu danh tính trực tiếp</h1>
    <p>Yêu cầu do bệnh nhân gửi sau khi đọc giấy tờ trên điện thoại. Không có ảnh căn cước hoặc selfie. Chỉ xác nhận khi người bệnh có mặt cùng giấy tờ bản gốc; kết quả không tự liên kết bệnh án.</p>
    {error && <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-700">{error}</p>}
    <button className={button} disabled={busy} onClick={() => { setError(""); void load(); }}>Tải lại hàng chờ</button>
    <div className="overflow-x-auto rounded-xl border bg-white"><table className="w-full text-left">
      <thead><tr><th className="p-3">Tài khoản</th><th>Căn cước</th><th>Thời điểm gửi</th><th>Thao tác</th></tr></thead>
      <tbody>{items.map(item => <tr key={item.check.id} className="border-t"><td className="p-3">{item.username}</td><td>{item.check.maskedDocumentNumber}</td><td>{new Date(item.check.createdAt).toLocaleString("vi-VN")}</td><td><button className={button} disabled={busy} onClick={() => void open(item.check.id)}>Đối chiếu</button></td></tr>)}</tbody>
    </table>{items.length === 0 && <p className="p-5">Không có yêu cầu chờ ở trang này.</p>}</div>
    <div className="flex items-center gap-4"><button className={button} disabled={busy || page === 0} onClick={() => { selection.current++; setDetail(null); setPage(page-1); }}>Trang trước</button><span>Trang {page+1}</span><button className={button} disabled={busy || items.length < 25} onClick={() => { selection.current++; setDetail(null); setPage(page+1); }}>Trang sau</button></div>
    {detail && <section className="space-y-4 rounded-xl border bg-white p-5">
      <h2 className="text-lg font-semibold">Kiểm tra với giấy tờ gốc</h2>
      {detail.fields ? <>
        <dl className="grid gap-3 sm:grid-cols-3"><div><dt>Số căn cước</dt><dd className="font-semibold">{detail.fields.documentNumber}</dd></div><div><dt>Họ tên</dt><dd className="font-semibold">{detail.fields.fullName}</dd></div><div><dt>Ngày sinh</dt><dd className="font-semibold">{detail.fields.birthDate}</dd></div></dl>
        {["Đã xem căn cước bản gốc tại quầy", "Người xuất trình khớp ảnh trên giấy tờ", "Số căn cước, họ tên và ngày sinh đều khớp"].map((label,index) => <label key={label} className="flex items-center gap-3"><input type="checkbox" disabled={busy} checked={checks[index]} onChange={e => setChecks(checks.map((v,i) => i === index ? e.target.checked : v))} />{label}</label>)}
        <label className="block">Mã quầy hoặc mã biên bản (không nhập dữ liệu cá nhân)<input className="mt-2 block w-full rounded-xl border p-3" aria-label="Mã đối chiếu" disabled={busy} maxLength={80} value={reference} onChange={e => setReference(e.target.value)} placeholder="VD: DESK-01" /></label>
        <p>Thông tin chi tiết được xóa sau quyết định; chỉ giữ số đã che và nhật ký người đối chiếu. Từ chối khi thông tin không khớp, không xác nhận thay cho người bệnh vắng mặt.</p>
        <div className="flex flex-wrap gap-3"><button className={button + " bg-purple-700 text-white hover:bg-purple-800"} disabled={busy || !checks.every(Boolean) || !/^[A-Za-z0-9][A-Za-z0-9 ._:/-]{2,79}$/.test(reference.trim())} onClick={() => void review(true)}>Xác nhận đã đối chiếu</button><button className={button} disabled={busy || !/^[A-Za-z0-9][A-Za-z0-9 ._:/-]{2,79}$/.test(reference.trim())} onClick={() => void review(false)}>Từ chối do không khớp</button></div>
      </> : <p>Yêu cầu đã kết thúc hoặc hết hạn. Hãy tải lại hàng chờ.</p>}
      <button className={button} disabled={busy} onClick={() => { selection.current++; setDetail(null); setReference(""); }}>Đóng thông tin</button>
    </section>}
  </div>;
}
