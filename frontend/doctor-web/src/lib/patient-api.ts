import { api } from "./api";

export interface PatientAllergyResponse {
  id: string;
  patientId?: string;
  allergyName: string;
  allergyGroup?: string;
  severity: "CRITICAL" | "WARNING" | "INFO";
  reaction?: string;
  confirmedBy?: string;
}

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
  allergyNotes?: string | null;
  medicalHistory?: string | null;
  allergies?: PatientAllergyResponse[];
}

export const patientApi = {
  getPatientById: (id: string) => 
    api.get<PatientResponse>(`/api/patients/${id}`),

  getPatientByUserId: (userId: string) => 
    api.get<PatientResponse>(`/api/patients/user/${userId}`),
};
