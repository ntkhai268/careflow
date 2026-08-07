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

export interface PatientOperationalResponse {
  id: string;
  fullName: string;
  dateOfBirth?: string;
  gender?: string;
}

export interface EmrSummaryResponse {
  patient: PatientResponse;
  medicalRecord: {
    id: string;
    recordNumber: string;
    bloodType: string;
    medicalHistory: string;
  };
  allergies: PatientAllergyResponse[];
  recentConsultations: unknown[];
  recentPrescriptions: unknown[];
  recentLabOrders: unknown[];
}

export const patientApi = {
  getPatientById: (id: string) => 
    api.get<PatientResponse>(`/api/patients/${id}`),

  getOperationalSummary: (patientId: string, appointmentId: string, roomId: string) =>
    api.get<PatientOperationalResponse>(
      `/api/patients/${patientId}/operational-summary?appointmentId=${encodeURIComponent(appointmentId)}&roomId=${encodeURIComponent(roomId)}`
    ),

  getPatientByUserId: (userId: string) => 
    api.get<PatientResponse>(`/api/patients/user/${userId}`),
};

export const emrApi = {
  getPatientSummary: (patientId: string) =>
    api.get<EmrSummaryResponse>(`/api/emr/patients/${patientId}/summary`),
};

