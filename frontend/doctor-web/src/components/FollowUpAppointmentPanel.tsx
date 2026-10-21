"use client";
import { useEffect, useState } from "react";
import FollowUpFields from "./FollowUpFields";
import { followUpApi, FollowUpAppointment, FollowUpPlan } from "@/lib/follow-up-api";
import { getErrorMessage } from "@/lib/error-utils";

export default function FollowUpAppointmentPanel({ consultationId, patientId, doctorId }: { consultationId: string; patientId: string; doctorId: string }) {
  const [plan, setPlan] = useState<FollowUpPlan>({ date: "", department: "", timeSlot: "" });
  const [bookings, setBookings] = useState<FollowUpAppointment[] | null>(null), [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false), [retry, setRetry] = useState(0);
  useEffect(() => {
    const request = new AbortController(); setBookings(null); setError(null);
    void followUpApi.list(consultationId, request.signal).then(result => {
      if (!Array.isArray(result.data) || result.data.some(item => item.patientId !== patientId || item.sourceConsultationId !== consultationId)) throw new Error("Danh sách tái khám không khớp ca nguồn.");
      if (!request.signal.aborted) setBookings(result.data);
    }).catch(failure => { if (!request.signal.aborted) setError(getErrorMessage(failure, "Không tải được lịch tái khám đã tạo; chưa thể xác nhận không có lịch.")); });
    return () => request.abort();
  }, [consultationId, patientId, retry]);
  async function book() {
    if (saving || bookings === null || !plan.date || !plan.department || !plan.timeSlot) return;
    setSaving(true); setError(null);
    try {
      const result = await followUpApi.create(consultationId, patientId, plan);
      if (result.data.status !== "CONFIRMED") throw new Error("Chưa xác nhận được lịch tái khám.");
      setBookings(current => [...(current || []).filter(item => item.id !== result.data.id), result.data]);
    } catch (failure) { setError(getErrorMessage(failure, "Chưa xác nhận tạo lịch. Tải lại danh sách trước khi thử lại cùng yêu cầu.")); }
    finally { setSaving(false); }
  }
  return <section className="rounded-xl border bg-white p-4 space-y-3">
    <h3 className="font-semibold">Hẹn tái khám từ ca khám</h3><p className="text-xs text-gray-500">Có thể hẹn khi không kê thuốc. Lịch được liên kết với ca hiện tại và tạo lượt khám mới.</p>
    {bookings === null && !error && <p role="status">Đang đối chiếu lịch đã tạo…</p>}
    {bookings?.map(item => <p className="rounded-lg bg-purple-50 p-2 text-sm" key={item.id}>{item.appointmentDate} · {item.timeSlot} · {item.roomDisplayName || item.department} · {item.status === "CONFIRMED" ? "Đã xác nhận" : item.status}</p>)}
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
    <button className="rounded-lg border px-3 py-1 text-sm" disabled={saving} onClick={() => setRetry(count => count + 1)}>Tải lại lịch đã tạo</button>
    <FollowUpFields doctorId={doctorId} value={plan} onChange={setPlan} disabled={saving} />
    <button className="rounded-lg bg-purple-800 px-4 py-2 text-white disabled:opacity-50" disabled={saving || bookings === null || !plan.date || !plan.department || !plan.timeSlot || bookings.some(item => item.appointmentDate === plan.date && item.status !== "CANCELLED")} onClick={() => void book()}>{saving ? "Đang xác nhận…" : "Tạo lịch tái khám"}</button>
  </section>;
}
