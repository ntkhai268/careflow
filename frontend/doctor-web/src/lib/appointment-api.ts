import { api } from "./api";

export interface AppointmentResponse {
  id: string;
  patientId: string;
  patientName?: string;
  department: string;
  departmentDisplayName?: string;
  doctorId?: string;
  doctorName?: string;
  appointmentDate: string;
  timeSlot: string;
  status: string;
  statusDisplayName?: string;
  reason?: string;
  notes?: string;
  queueNumber?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ClinicalContextRoom {
  roomId: string;
  roomDisplayName: string;
}

export interface ClinicalContextResponse {
  userId: string;
  doctorId: string;
  doctorName: string;
  department: string;
  departmentDisplayName?: string;
  rooms: ClinicalContextRoom[];
}

export const appointmentApi = {
  getClinicalContext: (date?: string, session?: string) => {
    const params = new URLSearchParams();
    if (date) params.set("date", date);
    if (session) params.set("session", session);
    const query = params.toString();
    return api.get<ClinicalContextResponse>(
      `/api/appointments/clinical-context/me${query ? `?${query}` : ""}`
    );
  },

  getAppointmentsByPatient: (patientId: string) => 
    api.get<AppointmentResponse[]>(`/api/appointments/patient/${patientId}`),

  getAppointmentsByDepartment: (department: string, date: string) => 
    api.get<AppointmentResponse[]>(`/api/appointments/department/${department}?date=${date}`),

  getAppointmentById: (id: string) => 
    api.get<AppointmentResponse>(`/api/appointments/${id}`),

  updateStatus: (id: string, status: string, notes?: string) =>
    api.put<AppointmentResponse>(`/api/appointments/${id}/status`, { status, notes }),
};
