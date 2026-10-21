"use client";

import { useEffect,useState } from "react";
import Link from "next/link";
import { useAuth } from "@/contexts/AuthContext";
import { api } from "@/lib/api";
import { passkeyResponse } from "@/lib/passkey";
import AuthShell,{ authPrimary } from "./AuthShell";

interface Key { id:string;created_at:string }
export default function StaffPasskeySettings() {
  const {logout}=useAuth();
  const [keys,setKeys]=useState<Key[]>([]),[busy,setBusy]=useState(false),[error,setError]=useState("");
  const [removeId,setRemoveId]=useState<string|null>(null);
  async function load() {
    try { const response=await api.get<{passkeys:Key[]}>("/api/auth/login-methods");setKeys(response.data.passkeys); }
    catch(e) { setError(e instanceof Error?e.message:"Không tải được passkey."); }
  }
  useEffect(()=>{void load();},[]);
  async function add() {
    setBusy(true);setError("");
    try {
      const response=await api.post<{requestId:string;options:string}>("/api/auth/staff-passkeys/self/options",{});
      const credential=await passkeyResponse(response.data.options,true);
      await api.post("/api/auth/passkeys/register/finish",{requestId:response.data.requestId,credential});await load();
    } catch(e) {setError(e instanceof Error?e.message:"Không thêm được passkey.");}
    finally {setBusy(false);}
  }
  async function remove() {
    setBusy(true);setError("");
    try {await api.post("/api/auth/staff-passkeys/self/remove",{credentialId:removeId});await logout();}
    catch(e) {setError(e instanceof Error?e.message:"Không gỡ được passkey.");}
    finally {setBusy(false);setRemoveId(null);}
  }
  return <AuthShell backHref="/dashboard/general" backLabel="Về trang chính">
    <h1 className="text-2xl font-semibold">Passkey nhân viên</h1>
    <p className="mt-3 text-sm leading-6 text-slate-600">Đăng nhập bằng passkey trong vòng 5 phút trước khi thêm hoặc gỡ khóa. Nên có một khóa dự phòng; hệ thống không cho gỡ khóa cuối cùng.</p>
    <Link href="/login/methods" className="mt-4 inline-block text-sm font-semibold text-purple-800 underline">Đăng nhập lại bằng passkey</Link>
    {error&&<p role="alert" className="mt-4 text-sm text-red-800">{error}</p>}
    <button className={`${authPrimary} mt-5`} disabled={busy} onClick={()=>void add()}>{busy?"Đang xử lý…":"Thêm passkey dự phòng"}</button>
    <ul className="mt-6 space-y-3">{keys.map((key,index)=><li key={key.id} className="flex items-center justify-between gap-3 rounded-xl border p-4"><span>Passkey {index+1}<small className="block text-slate-500">{new Date(key.created_at).toLocaleString("vi-VN")}</small></span><button className="rounded-lg px-3 py-2 text-red-700 disabled:opacity-40" disabled={busy||keys.length<2} onClick={()=>setRemoveId(key.id)}>Gỡ</button></li>)}</ul>
    {removeId&&<div role="dialog" aria-modal="true" aria-labelledby="remove-key-title" className="fixed inset-0 z-50 grid place-items-center bg-black/30 p-4"><div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-lg"><h2 id="remove-key-title" className="font-semibold">Gỡ passkey và kết thúc các phiên hiện tại?</h2><p className="mt-3 text-sm text-slate-600">Bạn sẽ đăng nhập lại bằng khóa còn lại.</p><div className="mt-5 flex gap-3"><button disabled={busy} className="rounded-lg border px-4 py-2" onClick={()=>setRemoveId(null)}>Giữ lại</button><button disabled={busy} className="rounded-lg bg-red-700 px-4 py-2 text-white" onClick={()=>void remove()}>Gỡ passkey</button></div></div></div>}
  </AuthShell>;
}
