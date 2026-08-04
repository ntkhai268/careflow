import { api } from "./api";

export interface LabCatalogItem {
  serviceCode: string;
  serviceName: string;
  category: "HEMATOLOGY" | "BIOCHEMISTRY" | "ULTRASOUND" | "XRAY" | "OTHER";
  servicePointId: string;
  servicePointName: string;
  price: number;
  preparationInstruction?: string;
}

export interface LabOrderItemRequest {
  serviceCode: string;
  serviceName: string;
  servicePointId: string;
  required: boolean;
  preparationInstruction?: string;
}

export interface CreateLabOrderRequest {
  consultationId: string;
  patientId: string;
  items: LabOrderItemRequest[];
  clinicalNote?: string;
  paymentRequired?: boolean;
}

export interface LabOrderItemResponse {
  id: string;
  serviceCode: string;
  serviceName: string;
  servicePointId: string;
  status: string;
  resultValue?: string;
  referenceRange?: string;
  unit?: string;
}

export interface LabOrderResponse {
  id: string;
  consultationId: string;
  patientId: string;
  orderedByDoctorId: string;
  status: "ORDERED" | "PAYMENT_PENDING" | "QUEUED" | "CALLED" | "IN_PROGRESS" | "RESULT_AVAILABLE" | "REVIEWED" | "CANCELLED";
  clinicalNote?: string;
  items: LabOrderItemResponse[];
  createdAt: string;
}

// Catalog tĩnh dịch vụ Cận lâm sàng cho Doctor Web chọn
export const MOCK_LAB_SERVICES: LabCatalogItem[] = [
  {
    serviceCode: "CBC",
    serviceName: "Công thức máu toàn phần (CBC)",
    category: "HEMATOLOGY",
    servicePointId: "LAB-HEMATOLOGY-01",
    servicePointName: "Phòng Xét nghiệm Huyết học 101",
    price: 120000,
    preparationInstruction: "Không cần nhịn ăn",
  },
  {
    serviceCode: "GLUCOSE",
    serviceName: "Định lượng Glucose máu",
    category: "BIOCHEMISTRY",
    servicePointId: "LAB-BIOCHEM-01",
    servicePointName: "Phòng Xét nghiệm Sinh hóa 102",
    price: 60000,
    preparationInstruction: "Nhịn ăn sáng ít nhất 8 tiếng",
  },
  {
    serviceCode: "US-ABD-GEN",
    serviceName: "Siêu âm bụng tổng quát",
    category: "ULTRASOUND",
    servicePointId: "US-ROOM-01",
    servicePointName: "Phòng Siêu âm 201",
    price: 250000,
    preparationInstruction: "Uống nhiều nước, nhịn tiểu",
  },
  {
    serviceCode: "XRAY-CHEST-AP",
    serviceName: "X-Quang Ngực thẳng (Chest AP/PA)",
    category: "XRAY",
    servicePointId: "XRAY-ROOM-01",
    servicePointName: "Phòng X-Quang 202",
    price: 180000,
    preparationInstruction: "Cởi bỏ đồ trang sức kim loại vùng ngực",
  },
];

export const labApi = {
  createOrder: (data: CreateLabOrderRequest) =>
    api.post<LabOrderResponse>("/api/labs/orders", data),

  getByConsultation: (consultationId: string) =>
    api.get<LabOrderResponse[]>(`/api/labs/orders/consultation/${consultationId}`),

  getById: (orderId: string) =>
    api.get<LabOrderResponse>(`/api/labs/orders/${orderId}`),
};
