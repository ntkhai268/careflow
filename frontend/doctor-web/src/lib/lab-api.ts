import { api } from "./api";

export interface LabCatalogItem {
  serviceCode: string;
  serviceName: string;
  servicePointId: string;
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
  resultFlag?: string;
  comment?: string;
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
  updatedAt?: string;
}

export const labApi = {
  getCatalog: () => api.get<LabCatalogItem[]>("/api/labs/services"),
  createOrder: (data: CreateLabOrderRequest) =>
    api.post<LabOrderResponse>("/api/labs/orders", data),

  getByConsultation: (consultationId: string) =>
    api.get<LabOrderResponse[]>(`/api/labs/orders/consultation/${consultationId}`),

  getById: (orderId: string) =>
    api.get<LabOrderResponse>(`/api/labs/orders/${orderId}`),

  startOrder: (orderId: string) =>
    api.post<LabOrderResponse>(`/api/labs/orders/${orderId}/start`, {}),

  submitItemResult: (orderId: string, itemId: string, data: {
    resultValue: string;
    referenceRange?: string;
    unit?: string;
    resultFlag?: string;
    comment?: string;
  }) =>
    api.put<LabOrderItemResponse>(`/api/labs/orders/${orderId}/items/${itemId}/result`, data),

  synchronizeReview: (orderId: string) => api.post<LabOrderResponse>(`/api/labs/orders/${orderId}/synchronize-review`, {}),

  markReviewed: (orderId: string) => api.post<LabOrderResponse>(`/api/labs/orders/${orderId}/mark-reviewed`, {}),

  finalizeOrder: (orderId: string) =>
    api.post<LabOrderResponse>(`/api/labs/orders/${orderId}/finalize`, {}),
};
