import { api } from "./api";

export interface PrescriptionItemRequest {
  medicineName: string;
  medicineCode: string | null;
  unit: string;
  dosage: string | null;
  frequency: string | null;
  timing: string | null;
  duration: number | null;
  quantity: number | null;
  notes?: string;
  details?: Record<string,string>;
}

export interface CreatePrescriptionRequest {
  consultationId: string;
  patientId: string;
  doctorId: string;
  diagnosis: string;
  notes?: string;
  followUpDate?: string;
  followUpDepartment?: string;
  followUpTimeSlot?: string; // YYYY-MM-DD format
  items: PrescriptionItemRequest[];
}

export interface AmendPrescriptionRequest {
  diagnosis: string;
  notes?: string;
  followUpDate?: string;
  followUpDepartment?: string;
  followUpTimeSlot?: string;
  items: PrescriptionItemRequest[];
}

export interface PrescriptionItemResponse {
  id: string;
  medicineName: string;
  medicineCode: string | null;
  unit: string;
  dosage: string | null;
  frequency: string | null;
  timing: string | null;
  duration: number | null;
  quantity: number | null;
  notes?: string;
  details?: Record<string,string>;
  createdAt: string;
}

export interface PrescriptionResponse {
  id: string;
  consultationId: string;
  patientId: string;
  doctorId: string;
  diagnosis: string;
  notes?: string;
  followUpDate?: string;
  followUpDepartment?: string;
  followUpTimeSlot?: string;
  status: "DRAFT" | "CONFIRMED" | "DISPENSED" | "CANCELLED" | "CANCELLED_BY_AMENDMENT";
  followUpStatus?: "NOT_REQUESTED" | "NEEDS_SELECTION" | "PENDING" | "FAILED" | "SCHEDULED";
  followUpAppointmentId?: string;
  followUpErrorCode?: string;
  dispensingServicePointId?: string;
  confirmedAt?: string;
  dispensedAt?: string;
  dispensedByUserId?: string;
  replacesPrescriptionId?: string;
  cancellationReason?: string;
  cancelledAt?: string;
  cancelledByUserId?: string;
  items: PrescriptionItemResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface MedicineCatalogItem {
  code: string;
  name: string;
  unit: string;
  description: string;
}

export const prescriptionApi = {
  createPrescription: (data: CreatePrescriptionRequest) => 
    api.post<PrescriptionResponse>("/api/prescriptions", data),
    
  getPrescription: (id: string) => 
    api.get<PrescriptionResponse>(`/api/prescriptions/${id}`),
    
  updatePrescription: (id: string, data: CreatePrescriptionRequest) => 
    api.put<PrescriptionResponse>(`/api/prescriptions/${id}`, data),
    
  confirmPrescription: (id: string) =>
    api.post<PrescriptionResponse>(`/api/prescriptions/${id}/release`, {}),

  retryFollowUp: (id: string) => api.post<PrescriptionResponse>(`/api/prescriptions/${id}/follow-up/retry`, {}),

  dispensePrescription: (id: string) =>
    api.post<PrescriptionResponse>(`/api/prescriptions/${id}/dispense`, {}),

  cancelPrescription: (id: string, reason: string) =>
    api.post<PrescriptionResponse>(`/api/prescriptions/${id}/cancel`, { reason }),

  amendPrescription: (id: string, data: AmendPrescriptionRequest) =>
    api.post<PrescriptionResponse>(`/api/prescriptions/${id}/amendments`, data),
    
  getByConsultation: (consultationId: string) => 
    api.get<PrescriptionResponse[]>(`/api/prescriptions/consultation/${consultationId}`),
    
  getByPatient: (patientId: string) => 
    api.get<PrescriptionResponse[]>(`/api/prescriptions/patient/${patientId}`),
    
  getMedicineCatalog: () => 
    api.get<MedicineCatalogItem[]>("/api/prescriptions/medicines"),
};
