"use client";

import { useState } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import { passkeyResponse } from "@/lib/passkey";
import AuthShell, { authInput, authPrimary } from "@/components/AuthShell";

export default function StaffEnrollmentPage() {
  const [mailCode,setMailCode]=useState("");
  const [activation,setActivation]=useState("");
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState("");
  const [done,setDone]=useState(false);
  async function submit(event:React.FormEvent) {
    event.preventDefault();setError("");setBusy(true);
    try {
      const [grantId,emailCode,...extra]=mailCode.trim().split(".");
      if(!grantId || !emailCode || extra.length || !activation.trim()) throw new Error("Nhập đầy đủ mã trong email và mã kích hoạt quản trị viên đã trao.");
      const response=await api.post<{requestId:string;options:string}>("/api/auth/staff-passkeys/enroll/options",{grantId,emailCode,activationCode:activation.trim()});
      const credential=await passkeyResponse(response.data.options,true);
      await api.post("/api/auth/staff-passkeys/enroll/finish",{requestId:response.data.requestId,credential});
      setMailCode("");setActivation("");setDone(true);
    } catch(e) { setError(e instanceof Error?e.message:"Chưa thiết lập được passkey."); }
    finally { setBusy(false); }
  }
  return <AuthShell backHref="/login" backLabel="Về đăng nhập">
    <h1 className="text-2xl font-semibold">Thiết lập passkey nhân viên</h1>
    <p className="mt-3 text-sm leading-6 text-slate-600">Chỉ tiếp tục sau khi quản trị viên đã kiểm tra danh tính. Bạn cần mã trong email và mã kích hoạt được quản trị viên trao riêng. Mã hết hạn sau 15 phút.</p>
    {done?<div className="mt-6"><p role="status" className="rounded-xl bg-green-50 p-4 text-green-800">Đã thiết lập passkey. Bạn có thể đăng nhập bằng khóa vừa tạo.</p><Link className={`${authPrimary} mt-5`} href="/login/methods">Đăng nhập bằng passkey</Link></div>:
      <form onSubmit={submit} className="mt-6 space-y-5">
        <label className="block text-sm font-semibold">Mã cấp quyền trong email<input className={authInput} value={mailCode} onChange={e=>setMailCode(e.target.value)} type="password" autoComplete="off" required maxLength={100} disabled={busy}/></label>
        <label className="block text-sm font-semibold">Mã kích hoạt từ quản trị viên<input className={authInput} value={activation} onChange={e=>setActivation(e.target.value)} type="password" autoComplete="off" required maxLength={43} disabled={busy}/></label>
        {error&&<p role="alert" className="text-sm text-red-800">{error}</p>}
        <button className={authPrimary} disabled={busy}>{busy?"Đang xác minh…":"Thiết lập passkey trên thiết bị này"}</button>
      </form>}
  </AuthShell>;
}
