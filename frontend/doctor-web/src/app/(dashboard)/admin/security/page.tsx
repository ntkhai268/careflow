"use client";

import {useEffect,useState} from "react";
import Link from "next/link";
import {api} from "@/lib/api";
interface Staff {id:string;username:string;email:string;role:string;status:string;passkey_count:number}
interface MailStatus {webhookConfigured:boolean;pendingTooLong:number;counts:{state:string;delivery_status:string;count:number}[]}
export default function AdminSecurityPage() {
  const [staff,setStaff]=useState<Staff[]>([]),[query,setQuery]=useState("");
  const [mail,setMail]=useState<MailStatus>();
  const [selected,setSelected]=useState<Staff|null>(null),[reference,setReference]=useState("");
  const [recover,setRecover]=useState(false),[confirmed,setConfirmed]=useState(false),[busy,setBusy]=useState(false);
  const [activation,setActivation]=useState("");
  const [error,setError]=useState("");
  async function load(search=query) {
    setError("");
    try {
      const [accounts,delivery]=await Promise.all([
        api.get<Staff[]>(`/api/auth/staff-passkeys/accounts?query=${encodeURIComponent(search)}`),
        api.get<MailStatus>("/api/auth/mail/operations")]);
      setStaff(accounts.data);setMail(delivery.data);
    } catch(e) {setError(e instanceof Error?e.message:"Không tải được dữ liệu.");}
  }
  useEffect(()=>{void load("");},[]);
  async function issue(event:React.FormEvent) {
    event.preventDefault();if(!selected||!confirmed)return;setBusy(true);setError("");
    try {
      const response=await api.post<{activationCode:string}>("/api/auth/staff-passkeys/grants",{userId:selected.id,recover,identityCheckReference:reference.trim()});
      setActivation(response.data.activationCode);await load();
    } catch(e) {setError(e instanceof Error?e.message:"Không cấp được quyền.");}
    finally {setBusy(false);}
  }
  function close() {setSelected(null);setActivation("");setReference("");setConfirmed(false);setRecover(false);}
  return <div className="relative z-10 space-y-6 p-4 md:p-6">
    <h1 className="text-2xl font-bold">Bảo mật tài khoản nội bộ</h1>
    <p className="max-w-3xl text-sm leading-6 text-slate-600">Quản trị viên phải đăng nhập bằng passkey trong vòng 5 phút trước khi cấp quyền. Kiểm tra danh tính nhân viên theo quy trình của cơ sở y tế; ghi mã phiếu hoặc căn cứ kiểm tra, không ghi số giấy tờ nhạy cảm.</p>
    <div className="flex flex-wrap gap-4 text-sm font-semibold text-purple-800"><Link href="/account/security">Passkey của tôi</Link><Link href="/login/methods">Đăng nhập lại bằng passkey</Link><Link href="/staff-enrollment">Trang thiết lập cho nhân viên</Link></div>
    {error&&<p role="alert" className="rounded-xl bg-red-50 p-4 text-red-800">{error}</p>}
    <form onSubmit={e=>{e.preventDefault();void load();}} className="flex gap-3"><input aria-label="Tìm nhân viên" className="min-w-0 flex-1 rounded-xl border bg-white px-4 py-3" placeholder="Tìm tài khoản hoặc email nhân viên" value={query} maxLength={100} onChange={e=>setQuery(e.target.value)}/><button className="rounded-xl bg-purple-800 px-5 py-3 text-white">Tìm kiếm</button></form>
    <div className="overflow-x-auto rounded-xl border bg-white"><table className="w-full text-left text-sm"><thead><tr className="border-b bg-purple-50"><th className="p-4">Tài khoản</th><th className="p-4">Email</th><th className="p-4">Vai trò</th><th className="p-4">Passkey</th><th className="p-4">Thao tác</th></tr></thead><tbody>{staff.map(user=><tr className="border-b last:border-0" key={user.id}><td className="p-4 font-semibold">{user.username}</td><td className="p-4">{user.email}</td><td className="p-4">{user.role}</td><td className="p-4">{user.passkey_count}</td><td className="p-4"><button disabled={user.status!=="ACTIVE"} className="rounded-lg bg-purple-50 px-4 py-2 font-semibold text-purple-800 disabled:opacity-40" onClick={()=>{setSelected(user);setRecover(user.passkey_count>0);setActivation("");setError("");}}>Cấp / khôi phục passkey</button></td></tr>)}</tbody></table>{!staff.length&&<p className="p-4 text-slate-500">Không có tài khoản phù hợp.</p>}</div>
    {mail&&<section className="rounded-xl border bg-white p-5"><h2 className="font-semibold">Tình trạng email bảo mật</h2><p className="mt-2 text-sm">{mail.webhookConfigured?"Đã cấu hình xác thực webhook; kiểm tra sự kiện nhận được để đối chiếu giao thư.":"Chưa cấu hình webhook: mới theo dõi được việc Resend chấp nhận gửi, chưa xác nhận thư tới hộp nhận."}</p><p className="mt-2 text-sm">Email chờ quá 2 phút: {mail.pendingTooLong}</p><ul className="mt-3 text-sm">{mail.counts.map(row=><li key={row.state+row.delivery_status}>{row.state} / {row.delivery_status}: {row.count}</li>)}</ul></section>}
    {selected&&<div role="dialog" aria-modal="true" aria-labelledby="grant-title" className="fixed inset-0 z-50 grid place-items-center bg-slate-950/40 p-4"><div className="max-h-[90dvh] w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-6 shadow-lg"><h2 id="grant-title" className="text-xl font-semibold">Passkey cho {selected.username}</h2>{activation?<><p className="mt-4 text-sm leading-6">Mã cấp quyền đã được xếp gửi tới email nhân viên. Trao riêng mã kích hoạt dưới đây sau khi kiểm tra danh tính; mã chỉ hiện ở lần này và hết hạn sau 15 phút.</p><p className="mt-4 break-all rounded-xl bg-purple-50 p-4 font-mono" aria-label="Mã kích hoạt một lần">{activation}</p><button className="mt-5 rounded-lg bg-purple-800 px-5 py-3 text-white" onClick={close}>Đã trao mã, đóng</button></>:<form onSubmit={issue} className="mt-4 space-y-4"><label className="block text-sm font-semibold">Căn cứ kiểm tra danh tính<input className="mt-2 w-full rounded-xl border px-4 py-3" required minLength={8} maxLength={300} value={reference} onChange={e=>setReference(e.target.value)} placeholder="Ví dụ: mã phiếu hỗ trợ, đối chiếu trực tiếp"/></label><label className="flex gap-3 text-sm"><input type="checkbox" checked={recover} onChange={e=>setRecover(e.target.checked)}/>Khôi phục do mất khóa — thu hồi các passkey và phiên cũ ngay khi cấp quyền.</label><label className="flex gap-3 text-sm"><input type="checkbox" required checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/>Tôi đã kiểm tra đúng nhân viên và sẽ trao mã kích hoạt qua kênh riêng.</label>{error&&<p role="alert" className="text-sm text-red-800">{error}</p>}<div className="flex gap-3"><button type="button" disabled={busy} className="rounded-lg border px-4 py-3" onClick={close}>Hủy</button><button disabled={busy||!confirmed} className="rounded-lg bg-purple-800 px-5 py-3 text-white">{busy?"Đang cấp quyền…":"Cấp quyền và gửi email"}</button></div></form>}</div></div>}
  </div>;
}
