import { api } from "./api";

export interface CreateConsultationRequest {
  appointmentId: string;
  patientId: string;
  doctorId: string;
}

export interface UpdateConsultationRequest {
  temperature?: number;
  bloodPressure?: string;
  heartRate?: number;
  spo2?: number;
  height?: number;
  weight?: number;
  symptoms?: string;
  clinicalNotes?: string;
  icd10Code?: string;
  icd10Name?: string;
  diagnosis?: string;
}

export interface ConsultationResponse {
  id: string;
  appointmentId?: string;
  patientId: string;
  doctorId: string;
  temperature?: number;
  bloodPressure?: string;
  heartRate?: number;
  spo2?: number;
  height?: number;
  weight?: number;
  symptoms?: string;
  clinicalNotes?: string;
  icd10Code?: string;
  icd10Name?: string;
  diagnosis?: string;
  status: "IN_PROGRESS" | "AWAITING_CLS" | "AWAITING_REVIEW" | "READY_TO_COMPLETE" | "COMPLETED" | "CANCELLED" | "TRANSFERRED";
  startedAt: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export const consultationApi = {
  createConsultation: (data: CreateConsultationRequest) => 
    api.post<ConsultationResponse>("/api/consultations", data),
    
  getConsultation: (id: string) => 
    api.get<ConsultationResponse>(`/api/consultations/${id}`),
    
  updateConsultation: (id: string, data: UpdateConsultationRequest) => 
    api.put<ConsultationResponse>(`/api/consultations/${id}`, data),
    
  updateStatus: (id: string, status: string) =>
    api.put<ConsultationResponse>(`/api/consultations/${id}/status`, { status }),

  updateClinicalData: (id: string, data: UpdateConsultationRequest) =>
    api.put<ConsultationResponse>(`/api/consultations/${id}/clinical-data`, data),

  waitForResults: (id: string) =>
    api.post<ConsultationResponse>(`/api/consultations/${id}/wait-for-results`, {}),

  resume: (id: string) =>
    api.post<ConsultationResponse>(`/api/consultations/${id}/resume`, {}),

  completeConsultation: (id: string) => 
    api.post<ConsultationResponse>(`/api/consultations/${id}/complete`, {}),
    
  getByPatient: (patientId: string) => 
    api.get<ConsultationResponse[]>(`/api/consultations/patient/${patientId}`),
    
  getByDoctor: (doctorId: string) => 
    api.get<ConsultationResponse[]>(`/api/consultations/doctor/${doctorId}`),
    
  getTodayByDoctor: (doctorId: string) => 
    api.get<ConsultationResponse[]>(`/api/consultations/doctor/${doctorId}/today`),
};
