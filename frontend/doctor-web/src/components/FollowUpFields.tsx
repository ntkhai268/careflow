"use client";
import { useEffect, useRef, useState } from "react";
import HandoverPicker from "@/components/ui/HandoverPicker";
import { appointmentApi, ClinicalContextResponse } from "@/lib/appointment-api";
import { followUpApi, FollowUpPlan, FollowUpSlot, hospitalToday } from "@/lib/follow-up-api";
import { getErrorMessage } from "@/lib/error-utils";

export default function FollowUpFields({ doctorId, value, onChange, disabled = false }: { doctorId: string; value: FollowUpPlan; onChange: (value: FollowUpPlan) => void; disabled?: boolean }) {
  const [context, setContext] = useState<ClinicalContextResponse | null>(null), [slots, setSlots] = useState<FollowUpSlot[] | null>(null);
  const [error, setError] = useState<string | null>(null), [retry, setRetry] = useState(0);
  const latest = useRef({ value, onChange }); latest.current = { value, onChange };
  useEffect(() => {
    let active = true; setContext(null); setError(null);
    void appointmentApi.getClinicalContext().then(result => {
      if (!active) return;
      if (result.data.userId !== doctorId || !result.data.department || !result.data.rooms.length) throw new Error("Chưa có phân công phòng hợp lệ để hẹn tái khám.");
      setContext(result.data);
      const { value: current, onChange: update } = latest.current;
      if (current.department !== result.data.department) update({ ...current, department: result.data.department, timeSlot: "" });
    }).catch(failure => { if (active) setError(getErrorMessage(failure, "Chưa tải được phân công bác sĩ.")); });
    return () => { active = false; };
  }, [doctorId, retry]);
  useEffect(() => {
    setSlots(null); if (!context || !value.date || context.department !== value.department) return;
    const request = new AbortController(); setError(null);
    void followUpApi.slots(context.department, value.date, doctorId, request.signal).then(result => {
      if (!Array.isArray(result.data)) throw new Error("Chưa tải được danh sách ca tái khám.");
      if (!request.signal.aborted) setSlots(result.data);
    }).catch(failure => { if (!request.signal.aborted) setError(getErrorMessage(failure, "Không tải được ca còn chỗ.")); });
    return () => request.abort();
  }, [context, value.date, value.department, doctorId, retry]);
  return <fieldset disabled={disabled} className="space-y-3 disabled:opacity-60">
    <label className="block text-xs font-semibold">Ngày tái khám (bỏ trống nếu không hẹn)<input type="date" min={hospitalToday()} value={value.date} onChange={event => onChange({ ...value, date: event.target.value, timeSlot: "" })} className="mt-1 w-full rounded-lg border px-3 py-2 text-sm" /></label>
    {value.date && <>
      <HandoverPicker label="Khoa tái khám theo phân công bác sĩ" value={value.department} options={context ? [{ value: context.department, label: context.departmentDisplayName || context.department }] : []} onChange={department => onChange({ ...value, department, timeSlot: "" })} />
      <HandoverPicker label="Ca tái khám còn chỗ" value={value.timeSlot} options={(slots || []).filter(slot => slot.available).map(slot => ({ value: slot.timeSlot, label: `${slot.timeSlot} · còn ${slot.remaining} chỗ` }))} onChange={timeSlot => onChange({ ...value, timeSlot })} />
      {!error && slots === null && <p role="status" className="text-xs">Đang tải ca theo lịch phòng…</p>}
      {slots && !slots.some(slot => slot.available) && <p className="text-xs text-amber-800">Ngày này không còn ca phù hợp. Chọn ngày khác.</p>}
    </>}
    {error && <div role="alert" className="text-sm text-red-700">{error}<button type="button" className="ml-2 underline" onClick={() => setRetry(count => count + 1)}>Thử lại</button></div>}
  </fieldset>;
}
