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

// 25 Thuốc mẫu chuẩn theo Phác đồ điều trị Bộ Y Tế & Dược thư Quốc gia (RAG Knowledge)
export const DEFAULT_MEDICINE_CATALOG: MedicineCatalogItem[] = [
  { code: "MED001", name: "Paracetamol 500mg", unit: "viên", description: "Giảm đau, hạ sốt (Paracetamol)" },
  { code: "MED002", name: "Amoxicillin 500mg", unit: "viên", description: "Kháng sinh nhóm Penicillin" },
  { code: "MED003", name: "Omeprazole 20mg", unit: "viên", description: "Ức chế bơm proton PPI, trào ngược dạ dày (DDI với Clopidogrel)" },
  { code: "MED004", name: "Metformin 500mg", unit: "viên", description: "Thuốc đầu tay điều trị Đái tháo đường type 2 (BYT)" },
  { code: "MED005", name: "Amlodipine 5mg", unit: "viên", description: "Thuốc chẹn kênh Canxi điều trị Tăng huyết áp (BYT)" },
  { code: "MED006", name: "Atorvastatin 10mg", unit: "viên", description: "Statin hạ lipid máu, phòng ngừa biến cố tim mạch" },
  { code: "MED007", name: "Cetirizine 10mg", unit: "viên", description: "Kháng histamin H1 chống dị ứng" },
  { code: "MED008", name: "Ibuprofen 400mg", unit: "viên", description: "Giảm đau, kháng viêm không steroid (NSAID)" },
  { code: "MED009", name: "Vitamin C 500mg", unit: "viên", description: "Bổ sung vitamin C, tăng cường đề kháng" },
  { code: "MED010", name: "Loperamide 2mg", unit: "viên", description: "Chống tiêu chảy cấp" },
  { code: "MED011", name: "Salbutamol 2mg", unit: "viên", description: "Giãn phế quản, cắt cơn hen suyễn" },
  { code: "MED012", name: "Diclofenac 50mg", unit: "viên", description: "Kháng viêm NSAID giảm đau khớp" },
  { code: "MED013", name: "Prednisolone 5mg", unit: "viên", description: "Corticosteroid kháng viêm, ức chế miễn dịch" },
  { code: "MED014", name: "Cephalexin 500mg", unit: "viên", description: "Kháng sinh Cephalosporin thế hệ 1" },
  { code: "MED015", name: "Domperidone 10mg", unit: "viên", description: "Chống nôn, kích thích nhu động ruột" },
  { code: "MED016", name: "Clopidogrel 75mg", unit: "viên", description: "Kháng kết tập tiểu cầu (Nguy cơ DDI tương tác mạnh với Omeprazole)" },
  { code: "MED017", name: "Augmentin 625mg", unit: "viên", description: "Amoxicillin + Acid Clavulanic điều trị Viêm phổi cộng đồng (BYT)" },
  { code: "MED018", name: "Azithromycin 500mg", unit: "viên", description: "Kháng sinh Macrolide hô hấp điều trị Viêm phổi" },
  { code: "MED019", name: "Levofloxacin 500mg", unit: "viên", description: "Kháng sinh Fluoroquinolone hô hấp" },
  { code: "MED020", name: "Losartan 50mg", unit: "viên", description: "Thuốc chẹn thụ thể ARB điều trị Tăng huyết áp" },
  { code: "MED021", name: "Telmisartan 40mg", unit: "viên", description: "Chẹn thụ thể ARB tác dụng kéo dài theo phác đồ BYT" },
  { code: "MED022", name: "Gliclazide MR 30mg", unit: "viên", description: "Sulfonylurea hạ đường huyết theo phác đồ ĐTĐ type 2" },
  { code: "MED023", name: "Dapagliflozin 10mg", unit: "viên", description: "Ức chế SGLT2 bảo vệ tim mạch, thận ở bệnh nhân ĐTĐ type 2" },
  { code: "MED024", name: "Aspirin 81mg", unit: "viên", description: "Aspirin liều thấp phòng ngừa huyết khối tim mạch" },
  { code: "MED025", name: "Esomeprazole 40mg", unit: "viên", description: "Ức chế bơm proton PPI thế hệ mới điều trị viêm loét dạ dày" },
];

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
    
  getMedicineCatalog: async () => {
    try {
      const res = await api.get<MedicineCatalogItem[]>("/api/prescriptions/medicines");
      if (res.data && Array.isArray(res.data) && res.data.length > 0) {
        return res;
      }
      return { data: DEFAULT_MEDICINE_CATALOG, status: 200 };
    } catch {
      return { data: DEFAULT_MEDICINE_CATALOG, status: 200 };
    }
  },
};
