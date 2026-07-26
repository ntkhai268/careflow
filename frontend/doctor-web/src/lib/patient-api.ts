import { api } from "./api";

export interface PatientResponse {
  id: string;
  userId?: string;
  fullName: string;
  dateOfBirth?: string;
  gender?: string;
  phone?: string;
  idCardNumber?: string;
  insuranceNumber?: string;
  address?: string;
  avatarUrl?: string;
}

export const patientApi = {
  getPatientById: (id: string) => 
    api.get<PatientResponse>(`/api/patients/${id}`),

  getPatientByUserId: (userId: string) => 
    api.get<PatientResponse>(`/api/patients/user/${userId}`),
};
