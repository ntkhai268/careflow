import { api, request } from "./api";
import type { DoctorProfileItem } from "./directory-api";

export type StaffRole = "DOCTOR" | "STAFF" | "LAB_TECHNICIAN" | "ADMIN";
export type AccountStatus = "ACTIVE" | "LOCKED" | "DISABLED";
export interface StaffAccount {
  id: string; username: string; email: string; displayName: string | null;
  role: StaffRole; status: AccountStatus; passkeyCount: number; lastLoginAt: string | null;
}
export interface StaffAssignment {
  id: string; userId: string; roomId: string; departmentCode: string; isActive: boolean;
}
export interface StaffPage { items: StaffAccount[]; total: number; page: number; size: number }
export interface CreateStaff { username: string; email: string; displayName: string; role: StaffRole }

export const staffApi = {
  roles: () => api.get<StaffRole[]>("/api/users/staff/roles"),
  list: (query: string, page = 0) => api.get<StaffPage>(`/api/users/staff?query=${encodeURIComponent(query)}&page=${page}`),
  create: (body: CreateStaff, key: string) => api.post<StaffAccount>("/api/users/staff", body, { headers: { "Idempotency-Key": key } }),
  status: (id: string, status: AccountStatus) => request<Pick<StaffAccount,"id"|"username"|"email"|"role"|"status"|"lastLoginAt">>(`/api/users/${id}/status`, { method: "PATCH", body: JSON.stringify({ status }) }),
  doctors: () => api.get<DoctorProfileItem[]>("/api/directory/doctors/admin"),
  assignments: () => api.get<StaffAssignment[]>("/api/directory/staff/assignments"),
  createAssignment: (body: Omit<StaffAssignment, "id">) => api.post<StaffAssignment>("/api/directory/staff/assignments", body),
  updateAssignment: (id: string, body: Omit<StaffAssignment, "id">) => api.put<StaffAssignment>(`/api/directory/staff/assignments/${id}`, body),
};
