"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/contexts/AuthContext";
import HandoverPicker from "@/components/ui/HandoverPicker";
import { directoryApi, DepartmentItem, DoctorProfileItem, RoomItem } from "@/lib/directory-api";
import { staffApi, StaffAccount, StaffAssignment, StaffPage, StaffRole, AccountStatus, CreateStaff } from "@/lib/staff-api";
import { getErrorMessage } from "@/lib/error-utils";

const roleLabels: Record<StaffRole,string> = { DOCTOR:"Bác sĩ", STAFF:"Nhân viên tiếp nhận / dược", LAB_TECHNICIAN:"Kỹ thuật viên CLS", ADMIN:"Quản trị viên" };
const statusLabels: Record<AccountStatus,string> = { ACTIVE:"Hoạt động", LOCKED:"Đã khóa", DISABLED:"Vô hiệu hóa" };
interface DirectorySnapshot { departments: DepartmentItem[]; rooms: RoomItem[]; doctors: DoctorProfileItem[]; assignments: StaffAssignment[] }

export default function AdminAccountsPage() {
  const { user } = useAuth();
  const [result,setResult] = useState<StaffPage|null>(null), [loading,setLoading] = useState(true);
  const [query,setQuery] = useState(""), [page,setPage] = useState(0), [roles,setRoles] = useState<StaffRole[]>([]);
  const [directory,setDirectory] = useState<DirectorySnapshot|null>(null), [directoryError,setDirectoryError] = useState("");
  const [error,setError] = useState(""), [notice,setNotice] = useState(""), [busy,setBusy] = useState(false);
  const [creating,setCreating] = useState(false), [form,setForm] = useState<CreateStaff>({ username:"",email:"",displayName:"",role:"DOCTOR" });
  const [confirmation,setConfirmation] = useState<{account:StaffAccount;status:AccountStatus}|null>(null);
  const [selected,setSelected] = useState<StaffAccount|null>(null), [department,setDepartment] = useState(""), [room,setRoom] = useState(""), [license,setLicense] = useState("");
  const requestId = useRef<string|null>(null), loadVersion = useRef(0);

  const loadAccounts = useCallback(async (search:string, nextPage:number) => {
    const version=++loadVersion.current;setLoading(true);setError("");
    try {
      const response=await staffApi.list(search,nextPage);
      if(version===loadVersion.current){setResult(response.data);setPage(nextPage);}
    } catch (failure) {
      if(version===loadVersion.current){setResult(null);setError(getErrorMessage(failure,"Không tải được tài khoản từ Identity."));}
    } finally {if(version===loadVersion.current)setLoading(false);}
  },[]);

  const loadDirectory = useCallback(async () => {
    try {
      const [departments,rooms,doctors,assignments]=await Promise.all([directoryApi.getDepartments(),directoryApi.getRooms(),staffApi.doctors(),staffApi.assignments()]);
      setDirectory({departments,rooms,doctors:doctors.data,assignments:assignments.data});setDirectoryError("");
    } catch (failure) {setDirectory(null);setDirectoryError(getErrorMessage(failure,"Không tải được phân công. Chưa thể xác định tài khoản đã sẵn sàng."));}
  },[]);

  useEffect(()=>{
    void loadAccounts("",0);void loadDirectory();
    void staffApi.roles().then(response=>setRoles(response.data)).catch(failure=>setError(getErrorMessage(failure,"Không tải được vai trò hệ thống.")));
  },[loadAccounts,loadDirectory]);

  function assignmentLabel(account:StaffAccount) {
    if(account.role==="ADMIN")return "Quản trị hệ thống";
    if(!directory)return "Chưa xác định phân công";
    if(account.role==="DOCTOR") {
      const profile=directory.doctors.find(item=>item.userId===account.id && item.isActive && item.assignedRoomId);
      return profile ? directory.rooms.find(item=>item.id===profile.assignedRoomId)?.displayName || profile.assignedRoomId : "Cần hồ sơ / phân công";
    }
    const assigned=directory.assignments.filter(item=>item.userId===account.id && item.isActive);
    return assigned.length ? assigned.map(item=>directory.rooms.find(candidate=>candidate.id===item.roomId)?.displayName || item.roomId).join(", ") : "Cần phân công";
  }

  async function create(event:React.FormEvent) {
    event.preventDefault();setBusy(true);setError("");setNotice("");
    try {
      requestId.current ??= crypto.randomUUID();
      const response=await staffApi.create(form,requestId.current);
      setCreating(false);requestId.current=null;
      setNotice(`Đã tạo tài khoản ${response.data.username} trong Identity. Hoàn thiện phân công và cấp passkey để nhân viên sử dụng.`);
      await loadAccounts(query,0);
      if(response.data.role!=="ADMIN")openAssignment(response.data);
    } catch (failure) {setError(getErrorMessage(failure,"Không tạo được tài khoản. Bạn có thể thử lại hoặc tìm tài khoản đã tạo."));}
    finally {setBusy(false);}
  }

  async function changeStatus() {
    if(!confirmation)return;setBusy(true);setError("");setNotice("");
    try {
      await staffApi.status(confirmation.account.id,confirmation.status);
      setNotice(`Đã cập nhật tài khoản ${confirmation.account.username}: ${statusLabels[confirmation.status]}.`);
      setConfirmation(null);await loadAccounts(query,page);
    } catch (failure) {setError(getErrorMessage(failure,"Máy chủ chưa lưu trạng thái tài khoản."));}
    finally {setBusy(false);}
  }

  function openAssignment(account:StaffAccount) {
    setSelected(account);setError("");
    const profile=directory?.doctors.find(item=>item.userId===account.id);
    setDepartment(profile?.departmentCode || "");setRoom(profile?.assignedRoomId || "");setLicense(profile?.licenseNumber || "");
  }

  async function saveAssignment(event:React.FormEvent) {
    event.preventDefault();if(!selected||!directory)return;
    const allowedRoom=directory.rooms.find(item=>item.id===room && item.departmentCode===department && item.isActive);
    if(!allowedRoom){setError("Chọn khoa và phòng đang hoạt động từ danh mục.");return;}
    setBusy(true);setError("");setNotice("");
    try {
      if(selected.role==="DOCTOR") {
        const previous=directory.doctors.find(item=>item.userId===selected.id);
        const payload={fullName:selected.displayName || previous?.fullName || selected.username,departmentCode:department,assignedRoomId:room,licenseNumber:license,isActive:true};
        if(previous)await directoryApi.updateDoctorProfile(previous.id,payload);
        else await directoryApi.createDoctorProfile({...payload,userId:selected.id});
      } else {
        const previous=directory.assignments.find(item=>item.userId===selected.id && item.roomId===room);
        const payload={userId:selected.id,departmentCode:department,roomId:room,isActive:true};
        if(previous)await staffApi.updateAssignment(previous.id,payload);else await staffApi.createAssignment(payload);
      }
      setNotice(`Đã lưu phân công cho ${selected.username}.`);setSelected(null);await loadDirectory();
    } catch (failure) {
      setError(getErrorMessage(failure,"Tài khoản Identity vẫn được giữ; phân công chưa hoàn tất. Hãy tải lại và tiếp tục."));
      await loadDirectory();
    } finally {setBusy(false);}
  }

  const roomTypes=selected?.role==="DOCTOR"?["CONSULTATION"]:selected?.role==="LAB_TECHNICIAN"?["LAB","IMAGING"]:["CONSULTATION","PHARMACY"];
  const rooms=directory?.rooms.filter(item=>item.isActive && item.departmentCode===department && roomTypes.includes(item.roomType)) || [];
  const fieldClass="w-full rounded-lg border border-card-border bg-white p-3 text-sm";
  const primaryClass="rounded-lg bg-[#6E2582] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50";

  return <div className="space-y-5 pb-12">
    <header className="rounded-xl border border-card-border bg-card-bg p-5 shadow-sm">
      <h1 className="text-2xl font-bold text-[#2B1D30]">Quản lý tài khoản nội bộ</h1>
      <p className="mt-2 text-sm text-[#6A5C70]">Tạo, khóa và mở khóa tài khoản được lưu tại máy chủ. Đăng nhập lại bằng passkey quản trị trong vòng 5 phút trước thao tác.</p>
      <div className="mt-4 flex flex-wrap items-center gap-4">
        <button className={primaryClass} disabled={!roles.length||busy} onClick={()=>{setCreating(true);setSelected(null);setConfirmation(null);setForm({username:"",email:"",displayName:"",role:roles.includes("DOCTOR")?"DOCTOR":roles[0]});requestId.current=crypto.randomUUID();setError("");}}>Thêm tài khoản</button>
        <Link href="/admin/security" className="text-sm font-semibold text-purple-800 underline">Cấp / khôi phục passkey</Link>
        <Link href="/login/methods" className="text-sm text-purple-800 underline">Đăng nhập bằng passkey</Link>
      </div>
    </header>
    {error&&<p role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-800">{error}</p>}
    {notice&&<p role="status" className="rounded-xl bg-green-50 p-4 text-sm text-green-800">{notice}</p>}
    {directoryError&&<div role="alert" className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">{directoryError} <button className="ml-2 underline" onClick={()=>void loadDirectory()}>Tải lại danh mục</button></div>}
    <section className="rounded-xl border border-card-border bg-card-bg p-5">
      <form className="mb-4 flex gap-3" onSubmit={event=>{event.preventDefault();void loadAccounts(query,0);}}>
        <input aria-label="Tìm tài khoản nội bộ" className={fieldClass} maxLength={100} value={query} onChange={event=>setQuery(event.target.value)} placeholder="Tìm tên tài khoản, họ tên hoặc email" />
        <button className={primaryClass} disabled={loading}>Tìm kiếm</button>
      </form>
      {loading?<p role="status">Đang tải tài khoản…</p>:result?<>
        <p className="mb-3 text-sm text-slate-600">{result.total} tài khoản từ Identity</p>
        <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr className="border-b text-slate-600"><th className="p-3">Tài khoản</th><th className="p-3">Email</th><th className="p-3">Vai trò / phân công</th><th className="p-3">Trạng thái</th><th className="p-3">Thao tác</th></tr></thead>
          <tbody>{result.items.map(account=><tr className="border-b last:border-0" key={account.id}>
            <td className="p-3"><p className="font-semibold">{account.displayName || directory?.doctors.find(item=>item.userId===account.id)?.fullName || account.username}</p><p className="text-xs text-slate-500">{account.username}</p></td>
            <td className="p-3">{account.email}</td>
            <td className="p-3"><p>{roleLabels[account.role]}</p><p className="mt-1 text-xs text-slate-600">{assignmentLabel(account)}</p><p className="mt-1 text-xs text-purple-800">{account.passkeyCount>0?`${account.passkeyCount} passkey`:"Chưa có passkey"}</p></td>
            <td className="p-3">{statusLabels[account.status]}</td>
            <td className="p-3"><div className="flex flex-wrap gap-2">
              {account.role!=="ADMIN"&&<button disabled={!directory||busy||account.status!=="ACTIVE"} onClick={()=>openAssignment(account)} className="rounded-lg bg-purple-50 px-3 py-2 text-purple-800 disabled:opacity-40">Phân công</button>}
              <button disabled={busy||account.id===user?.id} onClick={()=>{setConfirmation({account,status:account.status==="ACTIVE"?"LOCKED":"ACTIVE"});setError("");}} className="rounded-lg border px-3 py-2 disabled:opacity-40">{account.status==="ACTIVE"?"Khóa":"Mở khóa"}</button>
              {account.status!=="DISABLED"&&<button disabled={busy||account.id===user?.id} onClick={()=>{setConfirmation({account,status:"DISABLED"});setError("");}} className="rounded-lg border px-3 py-2 text-red-800 disabled:opacity-40">Vô hiệu hóa</button>}
            </div></td>
          </tr>)}</tbody></table></div>
        {!result.items.length&&<p className="p-4 text-sm text-slate-500">Không có tài khoản phù hợp.</p>}
        <div className="mt-4 flex items-center justify-end gap-3"><button disabled={page===0||loading} onClick={()=>void loadAccounts(query,page-1)} className="rounded-lg border px-3 py-2 disabled:opacity-40">Trang trước</button><span className="text-sm">Trang {page+1}</span><button disabled={(page+1)*result.size>=result.total||loading} onClick={()=>void loadAccounts(query,page+1)} className="rounded-lg border px-3 py-2 disabled:opacity-40">Trang sau</button></div>
      </>:<button onClick={()=>void loadAccounts(query,page)} className="rounded-lg border px-4 py-2">Tải lại tài khoản</button>}
    </section>
    {creating&&<div role="dialog" aria-modal="true" aria-labelledby="new-account-title" className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4"><form onSubmit={create} className="max-h-[90vh] w-full max-w-lg space-y-4 overflow-y-auto rounded-xl bg-white p-6 shadow-lg">
      <h2 id="new-account-title" className="text-lg font-bold">Tạo tài khoản nội bộ</h2>
      <label className="block text-sm">Họ và tên<input className={fieldClass} required minLength={2} maxLength={100} value={form.displayName} onChange={event=>setForm({...form,displayName:event.target.value})}/></label>
      <label className="block text-sm">Tên tài khoản<input className={fieldClass} required pattern="[A-Za-z0-9._-]{3,50}" maxLength={50} autoComplete="off" value={form.username} onChange={event=>setForm({...form,username:event.target.value})}/></label>
      <label className="block text-sm">Email<input className={fieldClass} type="email" required maxLength={255} value={form.email} onChange={event=>setForm({...form,email:event.target.value})}/></label>
      <HandoverPicker label="Vai trò" value={form.role} onChange={value=>setForm({...form,role:value as StaffRole})} options={roles.map(value=>({value,label:roleLabels[value]}))}/>
      <p className="text-sm text-slate-600">Tài khoản mới cần được cấp passkey. Không tạo hoặc gửi mật khẩu tạm.</p>
      {error&&<p role="alert" className="text-sm text-red-800">{error}</p>}
      <div className="flex justify-end gap-3"><button type="button" disabled={busy} className="rounded-lg border px-4 py-2" onClick={()=>setCreating(false)}>Hủy</button><button disabled={busy} className={primaryClass}>{busy?"Đang lưu…":"Tạo tài khoản"}</button></div>
    </form></div>}
    {selected&&<div role="dialog" aria-modal="true" aria-labelledby="assignment-title" className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4"><form onSubmit={saveAssignment} className="max-h-[90vh] w-full max-w-lg space-y-4 overflow-y-auto rounded-xl bg-white p-6 shadow-lg">
      <h2 id="assignment-title" className="text-lg font-bold">Phân công cho {selected.username}</h2>
      <p className="text-sm text-slate-600">Tài khoản đã lưu trong Identity. Bạn có thể đóng và tiếp tục phân công sau.</p>
      <HandoverPicker label="Khoa" value={department} onChange={value=>{setDepartment(value);setRoom("");}} options={(directory?.departments || []).filter(item=>item.isActive!==false).map(item=>({value:item.code,label:item.name}))}/>
      <HandoverPicker label="Phòng" value={room} onChange={setRoom} options={rooms.map(item=>({value:item.id,label:item.displayName}))}/>
      {selected.role==="DOCTOR"&&<label className="block text-sm">Số chứng chỉ hành nghề<input className={fieldClass} maxLength={100} value={license} onChange={event=>setLicense(event.target.value)}/></label>}
      {error&&<p role="alert" className="text-sm text-red-800">{error}</p>}
      {directoryError&&<p role="alert" className="text-sm text-amber-900">{directoryError}</p>}
      <div className="flex justify-end gap-3"><button type="button" disabled={busy} className="rounded-lg border px-4 py-2" onClick={()=>setSelected(null)}>Để sau</button><button disabled={busy||!directory||!department||!rooms.some(item=>item.id===room)} className={primaryClass}>{busy?"Đang lưu…":"Lưu phân công"}</button></div>
    </form></div>}
    {confirmation&&<div role="dialog" aria-modal="true" aria-labelledby="status-title" className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4"><div className="w-full max-w-md space-y-4 rounded-xl bg-white p-6 shadow-lg">
      <h2 id="status-title" className="text-lg font-bold">Xác nhận: {statusLabels[confirmation.status]}</h2>
      <p className="text-sm">Cập nhật tài khoản <strong>{confirmation.account.username}</strong> ({confirmation.account.email}). Phiên đăng nhập hiện có sẽ bị thu hồi khi trạng thái thay đổi.</p>
      {error&&<p role="alert" className="text-sm text-red-800">{error}</p>}
      <div className="flex justify-end gap-3"><button disabled={busy} className="rounded-lg border px-4 py-2" onClick={()=>setConfirmation(null)}>Hủy</button><button disabled={busy} className={primaryClass} onClick={()=>void changeStatus()}>{busy?"Đang lưu…":"Xác nhận"}</button></div>
    </div></div>}
  </div>;
}
