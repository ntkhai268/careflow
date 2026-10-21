"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/contexts/AuthContext";
import { api } from "@/lib/api";
import { passkeyResponse } from "@/lib/passkey";
import { getDefaultRouteForRole } from "@/lib/role-utils";
import AuthShell, { AuthIcon, authInput, authPrimary } from "./AuthShell";
import StaffPasskeySettings from "./StaffPasskeySettings";

const questions = [
  ["memory", "Kỷ niệm riêng nào bạn luôn nhớ?"],
  ["phrase", "Câu nói bí mật do bạn tự đặt là gì?"],
  ["place", "Tên địa điểm đặc biệt chỉ bạn biết là gì?"],
];
interface Settings { questionId: string; passkeys: {id: string; created_at: string}[] }
interface Options { requestId: string; options: string }

function SecretField({label, value, onChange, autoComplete = "off", minLength = 1}: {label: string; value: string; onChange: (value: string) => void; autoComplete?: string; minLength?: number}) {
  const [visible, setVisible] = useState(false);
  return <label className="block text-sm font-semibold">{label}<span className="relative block">
    <input className={`${authInput} pr-16 font-normal`} type={visible ? "text" : "password"} aria-label={label} value={value} onChange={e => onChange(e.target.value)} required minLength={minLength} maxLength={72} autoComplete={autoComplete}/>
    <button type="button" aria-label={`${visible ? 'Ẩn' : 'Hiện'} ${label.toLowerCase()}`} aria-pressed={visible} onClick={() => setVisible(!visible)} className="absolute bottom-0 right-1 min-h-12 rounded-lg px-3 text-xs font-semibold text-[#702983] hover:bg-purple-50 focus-visible:outline-2 focus-visible:outline-purple-700">{visible ? "Ẩn" : "Hiện"}</button>
  </span></label>;
}

function QuestionPicker({value, onChange}: {value: string; onChange: (id: string) => void}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    function outside(event: MouseEvent) { if (!ref.current?.contains(event.target as Node)) setOpen(false); }
    document.addEventListener("mousedown", outside);
    return () => document.removeEventListener("mousedown", outside);
  }, []);
  return <div ref={ref} className="relative">
    <span id="question-label" className="text-sm font-semibold">Câu hỏi bí mật</span>
    <button type="button" role="combobox" aria-labelledby="question-label" aria-controls="question-options" aria-expanded={open} onClick={() => setOpen(!open)} onKeyDown={e => {if (e.key === "Escape") setOpen(false); if (e.key === "ArrowDown") { e.preventDefault(); setOpen(true); requestAnimationFrame(() => ref.current?.querySelector<HTMLButtonElement>('[role=option]')?.focus()); }}} className={`${authInput} flex items-center justify-between gap-3 text-left`}>
      <span className="min-w-0">{questions.find(q => q[0] === value)?.[1]}</span><AuthIcon name="arrow" className={`h-4 w-4 shrink-0 transition-transform ${open ? '-rotate-90' : 'rotate-90'}`}/>
    </button>
    {open && <div id="question-options" role="listbox" aria-labelledby="question-label" className="absolute z-20 mt-2 w-full rounded-xl border border-[#E6E0EB] bg-white p-1.5 shadow-lg">
      {questions.map(([id, text], index) => <button type="button" key={id} role="option" aria-selected={id === value} onClick={() => {onChange(id); setOpen(false); ref.current?.querySelector<HTMLButtonElement>('[role=combobox]')?.focus();}} onKeyDown={e => {if (e.key === "Escape") {setOpen(false); ref.current?.querySelector<HTMLButtonElement>('[role=combobox]')?.focus();} if (e.key === "ArrowDown" || e.key === "ArrowUp") {e.preventDefault(); const items=ref.current?.querySelectorAll<HTMLButtonElement>('[role=option]'); items?.[(index + (e.key === "ArrowDown" ? 1 : 2)) % 3]?.focus();}}} className={`flex min-h-12 w-full items-center justify-between gap-3 rounded-lg px-3 py-2.5 text-left text-sm hover:bg-purple-50 focus-visible:bg-purple-50 focus-visible:outline-2 focus-visible:outline-purple-600 ${id === value ? 'bg-purple-50 text-purple-900' : ''}`}>{text}{id === value && <AuthIcon name="check" className="h-4 w-4 shrink-0"/>}</button>)}
    </div>}
  </div>;
}

export default function LoginMethodsForm({configure = false, recovery = false}: {configure?: boolean; recovery?: boolean}) {
  const { user, isAuthenticated, isLoading, loginAlternative, logout } = useAuth();
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [questionId, setQuestion] = useState("memory");
  const [answer, setAnswer] = useState("");
  const [method, setMethod] = useState("passkey");
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [recovered, setRecovered] = useState(false);
  const [settings, setSettings] = useState<Settings>();
  const [removeId, setRemoveId] = useState<string | null>(null);
  const dialog = useRef<HTMLDialogElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const load = useCallback(async () => {
    try { const r = await api.get<Settings>("/api/auth/login-methods"); setSettings(r.data); if (r.data.questionId) setQuestion(r.data.questionId); }
    catch (e) { setError(e instanceof Error ? e.message : "Không tải được cấu hình đăng nhập."); }
  }, []);
  useEffect(() => {
    if (configure && !isLoading) {
      if (!isAuthenticated) router.replace("/login");
      else void load();
    }
  }, [configure, isLoading, isAuthenticated, router, load]);
  useEffect(() => { if (removeId) dialog.current?.showModal(); else dialog.current?.close(); }, [removeId]);
  useEffect(() => { if (editing || recovered) heading.current?.focus(); }, [editing, recovered]);
  function choose(id: string) { setMethod(id); setEditing(true); setError(""); setMessage(""); setPassword(""); setAnswer(""); setConfirmation(""); }
  function back() { setEditing(false); setError(""); setPassword(""); setAnswer(""); setConfirmation(""); }
  async function submit(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError(""); setMessage("");
    try {
      if (recovery && password !== confirmation) throw new Error("Mật khẩu mới không khớp xác nhận.");
      if (configure) {
        if (method === "question") await api.post("/api/auth/login-methods/question", {password, questionId, answer});
        else {
          const start = await api.post<Options>("/api/auth/passkeys/register/options", {password});
          const credential = await passkeyResponse(start.data.options, true);
          await api.post("/api/auth/passkeys/register/finish", {requestId: start.data.requestId, credential});
        }
        setPassword(""); setAnswer(""); await load(); setEditing(false); setMessage(method === "question" ? "Đã lưu câu hỏi bí mật." : "Đã thêm passkey cho tài khoản.");
      } else if (recovery) {
        if (method === "question") await api.post("/api/auth/recovery/question", {usernameOrEmail: username, questionId, answer, newPassword: password});
        else {
          const start = await api.post<Options>("/api/auth/recovery/passkey/options", {usernameOrEmail: username});
          const credential = await passkeyResponse(start.data.options);
          await api.post("/api/auth/recovery/passkey/finish", {requestId: start.data.requestId, credential, newPassword: password});
        }
        setPassword(""); setConfirmation(""); setAnswer(""); setRecovered(true);
      } else await loginAlternative(method, username, questionId, answer);
    } catch (e) { setError(e instanceof Error ? e.message : "Không thực hiện được. Hãy thử lại."); }
    finally { setBusy(false); }
  }
  async function remove(id: string) {
    setBusy(true); setError("");
    try { await api.post("/api/auth/passkeys/remove", {credentialId: id, password}); await logout(); }
    catch (e) { setError(e instanceof Error ? e.message : "Không gỡ được passkey."); }
    finally { setBusy(false); }
  }
  if (configure && user?.role && user.role !== "PATIENT") return <StaffPasskeySettings />;
  const passkey = method === "passkey";
  const title = recovered ? "Đã đổi mật khẩu" : !editing ? configure ? "Bảo mật đăng nhập" : recovery ? "Quên mật khẩu?" : "Cách đăng nhập" : configure ? passkey ? "Thiết lập passkey" : "Thiết lập câu hỏi bí mật" : recovery ? "Khôi phục tài khoản" : passkey ? "Đăng nhập bằng passkey" : "Đăng nhập bằng câu hỏi";
  const description = recovered ? "Mật khẩu mới đã sẵn sàng. Các phiên đăng nhập cũ đã được đăng xuất." : !editing ? configure ? "Thiết lập và quản lý các cách truy cập tài khoản." : recovery ? "Chọn phương thức bạn đã thiết lập để đặt lại mật khẩu." : "Chọn phương thức đã thiết lập cho tài khoản của bạn." : passkey ? "Xác nhận bằng vân tay, khuôn mặt hoặc mã khóa trên thiết bị của bạn." : configure ? "Chọn một câu hỏi và câu trả lời riêng mà bạn có thể ghi nhớ." : "Chọn đúng câu hỏi và nhập câu trả lời bạn đã lưu.";
  return <AuthShell backHref={configure ? getDefaultRouteForRole(user?.role) : "/login"} onBack={editing && !recovered ? () => {if (!busy) back();} : undefined} backLabel={editing && !recovered ? "Chọn phương thức khác" : configure ? "Về trang chính" : "Quay lại đăng nhập"}>
    {configure && (isLoading || !isAuthenticated) ? <p role="status" className="py-8 text-center text-sm text-[#6E6B7F]">Đang xác minh phiên đăng nhập…</p> : <>
      {!configure && !recovered && <p className="mb-5 text-xs font-medium text-[#6E6B7F]">{editing ? "02" : "01"}<span className="mx-2 text-[#C3BBCB]">/</span>02 · {editing ? "Thông tin xác thực" : "Chọn phương thức"}</p>}
      <span className={`mb-5 flex h-14 w-14 items-center justify-center rounded-2xl ${recovered ? 'bg-[#E7F4ED] text-[#28744E]' : 'bg-[#F2EAF5] text-[#702983]'}`}><AuthIcon name={recovered ? "check" : editing ? passkey ? "passkey" : "question" : configure ? "shield" : recovery ? "lock" : "passkey"} className="h-7 w-7"/></span>
      <h1 ref={heading} tabIndex={-1} className="text-[26px] font-semibold leading-tight tracking-[-0.6px] outline-none">{title}</h1>
      <p className="mt-3 text-sm leading-6 text-[#686477]">{description}</p>
      {message && <p role="status" className="mt-5 flex items-center gap-2 rounded-xl bg-[#EAF5EE] p-3 text-sm text-[#286744]"><AuthIcon name="check" className="h-4 w-4 shrink-0"/>{message}</p>}
      {error && !removeId && <div role="alert" className="mt-5 rounded-xl border border-red-100 bg-red-50 p-3 text-sm leading-6 text-red-800">{error}{configure && !settings && <button type="button" onClick={() => {setError(""); void load();}} className="ml-2 font-semibold underline">Thử lại</button>}</div>}
      {recovered ? <Link href="/login" className={`${authPrimary} mt-7`}>Đăng nhập lại<AuthIcon name="arrow" className="h-4 w-4"/></Link> : !editing ? <>
        <div className="mt-7 space-y-3">
          {[["passkey", "Vân tay / Khuôn mặt", "Đăng nhập bằng passkey trên thiết bị."]].map(([id, label, text]) => <button key={id} type="button" disabled={configure && !settings} onClick={() => choose(id)} className="group flex min-h-24 w-full items-center gap-4 rounded-2xl border border-[#E4E0E9] p-4 text-left transition-colors hover:border-[#AE88BC] hover:bg-[#FCFAFD] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#702983] disabled:opacity-60">
            <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-[#F5F1F8] text-[#702983]"><AuthIcon name={id === "passkey" ? "passkey" : "question"} className="h-6 w-6"/></span>
            <span className="min-w-0 flex-1"><span className="block text-sm font-semibold">{label}</span><span className="mt-1 block text-xs leading-5 text-[#686477]">{configure ? !settings ? "Đang tải trạng thái…" : id === "passkey" ? settings.passkeys.length ? `${settings.passkeys.length} passkey đã thiết lập` : "Chưa thiết lập passkey" : settings.questionId ? "Đã thiết lập câu hỏi" : "Chưa thiết lập câu hỏi" : text}</span></span>
            <AuthIcon name="arrow" className="h-4 w-4 shrink-0 text-[#8B7B95] group-hover:text-[#702983]"/>
          </button>)}
        </div>
        {configure && settings && <p className="mt-5 flex items-start gap-2 text-xs leading-5 text-[#686477]"><AuthIcon name="lock" className="mt-0.5 h-4 w-4 shrink-0"/>Bạn cần mật khẩu hiện tại để thay đổi phương thức.</p>}
        {recovery && <p className="mt-6 border-t border-[#EFECF2] pt-5 text-xs leading-6 text-[#686477]">Chưa thiết lập phương thức khôi phục? Liên hệ bộ phận hỗ trợ để được xác minh tài khoản.</p>}
      </> : <form onSubmit={submit} className="mt-7 space-y-5" aria-busy={busy}>
        <fieldset disabled={busy} className="space-y-5 disabled:opacity-70">
          {!configure && <label className="block text-sm font-semibold">Tên đăng nhập hoặc email<input autoFocus className={`${authInput} font-normal`} value={username} onChange={e => setUsername(e.target.value)} required maxLength={255} autoComplete="username" placeholder="Nhập tài khoản của bạn"/></label>}
          {passkey ? <div className="rounded-xl bg-[#F7F5FA] p-4 text-sm leading-6 text-[#61586D]"><span className="mb-1 block font-semibold text-[#45354F]">Thiết bị sẽ yêu cầu xác nhận</span>Thao tác trong hộp thoại của trình duyệt khi bạn tiếp tục. Cách xác nhận tùy thiết bị.</div> : <><QuestionPicker value={questionId} onChange={setQuestion}/><SecretField label="Câu trả lời bí mật" value={answer} onChange={setAnswer} minLength={configure ? 8 : 1}/>{configure && <p className="-mt-2 text-xs leading-5 text-[#686477]">Dùng câu trả lời dài, khó đoán và không công khai. Từ 8–72 ký tự.</p>}</>}
          {(configure || recovery) && <SecretField label={configure ? "Mật khẩu hiện tại" : "Mật khẩu mới"} value={password} onChange={setPassword} minLength={recovery ? 8 : 1} autoComplete={configure ? "current-password" : "new-password"}/>}
          {recovery && <><SecretField label="Xác nhận mật khẩu mới" value={confirmation} onChange={setConfirmation} autoComplete="new-password"/><p className="-mt-2 text-xs leading-5 text-[#686477]">8–72 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.</p></>}
        </fieldset>
        <button disabled={busy} className={authPrimary}>{busy ? <><span aria-hidden="true" className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white"/>Đang xử lý…</> : <>{configure ? passkey ? "Thêm passkey" : "Lưu câu hỏi bí mật" : recovery ? "Đặt lại mật khẩu" : passkey ? "Tiếp tục với passkey" : "Đăng nhập"}<AuthIcon name="arrow" className="h-4 w-4"/></>}</button>
      </form>}
      {configure && editing && passkey && settings && <div className="mt-7 border-t border-[#EFECF2] pt-5"><h2 className="text-xs font-semibold uppercase tracking-wide text-[#686477]">Passkey của bạn</h2>{settings.passkeys.length ? settings.passkeys.map((p, index) => <div key={p.id} className="mt-3 flex items-center justify-between gap-3 rounded-xl border border-[#E8E3EC] p-3"><span className="flex items-center gap-3"><AuthIcon name="passkey"/><span className="text-sm font-medium">Passkey {index + 1}<span className="mt-1 block text-xs font-normal text-[#686477]">Đã thêm {new Date(p.created_at).toLocaleDateString("vi-VN")}</span></span></span><button disabled={busy} onClick={() => {setPassword(""); setError(""); setRemoveId(p.id);}} className="min-h-10 rounded-lg px-3 text-xs font-semibold text-red-700 hover:bg-red-50">Gỡ</button></div>) : <p className="mt-3 text-sm text-[#686477]">Chưa có passkey. Thêm khóa đầu tiên để đăng nhập trên thiết bị.</p>}</div>}
      {!configure && !recovered && <Link href="/login" className="mt-6 block min-h-10 pt-2 text-center text-sm font-semibold text-[#702983] hover:underline">Đăng nhập bằng mật khẩu</Link>}
    </>}
    <dialog ref={dialog} aria-labelledby="remove-title" onCancel={e => {if (busy) e.preventDefault(); else {setRemoveId(null); setPassword(""); setError("");}}} className="m-auto w-[calc(100%_-_32px)] max-w-md rounded-2xl border-0 p-6 text-[#25233A] shadow-xl backdrop:bg-[#24162E]/40">
      {removeId && <><h2 id="remove-title" className="text-xl font-semibold">Gỡ passkey này?</h2><p className="mt-3 text-sm leading-6 text-[#686477]">Khóa này sẽ không còn dùng để đăng nhập. Mọi phiên hiện tại sẽ được đăng xuất.</p>
      <form onSubmit={e => {e.preventDefault(); if (removeId) void remove(removeId);}} className="mt-5 space-y-4"><SecretField label="Mật khẩu hiện tại" value={password} onChange={setPassword} autoComplete="current-password"/>{error && <p role="alert" className="text-sm text-red-800">{error}</p>}<div className="flex gap-3"><button type="button" disabled={busy} onClick={() => {setRemoveId(null); setPassword(""); setError("");}} className="min-h-12 flex-1 rounded-xl border border-[#DCDAE5] text-sm font-semibold">Giữ passkey</button><button disabled={busy} className="min-h-12 flex-1 rounded-xl bg-red-700 px-3 text-sm font-semibold text-white disabled:opacity-60">{busy ? "Đang xử lý…" : "Gỡ passkey"}</button></div></form></>}
    </dialog>
  </AuthShell>;
}
