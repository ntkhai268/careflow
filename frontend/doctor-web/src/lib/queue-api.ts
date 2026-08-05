import { api } from "./api";

export interface QueueEntry {
  entryId: string;
  appointmentId: string;
  patientId: string;
  userId: string;
  departmentId: string;
  departmentName: string;
  roomCode: string;
  queueDate: string;
  queueNumber: string;
  priorityLevel: "APPOINTMENT" | "PRIORITY" | "EMERGENCY" | "WALK_IN" | "RESULT_REVIEW";
  queueStatus: "WAITING" | "TICKET_ISSUED" | "CHECKED_IN" | "CALLED" | "IN_PROGRESS" | "COMPLETED" | "MISSED" | "CANCELLED";
  effectivePosition: number | null;
  estimatedWaitMinutes: number | null;
  callAttempts: number;
  missedCount: number;
  calledByUserId: string | null;
  scheduledStartAt: string;
  checkedInAt: string | null;
  calledAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  missedAt: string | null;
}

export interface QueueDashboardResponse {
  departmentId: string;
  departmentName: string;
  roomCode: string;
  queueDate: string;
  entries: QueueEntry[];
  recommendedNext: QueueEntry | null;
}

export interface ServicePointQueueResponse {
  servicePointId: string;
  queueDate: string;
  entries: QueueEntry[];
  recommendedNext: QueueEntry | null;
}

export interface CheckInRequest {
  qrToken: string;
  roomId: string;
  queueClass?: "INITIAL" | "RESULT_REVIEW" | "PRIORITY";
  priorityReasonCode?: string;
}

export const queueApi = {
  getRoomActive: (roomId: string) =>
    api.get<QueueDashboardResponse>(`/api/queues/rooms/${roomId}/active`),

  callNextInRoom: (roomId: string) =>
    api.post<QueueEntry>(`/api/queues/rooms/${roomId}/call-next`, {}),

  getServicePointActive: (servicePointId: string, date?: string) =>
    api.get<ServicePointQueueResponse>(
      `/api/queues/service-points/${servicePointId}/active${date ? `?date=${date}` : ""}`
    ),

  callNextAtServicePoint: (servicePointId: string) =>
    api.post<QueueEntry>(`/api/queues/service-points/${servicePointId}/call-next`, {}),

  checkIn: (data: CheckInRequest) =>
    api.post<QueueEntry>(`/api/queues/check-in`, data),

  callEntry: (entryId: string) =>
    api.post<QueueEntry>(`/api/queues/entries/${entryId}/call`, {}),

  recallEntry: (entryId: string) =>
    api.post<QueueEntry>(`/api/queues/entries/${entryId}/recall`, {}),

  missEntry: (entryId: string) =>
    api.post<QueueEntry>(`/api/queues/entries/${entryId}/miss`, {}),

  startEntry: (entryId: string) =>
    api.post<QueueEntry>(`/api/queues/entries/${entryId}/start`, {}),

  completeEntry: (entryId: string) =>
    api.post<QueueEntry>(`/api/queues/entries/${entryId}/complete`, {}),

  requeueEntry: (entryId: string, reason?: string) =>
    api.post<QueueEntry>(`/api/queues/entries/${entryId}/requeue`, { reason }),

  getQr: (appointmentId: string) =>
    api.get<{ qrToken: string }>(`/api/queues/appointments/${appointmentId}/qr`),
};

