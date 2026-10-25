import { api } from "./api";
export interface FollowUpPlan { date: string; department: string; timeSlot: string }
export interface FollowUpAppointment { id: string; sourceConsultationId: string; patientId: string; appointmentDate: string; timeSlot: string; department: string; doctorName?: string; roomDisplayName?: string; status: string }
export interface FollowUpSlot { timeSlot: string; remaining: number; available: boolean }
export const followUpApi = {
  slots: (department: string, date: string, doctorId: string, signal?: AbortSignal) => api.get<FollowUpSlot[]>(`/api/appointments/time-slots/availability?${new URLSearchParams({ department, date, doctorId })}`, { signal, cache: "no-store" }),
  list: (consultationId: string, signal?: AbortSignal) => api.get<FollowUpAppointment[]>(`/api/appointments/follow-ups/consultations/${encodeURIComponent(consultationId)}`, { signal, cache: "no-store" }),
  create: (consultationId: string, patientId: string, plan: FollowUpPlan) => api.post<FollowUpAppointment>("/api/appointments/follow-ups", { consultationId, patientId, department: plan.department, recommendedDate: plan.date, timeSlot: plan.timeSlot }, { headers: { "Idempotency-Key": `follow-up-${consultationId}-${plan.date}` } }),
};
export const hospitalToday = () => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
