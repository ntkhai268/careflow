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

export interface EmrSummaryResponse {
  patient: PatientResponse;
  medicalRecord: {
    id: string;
    recordNumber: string;
    bloodType: string;
    medicalHistory: string;
  };
  allergies: PatientAllergyResponse[];
  recentConsultations: any[];
  recentPrescriptions: any[];
  recentLabOrders: any[];
}

export const patientApi = {
  getPatientById: (id: string) => 
    api.get<PatientResponse>(`/api/patients/${id}`),

  getPatientByUserId: (userId: string) => 
    api.get<PatientResponse>(`/api/patients/user/${userId}`),
};

export const emrApi = {
  getPatientSummary: (patientId: string) =>
    api.get<EmrSummaryResponse>(`/api/emr/patients/${patientId}/summary`),
};

