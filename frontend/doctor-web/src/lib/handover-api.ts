import { api } from "./api";
import type { DoctorProfileItem } from "./directory-api";
export interface HandoverCase { id: string; patientId: string; patientName?: string; appointmentId?: string; doctorId: string; doctorName?: string; status: string }
export interface ClinicalHandover {
  id: string; consultationId: string; patientId: string; patientName?: string; fromDoctorId: string; toDoctorId: string;
  initiatedBy: string; fromDoctorName: string; toDoctorName: string; roomId: string;
  reason: string; situation: string; background: string; assessment: string; recommendation: string;
  exceptional: boolean; status: "PENDING" | "ACCEPTED" | "REJECTED" | "CANCELLED";
  createdAt: string; decidedAt?: string; decisionNote?: string;
}
export interface HandoverWorkspace { coordinator: boolean; doctors: DoctorProfileItem[]; consultations: HandoverCase[]; handovers: ClinicalHandover[] }
export interface CoordinatorGrant { doctorId: string; active: boolean; grantedAt: string; grantedBy: string; grantReason: string; revokedAt?: string; revokeReason?: string }
const base = "/api/consultations/handovers";
export const handoverApi = {
  workspace: () => api.get<HandoverWorkspace>(base),
  request: (data: { consultationId: string; toDoctorId: string; reason: string; situation: string; background: string; assessment: string; recommendation: string }) => api.post<ClinicalHandover>(base, data),
  decide: (id: string, action: "accept" | "reject" | "cancel", note: string) => api.post<ClinicalHandover>(`${base}/${id}/${action}`, { note }),
  grants: () => api.get<CoordinatorGrant[]>(`${base}/coordinators`),
  grant: (doctorId: string, reason: string) => api.post<CoordinatorGrant>(`${base}/coordinators`, { doctorId, reason }),
  revoke: (doctorId: string, note: string) => api.post<CoordinatorGrant>(`${base}/coordinators/${doctorId}/revoke`, { note }),
};
