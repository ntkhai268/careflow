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

export const appointmentApi = {
  getAppointmentsByPatient: (patientId: string) => 
    api.get<AppointmentResponse[]>(`/api/appointments/patient/${patientId}`),

  getAppointmentsByDepartment: (department: string, date: string) => 
    api.get<AppointmentResponse[]>(`/api/appointments/department/${department}?date=${date}`),

  getAppointmentById: (id: string) => 
    api.get<AppointmentResponse>(`/api/appointments/${id}`),

  updateStatus: (id: string, status: string, notes?: string) =>
    api.put<AppointmentResponse>(`/api/appointments/${id}/status`, { status, notes }),
};
