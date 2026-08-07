import { api } from "./api";

export interface PrescriptionItemRequest {
  medicineName: string;
  medicineCode: string;
  unit: string;
  dosage: string;
  frequency: string;
  timing: string;
  duration: number;
  quantity: number;
  notes?: string;
}

export interface CreatePrescriptionRequest {
  consultationId: string;
  patientId: string;
  doctorId: string;
  diagnosis: string;
  notes?: string;
  followUpDate?: string; // YYYY-MM-DD format
  items: PrescriptionItemRequest[];
}

export interface AmendPrescriptionRequest {
  diagnosis: string;
  notes?: string;
  followUpDate?: string;
  items: PrescriptionItemRequest[];
}

export interface PrescriptionItemResponse {
  id: string;
  medicineName: string;
  medicineCode: string;
  unit: string;
  dosage: string;
  frequency: string;
  timing: string;
  duration: number;
  quantity: number;
  notes?: string;
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
  status: "DRAFT" | "CONFIRMED" | "DISPENSED" | "CANCELLED" | "CANCELLED_BY_AMENDMENT";
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
    api.post<PrescriptionResponse>(`/api/prescriptions/${id}/confirm`, {}),

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
